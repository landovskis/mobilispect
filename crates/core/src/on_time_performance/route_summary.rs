use anyhow::Result;
use serde::Serialize;

use crate::db::Database;
use crate::ids::{FeedId, RouteId};

#[derive(Debug, Serialize)]
pub struct RouteSummary {
    pub feed_id: FeedId,
    pub route_id: RouteId,
    pub short_name: String,
    pub long_name: String,
    pub avg_on_time_pct: Option<f64>,
    pub avg_delay_secs: Option<f64>,
    pub trips_run: Option<i64>,
    pub trips_total: Option<i64>,
    pub days_measured: Option<i64>,
}

impl RouteSummary {
    pub fn status_label(&self) -> &'static str {
        match self.avg_on_time_pct {
            Some(pct) if pct >= 80.0 => "On track",
            Some(pct) if pct >= 60.0 => "Degraded",
            Some(_) => "Poor",
            None => "No data",
        }
    }

    pub fn badge_variant(&self) -> &'static str {
        match self.avg_on_time_pct {
            Some(pct) if pct >= 80.0 => "good",
            Some(pct) if pct >= 60.0 => "mixed",
            Some(_) => "bad",
            None => "neutral",
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

/// Fetch route performance summary for the /api/routes endpoint (last N days).
/// If `feed_filter` is Some, only returns routes for that feed.
pub async fn route_summary(
    db: &Database,
    days: i64,
    feed_filter: Option<FeedId>,
) -> Result<Vec<RouteSummary>> {
    // Use dynamic query to avoid sqlx offline cache issues with conditional filter.
    #[allow(clippy::type_complexity)]
    let rows: Vec<(i64, String, String, String, Option<f64>, Option<f64>, Option<i64>, Option<i64>, Option<i64>)> =
        sqlx::query_as(
            "SELECT
               rds.feed_id,
               rds.route_id,
               r.short_name,
               r.long_name,
               ROUND(
                 AVG(
                   rds.on_time_stops::float8 / NULLIF(rds.total_stops, 0) * 100.0
                 )::NUMERIC, 1
               )::FLOAT8 AS avg_on_time_pct,
               ROUND(AVG(rds.avg_delay_secs)::NUMERIC, 0)::FLOAT8 AS avg_delay_secs,
               SUM(rds.trips_run)::BIGINT AS trips_run,
               SUM(rds.trips_total)::BIGINT AS trips_total,
               COUNT(DISTINCT rds.service_date) AS days_measured
             FROM route_daily_stats rds
             JOIN routes r ON rds.route_id = r.onestop_id
             WHERE rds.service_date >= CURRENT_DATE - ($1::INT * INTERVAL '1 day')
               AND ($2::BIGINT IS NULL OR rds.feed_id = $2)
             GROUP BY rds.feed_id, rds.route_id, r.short_name, r.long_name
             ORDER BY rds.feed_id,
               CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST,
               r.short_name",
        )
        .bind(days)
        .bind(feed_filter.map(|f| f.as_i64()))
        .fetch_all(&db.pool)
        .await?;

    Ok(rows
        .into_iter()
        .map(
            |(
                feed_id,
                route_id,
                short_name,
                long_name,
                avg_on_time_pct,
                avg_delay_secs,
                trips_run,
                trips_total,
                days_measured,
            )| {
                RouteSummary {
                    feed_id: FeedId::from(feed_id),
                    route_id: RouteId::from(route_id),
                    short_name,
                    long_name,
                    avg_on_time_pct,
                    avg_delay_secs,
                    trips_run,
                    trips_total,
                    days_measured,
                }
            },
        )
        .collect())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_route_summary(on_time: Option<f64>) -> RouteSummary {
        RouteSummary {
            feed_id: FeedId::from(1i64),
            route_id: "r-f25d-1".into(),
            short_name: "1".into(),
            long_name: "Route 1".into(),
            avg_on_time_pct: on_time,
            avg_delay_secs: None,
            trips_run: None,
            trips_total: None,
            days_measured: None,
        }
    }

    #[test]
    fn route_summary_badge_variant_good_at_or_above_80() {
        assert_eq!(make_route_summary(Some(80.0)).badge_variant(), "good");
        assert_eq!(make_route_summary(Some(95.0)).badge_variant(), "good");
    }

    #[test]
    fn route_summary_badge_variant_mixed_between_60_and_80() {
        assert_eq!(make_route_summary(Some(60.0)).badge_variant(), "mixed");
        assert_eq!(make_route_summary(Some(79.9)).badge_variant(), "mixed");
    }

    #[test]
    fn route_summary_badge_variant_bad_below_60() {
        assert_eq!(make_route_summary(Some(59.9)).badge_variant(), "bad");
        assert_eq!(make_route_summary(Some(0.0)).badge_variant(), "bad");
    }

    #[test]
    fn route_summary_badge_variant_neutral_when_no_data() {
        assert_eq!(make_route_summary(None).badge_variant(), "neutral");
    }

    #[test]
    fn status_label_on_track_at_or_above_80() {
        assert_eq!(make_route_summary(Some(80.0)).status_label(), "On track");
        assert_eq!(make_route_summary(Some(95.0)).status_label(), "On track");
    }

    #[test]
    fn status_label_degraded_between_60_and_80() {
        assert_eq!(make_route_summary(Some(60.0)).status_label(), "Degraded");
        assert_eq!(make_route_summary(Some(79.9)).status_label(), "Degraded");
    }

    #[test]
    fn status_label_poor_below_60() {
        assert_eq!(make_route_summary(Some(59.9)).status_label(), "Poor");
        assert_eq!(make_route_summary(Some(0.0)).status_label(), "Poor");
    }

    #[test]
    fn status_label_no_data_when_none() {
        assert_eq!(make_route_summary(None).status_label(), "No data");
    }

    #[test]
    fn on_time_display_formats_percentage() {
        assert_eq!(make_route_summary(Some(87.0)).on_time_display(), "87%");
    }

    #[test]
    fn on_time_display_dash_when_none() {
        assert_eq!(make_route_summary(None).on_time_display(), "—");
    }

    fn make_route_summary_with_delay(delay: Option<f64>) -> RouteSummary {
        RouteSummary {
            feed_id: FeedId::from(1i64),
            route_id: "r-f25d-1".into(),
            short_name: "1".into(),
            long_name: "Route 1".into(),
            avg_on_time_pct: None,
            avg_delay_secs: delay,
            trips_run: None,
            trips_total: None,
            days_measured: None,
        }
    }

    #[test]
    fn delay_display_formats_positive_delay() {
        assert_eq!(
            make_route_summary_with_delay(Some(120.0)).delay_display(),
            "+120s"
        );
    }

    #[test]
    fn delay_display_formats_non_positive_delay() {
        assert_eq!(
            make_route_summary_with_delay(Some(-30.0)).delay_display(),
            "-30s"
        );
        assert_eq!(
            make_route_summary_with_delay(Some(0.0)).delay_display(),
            "0s"
        );
    }

    #[test]
    fn delay_display_dash_when_none() {
        assert_eq!(make_route_summary_with_delay(None).delay_display(), "—");
    }

    #[tokio::test]
    async fn route_summary_filters_by_feed_id() {
        use crate::db::test_utils;

        let td = test_utils::setup().await;
        let db = td.db;

        // Insert two feeds
        sqlx::query(
            "INSERT INTO feeds (id, gtfs_static_url, name) VALUES (1, 'http://stm', 'STM'), (2, 'http://rtl', 'RTL')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        // Insert agencies required by routes FK
        sqlx::query(
            "INSERT INTO agencies (onestop_id, name) VALUES ('stm', 'STM'), ('rtl', 'RTL')",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        // Insert routes with Onestop IDs
        sqlx::query("INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('r-stm-15', 'stm', '15', 'Papineau', 3)")
            .execute(&db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('r-rtl-10', 'rtl', '10', 'Longueuil', 3)")
            .execute(&db.pool)
            .await
            .unwrap();

        // Insert route_daily_stats rows
        sqlx::query(
            "INSERT INTO route_daily_stats
             (feed_id, route_id, service_date, variant_id, on_time_stops, total_stops, skipped_stops, trips_run, trips_total, computed_at)
             VALUES (1, 'r-stm-15', CURRENT_DATE - 1, 'var1', 80, 100, 0, 10, 12, NOW())",
        )
        .execute(&db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_daily_stats
             (feed_id, route_id, service_date, variant_id, on_time_stops, total_stops, skipped_stops, trips_run, trips_total, computed_at)
             VALUES (2, 'r-rtl-10', CURRENT_DATE - 1, 'var2', 70, 100, 0, 8, 10, NOW())",
        )
        .execute(&db.pool)
        .await
        .unwrap();

        let all = route_summary(&db, 30, None).await.unwrap();
        assert_eq!(all.len(), 2);

        let feed1 = route_summary(&db, 30, Some(FeedId::from(1i64)))
            .await
            .unwrap();
        assert_eq!(feed1.len(), 1);
        assert_eq!(feed1[0].feed_id, 1i64);
        assert_eq!(feed1[0].route_id, "r-stm-15");

        let feed2 = route_summary(&db, 30, Some(FeedId::from(2i64)))
            .await
            .unwrap();
        assert_eq!(feed2.len(), 1);
        assert_eq!(feed2[0].feed_id, 2i64);
        assert_eq!(feed2[0].route_id, "r-rtl-10");
    }
}
