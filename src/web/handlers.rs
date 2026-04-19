use askama::Template;
use axum::{
    extract::{Query, State},
    response::Html,
};
use chrono::Utc;
use serde::Deserialize;
use serde_json;

use crate::metrics::{
    Benchmark, RouteSummary, RouteTrend, ScorecardRoute, StopHotspot, load_benchmarks,
    route_summary, route_trend, scorecard_routes, stop_hotspots,
};
use crate::speed::{
    RouteSpeedCard, RouteSpeedSummary, StopSpacing,
    build_speed_cards, route_speed_by_day_type, route_speed_summary,
    route_stop_spacings, route_speed_trend_by_direction,
};
use crate::web::AppState;

struct RouteSpeedDetailDirection {
    pub chart_id: String,
    pub direction_name: String,
    pub first_stop_name: String,
    pub avg_spacing_m: f64,
    pub spacings: Vec<StopSpacing>,
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
}

#[derive(Template)]
#[template(path = "route_speed_detail.html")]
struct RouteSpeedDetailTemplate {
    short_name: String,
    long_name: String,
    agency_id: String,
    directions: Vec<RouteSpeedDetailDirection>,
}

fn trend_to_json(points: Vec<(String, f64)>) -> String {
    #[derive(serde::Serialize)]
    struct TrendPoint {
        date: String,
        speed_kmh: f64,
    }
    let pts: Vec<TrendPoint> = points
        .into_iter()
        .map(|(date, mps)| TrendPoint {
            date,
            speed_kmh: (mps * 3.6 * 10.0).round() / 10.0,
        })
        .collect();
    serde_json::to_string(&pts).unwrap_or_else(|_| "[]".to_string())
}

pub async fn route_speed_detail(
    State(state): State<AppState>,
    axum::extract::Path((agency_id, route_id)): axum::extract::Path<(String, String)>,
) -> axum::response::Response {
    use axum::response::IntoResponse;

    let route_info: Option<(String, String)> = sqlx::query_as(
        "SELECT short_name, long_name FROM routes WHERE agency_id = $1 AND route_id = $2",
    )
    .bind(&agency_id)
    .bind(&route_id)
    .fetch_optional(&state.db.pool)
    .await
    .unwrap_or_else(|e| {
        tracing::error!("DB error fetching route {agency_id}/{route_id}: {e}");
        None
    });

    let (short_name, long_name) = match route_info {
        Some(r) => r,
        None => {
            return (
                axum::http::StatusCode::NOT_FOUND,
                Html("<h1>Not Found</h1>".to_string()),
            )
                .into_response()
        }
    };

    let (spacings_res, trends_res) = tokio::join!(
        route_stop_spacings(&state.db, &agency_id, &route_id),
        route_speed_trend_by_direction(&state.db, &agency_id, &route_id, 28),
    );

    let spacings = spacings_res.unwrap_or_else(|e| {
        tracing::error!("route_stop_spacings failed for {agency_id}/{route_id}: {e}");
        vec![]
    });
    let trends = trends_res.unwrap_or_else(|e| {
        tracing::error!("route_speed_trend_by_direction failed for {agency_id}/{route_id}: {e}");
        vec![]
    });

    if spacings.is_empty() {
        return (
            axum::http::StatusCode::NOT_FOUND,
            Html("<h1>Not Found</h1>".to_string()),
        )
            .into_response();
    }

    let directions: Vec<RouteSpeedDetailDirection> = spacings
        .into_iter()
        .enumerate()
        .map(|(i, spacing)| {
            let trend = trends.iter().find(|t| t.direction_id == spacing.direction_id);
            let (weekday, saturday, sunday) = trend
                .map(|t| (t.weekday.clone(), t.saturday.clone(), t.sunday.clone()))
                .unwrap_or_default();
            RouteSpeedDetailDirection {
                chart_id: format!("dir-{i}"),
                direction_name: spacing.direction_name,
                first_stop_name: spacing.first_stop_name,
                avg_spacing_m: spacing.avg_spacing_m,
                spacings: spacing.spacings,
                weekday_json: trend_to_json(weekday),
                saturday_json: trend_to_json(saturday),
                sunday_json: trend_to_json(sunday),
            }
        })
        .collect();

    let tmpl = RouteSpeedDetailTemplate {
        short_name,
        long_name,
        agency_id,
        directions,
    };
    Html(
        tmpl.render()
            .unwrap_or_else(|e| format!("Template error: {e}")),
    )
    .into_response()
}

