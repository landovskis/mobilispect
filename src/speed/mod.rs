use anyhow::Result;
use chrono::{NaiveDate, Utc};
use serde::Serialize;

use crate::config::AgencyConfig;
use crate::db::Database;

pub mod card;
pub use card::{
    RouteClass, RouteSpeedCard, build_speed_cards, classify_by_spacing,
};

pub(crate) fn direction_label(direction_id: i64) -> &'static str {
    match direction_id {
        0 => "Outbound",
        1 => "Inbound",
        _ => "Unknown",
    }
}

#[derive(Debug, sqlx::FromRow, Serialize)]
pub struct RouteSpeedSummary {
    pub agency_id: String,
    pub route_id: String,
    pub short_name: String,
    pub long_name: String,
    pub direction_id: i64,
    pub scheduled_speed_mps: f64,
    pub trip_count: i64,
    /// Average speed from vehicles observed in the last hour (m/s). None if no recent data.
    pub live_speed_mps: Option<f64>,
    /// Average actual speed over the last 7 days from stop arrival times (m/s). None if no history.
    pub actual_speed_mps: Option<f64>,
    /// Name of the last stop for this route+direction. None if stop data is unavailable.
    pub last_stop_name: Option<String>,
}

impl RouteSpeedSummary {
    /// Scheduled speed in km/h, rounded to one decimal place.
    pub fn scheduled_speed_kmh(&self) -> f64 {
        (self.scheduled_speed_mps * 3.6 * 10.0).round() / 10.0
    }

    pub fn actual_speed_display(&self) -> String {
        match self.actual_speed_mps {
            Some(s) => format!("{:.1} km/h", s * 3.6),
            None => "—".to_string(),
        }
    }

    pub fn live_speed_display(&self) -> String {
        match self.live_speed_mps {
            Some(s) => format!("{:.1} km/h", s * 3.6),
            None => "—".to_string(),
        }
    }

    /// Percentage by which actual speed lags scheduled speed.
    /// Positive = slower than scheduled, negative = faster.
    /// None if no actual speed data.
    pub fn speed_deficit_pct(&self) -> Option<f64> {
        let actual = self.actual_speed_mps?;
        if self.scheduled_speed_mps == 0.0 {
            return None;
        }
        Some((self.scheduled_speed_mps - actual) / self.scheduled_speed_mps * 100.0)
    }

    /// "—", "+X% faster", or "X% slower" relative to schedule.
    pub fn speed_vs_scheduled_display(&self) -> String {
        match self.speed_deficit_pct() {
            None => "—".to_string(),
            Some(d) if d > 1.0 => format!("{d:.0}% slower"),
            Some(d) if d < -1.0 => format!("{:.0}% faster", d.abs()),
            Some(_) => "On pace".to_string(),
        }
    }

    pub fn direction_label(&self) -> String {
        self.last_stop_name
            .clone()
            .unwrap_or_else(|| direction_label(self.direction_id).to_string())
    }

    /// CSS class for the vs-schedule cell: "slower", "faster", or "onpace".
    pub fn speed_class(&self) -> &'static str {
        match self.speed_deficit_pct() {
            Some(d) if d > 1.0 => "slower",
            Some(d) if d < -1.0 => "faster",
            Some(_) => "onpace",
            None => "onpace",
        }
    }
}

/// Haversine distance between two lat/lon points, in meters.
fn haversine_meters(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    const R: f64 = 6_371_000.0;
    let dlat = (lat2 - lat1).to_radians();
    let dlon = (lon2 - lon1).to_radians();
    let lat1 = lat1.to_radians();
    let lat2 = lat2.to_radians();
    let a = (dlat / 2.0).sin().powi(2) + lat1.cos() * lat2.cos() * (dlon / 2.0).sin().powi(2);
    let c = 2.0 * a.sqrt().asin();
    R * c
}

/// Parse "HH:MM:SS" (HH may be ≥ 24 for post-midnight service) into total seconds.
fn parse_time_secs(s: &str) -> Option<u32> {
    let mut parts = s.splitn(3, ':');
    let h: u32 = parts.next()?.parse().ok()?;
    let m: u32 = parts.next()?.parse().ok()?;
    let sec: u32 = parts.next()?.parse().ok()?;
    Some(h * 3600 + m * 60 + sec)
}

/// A single stop-to-stop segment along a route direction.
pub struct StopSpacing {
    pub to_stop_name: String,
    /// Haversine distance from the previous stop to this stop, in metres.
    pub distance_m: f64,
    /// True when `distance_m > 1.5 × avg_spacing_m` for this direction.
    pub is_outlier: bool,
    /// Pixel width for the strip segment (scaled so the longest segment = 200px).
    pub width_px: u32,
    /// Status relative to route class range: "below", "in_range", or "above"
    pub spacing_status: String,
}

/// All stop spacings for one direction of a route.
pub struct DirectionStopSpacings {
    pub direction_id: i64,
    pub variant_id: String,
    pub is_primary: bool,
    pub trip_count: i64,
    /// "First Stop → Last Stop"
    pub direction_name: String,
    /// Name of the first stop — rendered as the leftmost dot in the strip.
    pub first_stop_name: String,
    /// Mean distance between consecutive stops, in metres.
    pub avg_spacing_m: f64,
    pub spacings: Vec<StopSpacing>,
}

/// Raw row returned by the stop spacings SQL query.
#[derive(sqlx::FromRow)]
struct StopSpacingEntry {
    variant_id: String,
    direction_id: i64,
    is_primary: bool,
    trip_count: i64,
    to_stop_name: String,
    distance_m: Option<f64>,
    is_first: bool,
    is_last: bool,
}

impl StopSpacing {
    pub fn distance_display(&self) -> String {
        if self.distance_m >= 1000.0 {
            format!("{:.1} km", self.distance_m / 1000.0)
        } else {
            format!("{:.0} m", self.distance_m)
        }
    }

    pub fn spacing_status_class(&self) -> &str {
        match self.spacing_status.as_str() {
            "below" => "slow",
            "above" => "outlier",
            _ => "",
        }
    }
}

/// Builds per-direction spacing data from raw SQL rows.
/// Each direction's rows include one NULL-distance entry (the first stop, which has no
/// previous stop). `first_stop_name` and `direction_name` come from `is_first`/`is_last`
/// flags; the NULL-distance first row is filtered out before building `spacings`.
fn build_direction_spacings(rows: Vec<StopSpacingEntry>) -> Vec<DirectionStopSpacings> {
    let mut result: Vec<DirectionStopSpacings> = Vec::new();
    let mut i = 0;
    while i < rows.len() {
        let variant_id = rows[i].variant_id.clone();
        let end = rows[i..]
            .iter()
            .position(|r| r.variant_id != variant_id)
            .map(|p| i + p)
            .unwrap_or(rows.len());
        let dir_rows = &rows[i..end];

        let first_stop_name = dir_rows
            .iter()
            .find(|r| r.is_first)
            .map(|r| r.to_stop_name.clone())
            .unwrap_or_default();

        let last_stop_name = dir_rows
            .iter()
            .find(|r| r.is_last)
            .map(|r| r.to_stop_name.clone())
            .unwrap_or_default();

        let direction_name = format!("{first_stop_name} → {last_stop_name}");

        let distances: Vec<(String, f64)> = dir_rows
            .iter()
            .filter_map(|r| r.distance_m.map(|d| (r.to_stop_name.clone(), d)))
            .collect();

        let avg_spacing_m = if distances.is_empty() {
            0.0
        } else {
            distances.iter().map(|(_, d)| d).sum::<f64>() / distances.len() as f64
        };

        let max_dist = distances.iter().map(|(_, d)| *d).fold(0.0_f64, f64::max);
        let threshold = avg_spacing_m * 1.5;

        let (range_min, range_max) = if avg_spacing_m < 500.0 {
            (300.0, 500.0)
        } else if avg_spacing_m < 1500.0 {
            (500.0, 1500.0)
        } else {
            (1500.0, 5000.0)
        };

        let spacings = distances
            .into_iter()
            .map(|(name, dist)| {
                let spacing_status = if dist < range_min {
                    "below"
                } else if dist > range_max {
                    "above"
                } else {
                    "in_range"
                };
                StopSpacing {
                    to_stop_name: name,
                    distance_m: dist,
                    is_outlier: dist > threshold,
                    width_px: ((dist / max_dist.max(1.0)) * 200.0) as u32,
                    spacing_status: spacing_status.to_string(),
                }
            })
            .collect();

        let first_row = &dir_rows[0];
        result.push(DirectionStopSpacings {
            direction_id: first_row.direction_id,
            variant_id: variant_id.clone(),
            is_primary: first_row.is_primary,
            trip_count: first_row.trip_count,
            direction_name,
            first_stop_name,
            avg_spacing_m,
            spacings,
        });
        i = end;
    }
    result
}

/// Speed trend data for one direction of a route.
pub struct DirectionSpeedTrend {
    pub direction_id: i64,
    /// (service_date as "YYYY-MM-DD", actual_speed_mps, scheduled_speed_mps)
    /// `scheduled_speed_mps` is `None` when no scheduled speed has been computed for the route.
    pub weekday: Vec<(String, f64, Option<f64>)>,
    pub saturday: Vec<(String, f64, Option<f64>)>,
    pub sunday: Vec<(String, f64, Option<f64>)>,
}

/// Raw row returned by the speed trend SQL query.
#[derive(sqlx::FromRow)]
struct SpeedTrendRow {
    direction_id: i64,
    service_date: String,
    actual_speed_mps: f64,
    scheduled_speed_mps: Option<f64>,
}

fn build_direction_trends(rows: Vec<SpeedTrendRow>) -> Vec<DirectionSpeedTrend> {
    use chrono::Datelike;
    use std::str::FromStr;

    let mut map: std::collections::HashMap<
        i64,
        (
            Vec<(String, f64, Option<f64>)>,
            Vec<(String, f64, Option<f64>)>,
            Vec<(String, f64, Option<f64>)>,
        ),
    > = std::collections::HashMap::new();

    for row in rows {
        // num_days_from_sunday(): 0 = Sunday, 1 = Monday, …, 6 = Saturday
        let dow = chrono::NaiveDate::from_str(&row.service_date)
            .map(|d| d.weekday().num_days_from_sunday())
            .unwrap_or(1);
        let entry = map.entry(row.direction_id).or_default();
        let point = (
            row.service_date,
            row.actual_speed_mps,
            row.scheduled_speed_mps,
        );
        match dow {
            0 => entry.2.push(point), // Sunday
            6 => entry.1.push(point), // Saturday
            _ => entry.0.push(point), // Weekday
        }
    }

    let mut result: Vec<DirectionSpeedTrend> = map
        .into_iter()
        .map(
            |(direction_id, (weekday, saturday, sunday))| DirectionSpeedTrend {
                direction_id,
                weekday,
                saturday,
                sunday,
            },
        )
        .collect();
    result.sort_by_key(|t| t.direction_id);
    result
}

fn build_direction_trends_with_scheduled(rows: Vec<SpeedTrendRow>) -> Vec<DirectionSpeedTrend> {
    build_direction_trends(rows)
}

pub struct VariantSpeedTrend {
    pub variant_id: String,
    pub weekday: Vec<(String, f64, Option<f64>)>,
    pub saturday: Vec<(String, f64, Option<f64>)>,
    pub sunday: Vec<(String, f64, Option<f64>)>,
}

#[derive(sqlx::FromRow)]
struct SpeedTrendVariantRow {
    variant_id: String,
    service_date: String,
    actual_speed_mps: f64,
    scheduled_speed_mps: Option<f64>,
}

fn build_variant_trends(rows: Vec<SpeedTrendVariantRow>) -> Vec<VariantSpeedTrend> {
    use chrono::Datelike;
    use std::str::FromStr;

    let mut map: std::collections::HashMap<
        String,
        (
            Vec<(String, f64, Option<f64>)>,
            Vec<(String, f64, Option<f64>)>,
            Vec<(String, f64, Option<f64>)>,
        ),
    > = std::collections::HashMap::new();

    for row in rows {
        let dow = chrono::NaiveDate::from_str(&row.service_date)
            .map(|d| d.weekday().num_days_from_sunday())
            .unwrap_or(1);
        let entry = map.entry(row.variant_id).or_default();
        let point = (row.service_date, row.actual_speed_mps, row.scheduled_speed_mps);
        match dow {
            0 => entry.2.push(point), // Sunday
            6 => entry.1.push(point), // Saturday
            _ => entry.0.push(point), // Weekday
        }
    }

    let mut result: Vec<VariantSpeedTrend> = map
        .into_iter()
        .map(|(variant_id, (weekday, saturday, sunday))| VariantSpeedTrend {
            variant_id,
            weekday,
            saturday,
            sunday,
        })
        .collect();
    result.sort_by(|a, b| a.variant_id.cmp(&b.variant_id));
    result
}

pub async fn route_speed_trend_by_variant(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    days: i64,
) -> Result<Vec<VariantSpeedTrend>> {
    let rows: Vec<SpeedTrendVariantRow> = sqlx::query_as(
        "SELECT d.variant_id,
                d.service_date,
                d.actual_speed_mps,
                r.scheduled_speed_mps
         FROM route_speed_daily d
         LEFT JOIN route_speed r ON r.agency_id = d.agency_id
           AND r.route_id = d.route_id AND r.direction_id = d.direction_id
         WHERE d.agency_id = $1
           AND d.route_id = $2
           AND d.variant_id != ''
           AND d.service_date >= (CURRENT_DATE - $3::INT * INTERVAL '1 day')::TEXT
         ORDER BY d.variant_id, d.service_date",
    )
    .bind(agency_id)
    .bind(route_id)
    .bind(days)
    .fetch_all(&db.pool)
    .await?;

    Ok(build_variant_trends(rows))
}

