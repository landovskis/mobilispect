use anyhow::Result;
use serde::Serialize;

use crate::config::AgencyConfig;
use crate::db::Database;
use crate::ids::StopId;

/// A stop ranked by average delay — used for the hotspot map.
#[derive(Debug, sqlx::FromRow, Serialize)]
pub struct StopHotspot {
    pub stop_id: StopId,
    pub stop_name: String,
    pub stop_lat: f64,
    pub stop_lon: f64,
    pub avg_delay_secs: Option<f64>,
    pub observation_count: i64,
}

impl StopHotspot {
    pub fn delay_display(&self) -> String {
        match self.avg_delay_secs {
            Some(d) if d > 0.0 => format!("+{d:.0}s"),
            Some(d) => format!("{d:.0}s"),
            None => "—".to_string(),
        }
    }

    /// Hex color for map circle: red ≥ 5 min, orange ≥ 1 min, yellow > 0, green otherwise.
    pub fn color(&self) -> &'static str {
        match self.avg_delay_secs {
            Some(d) if d >= 300.0 => "#dc3545",
            Some(d) if d >= 60.0 => "#fd7e14",
            Some(d) if d > 0.0 => "#ffc107",
            _ => "#28a745",
        }
    }
}

/// Fetch the worst stops by average delay over the last N days.
/// Only includes stops with at least `min_observations` events.
pub async fn stop_hotspots(
    db: &Database,
    agency: &AgencyConfig,
    days: i64,
    limit: i64,
) -> Result<Vec<StopHotspot>> {
    let offset = &agency.agency_utc_offset;
    let rows: Vec<StopHotspot> = sqlx::query_as(
        "SELECT
           s.stop_id,
           s.stop_name,
           s.stop_lat,
           s.stop_lon,
           ROUND(AVG(CAST(COALESCE(
             ste.arrival_delay,
             CASE WHEN ste.arrival_time_unix IS NOT NULL
               THEN ste.arrival_time_unix - EXTRACT(EPOCH FROM (
                 ste.observed_at::TIMESTAMPTZ::DATE::TEXT || 'T' ||
                 CASE WHEN SUBSTRING(ss.arrival_time, 1, 2)::INTEGER >= 24
                   THEN LPAD((SUBSTRING(ss.arrival_time, 1, 2)::INTEGER - 24)::TEXT, 2, '0')
                        || SUBSTRING(ss.arrival_time, 3)
                   ELSE ss.arrival_time
                 END || $1
               )::TIMESTAMPTZ)::BIGINT
               ELSE NULL
             END
           ) AS DOUBLE PRECISION))::NUMERIC, 0) as avg_delay_secs,
           COUNT(*) as observation_count
         FROM stop_time_events ste
         JOIN scheduled_stops ss
           ON ss.trip_id = ste.trip_id AND ss.stop_id = ste.stop_id AND ss.agency_id = ste.agency_id
         JOIN stops s ON s.stop_id = ste.stop_id AND s.agency_id = ste.agency_id
         WHERE ste.observed_at::TIMESTAMPTZ >= NOW() - ($2::BIGINT * INTERVAL '1 day')
           AND (ste.arrival_delay IS NOT NULL OR ste.arrival_time_unix IS NOT NULL)
         GROUP BY s.stop_id, s.stop_name, s.stop_lat, s.stop_lon
         HAVING COUNT(*) >= 5
         ORDER BY avg_delay_secs DESC
         LIMIT $3",
    )
    .bind(offset)
    .bind(days)
    .bind(limit)
    .fetch_all(&db.pool)
    .await?;
    Ok(rows)
}