#[derive(Deserialize)]
pub struct AgencyFilterParams {
    agency: Option<String>,
}

#[derive(Deserialize)]
pub struct SpeedParams {
    agency: Option<String>,
    sort: Option<String>,
}

#[derive(Template)]
#[template(path = "dashboard.html")]
struct DashboardTemplate {
    routes: Vec<RouteSummary>,
    period_days: i64,
    agencies: Vec<(String, String)>,
    agency_names: std::collections::HashMap<String, String>,
    active_agency: String,
}

#[derive(Template)]
#[template(path = "report.html")]
struct ReportTemplate {
    routes: Vec<RouteSummary>,
    period_days: i64,
    generated_at: String,
    agency_names: std::collections::HashMap<String, String>,
}

pub async fn dashboard(
    State(state): State<AppState>,
    Query(params): Query<AgencyFilterParams>,
) -> Html<String> {
    let period_days: i64 = 7;
    let active_agency = params.agency.unwrap_or_default();
    let filter = if active_agency.is_empty() {
        None
    } else {
        Some(active_agency.as_str())
    };
    let routes = route_summary(&state.db, period_days, filter)
        .await
        .unwrap_or_default();
    let agencies: Vec<(String, String)> = state
        .config
        .agencies
        .iter()
        .map(|a| (a.slug.clone(), a.name.clone()))
        .collect();
    let agency_names: std::collections::HashMap<String, String> =
        agencies.iter().cloned().collect();
    let tmpl = DashboardTemplate {
        routes,
        period_days,
        agencies,
        agency_names,
        active_agency,
    };
    Html(
        tmpl.render()
            .unwrap_or_else(|e| format!("Template error: {e}")),
    )
}

pub async fn report(State(state): State<AppState>) -> Html<String> {
    let period_days: i64 = 7;
    let routes = route_summary(&state.db, period_days, None)
        .await
        .unwrap_or_default();
    let generated_at = Utc::now().format("%Y-%m-%d %H:%M UTC").to_string();
    let agency_names: std::collections::HashMap<String, String> = state
        .config
        .agencies
        .iter()
        .map(|a| (a.slug.clone(), a.name.clone()))
        .collect();
    let tmpl = ReportTemplate {
        routes,
        period_days,
        generated_at,
        agency_names,
    };
    Html(
        tmpl.render()
            .unwrap_or_else(|e| format!("Template error: {e}")),
    )
}

#[derive(Template)]
#[template(path = "hotspots.html")]
struct HotspotsTemplate {
    hotspots: Vec<StopHotspot>,
    hotspots_json: String,
    period_days: i64,
}

pub async fn hotspots(State(state): State<AppState>) -> Html<String> {
    let period_days: i64 = 7;
    // All monitored agencies share the same UTC offset (Montreal area), so using the
    // first agency's offset for time-bucketing is correct in practice. If agencies
    // from different timezones are ever added, this should be revisited.
    let agency = &state.config.agencies[0];
    let hotspots = stop_hotspots(&state.db, agency, period_days, 100)
        .await
        .unwrap_or_default();
    let hotspots_json = serde_json::to_string(&hotspots).unwrap_or_default();
    let tmpl = HotspotsTemplate {
        hotspots,
        hotspots_json,
        period_days,
    };
    Html(
        tmpl.render()
            .unwrap_or_else(|e| format!("Template error: {e}")),
    )
}

#[derive(Deserialize)]
pub struct ApiRoutesParams {
    days: Option<i64>,
}

/// Return route performance summary as JSON. Accepts optional ?days=N query param (default 7).
pub async fn api_routes(
    State(state): State<AppState>,
    Query(params): Query<ApiRoutesParams>,
) -> Result<axum::Json<Vec<RouteSummary>>, (axum::http::StatusCode, axum::Json<serde_json::Value>)>
{
    let days = params.days.unwrap_or(7);
    route_summary(&state.db, days, None)
        .await
        .map(axum::Json)
        .map_err(|e| {
            (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                axum::Json(serde_json::json!({ "error": e.to_string() })),
            )
        })
}

#[derive(Template)]
#[template(path = "speed.html")]
struct SpeedTemplate {
    cards: Vec<RouteSpeedCard>,
    agencies: Vec<(String, String)>,
    active_agency: String,
    active_sort: String,
}

