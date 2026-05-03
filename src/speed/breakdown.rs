// src/speed/breakdown.rs

use anyhow::Result;
use crate::db::Database;
use super::{parse_time_secs, haversine_meters};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FactorKind {
    DwellExcess,
    Bunching,
    RunningTimeLoss,
}

impl FactorKind {
    pub fn label(self) -> &'static str {
        match self {
            FactorKind::DwellExcess => "Dwell time at stops",
            FactorKind::Bunching => "Bunching",
            FactorKind::RunningTimeLoss => "Running time loss",
        }
    }

    pub fn color(self) -> &'static str {
        match self {
            FactorKind::DwellExcess => "#e74c3c",
            FactorKind::Bunching => "#27ae60",
            FactorKind::RunningTimeLoss => "#e67e22",
        }
    }
}

pub struct DeficitFactor {
    pub kind: FactorKind,
    pub delta_mps: f64,
    pub from_mps: f64,
    pub to_mps: f64,
    pub detail: String,
}

impl DeficitFactor {
    pub fn delta_kmh(&self) -> f64 { self.delta_mps * 3.6 }
    pub fn from_kmh(&self) -> f64 { self.from_mps * 3.6 }
    pub fn to_kmh(&self) -> f64 { self.to_mps * 3.6 }
}

pub struct SpeedDeficitBreakdown {
    pub scheduled_speed_mps: f64,
    pub actual_speed_mps: f64,
    pub factors: Vec<DeficitFactor>,
    pub unexplained_mps: f64,
}

impl SpeedDeficitBreakdown {
    pub fn scheduled_speed_kmh(&self) -> f64 { self.scheduled_speed_mps * 3.6 }
    pub fn actual_speed_kmh(&self) -> f64 { self.actual_speed_mps * 3.6 }

    pub fn has_deficit(&self) -> bool {
        self.scheduled_speed_mps > self.actual_speed_mps + 0.1
    }

    pub fn chart_json(&self) -> String {
        let mut labels: Vec<serde_json::Value> = vec![serde_json::json!("Scheduled")];
        let mut data: Vec<serde_json::Value> =
            vec![serde_json::json!([0.0, self.scheduled_speed_kmh()])];
        let mut colors: Vec<serde_json::Value> = vec![serde_json::json!("#2980b9")];

        for factor in &self.factors {
            labels.push(serde_json::json!(format!("− {}", factor.kind.label())));
            data.push(serde_json::json!([factor.to_kmh(), factor.from_kmh()]));
            colors.push(serde_json::json!(factor.kind.color()));
        }

        if self.unexplained_mps.abs() > 0.05 {
            let from_kmh = self
                .factors
                .last()
                .map(|f| f.to_kmh())
                .unwrap_or_else(|| self.scheduled_speed_kmh());
            let other_label = if self.unexplained_mps > 0.0 { "+ Other" } else { "− Other" };
            labels.push(serde_json::json!(other_label));
            data.push(serde_json::json!([self.actual_speed_kmh(), from_kmh]));
            colors.push(serde_json::json!("#aaaaaa"));
        }

        labels.push(serde_json::json!("Actual"));
        data.push(serde_json::json!([0.0, self.actual_speed_kmh()]));
        colors.push(serde_json::json!("#e67e22"));

        serde_json::to_string(&serde_json::json!({
            "labels": labels,
            "datasets": [{
                "data": data,
                "backgroundColor": colors,
                "borderWidth": 0,
            }]
        }))
        .unwrap_or_default()
    }
}

