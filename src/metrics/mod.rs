use anyhow::Result;
use chrono::NaiveDate;
use serde::Serialize;
use tracing::info;

use crate::config::{AgencyConfig, Config};
use crate::db::Database;

/// Compute on-time performance for all routes on a given service date.
pub async fn compute_route_daily(db: &Database, config: &Config, agency: &AgencyConfig, service_date: NaiveDate) -> Result<()> {
    let date_str = service_date.to_string();
    let now = chrono::Utc::now().to_rfc3339();

    let trips = sqlx::query!(
        "SELECT DISTINCT t.trip_id, t.route_id
         FROM trips t
         JOIN stop_time_events ste ON t.trip_id = ste.trip_id
         WHERE DATE(ste.observed_at) = ?",
        date_str,
    )
    .fetch_all(&db.pool)
    .await?;

    for trip in &trips {
        // Compute delay = actual_arrival_unix - scheduled_arrival_unix.
        // Scheduled time is HH:MM:SS; reconstruct Unix seconds for the service date.
        // Also handles feeds that provide delay directly.
        // Scheduled times are in agency local time; append UTC offset to get correct Unix epoch.
        // e.g. "18:51:00" + date + "-04:00" → correct UTC Unix timestamp.
        let offset = &agency.agency_utc_offset;
        let delays: Vec<i64> = sqlx::query_as::<_, (Option<i64>,)>(
            "SELECT
               CAST(COALESCE(
                 ste.arrival_delay,
                 CASE WHEN ste.arrival_time_unix IS NOT NULL
                   THEN ste.arrival_time_unix -
                        CAST(strftime('%s',
                          ? || 'T' ||
                          CASE WHEN CAST(substr(ss.arrival_time,1,2) AS INTEGER) >= 24
                            THEN printf('%02d', CAST(substr(ss.arrival_time,1,2) AS INTEGER) - 24)
                                 || substr(ss.arrival_time,3)
                            ELSE ss.arrival_time
                          END || ?) AS INTEGER)
                   ELSE NULL
                 END
               ) AS INTEGER) as delay
             FROM stop_time_events ste
             JOIN scheduled_stops ss
               ON ss.trip_id = ste.trip_id AND ss.stop_id = ste.stop_id
             WHERE ste.trip_id = ? AND DATE(ste.observed_at) = ?
               AND (ste.arrival_delay IS NOT NULL OR ste.arrival_time_unix IS NOT NULL)",
        )
        .bind(&date_str)
        .bind(offset)
        .bind(&trip.trip_id)
        .bind(&date_str)
        .fetch_all(&db.pool)
        .await?
        .into_iter()
        .filter_map(|(d,)| d)
        .collect();
        if delays.is_empty() { continue; }
        let avg_delay = delays.iter().sum::<i64>() as f64 / delays.len() as f64;
        let max_delay = delays.iter().copied().max().unwrap_or(0) as f64;
        let on_time_flag: i64 = if delays.iter().all(|&d| {
            d >= config.on_time_early_threshold_secs && d <= config.on_time_late_threshold_secs
        }) {
            1
        } else {
            0
        };

        sqlx::query!(
            "INSERT OR REPLACE INTO trip_results
             (trip_id, service_date, route_id, on_time, avg_delay_secs, max_delay_secs, completed, computed_at)
             VALUES (?, ?, ?, ?, ?, ?, 1, ?)",
            trip.trip_id,
            date_str,
            trip.route_id,
            on_time_flag,
            avg_delay,
            max_delay,
            now,
        )
        .execute(&db.pool)
        .await?;
    }

    // Aggregate to route_daily using non-macro query for complex aggregation
    let routes: Vec<(String,)> = sqlx::query_as(
        "SELECT DISTINCT route_id FROM trip_results WHERE service_date = ?",
    )
    .bind(&date_str)
    .fetch_all(&db.pool)
    .await?;

    for (route_id,) in &routes {
        let row: (i64, i64, f64, i64) = sqlx::query_as(
            "SELECT
               COUNT(*) as trips_run,
               COALESCE(SUM(on_time), 0) as on_time_count,
               COALESCE(AVG(avg_delay_secs), 0.0) as avg_delay,
               (SELECT COUNT(*) FROM trips WHERE route_id = ?) as trips_total
             FROM trip_results
             WHERE route_id = ? AND service_date = ?",
        )
        .bind(route_id)
        .bind(route_id)
        .bind(&date_str)
        .fetch_one(&db.pool)
        .await?;

        let (trips_run, on_time_count, avg_delay, trips_total) = row;
        let on_time_pct = if trips_run > 0 {
            (on_time_count as f64 / trips_run as f64) * 100.0
        } else {
            0.0
        };

        sqlx::query!(
            "INSERT OR REPLACE INTO route_daily
             (route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)",
            route_id,
            date_str,
            on_time_pct,
            avg_delay,
            trips_run,
            trips_total,
            now,
        )
        .execute(&db.pool)
        .await?;
    }

    info!(
        "Computed performance for {} trips, {} routes on {}",
        trips.len(),
        routes.len(),
        date_str
    );
    Ok(())
}