#[derive(Template)]
#[template(path = "route_detail.html")]
struct RouteDetailTemplate {
    trend: RouteTrend,
    trend_json: String,
    period_days: i64,
}

pub async fn route_detail(
    State(state): State<AppState>,
    axum::extract::Path((agency_id, route_id)): axum::extract::Path<(String, String)>,
) -> Html<String> {
    let period_days: i64 = 30;
    match route_trend(&state.db, &agency_id, &route_id, period_days).await {
        Ok(Some(trend)) => {
            let trend_json = serde_json::to_string(&trend.days).unwrap_or_default();
            let tmpl = RouteDetailTemplate {
                trend,
                trend_json,
                period_days,
            };
            Html(
                tmpl.render()
                    .unwrap_or_else(|e| format!("Template error: {e}")),
            )
        }
        Ok(None) => Html(format!(
            "<p>Route '{agency_id}/{route_id}' not found or no data yet.</p>"
        )),
        Err(e) => Html(format!("<p>Error: {e}</p>")),
    }
}

fn sort_speed_cards(cards: &mut Vec<RouteSpeedCard>, sort: &str) {
    match sort {
        "scheduled" => {
            cards.sort_by(
                |a, b| match (a.avg_scheduled_speed_mps, b.avg_scheduled_speed_mps) {
                    (Some(x), Some(y)) => x
                        .partial_cmp(&y)
                        .unwrap_or(std::cmp::Ordering::Equal)
                        .then(a.short_name.cmp(&b.short_name)),
                    (Some(_), None) => std::cmp::Ordering::Less,
                    (None, Some(_)) => std::cmp::Ordering::Greater,
                    (None, None) => a.short_name.cmp(&b.short_name),
                },
            )
        }
        "actual" => cards.sort_by(
            |a, b| match (a.avg_actual_speed_mps, b.avg_actual_speed_mps) {
                (Some(x), Some(y)) => x
                    .partial_cmp(&y)
                    .unwrap_or(std::cmp::Ordering::Equal)
                    .then(a.short_name.cmp(&b.short_name)),
                (Some(_), None) => std::cmp::Ordering::Less,
                (None, Some(_)) => std::cmp::Ordering::Greater,
                (None, None) => a.short_name.cmp(&b.short_name),
            },
        ),
        _ => {} // "name" or unknown — preserve SQL order
    }
}

pub async fn speed_page(
    State(state): State<AppState>,
    Query(params): Query<SpeedParams>,
) -> Html<String> {
    let agencies: Vec<(String, String)> = state
        .config
        .agencies
        .iter()
        .map(|a| (a.slug.clone(), a.name.clone()))
        .collect();
    let active_agency = params
        .agency
        .filter(|s| agencies.iter().any(|(slug, _)| slug == s))
        .unwrap_or_default();
    let active_sort = match params.sort.as_deref() {
        Some("scheduled") => "scheduled",
        Some("actual") => "actual",
        _ => "name",
    }
    .to_string();
    let filter = if active_agency.is_empty() {
        None
    } else {
        Some(active_agency.as_str())
    };
    let agency_names: std::collections::HashMap<String, String> =
        agencies.iter().cloned().collect();
    let rows = route_speed_by_day_type(&state.db, filter)
        .await
        .unwrap_or_default();
    let mut cards = build_speed_cards(rows, &agency_names);
    sort_speed_cards(&mut cards, &active_sort);
    for (i, card) in cards.iter_mut().enumerate() {
        card.idx = i;
    }
    let tmpl = SpeedTemplate {
        cards,
        agencies,
        active_agency,
        active_sort,
    };
    Html(
        tmpl.render()
            .unwrap_or_else(|e| format!("Template error: {e}")),
    )
}

#[derive(Template)]
#[template(path = "scorecard.html")]
struct ScorecardTemplate {
    routes: Vec<ScorecardRoute>,
    benchmarks: Vec<Benchmark>,
    floor_pct: f64,
    ceiling_pct: f64,
    floor_city: String,
    ceiling_city: String,
    routes_meeting_floor: usize,
    worst_gap: Option<f64>,
    period_days: i64,
    generated_at: String,
    agencies: Vec<(String, String)>,
    agency_names: std::collections::HashMap<String, String>,
    active_agency: String,
}

