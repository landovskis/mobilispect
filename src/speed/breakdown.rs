// src/speed/breakdown.rs

use anyhow::Result;
use crate::db::Database;

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
    pub fn delta_kmh(&self) -> f64 {
        self.delta_mps * 3.6
    }

    pub fn from_kmh(&self) -> f64 {
        self.from_mps * 3.6
    }

    pub fn to_kmh(&self) -> f64 {
        self.to_mps * 3.6
    }
}

pub struct SpeedDeficitBreakdown {
    pub scheduled_speed_mps: f64,
    pub actual_speed_mps: f64,
    pub factors: Vec<DeficitFactor>,
    pub unexplained_mps: f64,
}

impl SpeedDeficitBreakdown {
    pub fn scheduled_speed_kmh(&self) -> f64 {
        self.scheduled_speed_mps * 3.6
    }

    pub fn actual_speed_kmh(&self) -> f64 {
        self.actual_speed_mps * 3.6
    }

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
            labels.push(serde_json::json!("− Other"));
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

struct ScheduledTimings {
    route_distance_m: f64,
    scheduled_duration_secs: f64,
    scheduled_dwell_secs: f64,
    num_stops: usize,
}

#[derive(sqlx::FromRow)]
struct ScheduledStopRow {
    stop_lat: f64,
    stop_lon: f64,
    arrival_time: String,
    departure_time: String,
}

async fn fetch_scheduled_timings(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    direction_id: i64,
) -> Result<Option<ScheduledTimings>> {
    let rows: Vec<ScheduledStopRow> = sqlx::query_as(
        "WITH rep_trip AS (
            SELECT DISTINCT ON (1) trip_id
            FROM trips
            WHERE agency_id = $1 AND route_id = $2 AND COALESCE(direction_id, 0) = $3
            ORDER BY 1, trip_id
        )
        SELECT s.stop_lat, s.stop_lon, ss.arrival_time, ss.departure_time
        FROM rep_trip rt
        JOIN scheduled_stops ss ON ss.agency_id = $1 AND ss.trip_id = rt.trip_id
        JOIN stops s ON s.agency_id = $1 AND s.stop_id = ss.stop_id
        ORDER BY ss.stop_sequence",
    )
    .bind(agency_id)
    .bind(route_id)
    .bind(direction_id)
    .fetch_all(&db.pool)
    .await?;

    if rows.len() < 2 {
        return Ok(None);
    }

    let route_distance_m: f64 = rows
        .windows(2)
        .map(|w| {
            super::haversine_meters(w[0].stop_lat, w[0].stop_lon, w[1].stop_lat, w[1].stop_lon)
        })
        .sum();

    if route_distance_m < 1.0 {
        return Ok(None);
    }

    let scheduled_dwell_secs: f64 = rows
        .iter()
        .filter_map(|r| {
            let arr = super::parse_time_secs(&r.arrival_time)?;
            let dep = super::parse_time_secs(&r.departure_time)?;
            if dep >= arr {
                Some((dep - arr) as f64)
            } else {
                None
            }
        })
        .sum();

    let first_secs = super::parse_time_secs(&rows.first().unwrap().arrival_time);
    let last_secs = super::parse_time_secs(&rows.last().unwrap().arrival_time);
    let scheduled_duration_secs = match (first_secs, last_secs) {
        (Some(f), Some(l)) if l > f => (l - f) as f64,
        _ => return Ok(None),
    };

    Ok(Some(ScheduledTimings {
        route_distance_m,
        scheduled_duration_secs,
        scheduled_dwell_secs,
        num_stops: rows.len(),
    }))
}

pub async fn compute_speed_deficit_breakdown(
    _db: &Database,
    _agency_id: &str,
    _route_id: &str,
    _direction_id: i64,
    _days: i64,
) -> Result<Option<SpeedDeficitBreakdown>> {
    Ok(None)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[cfg(test)]
    mod integration {
        use crate::db::test_utils;
        use super::*;

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
    }

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
}