/// Fetch per-stop spacing data for a single route, grouped by direction.
/// Returns one `DirectionStopSpacings` per direction. Empty if route has no trips.
pub async fn route_stop_spacings(
    db: &Database,
    agency_id: &str,
    route_id: &str,
) -> Result<Vec<DirectionStopSpacings>> {
    let rows: Vec<StopSpacingEntry> = sqlx::query_as(
        "WITH all_variants AS (
            SELECT variant_id, direction_id, is_primary, trip_count
            FROM route_variants
            WHERE agency_id = $1 AND route_id = $2
        ),
        ordered AS (
            SELECT
                av.variant_id,
                av.direction_id,
                av.is_primary,
                av.trip_count,
                s.stop_name,
                s.stop_lat, s.stop_lon,
                ROW_NUMBER() OVER (PARTITION BY av.variant_id ORDER BY rvs.stop_sequence) AS rn,
                COUNT(*)    OVER (PARTITION BY av.variant_id)                              AS total_stops
            FROM all_variants av
            JOIN route_variant_stops rvs ON rvs.agency_id = $1 AND rvs.variant_id = av.variant_id
            JOIN stops s ON s.agency_id = $1 AND s.stop_id = rvs.stop_id
        ),
        with_prev AS (
            SELECT
                variant_id, direction_id, is_primary, trip_count,
                stop_name, rn, total_stops, stop_lat, stop_lon,
                LAG(stop_lat) OVER (PARTITION BY variant_id ORDER BY rn) AS prev_lat,
                LAG(stop_lon) OVER (PARTITION BY variant_id ORDER BY rn) AS prev_lon
            FROM ordered
        )
        SELECT
            variant_id,
            direction_id,
            is_primary,
            trip_count,
            stop_name AS to_stop_name,
            CASE WHEN prev_lat IS NOT NULL THEN
                2 * 6371000 * asin(sqrt(
                    power(sin((stop_lat - prev_lat) * pi() / 180.0 / 2.0), 2) +
                    cos(prev_lat * pi() / 180.0) * cos(stop_lat * pi() / 180.0) *
                    power(sin((stop_lon - prev_lon) * pi() / 180.0 / 2.0), 2)
                ))
            END AS distance_m,
            (rn = 1)           AS is_first,
            (rn = total_stops) AS is_last
        FROM with_prev
        ORDER BY trip_count DESC, variant_id, rn",
    )
    .bind(&agency_id)
    .bind(route_id)
    .fetch_all(&db.pool)
    .await?;

    Ok(build_direction_spacings(rows))
}

/// Fetch per-day actual speed for a single route, grouped by direction and day type.
/// `days` controls how many days back to look (use 28 to match other data windows).
pub async fn route_speed_trend_by_direction(
    db: &Database,
    agency_id: &str,
    route_id: &str,
    days: i64,
) -> Result<Vec<DirectionSpeedTrend>> {
    let rows: Vec<SpeedTrendRow> = sqlx::query_as(
        "SELECT d.direction_id,
                d.service_date,
                d.actual_speed_mps,
                r.scheduled_speed_mps
         FROM route_speed_daily d
         LEFT JOIN route_speed r ON r.agency_id = d.agency_id AND r.route_id = d.route_id AND r.direction_id = d.direction_id
         WHERE d.agency_id = $1
           AND d.route_id = $2
           AND d.service_date >= (CURRENT_DATE - $3::INT * INTERVAL '1 day')::TEXT
         ORDER BY d.direction_id, d.service_date",
    )
    .bind(&agency_id)
    .bind(route_id)
    .bind(days)
    .fetch_all(&db.pool)
    .await?;

    Ok(build_direction_trends_with_scheduled(rows))
}

/// Compute scheduled average speed (m/s) for every route+direction and store in `route_speed`.
/// Reads only static GTFS tables — safe to call on startup after GTFS load.
pub async fn compute_route_speed(db: &Database, agency: &AgencyConfig) -> Result<()> {
    let now = Utc::now().to_rfc3339();
    let agency_id = agency.id.to_string();

    // All distinct route + direction combinations that have trips with stops.
    let combos: Vec<(String, i64)> = sqlx::query_as(
        "SELECT DISTINCT t.route_id, COALESCE(t.direction_id, 0) as direction_id
         FROM trips t
         JOIN scheduled_stops ss ON ss.trip_id = t.trip_id AND ss.agency_id = t.agency_id
         WHERE t.agency_id = $1
         GROUP BY t.route_id, t.direction_id
         HAVING COUNT(ss.stop_sequence) >= 2",
    )
    .bind(&agency_id)
    .fetch_all(&db.pool)
    .await?;

    for (route_id, direction_id) in &combos {
        let trips: Vec<(String,)> = sqlx::query_as(
            "SELECT trip_id FROM trips WHERE agency_id = $1 AND route_id = $2 AND COALESCE(direction_id, 0) = $3",
        )
        .bind(&agency_id)
        .bind(route_id)
        .bind(direction_id)
        .fetch_all(&db.pool)
        .await?;

        let mut trip_speeds: Vec<f64> = Vec::new();

        for (trip_id,) in &trips {
            let stops: Vec<(f64, f64, String)> = sqlx::query_as(
                "SELECT s.stop_lat, s.stop_lon, ss.arrival_time
                 FROM scheduled_stops ss
                 JOIN stops s ON s.stop_id = ss.stop_id AND s.agency_id = ss.agency_id
                 WHERE ss.agency_id = $1 AND ss.trip_id = $2
                 ORDER BY ss.stop_sequence",
            )
            .bind(&agency_id)
            .bind(trip_id)
            .fetch_all(&db.pool)
            .await?;

            if stops.len() < 2 {
                continue;
            }

            let total_distance_m: f64 = stops
                .windows(2)
                .map(|w| haversine_meters(w[0].0, w[0].1, w[1].0, w[1].1))
                .sum();

            // Scheduled duration: last arrival minus first arrival.
            let first_secs = parse_time_secs(&stops.first().unwrap().2);
            let last_secs = parse_time_secs(&stops.last().unwrap().2);
            let duration_secs = match (first_secs, last_secs) {
                (Some(f), Some(l)) if l > f => (l - f) as f64,
                _ => continue,
            };

            if total_distance_m > 0.0 && duration_secs > 0.0 {
                trip_speeds.push(total_distance_m / duration_secs);
            }
        }

        if trip_speeds.is_empty() {
            continue;
        }

        // Derive avg_stop_spacing_m from the primary variant's canonical stop list,
        // not from averaging across all trips (which conflates short-turns and full routes).
        let primary_stops: Vec<(f64, f64)> = sqlx::query_as(
            "SELECT s.stop_lat, s.stop_lon
             FROM route_variant_stops rvs
             JOIN route_variants rv ON rv.agency_id = rvs.agency_id AND rv.variant_id = rvs.variant_id
             JOIN stops s ON s.agency_id = rvs.agency_id AND s.stop_id = rvs.stop_id
             WHERE rvs.agency_id = $1 AND rv.route_id = $2 AND rv.direction_id = $3 AND rv.is_primary = TRUE
             ORDER BY rvs.stop_sequence",
        )
        .bind(&agency_id)
        .bind(route_id)
        .bind(direction_id)
        .fetch_all(&db.pool)
        .await?;

        let avg_stop_spacing_m = if primary_stops.len() >= 2 {
            let spacings: Vec<f64> = primary_stops
                .windows(2)
                .map(|w| haversine_meters(w[0].0, w[0].1, w[1].0, w[1].1))
                .filter(|d| *d > 0.0)
                .collect();
            if spacings.is_empty() {
                None
            } else {
                Some(spacings.iter().sum::<f64>() / spacings.len() as f64)
            }
        } else {
            None
        };

        let avg_speed = trip_speeds.iter().sum::<f64>() / trip_speeds.len() as f64;
        let trip_count = trip_speeds.len() as i64;

        sqlx::query(
            "INSERT INTO route_speed
             (agency_id, route_id, direction_id, scheduled_speed_mps, avg_stop_spacing_m, trip_count, computed_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7)
             ON CONFLICT (agency_id, route_id, direction_id) DO UPDATE SET
               scheduled_speed_mps = EXCLUDED.scheduled_speed_mps,
               avg_stop_spacing_m = EXCLUDED.avg_stop_spacing_m,
               trip_count = EXCLUDED.trip_count,
               computed_at = EXCLUDED.computed_at",
        )
        .bind(&agency_id)
        .bind(route_id)
        .bind(direction_id)
        .bind(avg_speed)
        .bind(avg_stop_spacing_m)
        .bind(trip_count)
        .bind(&now)
        .execute(&db.pool)
        .await?;
    }

    Ok(())
}

/// Compute scheduled average speed per route+direction broken down by day type
/// (weekday / saturday / sunday) using GTFS calendar data.
/// Stores results in `route_speed_day_type`.
pub async fn compute_route_speed_by_day_type(db: &Database, agency: &AgencyConfig) -> Result<()> {
    let now = Utc::now().to_rfc3339();
    let agency_id = agency.id.to_string();

    let combos: Vec<(String, i64)> = sqlx::query_as(
        "SELECT DISTINCT t.route_id, COALESCE(t.direction_id, 0) as direction_id
         FROM trips t
         JOIN scheduled_stops ss ON ss.trip_id = t.trip_id AND ss.agency_id = t.agency_id
         WHERE t.agency_id = $1
         GROUP BY t.route_id, t.direction_id
         HAVING COUNT(ss.stop_sequence) >= 2",
    )
    .bind(&agency_id)
    .fetch_all(&db.pool)
    .await?;

    for (route_id, direction_id) in &combos {
        let trips: Vec<(String, String)> = sqlx::query_as(
            "SELECT t.trip_id, t.service_id FROM trips t
             WHERE t.agency_id = $1 AND t.route_id = $2 AND COALESCE(t.direction_id, 0) = $3",
        )
        .bind(&agency_id)
        .bind(route_id)
        .bind(direction_id)
        .fetch_all(&db.pool)
        .await?;

        // day_type -> Vec<speed_mps>
        let mut day_speeds: std::collections::HashMap<&'static str, Vec<f64>> =
            std::collections::HashMap::new();

        for (trip_id, service_id) in &trips {
            let cal: Option<(bool, bool, bool, bool, bool, bool, bool)> = sqlx::query_as(
                "SELECT monday, tuesday, wednesday, thursday, friday, saturday, sunday
                 FROM calendar WHERE agency_id = $1 AND service_id = $2",
            )
            .bind(&agency_id)
            .bind(service_id)
            .fetch_optional(&db.pool)
            .await?;

            let Some((mon, tue, wed, thu, fri, sat, sun)) = cal else {
                continue;
            };

            let stops: Vec<(f64, f64, String)> = sqlx::query_as(
                "SELECT s.stop_lat, s.stop_lon, ss.arrival_time
                 FROM scheduled_stops ss
                 JOIN stops s ON s.stop_id = ss.stop_id AND s.agency_id = ss.agency_id
                 WHERE ss.agency_id = $1 AND ss.trip_id = $2
                 ORDER BY ss.stop_sequence",
            )
            .bind(&agency_id)
            .bind(trip_id)
            .fetch_all(&db.pool)
            .await?;

            if stops.len() < 2 {
                continue;
            }

            let total_distance_m: f64 = stops
                .windows(2)
                .map(|w| haversine_meters(w[0].0, w[0].1, w[1].0, w[1].1))
                .sum();

            let first_secs = parse_time_secs(&stops.first().unwrap().2);
            let last_secs = parse_time_secs(&stops.last().unwrap().2);
            let duration_secs = match (first_secs, last_secs) {
                (Some(f), Some(l)) if l > f => (l - f) as f64,
                _ => continue,
            };

            if total_distance_m <= 0.0 || duration_secs <= 0.0 {
                continue;
            }

            let speed = total_distance_m / duration_secs;

            if mon || tue || wed || thu || fri {
                day_speeds.entry("weekday").or_default().push(speed);
            }
            if sat {
                day_speeds.entry("saturday").or_default().push(speed);
            }
            if sun {
                day_speeds.entry("sunday").or_default().push(speed);
            }
        }

        for (day_type, speeds) in &day_speeds {
            let avg_speed = speeds.iter().sum::<f64>() / speeds.len() as f64;
            let trip_count = speeds.len() as i64;
            sqlx::query(
                "INSERT INTO route_speed_day_type
                 (agency_id, route_id, direction_id, day_type, scheduled_speed_mps, trip_count, computed_at)
                 VALUES ($1, $2, $3, $4, $5, $6, $7)
                 ON CONFLICT (agency_id, route_id, direction_id, day_type) DO UPDATE SET
                   scheduled_speed_mps = EXCLUDED.scheduled_speed_mps,
                   trip_count          = EXCLUDED.trip_count,
                   computed_at         = EXCLUDED.computed_at",
            )
            .bind(agency_id.as_str())
            .bind(route_id.as_str())
            .bind(direction_id)
            .bind(*day_type)
            .bind(avg_speed)
            .bind(trip_count)
            .bind(now.as_str())
            .execute(&db.pool)
            .await?;
        }
    }

    Ok(())
}