pub async fn scorecard(
    State(state): State<AppState>,
    Query(params): Query<AgencyFilterParams>,
) -> Html<String> {
    let period_days: i64 = 7;
    let active_agency = params.agency.unwrap_or_default();
    let filter = if active_agency.is_empty() {
        None
    } else {
        Some(active_agency.as_str())
    };
    let benchmarks = load_benchmarks(&state.db).await.unwrap_or_default();
    let routes = scorecard_routes(&state.db, period_days, filter)
        .await
        .unwrap_or_default();

    let floor_pct = benchmarks.first().map(|b| b.on_time_pct).unwrap_or(89.0);
    let floor_speed = benchmarks
        .first()
        .map(|b| b.speed_vs_scheduled_pct)
        .unwrap_or(3.0);
    let ceiling_pct = benchmarks.last().map(|b| b.on_time_pct).unwrap_or(96.0);
    let floor_city = benchmarks
        .first()
        .map(|b| b.city.clone())
        .unwrap_or_else(|| "Helsinki".to_string());
    let ceiling_city = benchmarks
        .last()
        .map(|b| b.city.clone())
        .unwrap_or_else(|| "Tokyo".to_string());

    let routes_meeting_floor = routes
        .iter()
        .filter(|r| {
            r.avg_on_time_pct.map_or(false, |p| p >= floor_pct)
                && r.speed_vs_scheduled_pct.map_or(true, |s| s <= floor_speed)
        })
        .count();

    let worst_gap = routes
        .iter()
        .filter_map(|r| r.on_time_gap_vs(floor_pct))
        .reduce(f64::min);

    let generated_at = Utc::now().format("%Y-%m-%d %H:%M UTC").to_string();

    let agencies: Vec<(String, String)> = state
        .config
        .agencies
        .iter()
        .map(|a| (a.slug.clone(), a.name.clone()))
        .collect();
    let agency_names: std::collections::HashMap<String, String> =
        agencies.iter().cloned().collect();

    let tmpl = ScorecardTemplate {
        routes,
        benchmarks,
        floor_pct,
        ceiling_pct,
        floor_city,
        ceiling_city,
        routes_meeting_floor,
        worst_gap,
        period_days,
        generated_at,
        agencies,
        agency_names,
        active_agency,
    };
    Html(
        tmpl.render()
            .unwrap_or_else(|e| format!("Template error: {e}")),
    )
}

/// Return scheduled average speed per route+direction as JSON.
pub async fn api_route_speed(
    State(state): State<AppState>,
) -> Result<
    axum::Json<Vec<RouteSpeedSummary>>,
    (axum::http::StatusCode, axum::Json<serde_json::Value>),
