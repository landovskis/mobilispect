use crate::speed::{DirectionStopSpacings, StopSpacing, VariantSpeedTrend};

pub struct RouteSpeedDetailDirection {
    pub variant_id: String,
    pub direction_name: String,
    pub first_stop_name: String,
    pub is_primary: bool,
    pub trip_count: i64,
    pub avg_spacing_m: f64,
    pub spacings: Vec<StopSpacing>,
    pub weekday_chart_id: String,
    pub saturday_chart_id: String,
    pub sunday_chart_id: String,
    pub weekday_json: String,
    pub saturday_json: String,
    pub sunday_json: String,
}

impl RouteSpeedDetailDirection {
    pub fn avg_spacing_display(&self) -> String {
        if self.avg_spacing_m >= 1000.0 {
            format!("{:.1} km", self.avg_spacing_m / 1000.0)
        } else {
            format!("{:.0} m", self.avg_spacing_m)
        }
    }

    pub fn avg_spacing_status_class(&self) -> &str {
        let avg = self.avg_spacing_m;
        if avg < 300.0 {
            "slow"
        } else if avg <= 5000.0 {
            ""
        } else {
            "outlier"
        }
    }

    pub fn direction_badge_label(&self) -> String {
        if self.is_primary {
            format!("Primary \u{00B7} {} trips", self.trip_count)
        } else {
            format!("{} trips", self.trip_count)
        }
    }

    pub fn direction_badge_variant(&self) -> &'static str {
        if self.is_primary { "oxford" } else { "neutral" }
    }
}

fn trend_to_json(points: Vec<(String, f64, Option<f64>)>) -> String {
    #[derive(serde::Serialize)]
    struct TrendPoint {
        date: String,
        actual_kmh: f64,
        scheduled_kmh: Option<f64>,
    }
    let pts: Vec<TrendPoint> = points
        .into_iter()
        .map(|(date, actual_mps, scheduled_mps)| TrendPoint {
            date,
            actual_kmh: (actual_mps * 3.6 * 10.0).round() / 10.0,
            scheduled_kmh: scheduled_mps.map(|s| (s * 3.6 * 10.0).round() / 10.0),
        })
        .collect();
    serde_json::to_string(&pts).unwrap_or_else(|_| "[]".to_string())
}

pub fn build_detail_directions(
    spacings: Vec<DirectionStopSpacings>,
    trends: Vec<VariantSpeedTrend>,
) -> Vec<RouteSpeedDetailDirection> {
    spacings
        .into_iter()
        .enumerate()
        .map(|(i, spacing)| {
            let trend = trends.iter().find(|t| t.variant_id == spacing.variant_id);
            let (weekday, saturday, sunday) = trend
                .map(|t| (t.weekday.clone(), t.saturday.clone(), t.sunday.clone()))
                .unwrap_or_default();
            RouteSpeedDetailDirection {
                variant_id: spacing.variant_id,
                direction_name: spacing.direction_name,
                first_stop_name: spacing.first_stop_name,
                is_primary: spacing.is_primary,
                trip_count: spacing.trip_count,
                avg_spacing_m: spacing.avg_spacing_m,
                spacings: spacing.spacings,
                weekday_chart_id: format!("weekday-{i}"),
                saturday_chart_id: format!("saturday-{i}"),
                sunday_chart_id: format!("sunday-{i}"),
                weekday_json: trend_to_json(weekday),
                saturday_json: trend_to_json(saturday),
                sunday_json: trend_to_json(sunday),
            }
        })
        .collect()
}

#[derive(sqlx::FromRow)]
struct RouteInfoRow {
    short_name: String,
    long_name: String,
}

