use anyhow::Result;
use serde::Serialize;

use crate::db::Database;
use crate::ids::{AgencyId, RouteId};

#[derive(Debug, sqlx::FromRow, Serialize)]
pub struct RouteSummary {
    pub agency_id: AgencyId,
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
/// If `agency_filter` is Some, only returns routes for that agency.
pub async fn route_summary(
    db: &Database,
    days: i64,
    agency_filter: Option<&AgencyId>,
) -> Result<Vec<RouteSummary>> {
    let rows: Vec<RouteSummary> = match agency_filter {
        None => sqlx::query_as(
            "SELECT
               rd.agency_id,
               rd.route_id,
               r.short_name,
               r.long_name,
               ROUND(AVG(rd.on_time_pct)::NUMERIC, 1)::FLOAT8 as avg_on_time_pct,
               ROUND(AVG(rd.avg_delay_secs)::NUMERIC, 0)::FLOAT8 as avg_delay_secs,
               SUM(rd.trips_run)::BIGINT as trips_run,
               SUM(rd.trips_total)::BIGINT as trips_total,
               COUNT(rd.service_date) as days_measured
             FROM route_daily rd
             JOIN routes r ON rd.agency_id = r.agency_id AND rd.route_id = r.route_id
             WHERE rd.service_date >= (CURRENT_DATE - $1::INT * INTERVAL '1 day')::TEXT
             GROUP BY rd.agency_id, rd.route_id, r.short_name, r.long_name
             ORDER BY rd.agency_id,
               CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST,
               r.short_name",
        )
        .bind(days)
        .fetch_all(&db.pool)
        .await?,

        Some(agency) => sqlx::query_as(
            "SELECT
               rd.agency_id,
               rd.route_id,
               r.short_name,
               r.long_name,
               ROUND(AVG(rd.on_time_pct)::NUMERIC, 1)::FLOAT8 as avg_on_time_pct,
               ROUND(AVG(rd.avg_delay_secs)::NUMERIC, 0)::FLOAT8 as avg_delay_secs,
               SUM(rd.trips_run)::BIGINT as trips_run,
               SUM(rd.trips_total)::BIGINT as trips_total,
               COUNT(rd.service_date) as days_measured
             FROM route_daily rd
             JOIN routes r ON rd.agency_id = r.agency_id AND rd.route_id = r.route_id
             WHERE rd.service_date >= (CURRENT_DATE - $1::INT * INTERVAL '1 day')::TEXT
               AND rd.agency_id = $2
             GROUP BY rd.agency_id, rd.route_id, r.short_name, r.long_name
             ORDER BY rd.agency_id,
               CASE WHEN r.short_name ~ '^[0-9]+$' THEN r.short_name::INTEGER ELSE NULL END NULLS LAST,
               r.short_name",
        )
        .bind(days)
        .bind(agency.as_str())
        .fetch_all(&db.pool)
        .await?,
    };
    Ok(rows)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;
    use crate::ids::AgencyId;

    fn make_route_summary(on_time: Option<f64>) -> RouteSummary {
        RouteSummary {
            agency_id: "stm".into(),
            route_id: "R1".into(),
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

    #[tokio::test]
    async fn route_summary_filters_by_agency() {
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
            "INSERT INTO route_daily (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ('stm', 'R1', (CURRENT_DATE - INTERVAL '1 day')::TEXT, 80.0, 60.0, 10, 12, '2026-01-01T12:00:00Z')",
        ).execute(&db.pool).await.unwrap();
        sqlx::query(
            "INSERT INTO route_daily (agency_id, route_id, service_date, on_time_pct, avg_delay_secs, trips_run, trips_total, computed_at)
             VALUES ('rtl', 'R2', (CURRENT_DATE - INTERVAL '1 day')::TEXT, 70.0, 90.0, 8, 10, '2026-01-01T12:00:00Z')",
        ).execute(&db.pool).await.unwrap();

        let all = route_summary(&db, 30, None).await.unwrap();
        assert_eq!(all.len(), 2);

        let stm = route_summary(&db, 30, Some(&AgencyId::from("stm"))).await.unwrap();
        assert_eq!(stm.len(), 1);
        assert_eq!(stm[0].agency_id, "stm");

        let rtl = route_summary(&db, 30, Some(&AgencyId::from("rtl"))).await.unwrap();
        assert_eq!(rtl.len(), 1);
        assert_eq!(rtl[0].agency_id, "rtl");
    }
}