> {
    route_speed_summary(&state.db, None)
        .await
        .map(axum::Json)
        .map_err(|e| {
            (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                axum::Json(serde_json::json!({ "error": e.to_string() })),
            )
        })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::speed::RouteSpeedCard;

    fn card(short_name: &str, scheduled: f64, actual: Option<f64>) -> RouteSpeedCard {
        RouteSpeedCard {
            idx: 0,
            agency_name: "A".into(),
            agency_id: "a".into(),
            route_id: "R1".into(),
            short_name: short_name.into(),
            long_name: short_name.into(),
            charts: vec![],
            avg_scheduled_speed_mps: Some(scheduled),
            avg_actual_speed_mps: actual,
        }
    }

    fn card_no_scheduled(short_name: &str) -> RouteSpeedCard {
        RouteSpeedCard {
            idx: 0,
            agency_name: "A".into(),
            agency_id: "a".into(),
            route_id: "R1".into(),
            short_name: short_name.into(),
            long_name: short_name.into(),
            charts: vec![],
            avg_scheduled_speed_mps: None,
            avg_actual_speed_mps: None,
        }
    }

    #[test]
    fn sort_scheduled_orders_ascending_by_scheduled_speed() {
        let mut cards = vec![card("B", 10.0, None), card("A", 5.0, None)];
        sort_speed_cards(&mut cards, "scheduled");
        assert_eq!(cards[0].short_name, "A");
        assert_eq!(cards[1].short_name, "B");
    }

    #[test]
    fn sort_actual_orders_ascending_by_actual_speed() {
        let mut cards = vec![card("B", 10.0, Some(8.0)), card("A", 5.0, Some(3.0))];
        sort_speed_cards(&mut cards, "actual");
        assert_eq!(cards[0].short_name, "A");
        assert_eq!(cards[1].short_name, "B");
    }

    #[test]
    fn sort_actual_puts_none_last() {
        let mut cards = vec![card("A", 5.0, None), card("B", 10.0, Some(3.0))];
        sort_speed_cards(&mut cards, "actual");
        assert_eq!(cards[0].short_name, "B");
        assert_eq!(cards[1].short_name, "A");
    }

    #[test]
    fn sort_name_leaves_order_unchanged() {
        let mut cards = vec![card("B", 5.0, None), card("A", 10.0, None)];
        sort_speed_cards(&mut cards, "name");
        assert_eq!(cards[0].short_name, "B");
        assert_eq!(cards[1].short_name, "A");
    }

    #[test]
    fn sort_unknown_param_leaves_order_unchanged() {
        let mut cards = vec![card("B", 5.0, None), card("A", 10.0, None)];
        sort_speed_cards(&mut cards, "bogus");
        assert_eq!(cards[0].short_name, "B");
    }

    #[test]
    fn sort_scheduled_puts_none_last() {
        let mut cards = vec![card_no_scheduled("A"), card("B", 5.0, None)];
        sort_speed_cards(&mut cards, "scheduled");
        assert_eq!(cards[0].short_name, "B");
        assert_eq!(cards[1].short_name, "A");
    }

    #[test]
    fn sort_scheduled_breaks_ties_by_name() {
        let mut cards = vec![card("Z", 5.0, None), card("A", 5.0, None)];
        sort_speed_cards(&mut cards, "scheduled");
        assert_eq!(cards[0].short_name, "A");
    }
}

#[cfg(test)]
mod e2e_tests {
    use super::*;
    use crate::config::{AgencyConfig, Config};
    use crate::db::test_utils;
    use crate::web::build_router;
    use axum::body::Body;
    use axum::http::{Request, StatusCode};
    use tower::ServiceExt;

    fn test_config() -> Config {
        Config {
            agencies: vec![AgencyConfig {
                slug: "test".to_string(),
                name: "Test Agency".to_string(),
                gtfs_static_url: String::new(),
                gtfs_rt_vehicle_positions_url: String::new(),
                gtfs_rt_trip_updates_url: String::new(),
                gtfs_api_key: None,
                agency_utc_offset: "-04:00".to_string(),
            }],
            database_url: String::new(),
            poll_interval_secs: 30,
            bind_address: "0.0.0.0:3000".to_string(),
            on_time_early_threshold_secs: -60,
            on_time_late_threshold_secs: 300,
            retention_days: 30,
        }
    }

    #[tokio::test]
    async fn route_speed_detail_returns_200_with_direction_name() {
        let td = test_utils::setup().await;
        sqlx::query("INSERT INTO routes VALUES ('test', 'R1', '1', 'Route 1', 3)")
            .execute(&td.db.pool).await.unwrap();
        sqlx::query("INSERT INTO trips VALUES ('test', 'T1', 'R1', 'WD', 0, 'Downtown')")
            .execute(&td.db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('test', 'S1', 'Main St',  45.50, -73.50)")
            .execute(&td.db.pool).await.unwrap();
        sqlx::query("INSERT INTO stops VALUES ('test', 'S2', 'Downtown', 45.51, -73.50)")
            .execute(&td.db.pool).await.unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S1', 1, '08:00:00', '08:00:00')")
            .execute(&td.db.pool).await.unwrap();
        sqlx::query("INSERT INTO scheduled_stops VALUES ('test', 'T1', 'S2', 2, '08:10:00', '08:10:00')")
            .execute(&td.db.pool).await.unwrap();

        let state = AppState { db: td.db, config: test_config() };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/routes/test/R1/speed")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);

        let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024).await.unwrap();
        let html = String::from_utf8(bytes.to_vec()).unwrap();
        assert!(html.contains("Downtown"), "HTML should contain terminal stop name 'Downtown'");
        assert!(html.contains("Route 1"), "HTML should contain route long name");
    }

    #[tokio::test]
    async fn route_speed_detail_returns_404_for_unknown_route() {
        let td = test_utils::setup().await;
        let state = AppState { db: td.db, config: test_config() };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/routes/test/NONEXISTENT/speed")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }
}