/// Compute actual average speed per route+direction for a service date from stop arrival times.
/// Uses `stop_time_events.arrival_time_unix` to determine actual travel time per trip.
pub async fn compute_route_speed_daily(
    db: &Database,
    agency: &AgencyConfig,
    service_date: NaiveDate,
) -> Result<()> {
    let date_str = service_date.to_string();
    let now = Utc::now().to_rfc3339();
    let agency_id = agency.id.to_string();

    // All distinct route + direction + variant combos with stop time events on this date.
    let combos: Vec<(String, i64, String)> = sqlx::query_as(
        "SELECT DISTINCT
             t.route_id,
             COALESCE(t.direction_id, 0) AS direction_id,
             COALESCE(t.variant_id, '')  AS variant_id
         FROM stop_time_events ste
         JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
         WHERE ste.agency_id = $1 AND ste.observed_at::TIMESTAMPTZ::DATE = $2::DATE
           AND ste.arrival_time_unix IS NOT NULL",
    )
    .bind(&agency_id)
    .bind(&date_str)
    .fetch_all(&db.pool)
    .await?;

    for (route_id, direction_id, variant_id) in &combos {
        let trips: Vec<(String,)> = sqlx::query_as(
            "SELECT DISTINCT ste.trip_id
             FROM stop_time_events ste
             JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
             WHERE t.agency_id = $1 AND t.route_id = $2
               AND COALESCE(t.variant_id, '') = $3
               AND ste.observed_at::TIMESTAMPTZ::DATE = $4::DATE
               AND ste.arrival_time_unix IS NOT NULL",
        )
        .bind(&agency_id)
        .bind(route_id)
        .bind(variant_id)
        .bind(&date_str)
        .fetch_all(&db.pool)
        .await?;

        let mut trip_speeds: Vec<f64> = Vec::new();

        for (trip_id,) in &trips {
            // Get last observed arrival_time_unix per stop (closest to actual arrival).
            let stops: Vec<(f64, f64, i64)> = sqlx::query_as(
                "SELECT s.stop_lat, s.stop_lon, MAX(ste.arrival_time_unix) as arrival_time_unix
                 FROM stop_time_events ste
                 JOIN scheduled_stops ss
                   ON ss.trip_id = ste.trip_id AND ss.stop_id = ste.stop_id AND ss.agency_id = ste.agency_id
                 JOIN stops s ON s.stop_id = ste.stop_id AND s.agency_id = ste.agency_id
                 WHERE ste.agency_id = $1 AND ste.trip_id = $2
                   AND ste.observed_at::TIMESTAMPTZ::DATE = $3::DATE
                   AND ste.arrival_time_unix IS NOT NULL
                 GROUP BY ss.stop_sequence, s.stop_lat, s.stop_lon
                 ORDER BY ss.stop_sequence",
            )
            .bind(&agency_id)
            .bind(trip_id)
            .bind(&date_str)
            .fetch_all(&db.pool)
            .await?;

            if stops.len() < 2 {
                continue;
            }

            let total_distance_m: f64 = stops
                .windows(2)
                .map(|w| haversine_meters(w[0].0, w[0].1, w[1].0, w[1].1))
                .sum();

            let first_unix = stops.first().unwrap().2;
            let last_unix = stops.last().unwrap().2;
            let duration_secs = (last_unix - first_unix) as f64;

            if total_distance_m > 0.0 && duration_secs > 0.0 {
                trip_speeds.push(total_distance_m / duration_secs);
            }
        }

        if trip_speeds.is_empty() {
            continue;
        }

        let avg_speed = trip_speeds.iter().sum::<f64>() / trip_speeds.len() as f64;
        let trip_count = trip_speeds.len() as i64;

        let avg_dwell_secs: Option<f64> = sqlx::query_scalar!(
            "SELECT AVG(ste.dwell_secs)::DOUBLE PRECISION
             FROM stop_time_events ste
             JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
             WHERE ste.agency_id = $1
               AND t.route_id = $2
               AND COALESCE(t.direction_id, 0) = $3
               AND ste.observed_at::TIMESTAMPTZ::DATE = $4::DATE
               AND ste.dwell_secs > 0",
            &agency_id as &str,
            route_id as &str,
            *direction_id,
            &date_str as &str,
        )
        .fetch_one(&db.pool)
        .await?;

        sqlx::query!(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, variant_id, actual_speed_mps, trip_count, avg_dwell_secs, computed_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
             ON CONFLICT (agency_id, route_id, service_date, direction_id, variant_id) DO UPDATE SET
               actual_speed_mps = EXCLUDED.actual_speed_mps,
               trip_count = EXCLUDED.trip_count,
               avg_dwell_secs = EXCLUDED.avg_dwell_secs,
               computed_at = EXCLUDED.computed_at",
            &agency_id,
            route_id,
            date_str,
            direction_id,
            variant_id,
            avg_speed,
            trip_count,
            avg_dwell_secs as Option<f64>,
            now,
        )
        .execute(&db.pool)
        .await?;
    }

    Ok(())
}

/// Fetch scheduled, live, and historical actual speed for all routes.
/// Live speed: average from vehicle positions in the last hour.
/// Actual speed: average from route_speed_daily over the last 7 days.
/// If `agency_filter` is Some, only returns routes for that agency.
pub async fn route_speed_summary(
    db: &Database,
    agency_filter: Option<&str>,
) -> Result<Vec<RouteSpeedSummary>> {
    let rows: Vec<RouteSpeedSummary> = match agency_filter {
        None => sqlx::query_as(
            "SELECT
               rs.agency_id,
               rs.route_id,
               r.short_name,
               r.long_name,
               rs.direction_id,
               rs.scheduled_speed_mps,
               rs.trip_count,
               live.avg_live_speed as live_speed_mps,
               hist.avg_actual_speed as actual_speed_mps,
               lsn.stop_name as last_stop_name
             FROM route_speed rs
             JOIN routes r ON rs.agency_id = r.agency_id AND rs.route_id = r.route_id
             LEFT JOIN (
               SELECT t.agency_id, t.route_id, AVG(vp.speed) as avg_live_speed
               FROM vehicle_positions vp
               JOIN trips t ON t.trip_id = vp.trip_id AND t.agency_id = vp.agency_id
               WHERE vp.speed IS NOT NULL
                 AND vp.observed_at::TIMESTAMPTZ >= NOW() - INTERVAL '1 hour'
               GROUP BY t.agency_id, t.route_id
             ) live ON live.agency_id = rs.agency_id AND live.route_id = rs.route_id
             LEFT JOIN (
               SELECT agency_id, route_id, direction_id, AVG(actual_speed_mps) as avg_actual_speed
               FROM route_speed_daily
               WHERE service_date >= (CURRENT_DATE - INTERVAL '7 days')::TEXT
               GROUP BY agency_id, route_id, direction_id
             ) hist ON hist.agency_id = rs.agency_id AND hist.route_id = rs.route_id AND hist.direction_id = rs.direction_id
             LEFT JOIN (
               SELECT DISTINCT ON (t.agency_id, t.route_id, COALESCE(t.direction_id, 0))
                 t.agency_id, t.route_id, COALESCE(t.direction_id, 0) AS direction_id,
                 s.stop_name
               FROM trips t
               JOIN (
                 SELECT agency_id, trip_id, stop_id
                 FROM (
                   SELECT agency_id, trip_id, stop_id,
                          ROW_NUMBER() OVER (PARTITION BY agency_id, trip_id ORDER BY stop_sequence DESC) AS rn
                   FROM scheduled_stops
                 ) ranked
                 WHERE rn = 1
               ) last_ss ON last_ss.agency_id = t.agency_id AND last_ss.trip_id = t.trip_id
               JOIN stops s ON s.agency_id = t.agency_id AND s.stop_id = last_ss.stop_id
               ORDER BY t.agency_id, t.route_id, COALESCE(t.direction_id, 0)
             ) lsn ON lsn.agency_id = rs.agency_id AND lsn.route_id = rs.route_id AND lsn.direction_id = rs.direction_id
             ORDER BY rs.agency_id, CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST, r.short_name, rs.direction_id",
        )
        .fetch_all(&db.pool)
        .await?,

        Some(agency) => sqlx::query_as(
            "SELECT
               rs.agency_id,
               rs.route_id,
               r.short_name,
               r.long_name,
               rs.direction_id,
               rs.scheduled_speed_mps,
               rs.trip_count,
               live.avg_live_speed as live_speed_mps,
               hist.avg_actual_speed as actual_speed_mps,
               lsn.stop_name as last_stop_name
             FROM route_speed rs
             JOIN routes r ON rs.agency_id = r.agency_id AND rs.route_id = r.route_id
             LEFT JOIN (
               SELECT t.agency_id, t.route_id, AVG(vp.speed) as avg_live_speed
               FROM vehicle_positions vp
               JOIN trips t ON t.trip_id = vp.trip_id AND t.agency_id = vp.agency_id
               WHERE vp.speed IS NOT NULL
                 AND vp.observed_at::TIMESTAMPTZ >= NOW() - INTERVAL '1 hour'
               GROUP BY t.agency_id, t.route_id
             ) live ON live.agency_id = rs.agency_id AND live.route_id = rs.route_id
             LEFT JOIN (
               SELECT agency_id, route_id, direction_id, AVG(actual_speed_mps) as avg_actual_speed
               FROM route_speed_daily
               WHERE service_date >= (CURRENT_DATE - INTERVAL '7 days')::TEXT
               GROUP BY agency_id, route_id, direction_id
             ) hist ON hist.agency_id = rs.agency_id AND hist.route_id = rs.route_id AND hist.direction_id = rs.direction_id
             LEFT JOIN (
               SELECT DISTINCT ON (t.agency_id, t.route_id, COALESCE(t.direction_id, 0))
                 t.agency_id, t.route_id, COALESCE(t.direction_id, 0) AS direction_id,
                 s.stop_name
               FROM trips t
               JOIN (
                 SELECT agency_id, trip_id, stop_id
                 FROM (
                   SELECT agency_id, trip_id, stop_id,
                          ROW_NUMBER() OVER (PARTITION BY agency_id, trip_id ORDER BY stop_sequence DESC) AS rn
                   FROM scheduled_stops
                 ) ranked
                 WHERE rn = 1
               ) last_ss ON last_ss.agency_id = t.agency_id AND last_ss.trip_id = t.trip_id
               JOIN stops s ON s.agency_id = t.agency_id AND s.stop_id = last_ss.stop_id
               ORDER BY t.agency_id, t.route_id, COALESCE(t.direction_id, 0)
             ) lsn ON lsn.agency_id = rs.agency_id AND lsn.route_id = rs.route_id AND lsn.direction_id = rs.direction_id
             WHERE rs.agency_id = $1
             ORDER BY rs.agency_id, CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST, r.short_name, rs.direction_id",
        )
        .bind(agency)
        .fetch_all(&db.pool)
        .await?,
    };
    Ok(rows)
}

/// Compute actual average speed per route+direction for each UTC hour, from stop arrival times
/// observed in the last 4 hours. Called after every GTFS-RT poll so the data stays fresh.
pub async fn compute_route_speed_hourly(db: &Database, agency: &AgencyConfig) -> Result<()> {
    let now = Utc::now().to_rfc3339();
    let agency_id = agency.id.to_string();

    let combos: Vec<(String, i64)> = sqlx::query_as(
        "SELECT DISTINCT t.route_id, COALESCE(t.direction_id, 0) as direction_id
         FROM stop_time_events ste
         JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
         WHERE ste.agency_id = $1 AND ste.arrival_time_unix IS NOT NULL
           AND ste.observed_at::TIMESTAMPTZ >= NOW() - INTERVAL '4 hours'",
    )
    .bind(&agency_id)
    .fetch_all(&db.pool)
    .await?;

    for (route_id, direction_id) in &combos {
        let trips: Vec<(String,)> = sqlx::query_as(
            "SELECT DISTINCT ste.trip_id
             FROM stop_time_events ste
             JOIN trips t ON t.trip_id = ste.trip_id AND t.agency_id = ste.agency_id
             WHERE t.agency_id = $1 AND t.route_id = $2
               AND COALESCE(t.direction_id, 0) = $3
               AND ste.arrival_time_unix IS NOT NULL
               AND ste.observed_at::TIMESTAMPTZ >= NOW() - INTERVAL '4 hours'",
        )
        .bind(&agency_id)
        .bind(route_id)
        .bind(direction_id)
        .fetch_all(&db.pool)
        .await?;

        // Accumulate per-hour speed samples: hour_utc -> Vec<speed_mps>
        let mut hour_speeds: std::collections::HashMap<String, Vec<f64>> =
            std::collections::HashMap::new();

        for (trip_id,) in &trips {
            let stops: Vec<(f64, f64, i64)> = sqlx::query_as(
                "SELECT s.stop_lat, s.stop_lon, MAX(ste.arrival_time_unix) as arrival_time_unix
                 FROM stop_time_events ste
                 JOIN scheduled_stops ss
                   ON ss.trip_id = ste.trip_id AND ss.stop_id = ste.stop_id AND ss.agency_id = ste.agency_id
                 JOIN stops s ON s.stop_id = ste.stop_id AND s.agency_id = ste.agency_id
                 WHERE ste.agency_id = $1 AND ste.trip_id = $2
                   AND ste.arrival_time_unix IS NOT NULL
                 GROUP BY ss.stop_sequence, s.stop_lat, s.stop_lon
                 ORDER BY ss.stop_sequence",
            )
            .bind(&agency_id)
            .bind(trip_id)
            .fetch_all(&db.pool)
            .await?;

            if stops.len() < 2 {
                continue;
            }

            let total_distance_m: f64 = stops
                .windows(2)
                .map(|w| haversine_meters(w[0].0, w[0].1, w[1].0, w[1].1))
                .sum();

            let first_unix = stops.first().unwrap().2;
            let last_unix = stops.last().unwrap().2;
            let duration_secs = (last_unix - first_unix) as f64;

            if total_distance_m > 0.0 && duration_secs > 0.0 {
                let hour_utc = chrono::DateTime::from_timestamp(first_unix, 0)
                    .map(|dt: chrono::DateTime<Utc>| dt.format("%Y-%m-%d %H").to_string())
                    .unwrap_or_else(|| "1970-01-01 00".to_string());
                hour_speeds
                    .entry(hour_utc)
                    .or_default()
                    .push(total_distance_m / duration_secs);
            }
        }

        for (hour_utc, speeds) in &hour_speeds {
            let avg_speed = speeds.iter().sum::<f64>() / speeds.len() as f64;
            let trip_count = speeds.len() as i64;
            sqlx::query(
                "INSERT INTO route_speed_hourly
                 (agency_id, route_id, direction_id, hour_utc, actual_speed_mps, trip_count, computed_at)
                 VALUES ($1, $2, $3, $4, $5, $6, $7)
                 ON CONFLICT (agency_id, route_id, direction_id, hour_utc) DO UPDATE SET
                   actual_speed_mps = EXCLUDED.actual_speed_mps,
                   trip_count = EXCLUDED.trip_count,
                   computed_at = EXCLUDED.computed_at",
            )
            .bind(agency_id.as_str())
            .bind(route_id.as_str())
            .bind(direction_id)
            .bind(hour_utc.as_str())
            .bind(avg_speed)
            .bind(trip_count)
            .bind(now.as_str())
            .execute(&db.pool)
            .await?;
        }
    }

    Ok(())
}