pub async fn compute_speed_deficit_breakdown(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    direction_id: i64,
    days: i64,
) -> Result<Option<SpeedDeficitBreakdown>> {
    let (scheduled_res, actual_res, bunching_res) = tokio::join!(
        fetch_scheduled_timings(db, agency_id, route_id, direction_id),
        fetch_actual_timings(db, agency_id, route_id, direction_id, days),
        fetch_bunching_fraction(db, agency_id, route_id, direction_id, days),
    );

    let scheduled = match scheduled_res? {
        Some(s) => s,
        None => return Ok(None),
    };
    let actual = match actual_res? {
        Some(a) => a,
        None => return Ok(None),
    };
    let bunching_fraction = bunching_res.unwrap_or(0.0);

    let d = scheduled.route_distance_m;
    let t_sched = scheduled.scheduled_duration_secs;
    let scheduled_dwell = scheduled.scheduled_dwell_secs;
    let scheduled_running = (t_sched - scheduled_dwell).max(0.0);

    let actual_dwell = actual.avg_dwell_secs;
    let actual_running = (actual.avg_duration_secs - actual_dwell).max(0.0);

    let dwell_excess = (actual_dwell - scheduled_dwell).max(0.0);
    let running_excess = (actual_running - scheduled_running).max(0.0);

    // 15 s is the estimated extra dwell per bunching event (spec default; no traffic API yet)
    let bunching_dwell = (bunching_fraction * 15.0).min(dwell_excess);
    let pure_dwell_excess = dwell_excess - bunching_dwell;

    let scheduled_speed = d / t_sched;
    let speed_after_dwell = d / (t_sched + pure_dwell_excess).max(1.0);
    let speed_after_bunching = d / (t_sched + dwell_excess).max(1.0);
    let speed_after_running = d / (t_sched + dwell_excess + running_excess).max(1.0);
    let actual_speed = d / actual.avg_duration_secs.max(1.0);

    let scheduled_running_nonzero = scheduled_running.max(1.0);

    let factors = vec![
        DeficitFactor {
            kind: FactorKind::DwellExcess,
            delta_mps: speed_after_dwell - scheduled_speed,
            from_mps: scheduled_speed,
            to_mps: speed_after_dwell,
            detail: format!(
                "avg {:.0} s/stop across {} stops",
                actual_dwell / scheduled.num_stops as f64,
                scheduled.num_stops
            ),
        },
        DeficitFactor {
            kind: FactorKind::Bunching,
            delta_mps: speed_after_bunching - speed_after_dwell,
            from_mps: speed_after_dwell,
            to_mps: speed_after_bunching,
            detail: format!("{:.0}% of trips bunched", bunching_fraction * 100.0),
        },
        DeficitFactor {
            kind: FactorKind::RunningTimeLoss,
            delta_mps: speed_after_running - speed_after_bunching,
            from_mps: speed_after_bunching,
            to_mps: speed_after_running,
            detail: format!(
                "running time {:.0}% above schedule",
                running_excess / scheduled_running_nonzero * 100.0
            ),
        },
    ];

    let unexplained_mps = actual_speed - speed_after_running;

    Ok(Some(SpeedDeficitBreakdown {
        scheduled_speed_mps: scheduled_speed,
        actual_speed_mps: actual_speed,
        factors,
        unexplained_mps,
    }))
}

struct ScheduledTimings {
    route_distance_m: f64,
    scheduled_duration_secs: f64,
    scheduled_dwell_secs: f64,
    num_stops: i64,
}

async fn fetch_scheduled_timings(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    direction_id: i64,
) -> Result<Option<ScheduledTimings>> {
    let trip: Option<(String,)> = sqlx::query_as(
        "SELECT trip_id FROM trips
         WHERE agency_id = $1 AND route_id = $2 AND COALESCE(direction_id, 0) = $3
         ORDER BY trip_id LIMIT 1",
    )
    .bind(agency_id)
    .bind(route_id)
    .bind(direction_id)
    .fetch_optional(&db.pool)
    .await?;

    let Some((trip_id,)) = trip else {
        return Ok(None);
    };

    let stops: Vec<(f64, f64, String, String)> = sqlx::query_as(
        "SELECT s.stop_lat, s.stop_lon, ss.arrival_time, ss.departure_time
         FROM scheduled_stops ss
         JOIN stops s ON s.stop_id = ss.stop_id AND s.agency_id = ss.agency_id
         WHERE ss.agency_id = $1 AND ss.trip_id = $2
         ORDER BY ss.stop_sequence",
    )
    .bind(agency_id)
    .bind(&trip_id)
    .fetch_all(&db.pool)
    .await?;

    if stops.len() < 2 {
        return Ok(None);
    }

    let route_distance_m: f64 = stops
        .windows(2)
        .map(|w| haversine_meters(w[0].0, w[0].1, w[1].0, w[1].1))
        .sum();

    if route_distance_m < 1.0 {
        return Ok(None);
    }

    let first_arrival = parse_time_secs(&stops.first().unwrap().2);
    let last_arrival = parse_time_secs(&stops.last().unwrap().2);
    let scheduled_duration_secs = match (first_arrival, last_arrival) {
        (Some(f), Some(l)) if l > f => (l - f) as f64,
        _ => return Ok(None),
    };

    let scheduled_dwell_secs: f64 = stops
        .iter()
        .filter_map(|(_, _, arr, dep)| {
            let a = parse_time_secs(arr)?;
            let d = parse_time_secs(dep)?;
            if d >= a { Some((d - a) as f64) } else { None }
        })
        .sum();

    Ok(Some(ScheduledTimings {
        route_distance_m,
        scheduled_duration_secs,
        scheduled_dwell_secs,
        num_stops: stops.len() as i64,
    }))
}