#[derive(Debug, sqlx::FromRow, serde::Serialize)]
pub struct RouteSummary {
    pub route_id: String,
    pub short_name: String,
    pub long_name: String,
    pub avg_on_time_pct: Option<f64>,
    pub avg_delay_secs: Option<f64>,
    pub trips_run: Option<i64>,
    pub trips_total: Option<i64>,
    pub days_measured: Option<i64>,
}

impl RouteSummary {
    /// "green", "yellow", "red", or "none"
    pub fn status_class(&self) -> &'static str {
        match self.avg_on_time_pct {
            Some(pct) if pct >= 80.0 => "green",
            Some(pct) if pct >= 60.0 => "yellow",
            Some(_) => "red",
            None => "none",
        }
    }

    pub fn status_label(&self) -> &'static str {
        match self.avg_on_time_pct {
            Some(pct) if pct >= 80.0 => "On track",
            Some(pct) if pct >= 60.0 => "Degraded",
            Some(_) => "Poor",
            None => "No data",
        }
    }

    pub fn on_time_display(&self) -> String {
        match self.avg_on_time_pct {
            Some(pct) => format!("{pct}%"),
            None => "—".to_string(),
        }
    }

    pub fn delay_display(&self) -> String {
        match self.avg_delay_secs {
            Some(d) if d > 0.0 => format!("+{d}s"),
            Some(d) => format!("{d}s"),
            None => "—".to_string(),
        }
    }
}

/// A stop ranked by average delay — used for the hotspot map.
#[derive(Debug, sqlx::FromRow, Serialize)]
pub struct StopHotspot {
    pub stop_id: String,
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
               THEN ste.arrival_time_unix - CAST(strftime('%s',
                 DATE(ste.observed_at) || 'T' ||
                 CASE WHEN CAST(substr(ss.arrival_time,1,2) AS INTEGER) >= 24
                   THEN printf('%02d', CAST(substr(ss.arrival_time,1,2) AS INTEGER) - 24)
                        || substr(ss.arrival_time,3)
                   ELSE ss.arrival_time
                 END || ?) AS INTEGER)
               ELSE NULL
             END
           ) AS REAL)), 0) as avg_delay_secs,
           COUNT(*) as observation_count
         FROM stop_time_events ste
         JOIN scheduled_stops ss
           ON ss.trip_id = ste.trip_id AND ss.stop_id = ste.stop_id
         JOIN stops s ON s.stop_id = ste.stop_id
         WHERE ste.observed_at >= datetime('now', '-' || ? || ' days')
           AND (ste.arrival_delay IS NOT NULL OR ste.arrival_time_unix IS NOT NULL)
         GROUP BY s.stop_id, s.stop_name, s.stop_lat, s.stop_lon
         HAVING COUNT(*) >= 5
         ORDER BY avg_delay_secs DESC
         LIMIT ?",
    )
    .bind(offset)
    .bind(days)
    .bind(limit)
    .fetch_all(&db.pool)
    .await?;
    Ok(rows)
}

/// Fetch route performance summary for the dashboard (last N days).
pub async fn route_summary(db: &Database, days: i64) -> Result<Vec<RouteSummary>> {
    let rows: Vec<RouteSummary> = sqlx::query_as(
        "SELECT
           rd.route_id,
           r.short_name,
           r.long_name,
           ROUND(AVG(rd.on_time_pct), 1) as avg_on_time_pct,
           ROUND(AVG(rd.avg_delay_secs), 0) as avg_delay_secs,
           SUM(rd.trips_run) as trips_run,
           SUM(rd.trips_total) as trips_total,
           COUNT(rd.service_date) as days_measured
         FROM route_daily rd
         JOIN routes r ON rd.route_id = r.route_id
         WHERE rd.service_date >= DATE('now', '-' || ? || ' days')
         GROUP BY rd.route_id, r.short_name, r.long_name
         ORDER BY CAST(r.short_name AS INTEGER), r.short_name",
    )
    .bind(days)
    .fetch_all(&db.pool)
    .await?;

    Ok(rows)
}