/// Per-route, per-direction scheduled speed broken down by day type
/// (weekday / Saturday / Sunday), sourced from `route_speed_day_type`.
#[derive(Debug, sqlx::FromRow)]
pub struct RouteSpeedDayType {
    pub agency_id: String,
    pub route_id: String,
    pub short_name: String,
    pub long_name: String,
    pub direction_id: i64,
    pub weekday_speed_mps: Option<f64>,
    pub saturday_speed_mps: Option<f64>,
    pub sunday_speed_mps: Option<f64>,
    pub actual_weekday_speed_mps: Option<f64>,
    pub actual_saturday_speed_mps: Option<f64>,
    pub actual_sunday_speed_mps: Option<f64>,
    /// Name of the last stop for this route+direction. None if stop data is unavailable.
    pub last_stop_name: Option<String>,
    /// Average distance between consecutive stops for this route+direction (metres).
    pub avg_stop_spacing_m: Option<f64>,
    /// Average dwell time at stops over the last 28 days (seconds). None if no data.
    pub avg_dwell_secs: Option<f64>,
}

pub async fn route_speed_by_day_type(
    db: &Database,
    agency_filter: Option<&str>,
) -> Result<Vec<RouteSpeedDayType>> {
    let base_sql = "WITH actual_by_day_type AS (
            SELECT
                agency_id,
                route_id,
                direction_id,
                AVG(CASE WHEN EXTRACT(DOW FROM service_date::date) BETWEEN 1 AND 5
                         THEN actual_speed_mps END) AS actual_weekday_speed_mps,
                AVG(CASE WHEN EXTRACT(DOW FROM service_date::date) = 6
                         THEN actual_speed_mps END) AS actual_saturday_speed_mps,
                AVG(CASE WHEN EXTRACT(DOW FROM service_date::date) = 0
                         THEN actual_speed_mps END) AS actual_sunday_speed_mps,
                AVG(avg_dwell_secs) AS avg_dwell_secs
            FROM route_speed_daily
            WHERE service_date::date >= CURRENT_DATE - INTERVAL '28 days'
              AND actual_speed_mps IS NOT NULL
            GROUP BY agency_id, route_id, direction_id
        ),
        last_stop_per_route_dir AS (
            SELECT DISTINCT ON (t.agency_id, t.route_id, COALESCE(t.direction_id, 0))
              t.agency_id, t.route_id, COALESCE(t.direction_id, 0) AS direction_id,
              s.stop_name
            FROM trips t
            JOIN (
              SELECT agency_id, trip_id, stop_id
              FROM (
                SELECT agency_id, trip_id, stop_id,
                       ROW_NUMBER() OVER (PARTITION BY agency_id, trip_id ORDER BY stop_sequence DESC) AS rn
                FROM scheduled_stops
              ) ranked
              WHERE rn = 1
            ) last_ss ON last_ss.agency_id = t.agency_id AND last_ss.trip_id = t.trip_id
            JOIN stops s ON s.agency_id = t.agency_id AND s.stop_id = last_ss.stop_id
            ORDER BY t.agency_id, t.route_id, COALESCE(t.direction_id, 0)
        )
        SELECT
          rs.agency_id,
          rs.route_id,
          r.short_name,
          r.long_name,
          rs.direction_id,
          wd.scheduled_speed_mps  AS weekday_speed_mps,
          sat.scheduled_speed_mps AS saturday_speed_mps,
          sun.scheduled_speed_mps AS sunday_speed_mps,
          act.actual_weekday_speed_mps,
          act.actual_saturday_speed_mps,
          act.actual_sunday_speed_mps,
          act.avg_dwell_secs,
          lsn.stop_name AS last_stop_name,
          rs.avg_stop_spacing_m
        FROM route_speed rs
        JOIN routes r ON r.agency_id = rs.agency_id AND r.route_id = rs.route_id
        LEFT JOIN route_speed_day_type wd
          ON wd.agency_id = rs.agency_id AND wd.route_id = rs.route_id
         AND wd.direction_id = rs.direction_id AND wd.day_type = 'weekday'
        LEFT JOIN route_speed_day_type sat
          ON sat.agency_id = rs.agency_id AND sat.route_id = rs.route_id
         AND sat.direction_id = rs.direction_id AND sat.day_type = 'saturday'
        LEFT JOIN route_speed_day_type sun
          ON sun.agency_id = rs.agency_id AND sun.route_id = rs.route_id
         AND sun.direction_id = rs.direction_id AND sun.day_type = 'sunday'
        LEFT JOIN actual_by_day_type act
          ON act.agency_id = rs.agency_id AND act.route_id = rs.route_id
         AND act.direction_id = rs.direction_id
        LEFT JOIN last_stop_per_route_dir lsn
          ON lsn.agency_id = rs.agency_id AND lsn.route_id = rs.route_id
         AND lsn.direction_id = rs.direction_id
        ";

    let order_sql = "ORDER BY rs.agency_id,
          CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST,
          r.short_name, rs.direction_id";

    let rows = sqlx::query_as(&format!(
        "{base_sql} WHERE ($1::text IS NULL OR rs.agency_id = $1) AND r.route_type IN (0, 3) {order_sql}"
    ))
    .bind(agency_filter)
    .fetch_all(&db.pool)
    .await?;
    Ok(rows)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::AgencyConfig;
    use crate::db::test_utils;

    fn test_agency() -> AgencyConfig {
        AgencyConfig {
            id: 0,
            name: "Test Agency".to_string(),
            gtfs_static_url: String::new(),
            gtfs_rt_vehicle_positions_url: None,
            gtfs_rt_trip_updates_url: None,
            gtfs_api_key: None,
            agency_utc_offset: "-04:00".to_string(),
        }
    }

    #[test]
    fn haversine_equator_one_degree_longitude() {
        // At the equator, 1 degree of longitude ≈ 111,195 m.
        let d = haversine_meters(0.0, 0.0, 0.0, 1.0);
        assert!((d - 111_195.0).abs() < 100.0, "expected ~111195 m, got {d}");
    }

    #[test]
    fn haversine_same_point_is_zero() {
        let d = haversine_meters(45.5, -73.5, 45.5, -73.5);
        assert_eq!(d, 0.0);
    }

    #[test]
    fn parse_time_secs_normal() {
        assert_eq!(parse_time_secs("08:30:00"), Some(8 * 3600 + 30 * 60));
    }

    #[test]
    fn parse_time_secs_past_midnight() {
        // 25:15:00 = 25h past midnight = 91500 s
        assert_eq!(parse_time_secs("25:15:00"), Some(25 * 3600 + 15 * 60));
    }

    #[test]
    fn parse_time_secs_invalid_returns_none() {
        assert_eq!(parse_time_secs("not-a-time"), None);
    }

    #[tokio::test]
    async fn compute_route_speed_stores_result_for_simple_route() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S1', 'Stop 1', 45.50, -73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S2', 'Stop 2', 45.51, -73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S1', 1, '08:00:00', '08:00:00')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S2', 2, '08:10:00', '08:10:00')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        compute_route_speed(&db, &test_agency()).await.unwrap();

        let row: (f64, i64) = sqlx::query_as(
            "SELECT scheduled_speed_mps, trip_count FROM route_speed WHERE route_id = 'R1' AND direction_id = 0"
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();

        let (speed, count) = row;
        assert!(
            (speed - 1.852).abs() < 0.05,
            "expected ~1.852 m/s, got {speed}"
        );
        assert_eq!(count, 1);
    }

    #[tokio::test]
    async fn compute_route_speed_averages_across_all_service_days() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        // Three trips: weekday, Saturday, Sunday — each on the same stop pair.
        sqlx::query("INSERT INTO trips VALUES ('0', 'T_WD', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T_SAT', 'R1', 'SAT', 0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T_SUN', 'R1', 'SUN', 0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S1', 'Stop 1', 45.50, -73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S2', 'Stop 2', 45.51, -73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        // Weekday: 10 min → ~1.852 m/s
        for (trip_id, dep, arr) in [
            ("T_WD", "08:00:00", "08:10:00"),
            ("T_SAT", "08:00:00", "08:20:00"), // 20 min → ~0.926 m/s
            ("T_SUN", "08:00:00", "08:15:00"), // 15 min → ~1.235 m/s
        ] {
            sqlx::query(&format!(
                "INSERT INTO scheduled_stops VALUES ('0', '{trip_id}', 'S1', 1, '{dep}', '{dep}')"
            ))
            .execute(&db.pool)
            .await
            .unwrap();
            sqlx::query(&format!(
                "INSERT INTO scheduled_stops VALUES ('0', '{trip_id}', 'S2', 2, '{arr}', '{arr}')"
            ))
            .execute(&db.pool)
            .await
            .unwrap();
        }

        compute_route_speed(&db, &test_agency()).await.unwrap();

        let row: (f64, i64) = sqlx::query_as(
            "SELECT scheduled_speed_mps, trip_count FROM route_speed WHERE route_id = 'R1' AND direction_id = 0",
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();

        let (speed, count) = row;
        // Average of 1.852, 0.926, 1.235 ≈ 1.338 m/s
        assert_eq!(count, 3, "expected one trip per service day");
        assert!(
            (speed - 1.338).abs() < 0.05,
            "expected ~1.338 m/s (average across WD/SAT/SUN), got {speed}"
        );
    }

    #[tokio::test]
    async fn compute_route_speed_stores_result_for_both_directions() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        // Trip in direction 0 (outbound): S1 → S2
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Outbound')")
            .execute(&db.pool)
            .await
            .unwrap();
        // Trip in direction 1 (inbound): S2 → S1
        sqlx::query("INSERT INTO trips VALUES ('0', 'T2', 'R1', 'WD', 1, 'Inbound')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S1', 'Stop 1', 45.50, -73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S2', 'Stop 2', 45.51, -73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        // Outbound: S1 at 08:00, S2 at 08:10
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S1', 1, '08:00:00', '08:00:00')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S2', 2, '08:10:00', '08:10:00')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        // Inbound: S2 at 09:00, S1 at 09:10
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T2', 'S2', 1, '09:00:00', '09:00:00')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T2', 'S1', 2, '09:10:00', '09:10:00')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        compute_route_speed(&db, &test_agency()).await.unwrap();

        let rows: Vec<(i64, f64)> = sqlx::query_as(
            "SELECT direction_id, scheduled_speed_mps FROM route_speed WHERE route_id = 'R1' ORDER BY direction_id",
        )
        .fetch_all(&db.pool)
        .await
        .unwrap();

        assert_eq!(
            rows.len(),
            2,
            "expected one row per direction, got {}",
            rows.len()
        );
        assert_eq!(rows[0].0, 0, "first row should be direction 0");
        assert_eq!(rows[1].0, 1, "second row should be direction 1");
        // Both directions cover the same distance in the same time, so speeds should match.
        assert!(
            (rows[0].1 - rows[1].1).abs() < 0.01,
            "outbound and inbound speeds should be equal, got {} vs {}",
            rows[0].1,
            rows[1].1
        );
    }

    #[tokio::test]
    async fn route_speed_summary_returns_route_names() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '42', 'The Answer', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at) VALUES ('0', 'R1', 0, 10.0, 5, '2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        assert_eq!(summary[0].short_name, "42");
        assert_eq!(summary[0].long_name, "The Answer");
        assert_eq!(summary[0].direction_id, 0);
        assert_eq!(summary[0].scheduled_speed_mps, 10.0);
        assert_eq!(summary[0].trip_count, 5);
    }

    #[tokio::test]
    async fn route_speed_summary_includes_live_speed_from_recent_vehicles() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '10', 'Route 10', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at) VALUES ('0', 'R1', 0, 8.0, 3, '2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        // Vehicle active now with speed 15 m/s.
        sqlx::query(
            "INSERT INTO vehicle_positions
             (agency_id, observed_at, trip_id, latitude, longitude, speed)
             VALUES ('0', NOW()::TEXT, 'T1', 45.5, -73.5, 15.0)",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        let live = summary[0].live_speed_mps.expect("expected live speed");
        assert!((live - 15.0).abs() < 0.01, "expected 15.0 m/s, got {live}");
    }

    #[tokio::test]
    async fn route_speed_summary_live_speed_is_none_when_no_recent_vehicles() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '10', 'Route 10', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at) VALUES ('0', 'R1', 0, 8.0, 3, '2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        // Vehicle last seen 2 hours ago — outside the 1-hour window.
        sqlx::query(
            "INSERT INTO vehicle_positions
             (agency_id, observed_at, trip_id, latitude, longitude, speed)
             VALUES ('0', (NOW() - INTERVAL '2 hours')::TEXT, 'T1', 45.5, -73.5, 15.0)",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        assert!(
            summary[0].live_speed_mps.is_none(),
            "expected None for stale vehicle"
        );
    }

    #[tokio::test]
    async fn compute_route_speed_daily_stores_actual_speed() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S1', 'Stop 1', 45.50, -73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S2', 'Stop 2', 45.51, -73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S1', 1, '08:00:00', '08:00:00')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S2', 2, '08:10:00', '08:10:00')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        let t_s1: i64 = 1767225600;
        let t_s2: i64 = t_s1 + 900;
        sqlx::query(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix)
             VALUES ('0', '2026-01-01T08:00:00Z', 'T1', 'S1', 1, $1)",
        )
        .bind(t_s1)
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix)
             VALUES ('0', '2026-01-01T08:15:00Z', 'T1', 'S2', 2, $1)",
        )
        .bind(t_s2)
        .execute(&db.pool)
        .await
        .unwrap();

        let date = chrono::NaiveDate::from_ymd_opt(2026, 1, 1).unwrap();
        compute_route_speed_daily(&db, &test_agency(), date)
            .await
            .unwrap();

        let row: (f64, i64) = sqlx::query_as(
            "SELECT actual_speed_mps, trip_count
             FROM route_speed_daily
             WHERE route_id = 'R1' AND service_date = '2026-01-01' AND direction_id = 0",
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();

        let (speed, count) = row;
        assert!(
            (speed - 1.235).abs() < 0.05,
            "expected ~1.235 m/s, got {speed}"
        );
        assert_eq!(count, 1);
    }

    #[tokio::test]
    async fn compute_route_speed_daily_stores_variant_id() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool).await.unwrap();
        // Trip T1 with variant_id set
        sqlx::query(
            "INSERT INTO trips (agency_id, trip_id, route_id, service_id, direction_id, variant_id)
             VALUES ('0', 'T1', 'R1', 'WD', 0, 'VAR1')",
        ).execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S1', 'Stop 1', 45.50, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S2', 'Stop 2', 45.51, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S1', 1, '08:00:00', '08:00:00')",
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S2', 2, '08:10:00', '08:10:00')",
        ).execute(&db.pool).await.unwrap();

        let t_s1: i64 = 1767225600;
        let t_s2: i64 = t_s1 + 900;
        sqlx::query(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix)
             VALUES ('0', '2026-01-01T08:00:00Z', 'T1', 'S1', 1, $1)",
        ).bind(t_s1).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix)
             VALUES ('0', '2026-01-01T08:15:00Z', 'T1', 'S2', 2, $1)",
        ).bind(t_s2).execute(&db.pool).await.unwrap();

        let date = chrono::NaiveDate::from_ymd_opt(2026, 1, 1).unwrap();
        compute_route_speed_daily(&db, &test_agency(), date).await.unwrap();

        let variant_id: (String,) = sqlx::query_as(
            "SELECT variant_id FROM route_speed_daily WHERE route_id = 'R1' AND service_date = '2026-01-01'",
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();
        assert_eq!(variant_id.0, "VAR1");
    }

    #[tokio::test]
    async fn route_speed_summary_includes_actual_speed_from_history() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '99', 'Route 99', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at) VALUES ('0', 'R1', 0, 8.0, 3, '2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, computed_at)
             VALUES ('0', 'R1', (CURRENT_DATE - 1)::TEXT, 0, 6.5, 10, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        let actual = summary[0].actual_speed_mps.expect("expected actual speed");
        assert!(
            (actual - 6.5).abs() < 0.01,
            "expected 6.5 m/s, got {actual}"
        );
    }

    #[tokio::test]
    async fn route_speed_summary_actual_speed_is_none_when_no_history() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '99', 'Route 99', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at) VALUES ('0', 'R1', 0, 8.0, 3, '2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        assert!(
            summary[0].actual_speed_mps.is_none(),
            "expected None without history"
        );
    }

    #[tokio::test]
    async fn route_speed_summary_filters_by_agency() {
        let td = test_utils::setup().await;
        let db = td.db;
        sqlx::query("INSERT INTO routes VALUES ('stm', 'R1', '15', 'Papineau', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO routes VALUES ('rtl', 'R2', '10', 'Longueuil', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
             VALUES ('stm', 'R1', 0, 5.5, 10, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
             VALUES ('rtl', 'R2', 0, 6.0, 8, '2026-01-01T00:00:00Z')"
        ).execute(&db.pool).await.unwrap();

        let all = route_speed_summary(&db, None).await.unwrap();
        assert_eq!(all.len(), 2);

        let stm = route_speed_summary(&db, Some("stm")).await.unwrap();
        assert_eq!(stm.len(), 1);
        assert_eq!(stm[0].agency_id, "stm");

        let rtl = route_speed_summary(&db, Some("rtl")).await.unwrap();
        assert_eq!(rtl.len(), 1);
        assert_eq!(rtl[0].agency_id, "rtl");
    }

    #[tokio::test]
    async fn compute_route_speed_hourly_stores_avg_speed_per_hour() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S1', 'Stop 1', 45.50, -73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S2', 'Stop 2', 45.51, -73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S1', 1, '08:00:00', '08:00:00')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S2', 2, '08:10:00', '08:10:00')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        // Use timestamps within the last 4 hours so the hourly query picks them up.
        let t_s1 = chrono::Utc::now().timestamp() - 1800; // 30 min ago
        let t_s2 = t_s1 + 900; // 15 min later

        sqlx::query(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix)
             VALUES ('0', NOW()::TEXT, 'T1', 'S1', 1, $1)",
        )
        .bind(t_s1)
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix)
             VALUES ('0', NOW()::TEXT, 'T1', 'S2', 2, $1)",
        )
        .bind(t_s2)
        .execute(&db.pool)
        .await
        .unwrap();

        compute_route_speed_hourly(&db, &test_agency())
            .await
            .unwrap();

        let row: (f64, i64) = sqlx::query_as(
            "SELECT actual_speed_mps, trip_count
             FROM route_speed_hourly
             WHERE agency_id = '0' AND route_id = 'R1' AND direction_id = 0",
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();

        let (speed, count) = row;
        // Distance S1→S2 ≈ 1111 m over 900 s ≈ 1.235 m/s
        assert!(
            (speed - 1.235).abs() < 0.05,
            "expected ~1.235 m/s, got {speed}"
        );
        assert_eq!(count, 1);
    }

    fn make_summary(scheduled: f64, actual: Option<f64>) -> RouteSpeedSummary {
        RouteSpeedSummary {
            agency_id: "0".into(),
            route_id: "R1".into(),
            short_name: "1".into(),
            long_name: "Route 1".into(),
            direction_id: 0,
            scheduled_speed_mps: scheduled,
            trip_count: 1,
            live_speed_mps: None,
            actual_speed_mps: actual,
            last_stop_name: None,
        }
    }

    #[test]
    fn scheduled_speed_kmh_converts_correctly() {
        let s = make_summary(10.0, None);
        // 10 m/s * 3.6 = 36.0 km/h
        assert_eq!(s.scheduled_speed_kmh(), 36.0);
    }

    #[test]
    fn speed_deficit_pct_is_none_without_actual_data() {
        let s = make_summary(10.0, None);
        assert!(s.speed_deficit_pct().is_none());
    }

    #[test]
    fn speed_deficit_pct_positive_when_slower_than_scheduled() {
        // scheduled 10 m/s, actual 8 m/s → 20% slower
        let s = make_summary(10.0, Some(8.0));
        let pct = s.speed_deficit_pct().unwrap();
        assert!((pct - 20.0).abs() < 0.01, "expected 20.0%, got {pct}");
    }

    #[test]
    fn speed_deficit_pct_negative_when_faster_than_scheduled() {
        // scheduled 10 m/s, actual 11 m/s → -10% (faster)
        let s = make_summary(10.0, Some(11.0));
        let pct = s.speed_deficit_pct().unwrap();
        assert!((pct - (-10.0)).abs() < 0.01, "expected -10.0%, got {pct}");
    }

    #[test]
    fn speed_vs_scheduled_display_shows_slower() {
        let s = make_summary(10.0, Some(8.0));
        assert_eq!(s.speed_vs_scheduled_display(), "20% slower");
    }

    #[test]
    fn speed_vs_scheduled_display_shows_faster() {
        let s = make_summary(10.0, Some(11.0));
        assert_eq!(s.speed_vs_scheduled_display(), "10% faster");
    }

    #[test]
    fn speed_vs_scheduled_display_shows_on_pace_within_one_pct() {
        let s = make_summary(10.0, Some(10.05)); // 0.5% faster — within threshold
        assert_eq!(s.speed_vs_scheduled_display(), "On pace");
    }

    #[test]
    fn speed_vs_scheduled_display_is_dash_without_data() {
        let s = make_summary(10.0, None);
        assert_eq!(s.speed_vs_scheduled_display(), "—");
    }

    #[tokio::test]
    async fn compute_route_speed_by_day_type_stores_speed_per_day_type() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T_WD',  'R1', 'WD',  0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T_SAT', 'R1', 'SAT', 0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T_SUN', 'R1', 'SUN', 0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();

        // Calendar: WD = Mon-Fri, SAT = Saturday only, SUN = Sunday only.
        sqlx::query("INSERT INTO calendar VALUES ('0','WD', true,true,true,true,true,false,false)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO calendar VALUES ('0','SAT',false,false,false,false,false,true,false)",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO calendar VALUES ('0','SUN',false,false,false,false,false,false,true)",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        // Two stops ~1111 m apart.
        sqlx::query("INSERT INTO stops VALUES ('0','S1','Stop 1',45.50,-73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0','S2','Stop 2',45.51,-73.50)")
            .execute(&db.pool)
            .await
            .unwrap();

        // WD: 10 min → ~1.852 m/s; SAT: 20 min → ~0.926 m/s; SUN: 15 min → ~1.235 m/s
        for (trip_id, arr) in [
            ("T_WD", "08:10:00"),
            ("T_SAT", "08:20:00"),
            ("T_SUN", "08:15:00"),
        ] {
            sqlx::query(&format!(
                "INSERT INTO scheduled_stops VALUES ('0','{trip_id}','S1',1,'08:00:00','08:00:00')"
            ))
            .execute(&db.pool)
            .await
            .unwrap();
            sqlx::query(&format!(
                "INSERT INTO scheduled_stops VALUES ('0','{trip_id}','S2',2,'{arr}','{arr}')"
            ))
            .execute(&db.pool)
            .await
            .unwrap();
        }

        compute_route_speed_by_day_type(&db, &test_agency())
            .await
            .unwrap();

        let rows: Vec<(String, f64, i64)> = sqlx::query_as(
            "SELECT day_type, scheduled_speed_mps, trip_count
             FROM route_speed_day_type
             WHERE agency_id = '0' AND route_id = 'R1' AND direction_id = 0
             ORDER BY day_type",
        )
        .fetch_all(&db.pool)
        .await
        .unwrap();

        assert_eq!(rows.len(), 3, "expected one row per day type");

        let sat = rows.iter().find(|r| r.0 == "saturday").unwrap();
        let sun = rows.iter().find(|r| r.0 == "sunday").unwrap();
        let wd = rows.iter().find(|r| r.0 == "weekday").unwrap();

        assert_eq!(wd.2, 1, "weekday trip_count");
        assert_eq!(sat.2, 1, "saturday trip_count");
        assert_eq!(sun.2, 1, "sunday trip_count");

        assert!(
            (wd.1 - 1.852).abs() < 0.05,
            "weekday speed ~1.852 m/s, got {}",
            wd.1
        );
        assert!(
            (sat.1 - 0.926).abs() < 0.05,
            "saturday speed ~0.926 m/s, got {}",
            sat.1
        );
        assert!(
            (sun.1 - 1.235).abs() < 0.05,
            "sunday speed ~1.235 m/s, got {}",
            sun.1
        );
    }

    #[tokio::test]
    async fn route_speed_by_day_type_returns_scheduled_speeds_from_calendar() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0','R1','1','Route 1',3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at) VALUES ('0','R1',0,10.0,5,'2026-01-01T00:00:00Z')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_speed_day_type
             (agency_id,route_id,direction_id,day_type,scheduled_speed_mps,trip_count,computed_at)
             VALUES ('0','R1',0,'weekday',8.0,10,'2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_speed_day_type
             (agency_id,route_id,direction_id,day_type,scheduled_speed_mps,trip_count,computed_at)
             VALUES ('0','R1',0,'saturday',6.0,5,'2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        let rows = route_speed_by_day_type(&db, Some("0")).await.unwrap();

        assert_eq!(rows.len(), 1);
        let r = &rows[0];
        assert!(
            (r.weekday_speed_mps.unwrap() - 8.0).abs() < 0.01,
            "weekday speed should be 8.0, got {:?}",
            r.weekday_speed_mps
        );
        assert!(
            (r.saturday_speed_mps.unwrap() - 6.0).abs() < 0.01,
            "saturday speed should be 6.0, got {:?}",
            r.saturday_speed_mps
        );
        assert!(
            r.sunday_speed_mps.is_none(),
            "sunday should be None when no row exists"
        );
    }

    /// Integration test: calendar_dates.txt only (no calendar.txt) → speed by day type is computed.
    /// This covers the STM case where the feed uses calendar_dates exclusively.
    #[tokio::test]
    async fn compute_route_speed_by_day_type_with_calendar_dates_only() {
        use crate::gtfs::static_feed::load_calendar_from_dates;
        use chrono::NaiveDate;
        use gtfs_structures::{CalendarDate, Exception};
        use std::collections::HashMap;

        let td = test_utils::setup().await;
        let db = td.db;

        // Seed routes, trips with service_ids that only appear in calendar_dates.
        sqlx::query("INSERT INTO routes VALUES ('0','R1','1','Route 1',3)")
            .execute(&db.pool)
            .await
            .unwrap();
        for (trip_id, svc) in [
            ("T_WD", "SVC_WD"),
            ("T_SAT", "SVC_SAT"),
            ("T_SUN", "SVC_SUN"),
        ] {
            sqlx::query(&format!(
                "INSERT INTO trips VALUES ('0','{trip_id}','R1','{svc}',0,'Dest')"
            ))
            .execute(&db.pool)
            .await
            .unwrap();
        }
        for (sid, lat) in [("S1", 45.50_f64), ("S2", 45.51_f64)] {
            sqlx::query(&format!(
                "INSERT INTO stops VALUES ('0','{sid}','Stop',$1,-73.50)"
            ))
            .bind(lat)
            .execute(&db.pool)
            .await
            .unwrap();
        }
        // WD: 10 min → ~1.852 m/s  SAT: 20 min → ~0.926 m/s  SUN: 15 min → ~1.235 m/s
        for (trip_id, arr) in [
            ("T_WD", "08:10:00"),
            ("T_SAT", "08:20:00"),
            ("T_SUN", "08:15:00"),
        ] {
            sqlx::query(&format!(
                "INSERT INTO scheduled_stops VALUES ('0','{trip_id}','S1',1,'08:00:00','08:00:00')"
            ))
            .execute(&db.pool)
            .await
            .unwrap();
            sqlx::query(&format!(
                "INSERT INTO scheduled_stops VALUES ('0','{trip_id}','S2',2,'{arr}','{arr}')"
            ))
            .execute(&db.pool)
            .await
            .unwrap();
        }

        // Synthesize calendar from calendar_dates — no calendar.txt entries exist.
        // 2026-01-05 = Monday, 2026-01-10 = Saturday, 2026-01-11 = Sunday
        let mut calendar_dates: HashMap<String, Vec<CalendarDate>> = HashMap::new();
        calendar_dates.insert(
            "SVC_WD".to_string(),
            vec![CalendarDate {
                service_id: "SVC_WD".to_string(),
                date: NaiveDate::from_ymd_opt(2026, 1, 5).unwrap(),
                exception_type: Exception::Added,
            }],
        );
        calendar_dates.insert(
            "SVC_SAT".to_string(),
            vec![CalendarDate {
                service_id: "SVC_SAT".to_string(),
                date: NaiveDate::from_ymd_opt(2026, 1, 10).unwrap(),
                exception_type: Exception::Added,
            }],
        );
        calendar_dates.insert(
            "SVC_SUN".to_string(),
            vec![CalendarDate {
                service_id: "SVC_SUN".to_string(),
                date: NaiveDate::from_ymd_opt(2026, 1, 11).unwrap(),
                exception_type: Exception::Added,
            }],
        );
        let mut tx = db.pool.begin().await.unwrap();
        load_calendar_from_dates(&mut tx, "0", &calendar_dates)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        compute_route_speed_by_day_type(&db, &test_agency())
            .await
            .unwrap();

        let rows: Vec<(String, f64)> = sqlx::query_as(
            "SELECT day_type, scheduled_speed_mps
             FROM route_speed_day_type
             WHERE agency_id = '0' AND route_id = 'R1' AND direction_id = 0
             ORDER BY day_type",
        )
        .fetch_all(&db.pool)
        .await
        .unwrap();

        assert_eq!(
            rows.len(),
            3,
            "expected one row per day type, got {}",
            rows.len()
        );

        let wd = rows.iter().find(|r| r.0 == "weekday").unwrap();
        let sat = rows.iter().find(|r| r.0 == "saturday").unwrap();
        let sun = rows.iter().find(|r| r.0 == "sunday").unwrap();

        assert!(
            (wd.1 - 1.852).abs() < 0.05,
            "weekday ~1.852 m/s, got {}",
            wd.1
        );
        assert!(
            (sat.1 - 0.926).abs() < 0.05,
            "saturday ~0.926 m/s, got {}",
            sat.1
        );
        assert!(
            (sun.1 - 1.235).abs() < 0.05,
            "sunday ~1.235 m/s, got {}",
            sun.1
        );
    }

    #[tokio::test]
    async fn route_speed_by_day_type_filters_by_agency() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('agency_a', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO routes VALUES ('agency_b', 'R2', '2', 'Route 2', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at) VALUES ('agency_a', 'R1', 0, 10.0, 1, '2026-01-01')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at) VALUES ('agency_b', 'R2', 0, 12.0, 1, '2026-01-01')")
            .execute(&db.pool)
            .await
            .unwrap();

        let rows = route_speed_by_day_type(&db, Some("agency_a"))
            .await
            .unwrap();
        assert_eq!(rows.len(), 1);
        assert_eq!(rows[0].agency_id, "agency_a");
        assert_eq!(rows[0].route_id, "R1");
    }

    #[tokio::test]
    async fn route_speed_by_day_type_includes_actual_speed_by_day_type() {
        let td = test_utils::setup().await;
        let db = td.db;

        // Set up route, trip, stops
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S1', 'Stop 1', 45.50, -73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S2', 'Stop 2', 45.51, -73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S1', 1, '08:00:00', '08:00:00')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S2', 2, '08:10:00', '08:10:00')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        compute_route_speed(&db, &test_agency()).await.unwrap();

        // Seed route_speed_day_type for the scheduled side
        sqlx::query(
            "INSERT INTO route_speed_day_type
             (agency_id, route_id, direction_id, day_type, scheduled_speed_mps, trip_count, computed_at)
             VALUES ('0', 'R1', 0, 'weekday', 1.852, 1, '2026-01-01')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        // Seed route_speed_daily with a recent weekday row (2026-04-07 = Monday)
        // and an old row that should be excluded (2024-01-01)
        sqlx::query(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, computed_at)
             VALUES ('0', 'R1', '2026-04-07', 0, 2.0, 1, '2026-04-07')",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, computed_at)
             VALUES ('0', 'R1', '2024-01-01', 0, 99.0, 1, '2024-01-01')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        let rows = route_speed_by_day_type(&db, None).await.unwrap();
        assert_eq!(rows.len(), 1);
        let row = &rows[0];
        assert!(
            row.actual_weekday_speed_mps.is_some(),
            "expected weekday actual speed to be populated"
        );
        let actual_kmh = row.actual_weekday_speed_mps.unwrap() * 3.6;
        assert!(
            (actual_kmh - 7.2).abs() < 0.1,
            "expected ~7.2 km/h (2.0 m/s), got {actual_kmh}"
        );
        assert!(
            row.actual_saturday_speed_mps.is_none(),
            "expected saturday actual to be None (no saturday data)"
        );
    }

    #[tokio::test]
    async fn route_speed_summary_includes_last_stop_name() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '99', 'Route 99', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0','S1','First Stop',45.50,-73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0','S2','Last Stop',45.51,-73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0','T1','S1',1,'08:00:00','08:00:00')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0','T1','S2',2,'08:10:00','08:10:00')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at) VALUES ('0', 'R1', 0, 8.0, 3, '2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        let summary = route_speed_summary(&db, None).await.unwrap();

        assert_eq!(summary.len(), 1);
        assert_eq!(
            summary[0].last_stop_name.as_deref(),
            Some("Last Stop"),
            "expected last stop name to be the terminal stop"
        );
    }

    #[tokio::test]
    async fn route_speed_by_day_type_includes_last_stop_name() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0','S1','First Stop',45.50,-73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0','S2','Last Stop',45.51,-73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0','T1','S1',1,'08:00:00','08:00:00')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0','T1','S2',2,'08:10:00','08:10:00')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_speed
             (agency_id, route_id, direction_id, scheduled_speed_mps, avg_stop_spacing_m, trip_count, computed_at)
             VALUES ('0', 'R1', 0, 8.0, 1111.0, 1, '2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        let rows = route_speed_by_day_type(&db, None).await.unwrap();

        assert_eq!(rows.len(), 1);
        assert_eq!(
            rows[0].last_stop_name.as_deref(),
            Some("Last Stop"),
            "expected last stop name to be the terminal stop"
        );
    }

    #[tokio::test]
    async fn route_speed_by_day_type_includes_avg_stop_spacing() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();
        // S1→S2: ~1111 m apart (0.01° latitude)
        sqlx::query("INSERT INTO stops VALUES ('0','S1','Stop 1',45.50,-73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0','S2','Stop 2',45.51,-73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0','T1','S1',1,'08:00:00','08:00:00')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0','T1','S2',2,'08:10:00','08:10:00')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO route_speed
             (agency_id, route_id, direction_id, scheduled_speed_mps, avg_stop_spacing_m, trip_count, computed_at)
             VALUES ('0', 'R1', 0, 8.0, 1111.0, 1, '2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        let rows = route_speed_by_day_type(&db, None).await.unwrap();

        assert_eq!(rows.len(), 1);
        let spacing = rows[0]
            .avg_stop_spacing_m
            .expect("expected avg_stop_spacing_m to be Some");
        assert!(
            (spacing - 1111.0).abs() < 10.0,
            "expected ~1111 m spacing, got {spacing}"
        );
    }

    #[tokio::test]
    async fn route_speed_by_day_type_uses_persisted_avg_stop_spacing() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0','S1','Stop 1',45.50,-73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0','S2','Stop 2',45.51,-73.50)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0','T1','S1',1,'08:00:00','08:00:00')")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0','T1','S2',2,'08:10:00','08:10:00')")
            .execute(&db.pool)
            .await
            .unwrap();

        compute_route_speed(&db, &test_agency()).await.unwrap();

        let persisted: (Option<f64>,) =
            sqlx::query_as("SELECT avg_stop_spacing_m FROM route_speed WHERE route_id = 'R1'")
                .fetch_one(&db.pool)
                .await
                .unwrap();
        assert!(
            persisted.0.is_some(),
            "compute_route_speed should persist avg_stop_spacing_m"
        );

        sqlx::query("DELETE FROM scheduled_stops WHERE agency_id = '0'")
            .execute(&db.pool)
            .await
            .unwrap();

        let rows = route_speed_by_day_type(&db, None).await.unwrap();

        assert_eq!(rows.len(), 1);
        assert_eq!(rows[0].avg_stop_spacing_m, persisted.0);
    }

    #[tokio::test]
    async fn route_speed_by_day_type_aggregates_avg_dwell_secs() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed
             (agency_id, route_id, direction_id, scheduled_speed_mps, avg_stop_spacing_m, trip_count, computed_at)
             VALUES ('0', 'R1', 0, 8.0, 500.0, 1, '2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool).await.unwrap();
        // Two weekday rows within the 28-day window with avg_dwell_secs
        sqlx::query(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, avg_dwell_secs, computed_at)
             VALUES
               ('0', 'R1', (CURRENT_DATE - INTERVAL '2 days')::TEXT, 0, 5.0, 10, 25.0, '2026-01-01T00:00:00Z'),
               ('0', 'R1', (CURRENT_DATE - INTERVAL '3 days')::TEXT, 0, 5.0, 10, 35.0, '2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool).await.unwrap();

        let rows = route_speed_by_day_type(&db, None).await.unwrap();
        assert_eq!(rows.len(), 1);
        let dwell = rows[0].avg_dwell_secs.expect("expected avg_dwell_secs to be Some");
        assert!(
            (dwell - 30.0).abs() < 0.01,
            "expected avg dwell ~30.0, got {dwell}"
        );
    }

    #[tokio::test]
    async fn route_speed_by_day_type_avg_dwell_secs_null_when_no_dwell_data() {
        let td = test_utils::setup().await;
        let db = td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed
             (agency_id, route_id, direction_id, scheduled_speed_mps, avg_stop_spacing_m, trip_count, computed_at)
             VALUES ('0', 'R1', 0, 8.0, 500.0, 1, '2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, avg_dwell_secs, computed_at)
             VALUES ('0', 'R1', (CURRENT_DATE - INTERVAL '1 day')::TEXT, 0, 5.0, 10, NULL, '2026-01-01T00:00:00Z')",
        )
        .execute(&db.pool).await.unwrap();

        let rows = route_speed_by_day_type(&db, None).await.unwrap();
        assert_eq!(rows.len(), 1);
        assert!(rows[0].avg_dwell_secs.is_none());
    }

    #[tokio::test]
    async fn compute_route_speed_daily_stores_avg_dwell_secs() {
        let td = test_utils::setup().await;
        let db = td.db;
        let agency = test_agency();

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0','S1','Stop 1',45.50,-73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0','S2','Stop 2',45.51,-73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0','T1','S1',1,'08:00:00','08:00:00')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0','T1','S2',2,'08:10:00','08:10:00')")
            .execute(&db.pool).await.unwrap();
        // S1: arrive 1000, depart 1030 → dwell_secs = 30
        // S2: arrive 1700, depart 1720 → dwell_secs = 20
        // avg = 25.0
        sqlx::query(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix)
             VALUES ('0','2026-04-01T08:00:00Z','T1','S1',1,1000,1030)",
        )
        .execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix)
             VALUES ('0','2026-04-01T08:10:00Z','T1','S2',2,1700,1720)",
        )
        .execute(&db.pool).await.unwrap();

        let service_date = chrono::NaiveDate::from_ymd_opt(2026, 4, 1).unwrap();
        compute_route_speed_daily(&db, &agency, service_date)
            .await
            .unwrap();

        let dwell: Option<f64> = sqlx::query_scalar(
            "SELECT avg_dwell_secs FROM route_speed_daily WHERE agency_id = '0' AND route_id = 'R1'",
        )
        .fetch_one(&db.pool)
        .await
        .unwrap();

        let dwell = dwell.expect("expected avg_dwell_secs to be Some");
        assert!(
            (dwell - 25.0).abs() < 0.01,
            "expected avg dwell ~25.0 (30+20)/2, got {dwell}"
        );
    }

    #[tokio::test]
    async fn compute_route_speed_daily_deduplicates_dwell_per_stop_visit() {
        let td = test_utils::setup().await;
        let db = td.db;
        let agency = test_agency();

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Dest')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0','S1','Stop 1',45.50,-73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0','S2','Stop 2',45.51,-73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0','T1','S1',1,'08:00:00','08:00:00')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('0','T1','S2',2,'08:10:00','08:10:00')")
            .execute(&db.pool).await.unwrap();

        // S1: dwell = 30s, but appears 5 times due to repeated RT polls
        for i in 0..5i64 {
            sqlx::query(
                "INSERT INTO stop_time_events
                 (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix)
                 VALUES ('0','2026-04-01T08:00:00Z','T1','S1',1,1000,1030)",
            )
            .execute(&db.pool).await.unwrap();
            let _ = i;
        }
        // S2: dwell = 20s, appears once
        sqlx::query(
            "INSERT INTO stop_time_events
             (agency_id, observed_at, trip_id, stop_id, stop_sequence, arrival_time_unix, departure_time_unix)
             VALUES ('0','2026-04-01T08:10:00Z','T1','S2',2,1700,1720)",
        )
        .execute(&db.pool).await.unwrap();

        // Without dedup: (30×5 + 20×1)/6 = 170/6 ≈ 28.3s — wrong
        // With dedup:    (30 + 20)/2       = 25.0s — correct
        let service_date = chrono::NaiveDate::from_ymd_opt(2026, 4, 1).unwrap();
        compute_route_speed_daily(&db, &agency, service_date).await.unwrap();

        let dwell: Option<f64> = sqlx::query_scalar(
            "SELECT avg_dwell_secs FROM route_speed_daily WHERE agency_id = '0' AND route_id = 'R1'",
        )
        .fetch_one(&db.pool).await.unwrap();

        let dwell = dwell.expect("expected avg_dwell_secs to be Some");
        assert!(
            (dwell - 25.0).abs() < 0.01,
            "expected 25.0 (deduped), got {dwell} — duplicate rows are inflating the average"
        );
    }

    #[test]
    fn build_direction_spacings_flags_outliers() {
        // distances: [100, 100, 300] → avg = 166.7, threshold = 1.5 × 166.7 = 250
        // only the 300m segment is an outlier
        let rows = vec![
            StopSpacingEntry {
                variant_id: "VAR0".into(),
                direction_id: 0,
                is_primary: true,
                trip_count: 1,
                to_stop_name: "A".into(),
                distance_m: Some(100.0),
                is_first: true,
                is_last: false,
            },
            StopSpacingEntry {
                variant_id: "VAR0".into(),
                direction_id: 0,
                is_primary: true,
                trip_count: 1,
                to_stop_name: "B".into(),
                distance_m: Some(100.0),
                is_first: true,
                is_last: false,
            },
            StopSpacingEntry {
                variant_id: "VAR0".into(),
                direction_id: 0,
                is_primary: true,
                trip_count: 1,
                to_stop_name: "C".into(),
                distance_m: Some(300.0),
                is_first: true,
                is_last: false,
            },
            StopSpacingEntry {
                variant_id: "VAR0".into(),
                direction_id: 0,
                is_primary: true,
                trip_count: 1,
                to_stop_name: "D".into(),
                distance_m: Some(100.0),
                is_first: true,
                is_last: false,
            },
        ];
        let result = build_direction_spacings(rows);
        assert_eq!(result.len(), 1);
        let s = &result[0].spacings;
        assert!(!s[0].is_outlier, "100m should not be an outlier");
        assert!(!s[1].is_outlier, "100m should not be an outlier");
        assert!(s[2].is_outlier, "300m > 250 threshold should be an outlier");
    }

    #[test]
    fn build_direction_spacings_sets_first_and_direction_names() {
        let rows = vec![
            StopSpacingEntry {
                variant_id: "VAR0".into(),
                direction_id: 0,
                is_primary: true,
                trip_count: 1,
                to_stop_name: "Origin".into(),
                distance_m: Some(100.0),
                is_first: true,
                is_last: false,
            },
            StopSpacingEntry {
                variant_id: "VAR0".into(),
                direction_id: 0,
                is_primary: true,
                trip_count: 1,
                to_stop_name: "Middle".into(),
                distance_m: Some(100.0),
                is_first: false,
                is_last: false,
            },
            StopSpacingEntry {
                variant_id: "VAR0".into(),
                direction_id: 0,
                is_primary: true,
                trip_count: 1,
                to_stop_name: "Terminal".into(),
                distance_m: Some(100.0),
                is_first: false,
                is_last: true,
            },
        ];
        let result = build_direction_spacings(rows);
        assert_eq!(result[0].first_stop_name, "Origin");
        assert_eq!(result[0].direction_name, "Origin → Terminal");
    }

    #[test]
    fn build_direction_spacings_groups_two_directions() {
        let rows = vec![
            StopSpacingEntry {
                variant_id: "VAR0".into(),
                direction_id: 0,
                is_primary: true,
                trip_count: 1,
                to_stop_name: "A".into(),
                distance_m: Some(100.0),
                is_first: true,
                is_last: false,
            },
            StopSpacingEntry {
                variant_id: "VAR0".into(),
                direction_id: 0,
                is_primary: true,
                trip_count: 1,
                to_stop_name: "B".into(),
                distance_m: Some(300.0),
                is_first: true,
                is_last: false,
            },
            StopSpacingEntry {
                variant_id: "VAR1".into(),
                direction_id: 1,
                is_primary: true,
                trip_count: 1,
                to_stop_name: "X".into(),
                distance_m: Some(100.0),
                is_first: true,
                is_last: false,
            },
            StopSpacingEntry {
                variant_id: "VAR1".into(),
                direction_id: 1,
                is_primary: true,
                trip_count: 1,
                to_stop_name: "Y".into(),
                distance_m: Some(100.0),
                is_first: true,
                is_last: false,
            },
        ];
        let result = build_direction_spacings(rows);
        assert_eq!(result.len(), 2);
        assert_eq!(result[0].direction_id, 0);
        assert_eq!(result[1].direction_id, 1);
    }

    #[test]
    fn build_direction_spacings_width_px_max_is_200() {
        let rows = vec![
            StopSpacingEntry {
                variant_id: "VAR0".into(),
                direction_id: 0,
                is_primary: true,
                trip_count: 1,
                to_stop_name: "A".into(),
                distance_m: Some(100.0),
                is_first: true,
                is_last: false,
            },
            StopSpacingEntry {
                variant_id: "VAR0".into(),
                direction_id: 0,
                is_primary: true,
                trip_count: 1,
                to_stop_name: "B".into(),
                distance_m: Some(300.0),
                is_first: true,
                is_last: false,
            },
            StopSpacingEntry {
                variant_id: "VAR0".into(),
                direction_id: 0,
                is_primary: true,
                trip_count: 1,
                to_stop_name: "C".into(),
                distance_m: Some(100.0),
                is_first: true,
                is_last: false,
            },
        ];
        let result = build_direction_spacings(rows);
        let spacings = &result[0].spacings;
        assert_eq!(
            spacings[1].width_px, 200,
            "max distance should map to 200px"
        );
        assert!(
            spacings[0].width_px < 200,
            "smaller distance should map to less than 200px"
        );
    }

    #[test]
    fn build_direction_spacings_formats_direction_name_as_first_to_last() {
        let rows = vec![
            StopSpacingEntry {
                variant_id: "VAR1".into(),
                direction_id: 0,
                is_primary: true,
                trip_count: 5,
                to_stop_name: "First Stop".into(),
                distance_m: None,
                is_first: true,
                is_last: false,
            },
            StopSpacingEntry {
                variant_id: "VAR1".into(),
                direction_id: 0,
                is_primary: true,
                trip_count: 5,
                to_stop_name: "Last Stop".into(),
                distance_m: Some(500.0),
                is_first: false,
                is_last: true,
            },
        ];
        let result = build_direction_spacings(rows);
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].direction_name, "First Stop → Last Stop");
    }

    #[test]
    fn build_direction_spacings_carries_variant_fields() {
        let rows = vec![
            StopSpacingEntry {
                variant_id: "VARABC".into(),
                direction_id: 1,
                is_primary: false,
                trip_count: 3,
                to_stop_name: "A".into(),
                distance_m: None,
                is_first: true,
                is_last: false,
            },
            StopSpacingEntry {
                variant_id: "VARABC".into(),
                direction_id: 1,
                is_primary: false,
                trip_count: 3,
                to_stop_name: "B".into(),
                distance_m: Some(300.0),
                is_first: false,
                is_last: true,
            },
        ];
        let result = build_direction_spacings(rows);
        assert_eq!(result[0].variant_id, "VARABC");
        assert_eq!(result[0].direction_id, 1);
        assert!(!result[0].is_primary);
        assert_eq!(result[0].trip_count, 3);
    }

    #[test]
    fn build_direction_trends_buckets_weekday_saturday_sunday() {
        // 2024-01-01 = Monday (weekday), 2024-01-06 = Saturday, 2024-01-07 = Sunday
        let rows = vec![
            SpeedTrendRow {
                direction_id: 0,
                service_date: "2024-01-01".into(),
                actual_speed_mps: 5.0,
                scheduled_speed_mps: Some(6.0),
            },
            SpeedTrendRow {
                direction_id: 0,
                service_date: "2024-01-06".into(),
                actual_speed_mps: 6.0,
                scheduled_speed_mps: Some(7.0),
            },
            SpeedTrendRow {
                direction_id: 0,
                service_date: "2024-01-07".into(),
                actual_speed_mps: 7.0,
                scheduled_speed_mps: Some(8.0),
            },
        ];
        let result = build_direction_trends(rows);
        assert_eq!(result.len(), 1);
        let t = &result[0];
        assert_eq!(t.weekday, vec![("2024-01-01".to_string(), 5.0, Some(6.0))]);
        assert_eq!(t.saturday, vec![("2024-01-06".to_string(), 6.0, Some(7.0))]);
        assert_eq!(t.sunday, vec![("2024-01-07".to_string(), 7.0, Some(8.0))]);
    }

    #[test]
    fn build_direction_trends_groups_two_directions() {
        let rows = vec![
            SpeedTrendRow {
                direction_id: 0,
                service_date: "2024-01-01".into(),
                actual_speed_mps: 5.0,
                scheduled_speed_mps: Some(6.0),
            },
            SpeedTrendRow {
                direction_id: 1,
                service_date: "2024-01-01".into(),
                actual_speed_mps: 4.0,
                scheduled_speed_mps: Some(5.0),
            },
        ];
        let result = build_direction_trends(rows);
        assert_eq!(result.len(), 2);
        // sorted by direction_id
        assert_eq!(result[0].direction_id, 0);
        assert_eq!(result[1].direction_id, 1);
        assert_eq!(result[0].weekday[0].1, 5.0);
        assert_eq!(result[1].weekday[0].1, 4.0);
    }

    #[tokio::test]
    async fn route_stop_spacings_returns_correct_order_and_distances() {
        let td = test_utils::setup().await;
        let db = &td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool).await.unwrap();

        // S1 and S2 are 0.01° apart in latitude ≈ 1111 m; S2 and S3 are 0.001° ≈ 111 m
        sqlx::query("INSERT INTO stops VALUES ('0', 'S1', 'First Stop',  45.500, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S2', 'Middle Stop', 45.510, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S3', 'Terminus',    45.511, -73.50)")
            .execute(&db.pool).await.unwrap();

        // One variant, direction 0, is_primary=true, trip_count=5
        sqlx::query(
            "INSERT INTO route_variants (agency_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary)
             VALUES ('0', 'VAR1', 'R1', 0, 3, 5, true)",
        ).execute(&db.pool).await.unwrap();

        sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 1, 'S1')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 2, 'S2')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 3, 'S3')")
            .execute(&db.pool).await.unwrap();

        let directions = route_stop_spacings(db, "0", "R1").await.unwrap();

        assert_eq!(directions.len(), 1, "one variant expected");
        let dir = &directions[0];
        assert_eq!(dir.variant_id, "VAR1");
        assert!(dir.is_primary);
        assert_eq!(dir.trip_count, 5);
        assert_eq!(dir.first_stop_name, "First Stop");
        assert_eq!(dir.direction_name, "First Stop → Terminus");
        assert_eq!(dir.spacings.len(), 2, "two segments (S1→S2, S2→S3)");
        assert_eq!(dir.spacings[0].to_stop_name, "Middle Stop");
        assert_eq!(dir.spacings[1].to_stop_name, "Terminus");
        assert!(
            (dir.spacings[0].distance_m - 1111.0).abs() < 50.0,
            "S1→S2 should be ~1111 m, got {}",
            dir.spacings[0].distance_m
        );
        assert!(
            (dir.spacings[1].distance_m - 111.0).abs() < 10.0,
            "S2→S3 should be ~111 m, got {}",
            dir.spacings[1].distance_m
        );
        assert!(dir.spacings[0].is_outlier, "S1→S2 should be flagged as outlier");
        assert!(!dir.spacings[1].is_outlier, "S2→S3 should not be an outlier");
    }

    #[tokio::test]
    async fn route_stop_spacings_returns_all_variants_ordered_by_trip_count_desc() {
        let td = test_utils::setup().await;
        let db = &td.db;

        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&db.pool).await.unwrap();

        sqlx::query("INSERT INTO stops VALUES ('0', 'SA', 'Alpha',  45.500, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'SB', 'Beta',   45.505, -73.50)")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'SC', 'Gamma',  45.510, -73.50)")
            .execute(&db.pool).await.unwrap();

        // VAR2 has fewer trips than VAR1 — should come second even though lexicographically first
        sqlx::query(
            "INSERT INTO route_variants (agency_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary)
             VALUES ('0', 'VAR1', 'R1', 0, 3, 10, true),
                    ('0', 'VAR2', 'R1', 0, 2,  3, false)",
        ).execute(&db.pool).await.unwrap();

        // VAR1: SA → SB → SC
        sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 1, 'SA')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 2, 'SB')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 3, 'SC')")
            .execute(&db.pool).await.unwrap();

        // VAR2: SA → SC (short turn, skips SB)
        sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR2', 1, 'SA')")
            .execute(&db.pool).await.unwrap();
        sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR2', 2, 'SC')")
            .execute(&db.pool).await.unwrap();

        let directions = route_stop_spacings(db, "0", "R1").await.unwrap();

        assert_eq!(directions.len(), 2, "two variants expected");
        // VAR1 first (trip_count=10 > 3)
        assert_eq!(directions[0].variant_id, "VAR1");
        assert!(directions[0].is_primary);
        assert_eq!(directions[0].trip_count, 10);
        assert_eq!(directions[0].direction_name, "Alpha → Gamma");
        assert_eq!(directions[0].spacings.len(), 2);
        // VAR2 second
        assert_eq!(directions[1].variant_id, "VAR2");
        assert!(!directions[1].is_primary);
        assert_eq!(directions[1].trip_count, 3);
        assert_eq!(directions[1].direction_name, "Alpha → Gamma");
        assert_eq!(directions[1].spacings.len(), 1);
    }

    #[tokio::test]
    async fn route_stop_spacings_returns_empty_for_unknown_route() {
        let td = test_utils::setup().await;
        let result = route_stop_spacings(&td.db, "0", "NONEXISTENT")
            .await
            .unwrap();
        assert!(result.is_empty());
    }

    #[tokio::test]
    async fn route_speed_trend_by_direction_groups_and_buckets() {
        use chrono::{Datelike, Duration, Local};

        // Compute dates relative to today so the test never falls outside the window.
        // Find the most recent Monday (0 weeks back), then derive Saturday and Sunday.
        fn last_monday(offset_weeks: i64) -> chrono::NaiveDate {
            let today = Local::now().naive_local().date();
            let days_from_monday = today.weekday().num_days_from_monday() as i64;
            today - Duration::days(days_from_monday + offset_weeks * 7)
        }
        fn fmt(d: chrono::NaiveDate) -> String {
            d.format("%Y-%m-%d").to_string()
        }

        let monday = last_monday(1); // one week back ensures we're not on today
        let weekday_date = fmt(monday);
        let saturday_date = fmt(monday + Duration::days(5));
        let sunday_date = fmt(monday + Duration::days(6));

        let td = test_utils::setup().await;
        let db = &td.db;

        // Insert scheduled speed first (needed by the JOIN)
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
             VALUES ('0', 'R1', 0, 6.0, 1, 'now'),
                    ('0', 'R1', 1, 5.0, 1, 'now')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        for (date, dir, speed) in [
            (weekday_date.as_str(), 0_i64, 5.0_f64),
            (saturday_date.as_str(), 0, 6.0),
            (sunday_date.as_str(), 0, 7.0),
            (weekday_date.as_str(), 1, 4.0),
        ] {
            sqlx::query(
                "INSERT INTO route_speed_daily
                 (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, computed_at)
                 VALUES ('0', 'R1', $1, $2, $3, 1, 'now')",
            )
            .bind(date)
            .bind(dir)
            .bind(speed)
            .execute(&db.pool)
            .await
            .unwrap();
        }

        let trends = route_speed_trend_by_direction(db, "0", "R1", 28)
            .await
            .unwrap();
        assert_eq!(trends.len(), 2, "two directions");

        let dir0 = trends.iter().find(|t| t.direction_id == 0).unwrap();
        assert_eq!(dir0.weekday.len(), 1);
        assert_eq!(dir0.weekday[0].0, weekday_date);
        assert!((dir0.weekday[0].1 - 5.0).abs() < 0.001);
        assert!((dir0.weekday[0].2.unwrap() - 6.0).abs() < 0.001);
        assert_eq!(dir0.saturday.len(), 1);
        assert_eq!(dir0.sunday.len(), 1);

        let dir1 = trends.iter().find(|t| t.direction_id == 1).unwrap();
        assert_eq!(dir1.weekday.len(), 1);
        assert!((dir1.weekday[0].1 - 4.0).abs() < 0.001);
        assert!((dir1.weekday[0].2.unwrap() - 5.0).abs() < 0.001);
        assert!(dir1.saturday.is_empty());
        assert!(dir1.sunday.is_empty());
    }

    #[tokio::test]
    async fn route_speed_trend_by_direction_returns_empty_when_no_data() {
        let td = test_utils::setup().await;
        let result = route_speed_trend_by_direction(&td.db, "0", "R1", 28)
            .await
            .unwrap();
        assert!(result.is_empty());
    }

    #[tokio::test]
    async fn route_speed_trend_returns_actual_data_when_no_scheduled_speed() {
        use chrono::{Datelike, Duration, Local};

        let today = Local::now().naive_local().date();
        let days_from_monday = today.weekday().num_days_from_monday() as i64;
        let monday = today - Duration::days(days_from_monday + 7);
        let weekday_date = monday.format("%Y-%m-%d").to_string();

        let td = test_utils::setup().await;
        let db = &td.db;

        // Insert actual data only — no row in route_speed (no scheduled speed).
        sqlx::query(
            "INSERT INTO route_speed_daily
             (agency_id, route_id, service_date, direction_id, actual_speed_mps, trip_count, computed_at)
             VALUES ('0', 'R1', $1, 0, 8.0, 1, 'now')",
        )
        .bind(&weekday_date)
        .execute(&db.pool)
        .await
        .unwrap();

        let trends = route_speed_trend_by_direction(db, "0", "R1", 28)
            .await
            .unwrap();

        // Actual data must appear even without a scheduled speed row.
        assert_eq!(trends.len(), 1, "one direction");
        let dir = &trends[0];
        assert_eq!(dir.weekday.len(), 1);
        assert!(
            (dir.weekday[0].1 - 8.0).abs() < 0.001,
            "actual speed present"
        );
        // Scheduled speed is absent — represented as None.
        assert!(
            dir.weekday[0].2.is_none(),
            "scheduled speed should be None when route_speed row is missing"
        );
    }

    #[test]
    fn build_variant_trends_buckets_weekday_saturday_sunday() {
        // 2024-01-01 = Monday, 2024-01-06 = Saturday, 2024-01-07 = Sunday
        let rows = vec![
            SpeedTrendVariantRow {
                variant_id: "VAR1".into(),
                service_date: "2024-01-01".into(),
                actual_speed_mps: 5.0,
                scheduled_speed_mps: Some(6.0),
            },
            SpeedTrendVariantRow {
                variant_id: "VAR1".into(),
                service_date: "2024-01-06".into(),
                actual_speed_mps: 6.0,
                scheduled_speed_mps: Some(7.0),
            },
            SpeedTrendVariantRow {
                variant_id: "VAR1".into(),
                service_date: "2024-01-07".into(),
                actual_speed_mps: 7.0,
                scheduled_speed_mps: Some(8.0),
            },
        ];
        let result = build_variant_trends(rows);
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].variant_id, "VAR1");
        assert_eq!(result[0].weekday, vec![("2024-01-01".to_string(), 5.0, Some(6.0))]);
        assert_eq!(result[0].saturday, vec![("2024-01-06".to_string(), 6.0, Some(7.0))]);
        assert_eq!(result[0].sunday, vec![("2024-01-07".to_string(), 7.0, Some(8.0))]);
    }

    #[test]
    fn build_variant_trends_groups_two_variants() {
        let rows = vec![
            SpeedTrendVariantRow {
                variant_id: "VAR1".into(),
                service_date: "2024-01-01".into(),
                actual_speed_mps: 5.0,
                scheduled_speed_mps: Some(6.0),
            },
            SpeedTrendVariantRow {
                variant_id: "VAR2".into(),
                service_date: "2024-01-01".into(),
                actual_speed_mps: 4.0,
                scheduled_speed_mps: Some(5.0),
            },
        ];
        let result = build_variant_trends(rows);
        assert_eq!(result.len(), 2);
        // sorted by variant_id
        assert_eq!(result[0].variant_id, "VAR1");
        assert_eq!(result[1].variant_id, "VAR2");
        assert_eq!(result[0].weekday[0].1, 5.0);
        assert_eq!(result[1].weekday[0].1, 4.0);
    }

    #[tokio::test]
    async fn route_speed_trend_by_variant_groups_and_buckets() {
        use chrono::{Datelike, Duration, Local};

        fn last_monday(offset_weeks: i64) -> chrono::NaiveDate {
            let today = Local::now().naive_local().date();
            let days_from_monday = today.weekday().num_days_from_monday() as i64;
            today - Duration::days(days_from_monday + offset_weeks * 7)
        }
        fn fmt(d: chrono::NaiveDate) -> String {
            d.format("%Y-%m-%d").to_string()
        }

        let monday = last_monday(1);
        let weekday_date = fmt(monday);
        let saturday_date = fmt(monday + Duration::days(5));

        let td = test_utils::setup().await;
        let db = &td.db;

        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at)
             VALUES ('0', 'R1', 0, 6.0, 1, 'now')",
        ).execute(&db.pool).await.unwrap();

        for (date, variant_id, speed) in [
            (weekday_date.as_str(), "VAR1", 5.0_f64),
            (saturday_date.as_str(), "VAR1", 6.0),
            (weekday_date.as_str(), "VAR2", 4.0),
        ] {
            sqlx::query(
                "INSERT INTO route_speed_daily
                 (agency_id, route_id, service_date, direction_id, variant_id, actual_speed_mps, trip_count, computed_at)
                 VALUES ('0', 'R1', $1, 0, $2, $3, 1, 'now')",
            )
            .bind(date)
            .bind(variant_id)
            .bind(speed)
            .execute(&db.pool)
            .await
            .unwrap();
        }

        let trends = route_speed_trend_by_variant(db, "0", "R1", 28).await.unwrap();

        assert_eq!(trends.len(), 2, "two variants expected");
        assert_eq!(trends[0].variant_id, "VAR1");
        assert_eq!(trends[0].weekday.len(), 1);
        assert!((trends[0].weekday[0].1 - 5.0).abs() < 0.01);
        assert_eq!(trends[0].saturday.len(), 1);
        assert!((trends[0].saturday[0].1 - 6.0).abs() < 0.01);
        assert_eq!(trends[1].variant_id, "VAR2");
        assert_eq!(trends[1].weekday.len(), 1);
        assert!((trends[1].weekday[0].1 - 4.0).abs() < 0.01);
    }
}