pub async fn fetch_route_info(
    db: &crate::db::Database,
    agency_id: &str,
    route_id: &str,
) -> anyhow::Result<Option<(String, String)>> {
    let row = sqlx::query_as!(
        RouteInfoRow,
        "SELECT short_name, long_name FROM routes WHERE agency_id = $1 AND route_id = $2",
        agency_id,
        route_id,
    )
    .fetch_optional(&db.pool)
    .await?;
    Ok(row.map(|r| (r.short_name, r.long_name)))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_spacing(variant_id: &str) -> DirectionStopSpacings {
        DirectionStopSpacings {
            direction_id: 0,
            variant_id: variant_id.to_string(),
            is_primary: true,
            trip_count: 10,
            direction_name: "A \u{2192} B".to_string(),
            first_stop_name: "A".to_string(),
            avg_spacing_m: 500.0,
            spacings: vec![],
        }
    }

    fn make_trend(variant_id: &str) -> VariantSpeedTrend {
        VariantSpeedTrend {
            variant_id: variant_id.to_string(),
            weekday: vec![("2026-01-06".to_string(), 8.0, Some(9.0))],
            saturday: vec![],
            sunday: vec![],
        }
    }

    #[test]
    fn build_detail_directions_generates_chart_ids_with_index() {
        let spacings = vec![make_spacing("V1"), make_spacing("V2")];
        let dirs = build_detail_directions(spacings, vec![]);
        assert_eq!(dirs[0].weekday_chart_id, "weekday-0");
        assert_eq!(dirs[0].saturday_chart_id, "saturday-0");
        assert_eq!(dirs[0].sunday_chart_id, "sunday-0");
        assert_eq!(dirs[1].weekday_chart_id, "weekday-1");
    }

    #[test]
    fn build_detail_directions_no_matching_trend_produces_empty_json() {
        let spacings = vec![make_spacing("V1")];
        let dirs = build_detail_directions(spacings, vec![]);
        assert_eq!(dirs[0].weekday_json, "[]");
        assert_eq!(dirs[0].saturday_json, "[]");
        assert_eq!(dirs[0].sunday_json, "[]");
    }

    #[test]
    fn build_detail_directions_matching_trend_weekday_json_contains_date_and_speeds() {
        let spacings = vec![make_spacing("V1")];
        let trends = vec![make_trend("V1")];
        let dirs = build_detail_directions(spacings, trends);
        // make_trend sets weekday: [("2026-01-06", 8.0 mps, Some(9.0 mps))]
        // 8.0 m/s → 28.8 km/h, 9.0 m/s → 32.4 km/h
        assert!(dirs[0].weekday_json.contains("2026-01-06"), "date must appear in JSON");
        assert!(dirs[0].weekday_json.contains("28.8"), "actual km/h must appear");
        assert!(dirs[0].weekday_json.contains("32.4"), "scheduled km/h must appear");
    }

    #[test]
    fn build_detail_directions_preserves_spacing_fields() {
        let spacings = vec![make_spacing("V1")];
        let dirs = build_detail_directions(spacings, vec![]);
        assert_eq!(dirs[0].variant_id, "V1");
        assert_eq!(dirs[0].direction_name, "A \u{2192} B");
        assert_eq!(dirs[0].first_stop_name, "A");
        assert!(dirs[0].is_primary);
        assert_eq!(dirs[0].trip_count, 10);
        assert!((dirs[0].avg_spacing_m - 500.0).abs() < 0.001);
    }

    #[test]
    fn build_detail_directions_ignores_trend_for_different_variant() {
        let spacings = vec![make_spacing("V1")];
        let trends = vec![make_trend("V2")];
        let dirs = build_detail_directions(spacings, trends);
        assert_eq!(dirs[0].weekday_json, "[]");
    }

    #[test]
    fn avg_spacing_display_under_1km_shows_metres() {
        let d = RouteSpeedDetailDirection {
            avg_spacing_m: 342.0,
            variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), is_primary: false, trip_count: 0,
            spacings: vec![], weekday_chart_id: String::new(),
            saturday_chart_id: String::new(), sunday_chart_id: String::new(),
            weekday_json: String::new(), saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.avg_spacing_display(), "342 m");
    }

    #[test]
    fn avg_spacing_display_at_or_over_1km_shows_km() {
        let d = RouteSpeedDetailDirection {
            avg_spacing_m: 1200.0,
            variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), is_primary: false, trip_count: 0,
            spacings: vec![], weekday_chart_id: String::new(),
            saturday_chart_id: String::new(), sunday_chart_id: String::new(),
            weekday_json: String::new(), saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.avg_spacing_display(), "1.2 km");
    }

    #[test]
    fn avg_spacing_status_class_below_local_range_min_is_slow() {
        let d = RouteSpeedDetailDirection {
            avg_spacing_m: 200.0,
            variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), is_primary: false, trip_count: 0,
            spacings: vec![], weekday_chart_id: String::new(),
            saturday_chart_id: String::new(), sunday_chart_id: String::new(),
            weekday_json: String::new(), saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.avg_spacing_status_class(), "slow");
    }

    #[test]
    fn avg_spacing_status_class_in_range_is_empty() {
        let d = RouteSpeedDetailDirection {
            avg_spacing_m: 400.0,
            variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), is_primary: false, trip_count: 0,
            spacings: vec![], weekday_chart_id: String::new(),
            saturday_chart_id: String::new(), sunday_chart_id: String::new(),
            weekday_json: String::new(), saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.avg_spacing_status_class(), "");
    }

    #[test]
    fn avg_spacing_status_class_exactly_300_is_in_range() {
        let d = RouteSpeedDetailDirection {
            avg_spacing_m: 300.0,
            variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), is_primary: false, trip_count: 0,
            spacings: vec![], weekday_chart_id: String::new(),
            saturday_chart_id: String::new(), sunday_chart_id: String::new(),
            weekday_json: String::new(), saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.avg_spacing_status_class(), "");
    }

    #[test]
    fn avg_spacing_status_class_exactly_5000_is_in_range() {
        let d = RouteSpeedDetailDirection {
            avg_spacing_m: 5000.0,
            variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), is_primary: false, trip_count: 0,
            spacings: vec![], weekday_chart_id: String::new(),
            saturday_chart_id: String::new(), sunday_chart_id: String::new(),
            weekday_json: String::new(), saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.avg_spacing_status_class(), "");
    }

    #[test]
    fn avg_spacing_status_class_above_express_max_is_outlier() {
        let d = RouteSpeedDetailDirection {
            avg_spacing_m: 6000.0,
            variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), is_primary: false, trip_count: 0,
            spacings: vec![], weekday_chart_id: String::new(),
            saturday_chart_id: String::new(), sunday_chart_id: String::new(),
            weekday_json: String::new(), saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.avg_spacing_status_class(), "outlier");
    }

    #[test]
    fn direction_badge_label_primary_includes_primary_text() {
        let d = RouteSpeedDetailDirection {
            is_primary: true, trip_count: 42,
            avg_spacing_m: 0.0, variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), spacings: vec![],
            weekday_chart_id: String::new(), saturday_chart_id: String::new(),
            sunday_chart_id: String::new(), weekday_json: String::new(),
            saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.direction_badge_label(), "Primary \u{00B7} 42 trips");
    }

    #[test]
    fn direction_badge_label_non_primary_shows_trip_count_only() {
        let d = RouteSpeedDetailDirection {
            is_primary: false, trip_count: 7,
            avg_spacing_m: 0.0, variant_id: String::new(), direction_name: String::new(),
            first_stop_name: String::new(), spacings: vec![],
            weekday_chart_id: String::new(), saturday_chart_id: String::new(),
            sunday_chart_id: String::new(), weekday_json: String::new(),
            saturday_json: String::new(), sunday_json: String::new(),
        };
        assert_eq!(d.direction_badge_label(), "7 trips");
    }
}