struct ActualTimings {
    avg_dwell_secs: f64,
    avg_duration_secs: f64,
    #[allow(dead_code)]
    trip_count: i64,
}

#[derive(sqlx::FromRow)]
struct ActualTimingsRow {
    avg_dwell_secs: Option<f64>,
    avg_duration_secs: Option<f64>,
    trip_count: i64,
}

async fn fetch_actual_timings(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    direction_id: i64,
    days: i64,
) -> Result<Option<ActualTimings>> {
    let row: ActualTimingsRow = sqlx::query_as(
        "SELECT
             AVG(total_dwell)::DOUBLE PRECISION        AS avg_dwell_secs,
             AVG(total_duration)::DOUBLE PRECISION     AS avg_duration_secs,
             COUNT(*)::BIGINT                          AS trip_count
         FROM (
             SELECT
                 ste.trip_id,
                 SUM(CASE WHEN ste.dwell_secs >= 0 THEN ste.dwell_secs ELSE 0 END) AS total_dwell,
                 (MAX(ste.arrival_time_unix) - MIN(ste.arrival_time_unix))          AS total_duration
             FROM stop_time_events ste
             JOIN trips t ON t.agency_id = ste.agency_id AND t.trip_id = ste.trip_id
             WHERE ste.agency_id = $1
               AND t.route_id = $2
               AND COALESCE(t.direction_id, 0) = $3
               AND ste.arrival_time_unix IS NOT NULL
               AND ste.arrival_time_unix >
                       EXTRACT(EPOCH FROM NOW() - $4::BIGINT * INTERVAL '1 day')::BIGINT
             GROUP BY ste.trip_id
             HAVING COUNT(*) >= 2
                AND MAX(ste.arrival_time_unix) > MIN(ste.arrival_time_unix)
         ) AS trip_agg",
    )
    .bind(agency_id)
    .bind(route_id)
    .bind(direction_id)
    .bind(days)
    .fetch_one(&db.pool)
    .await?;

    match (row.avg_dwell_secs, row.avg_duration_secs) {
        (Some(dwell), Some(duration)) if row.trip_count > 0 && duration > 0.0 => {
            Ok(Some(ActualTimings {
                avg_dwell_secs: dwell,
                avg_duration_secs: duration,
                trip_count: row.trip_count,
            }))
        }
        _ => Ok(None),
    }
}

#[derive(sqlx::FromRow)]
struct BunchingRow {
    bunched_count: f64,
    total_count: Option<f64>,
}

async fn fetch_bunching_fraction(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    direction_id: i64,
    days: i64,
) -> Result<f64> {
    let row: BunchingRow = sqlx::query_as(
        "WITH route_trips AS (
             SELECT vp.trip_id,
                    vp.stop_sequence,
                    EXTRACT(EPOCH FROM vp.observed_at::TIMESTAMPTZ)::BIGINT AS epoch_secs
             FROM vehicle_positions vp
             JOIN trips t ON t.agency_id = vp.agency_id AND t.trip_id = vp.trip_id
             WHERE vp.agency_id = $1
               AND t.route_id = $2
               AND COALESCE(t.direction_id, 0) = $3
               AND vp.stop_sequence IS NOT NULL
               AND vp.observed_at::TIMESTAMPTZ >= NOW() - $4::BIGINT * INTERVAL '1 day'
         ),
         bunched_trips AS (
             SELECT DISTINCT a.trip_id
             FROM route_trips a
             JOIN route_trips b
                 ON a.stop_sequence = b.stop_sequence
                AND a.trip_id != b.trip_id
                AND ABS(a.epoch_secs - b.epoch_secs) < 60
         )
         SELECT
             COUNT(DISTINCT b.trip_id)::DOUBLE PRECISION    AS bunched_count,
             NULLIF(COUNT(DISTINCT r.trip_id), 0)::DOUBLE PRECISION AS total_count
         FROM route_trips r
         LEFT JOIN bunched_trips b ON b.trip_id = r.trip_id",
    )
    .bind(agency_id)
    .bind(route_id)
    .bind(direction_id)
    .bind(days)
    .fetch_one(&db.pool)
    .await?;

    Ok(match row.total_count {
        Some(total) if total > 0.0 => row.bunched_count / total,
        _ => 0.0,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn deficit_factor_delta_kmh_converts_mps() {
        let f = DeficitFactor {
            kind: FactorKind::DwellExcess,
            delta_mps: -1.0,
            from_mps: 5.0,
            to_mps: 4.0,
            detail: "test".to_string(),
        };
        assert!((f.delta_kmh() - (-3.6)).abs() < 0.001);
        assert!((f.from_kmh() - 18.0).abs() < 0.001);
        assert!((f.to_kmh() - 14.4).abs() < 0.001);
    }

    #[test]
    fn breakdown_has_deficit_true_when_gap_exceeds_threshold() {
        let bd = SpeedDeficitBreakdown {
            scheduled_speed_mps: 6.0,
            actual_speed_mps: 4.5,
            factors: vec![],
            unexplained_mps: 1.5,
        };
        assert!(bd.has_deficit());
    }

    #[test]
    fn breakdown_has_deficit_false_when_gap_below_threshold() {
        let bd = SpeedDeficitBreakdown {
            scheduled_speed_mps: 5.0,
            actual_speed_mps: 4.95,
            factors: vec![],
            unexplained_mps: 0.05,
        };
        assert!(!bd.has_deficit());
    }

    #[test]
    fn breakdown_scheduled_and_actual_kmh_conversion() {
        let bd = SpeedDeficitBreakdown {
            scheduled_speed_mps: 5.0,
            actual_speed_mps: 4.0,
            factors: vec![],
            unexplained_mps: 1.0,
        };
        assert!((bd.scheduled_speed_kmh() - 18.0).abs() < 0.001);
        assert!((bd.actual_speed_kmh() - 14.4).abs() < 0.001);
    }

    #[test]
    fn chart_json_is_valid_json_with_labels_and_datasets() {
        let bd = SpeedDeficitBreakdown {
            scheduled_speed_mps: 5.0,
            actual_speed_mps: 4.0,
            factors: vec![DeficitFactor {
                kind: FactorKind::DwellExcess,
                delta_mps: -0.5,
                from_mps: 5.0,
                to_mps: 4.5,
                detail: "avg 30 s/stop".to_string(),
            }],
            unexplained_mps: 0.5,
        };
        let json = bd.chart_json();
        let v: serde_json::Value = serde_json::from_str(&json).unwrap();
        assert!(v["labels"].is_array());
        assert!(v["datasets"].is_array());
    }

    mod integration {
        use super::*;
        use crate::db::test_utils;

        #[tokio::test]
        async fn fetch_scheduled_timings_returns_distance_and_dwell() {
            let td = test_utils::setup().await;
            let db = &td.db;

            sqlx::query("INSERT INTO routes VALUES ('a0', 'R1', '1', 'Route 1', 3)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T1', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();
            // Two stops ~1112 m apart (0.01° longitude at equator ≈ 1112 m)
            sqlx::query("INSERT INTO stops VALUES ('a0', 'S1', 'A', 0.0, 0.0)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO stops VALUES ('a0', 'S2', 'B', 0.0, 0.01)")
                .execute(&db.pool).await.unwrap();
            // 30 s dwell at S1, 10 min total trip
            sqlx::query("INSERT INTO scheduled_stops VALUES ('a0', 'T1', 'S1', 1, '08:00:00', '08:00:30')")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('a0', 'T1', 'S2', 2, '08:10:00', '08:10:00')")
                .execute(&db.pool).await.unwrap();

            let result = fetch_scheduled_timings(db, "a0", "R1", 0).await.unwrap();
            assert!(result.is_some());
            let t = result.unwrap();
            assert!((t.route_distance_m - 1112.0).abs() < 50.0);
            assert!((t.scheduled_dwell_secs - 30.0).abs() < 1.0);
            assert!((t.scheduled_duration_secs - 600.0).abs() < 1.0);
            assert_eq!(t.num_stops, 2);
        }

        #[tokio::test]
        async fn fetch_scheduled_timings_returns_none_for_unknown_route() {
            let td = test_utils::setup().await;
            let result = fetch_scheduled_timings(&td.db, "x", "NONE", 0).await.unwrap();
            assert!(result.is_none());
        }

        #[tokio::test]
        async fn fetch_actual_timings_averages_dwell_and_duration_across_trips() {
            let td = test_utils::setup().await;
            let db = &td.db;

            sqlx::query("INSERT INTO routes VALUES ('a0', 'R1', '1', 'Route 1', 3)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T1', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T2', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();

            // T1: two stop events, arrival_time_unix span = 300s, dwell = 60s
            // T2: two stop events, arrival_time_unix span = 240s, dwell = 40s
            // avg duration = 270s, avg dwell = 50s
            let now_epoch: i64 = chrono::Utc::now().timestamp();
            let obs = chrono::Utc::now().to_rfc3339();

            sqlx::query(
                "INSERT INTO stop_time_events (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix) VALUES ($1,$2,$3,'S1',1,$4,$5)"
            )
            .bind("a0").bind(&obs).bind("T1").bind(now_epoch).bind(now_epoch + 60)
            .execute(&db.pool).await.unwrap();

            sqlx::query(
                "INSERT INTO stop_time_events (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix) VALUES ($1,$2,$3,'S2',2,$4,$5)"
            )
            .bind("a0").bind(&obs).bind("T1").bind(now_epoch + 300).bind(now_epoch + 300)
            .execute(&db.pool).await.unwrap();

            sqlx::query(
                "INSERT INTO stop_time_events (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix) VALUES ($1,$2,$3,'S1',1,$4,$5)"
            )
            .bind("a0").bind(&obs).bind("T2").bind(now_epoch).bind(now_epoch + 40)
            .execute(&db.pool).await.unwrap();

            sqlx::query(
                "INSERT INTO stop_time_events (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix) VALUES ($1,$2,$3,'S2',2,$4,$5)"
            )
            .bind("a0").bind(&obs).bind("T2").bind(now_epoch + 240).bind(now_epoch + 240)
            .execute(&db.pool).await.unwrap();

            let result = fetch_actual_timings(db, "a0", "R1", 0, 1).await.unwrap();
            assert!(result.is_some());
            let t = result.unwrap();
            assert!((t.avg_dwell_secs - 50.0).abs() < 5.0);
            assert!((t.avg_duration_secs - 270.0).abs() < 5.0);
            assert_eq!(t.trip_count, 2);
        }

        #[tokio::test]
        async fn fetch_actual_timings_returns_none_when_no_data() {
            let td = test_utils::setup().await;
            let result = fetch_actual_timings(&td.db, "x", "NONE", 0, 28).await.unwrap();
            assert!(result.is_none());
        }

        #[tokio::test]
        async fn fetch_bunching_fraction_detects_co_located_trips() {
            let td = test_utils::setup().await;
            let db = &td.db;

            sqlx::query("INSERT INTO routes VALUES ('a0', 'R1', '1', 'Route 1', 3)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T1', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T2', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();

            // Two vehicles at stop_sequence 1 within 30 seconds of each other
            let t1 = chrono::Utc::now().to_rfc3339();
            let t2 = (chrono::Utc::now() + chrono::TimeDelta::seconds(30)).to_rfc3339();

            sqlx::query(
                "INSERT INTO vehicle_positions (agency_id, observed_at, trip_id, vehicle_id, latitude, longitude, stop_sequence) VALUES ($1,$2,$3,$4,0,0,1)"
            )
            .bind("a0").bind(&t1).bind("T1").bind("V1")
            .execute(&db.pool).await.unwrap();

            sqlx::query(
                "INSERT INTO vehicle_positions (agency_id, observed_at, trip_id, vehicle_id, latitude, longitude, stop_sequence) VALUES ($1,$2,$3,$4,0,0,1)"
            )
            .bind("a0").bind(&t2).bind("T2").bind("V2")
            .execute(&db.pool).await.unwrap();

            let fraction = fetch_bunching_fraction(db, "a0", "R1", 0, 1).await.unwrap();
            assert!(fraction > 0.0, "expected bunching > 0, got {fraction}");
            assert!(fraction <= 1.0);
        }

        #[tokio::test]
        async fn fetch_bunching_fraction_returns_zero_when_no_vehicle_data() {
            let td = test_utils::setup().await;
            let fraction = fetch_bunching_fraction(&td.db, "x", "NONE", 0, 28).await.unwrap();
            assert_eq!(fraction, 0.0);
        }

        #[tokio::test]
        async fn fetch_bunching_fraction_returns_zero_when_vehicles_far_apart_in_time() {
            let td = test_utils::setup().await;
            let db = &td.db;

            sqlx::query("INSERT INTO routes VALUES ('a0', 'R1', '1', 'Route 1', 3)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T1', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T2', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();

            // Two vehicles at same stop but 5 minutes apart — not bunched
            let t1 = chrono::Utc::now().to_rfc3339();
            let t2 = (chrono::Utc::now() + chrono::TimeDelta::seconds(300)).to_rfc3339();

            sqlx::query(
                "INSERT INTO vehicle_positions (agency_id, observed_at, trip_id, vehicle_id, latitude, longitude, stop_sequence) VALUES ($1,$2,$3,$4,0,0,1)"
            )
            .bind("a0").bind(&t1).bind("T1").bind("V1")
            .execute(&db.pool).await.unwrap();

            sqlx::query(
                "INSERT INTO vehicle_positions (agency_id, observed_at, trip_id, vehicle_id, latitude, longitude, stop_sequence) VALUES ($1,$2,$3,$4,0,0,1)"
            )
            .bind("a0").bind(&t2).bind("T2").bind("V2")
            .execute(&db.pool).await.unwrap();

            let fraction = fetch_bunching_fraction(db, "a0", "R1", 0, 1).await.unwrap();
            assert_eq!(fraction, 0.0);
        }

        #[tokio::test]
        async fn compute_breakdown_returns_none_when_no_actual_data() {
            let td = test_utils::setup().await;
            let result = compute_speed_deficit_breakdown(&td.db, "a0", "R1", 0, 1).await.unwrap();
            assert!(result.is_none());
        }

        #[tokio::test]
        async fn compute_breakdown_produces_negative_dwell_delta_when_actual_dwell_exceeds_scheduled() {
            let td = test_utils::setup().await;
            let db = &td.db;

            // Setup scheduled: 1000m, 200s duration (5mps), 20s dwell
            sqlx::query("INSERT INTO routes VALUES ('a0', 'R1', '1', 'Route 1', 3)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO trips VALUES ('a0', 'T1', 'R1', 'WD', 0, NULL)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO stops VALUES ('a0', 'S1', 'A', 0.0, 0.0)")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO stops VALUES ('a0', 'S2', 'B', 0.0, 0.009)") // ~1000m
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('a0', 'T1', 'S1', 1, '08:00:00', '08:00:20')")
                .execute(&db.pool).await.unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('a0', 'T1', 'S2', 2, '08:03:20', '08:03:20')")
                .execute(&db.pool).await.unwrap();

            // Setup actual: 250s duration (4mps), 50s dwell (30s excess)
            let now_epoch = chrono::Utc::now().timestamp();
            let obs = chrono::Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO stop_time_events (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix) VALUES ($1,$2,$3,'S1',1,$4,$5)"
            )
            .bind("a0").bind(&obs).bind("T1").bind(now_epoch).bind(now_epoch + 50)
            .execute(&db.pool).await.unwrap();

            sqlx::query(
                "INSERT INTO stop_time_events (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix) VALUES ($1,$2,$3,'S2',2,$4,$5)"
            )
            .bind("a0").bind(&obs).bind("T1").bind(now_epoch + 250).bind(now_epoch + 250)
            .execute(&db.pool).await.unwrap();

            let result = compute_speed_deficit_breakdown(db, "a0", "R1", 0, 1).await.unwrap();
            assert!(result.is_some());
            let breakdown = result.unwrap();

            // scheduled: 1000m / 200s = 5.0 mps
            // actual: 1000m / 250s = 4.0 mps
            assert!((breakdown.scheduled_speed_mps - 5.0).abs() < 0.1);
            assert!((breakdown.actual_speed_mps - 4.0).abs() < 0.1);

            let dwell_factor = breakdown.factors.iter().find(|f| f.kind == FactorKind::DwellExcess);
            assert!(dwell_factor.is_some());
            assert!(dwell_factor.unwrap().delta_mps < 0.0);
        }
    }
}
