use askama::Template;
use axum::{
    extract::{Query, State},
    http::HeaderMap,
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
    RouteClass, RouteSpeedCard, RouteSpeedSummary, StopSpacing,
    build_speed_cards, classify_by_spacing,
    route_speed_by_day_type, route_speed_summary, route_speed_trend_by_direction,
    route_stop_spacings,
};
use crate::web::AppState;

struct RouteSpeedDetailDirection {
    pub direction_name: String,
    pub first_stop_name: String,
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
        let (range_min, range_max) = if avg < 500.0 {
            (300.0, 500.0)
        } else if avg < 1500.0 {
            (500.0, 1500.0)
        } else {
            (1500.0, 5000.0)
        };
        if avg < range_min {
            "slow"
        } else if avg > range_max {
            "outlier"
        } else {
            ""
        }
    }
}

#[derive(Template)]
#[template(path = "route_speed_detail.html")]
struct RouteSpeedDetailTemplate {
    region_name: String,
    short_name: String,
    long_name: String,
    agency_id: String,
    directions: Vec<RouteSpeedDetailDirection>,
    classification: Option<RouteClass>,
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
                .into_response();
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
            let trend = trends
                .iter()
                .find(|t| t.direction_id == spacing.direction_id);
            let (weekday, saturday, sunday) = trend
                .map(|t| (t.weekday.clone(), t.saturday.clone(), t.sunday.clone()))
                .unwrap_or_default();
            RouteSpeedDetailDirection {
                direction_name: spacing.direction_name,
                first_stop_name: spacing.first_stop_name,
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
        .collect();

    let avg_spacing_m: Option<f64> = {
        let vals: Vec<f64> = directions.iter().map(|d| d.avg_spacing_m).collect();
        if vals.is_empty() {
            None
        } else {
            Some(vals.iter().sum::<f64>() / vals.len() as f64)
        }
    };
    let classification = avg_spacing_m.map(classify_by_spacing);

    let tmpl = RouteSpeedDetailTemplate {
        region_name: state.config.region.name.clone(),
        short_name,
        long_name,
        agency_id,
        directions,
        classification,
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
    class: Option<String>,
}

#[derive(Template)]
#[template(path = "dashboard.html")]
struct DashboardTemplate {
    region_name: String,
    routes: Vec<RouteSummary>,
    period_days: i64,
    agencies: Vec<(String, String)>,
    agency_names: std::collections::HashMap<String, String>,
    active_agency: String,
}

#[derive(Template)]
#[template(path = "report.html")]
struct ReportTemplate {
    region_name: String,
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
        .map(|a| (a.id.to_string(), a.name.clone()))
        .collect();
    let agency_names: std::collections::HashMap<String, String> =
        agencies.iter().cloned().collect();
    let tmpl = DashboardTemplate {
        region_name: state.config.region.name.clone(),
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
        .map(|a| (a.id.to_string(), a.name.clone()))
        .collect();
    let tmpl = ReportTemplate {
        region_name: state.config.region.name.clone(),
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
    region_name: String,
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
        region_name: state.config.region.name.clone(),
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
    region_name: String,
    cards: Vec<RouteSpeedCard>,
    agencies: Vec<(String, String)>,
    active_agency: String,
    active_sort: String,
    active_class: String,
}

#[derive(Template)]
#[template(path = "speed_content.html")]
struct SpeedContentTemplate {
    cards: Vec<RouteSpeedCard>,
    agencies: Vec<(String, String)>,
    active_agency: String,
    active_sort: String,
    active_class: String,
}

#[derive(Template)]
#[template(path = "route_detail.html")]
struct RouteDetailTemplate {
    region_name: String,
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
                region_name: state.config.region.name.clone(),
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

fn parse_class(class: &str) -> Option<RouteClass> {
    match class {
        "local" => Some(RouteClass::Local),
        "rapid" => Some(RouteClass::Rapid),
        "express" => Some(RouteClass::Express),
        _ => None,
    }
}

fn filter_speed_cards(cards: &mut Vec<RouteSpeedCard>, class: &str) {
    let Some(target) = parse_class(class) else {
        return;
    };
    cards.retain(|c| c.classification == Some(target));
}

pub async fn speed_page(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(params): Query<SpeedParams>,
) -> Html<String> {
    let agencies: Vec<(String, String)> = state
        .config
        .agencies
        .iter()
        .map(|a| (a.id.to_string(), a.name.clone()))
        .collect();
    let active_agency = params
        .agency
        .filter(|s| agencies.iter().any(|(id, _)| id == s))
        .unwrap_or_default();
    let active_sort = match params.sort.as_deref() {
        Some("scheduled") => "scheduled",
        Some("actual") => "actual",
        _ => "name",
    }
    .to_string();
    let active_class = match params.class.as_deref() {
        Some("local") => "local",
        Some("rapid") => "rapid",
        Some("express") => "express",
        _ => "",
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
    filter_speed_cards(&mut cards, &active_class);
    sort_speed_cards(&mut cards, &active_sort);
    for (i, card) in cards.iter_mut().enumerate() {
        card.idx = i;
    }

    if headers.contains_key("hx-request") {
        let tmpl = SpeedContentTemplate {
            cards,
            agencies,
            active_agency,
            active_sort,
            active_class,
        };
        Html(
            tmpl.render()
                .unwrap_or_else(|e| format!("Template error: {e}")),
        )
    } else {
        let tmpl = SpeedTemplate {
            region_name: state.config.region.name.clone(),
            cards,
            agencies,
            active_agency,
            active_sort,
            active_class,
        };
        Html(
            tmpl.render()
                .unwrap_or_else(|e| format!("Template error: {e}")),
        )
    }
}

#[derive(Template)]
#[template(path = "scorecard.html")]
struct ScorecardTemplate {
    region_name: String,
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
        .map(|a| (a.id.to_string(), a.name.clone()))
        .collect();
    let agency_names: std::collections::HashMap<String, String> =
        agencies.iter().cloned().collect();

    let tmpl = ScorecardTemplate {
        region_name: state.config.region.name.clone(),
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
            avg_scheduled_speed_mps: Some(scheduled),
            avg_actual_speed_mps: actual,
            avg_stop_spacing_m: None,
            classification: None,
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
            avg_scheduled_speed_mps: None,
            avg_actual_speed_mps: None,
            avg_stop_spacing_m: None,
            classification: None,
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

    fn card_with_class(short_name: &str, class: Option<RouteClass>) -> RouteSpeedCard {
        RouteSpeedCard {
            idx: 0,
            agency_name: "A".into(),
            agency_id: "a".into(),
            route_id: "R1".into(),
            short_name: short_name.into(),
            long_name: short_name.into(),
            avg_scheduled_speed_mps: None,
            avg_actual_speed_mps: None,
            avg_stop_spacing_m: None,
            classification: class,
        }
    }

    #[test]
    fn filter_by_class_keeps_matching_cards() {
        let mut cards = vec![
            card_with_class("L1", Some(RouteClass::Local)),
            card_with_class("R1", Some(RouteClass::Rapid)),
            card_with_class("U1", None),
        ];
        filter_speed_cards(&mut cards, "rapid");
        assert_eq!(cards.len(), 1);
        assert_eq!(cards[0].short_name, "R1");
    }

    #[test]
    fn filter_by_class_hides_unclassified() {
        let mut cards = vec![
            card_with_class("U1", None),
            card_with_class("L1", Some(RouteClass::Local)),
        ];
        filter_speed_cards(&mut cards, "local");
        assert_eq!(cards.len(), 1);
        assert_eq!(cards[0].short_name, "L1");
    }

    #[test]
    fn filter_by_empty_class_keeps_all() {
        let mut cards = vec![
            card_with_class("L1", Some(RouteClass::Local)),
            card_with_class("U1", None),
        ];
        filter_speed_cards(&mut cards, "");
        assert_eq!(cards.len(), 2);
    }

    #[test]
    fn filter_by_class_keeps_only_express_cards() {
        let mut cards = vec![
            card_with_class("E1", Some(RouteClass::Express)),
            card_with_class("L1", Some(RouteClass::Local)),
            card_with_class("U1", None),
        ];
        filter_speed_cards(&mut cards, "express");
        assert_eq!(cards.len(), 1);
        assert_eq!(cards[0].short_name, "E1");
    }

    fn direction(avg_spacing_m: f64) -> RouteSpeedDetailDirection {
        RouteSpeedDetailDirection {
            direction_name: String::new(),
            first_stop_name: String::new(),
            avg_spacing_m,
            spacings: vec![],
            weekday_chart_id: String::new(),
            saturday_chart_id: String::new(),
            sunday_chart_id: String::new(),
            weekday_json: String::new(),
            saturday_json: String::new(),
            sunday_json: String::new(),
        }
    }

    #[test]
    fn avg_spacing_status_class_returns_slow_when_below_local_range_min() {
        assert_eq!(direction(200.0).avg_spacing_status_class(), "slow");
    }

    #[test]
    fn avg_spacing_status_class_returns_empty_when_in_range() {
        assert_eq!(direction(400.0).avg_spacing_status_class(), "");
        assert_eq!(direction(1000.0).avg_spacing_status_class(), "");
        assert_eq!(direction(2000.0).avg_spacing_status_class(), "");
    }

    #[test]
    fn avg_spacing_status_class_returns_outlier_when_above_express_range_max() {
        assert_eq!(direction(6000.0).avg_spacing_status_class(), "outlier");
    }
}

#[cfg(test)]
mod e2e_tests {
    use super::*;
    use crate::config::{AgencyConfig, Config, RegionConfig};
    use crate::db::test_utils;
    use crate::web::build_router;
    use axum::body::Body;
    use axum::http::{Request, StatusCode};
    use tower::ServiceExt;

    fn test_config() -> Config {
        let agencies = vec![AgencyConfig {
            id: 0,
            name: "Test Agency".to_string(),
            gtfs_static_url: String::new(),
            gtfs_rt_vehicle_positions_url: None,
            gtfs_rt_trip_updates_url: None,
            gtfs_api_key: None,
            agency_utc_offset: "-04:00".to_string(),
        }];

        Config {
            agencies: agencies.clone(),
            region: RegionConfig {
                name: "Test Region".to_string(),
                timezone: "America/Toronto".to_string(),
                agencies,
            },
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
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Downtown')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S1', 'Main St',  45.50, -73.50)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S2', 'Downtown', 45.51, -73.50)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S1', 1, '08:00:00', '08:00:00')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S2', 2, '08:10:00', '08:10:00')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        let state = AppState {
            db: td.db,
            config: test_config(),
        };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/routes/0/R1/speed")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);

        let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024)
            .await
            .unwrap();
        let html = String::from_utf8(bytes.to_vec()).unwrap();
        assert!(
            html.contains("Downtown"),
            "HTML should contain terminal stop name 'Downtown'"
        );
        assert!(
            html.contains("Route 1"),
            "HTML should contain route long name"
        );
        assert!(
            html.contains("Stop spacing"),
            "HTML should contain stop spacing label.\n\nHTML:\n{}",
            html
        );

        if !html.contains(" km") && !html.contains(" m") {
            eprintln!("FULL HTML:\n{}", html);
            panic!("HTML should contain average spacing value (km or m)");
        }
    }

    #[tokio::test]
    async fn route_speed_detail_returns_404_for_unknown_route() {
        let td = test_utils::setup().await;
        let state = AppState {
            db: td.db,
            config: test_config(),
        };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/routes/0/NONEXISTENT/speed")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }

    #[tokio::test]
    async fn speed_page_shows_classification_badge_for_rapid_route() {
        let td = test_utils::setup().await;
        // R1: two stops ~1111 m apart (0.01° lat). Single segment → avg = 1111 m → Rapid.
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Terminus')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S1', 'First',   45.500, -73.50)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S2', 'Terminus', 45.510, -73.50)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S1', 1, '08:00:00', '08:00:00')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S2', 2, '08:10:00', '08:10:00')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_speed (agency_id, route_id, direction_id, scheduled_speed_mps, trip_count, computed_at) VALUES ('0', 'R1', 0, 8.0, 1, '2026-01-01T00:00:00Z')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        let state = AppState {
            db: td.db,
            config: test_config(),
        };
        let app = build_router(state);
        let response = app
            .oneshot(
                Request::builder()
                    .uri("/speed?agency=0")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::OK);
        let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024)
            .await
            .unwrap();
        let html = String::from_utf8(bytes.to_vec()).unwrap();
        assert!(
            html.contains("Rapid"),
            "speed page HTML should contain 'Rapid' badge text"
        );
        assert!(
            html.contains("badge--rapid"),
            "speed page HTML should contain 'badge--rapid' CSS class"
        );
    }

    #[tokio::test]
    async fn speed_page_filters_by_class_local() {
        let td = test_utils::setup().await;

        // Local route: two stops ~333 m apart (0.003° lat × 111 km/°) → avg < 500 m → Local
        sqlx::query("INSERT INTO routes VALUES ('0', 'RL', 'LocalX', 'Local Route', 3)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'TL', 'RL', 'WD', 0, 'End Local')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'SL1', 'Local A', 45.500, -73.50)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'SL2', 'Local B', 45.503, -73.50)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'TL', 'SL1', 1, '08:00:00', '08:00:00')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'TL', 'SL2', 2, '08:05:00', '08:05:00')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_speed
             (agency_id, route_id, direction_id, scheduled_speed_mps, avg_stop_spacing_m, trip_count, computed_at)
             VALUES ('0', 'RL', 0, 5.0, 333.0, 1, '2026-01-01T00:00:00Z')",
        )
            .execute(&td.db.pool).await.unwrap();

        // Rapid route: two stops ~1111 m apart (0.010° lat) → avg 500–1500 m → Rapid
        sqlx::query("INSERT INTO routes VALUES ('0', 'RR', 'RapidX', 'Rapid Route', 3)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'TR', 'RR', 'WD', 0, 'End Rapid')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'SR1', 'Rapid A', 45.500, -73.50)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'SR2', 'Rapid B', 45.510, -73.50)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'TR', 'SR1', 1, '08:00:00', '08:00:00')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'TR', 'SR2', 2, '08:10:00', '08:10:00')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_speed
             (agency_id, route_id, direction_id, scheduled_speed_mps, avg_stop_spacing_m, trip_count, computed_at)
             VALUES ('0', 'RR', 0, 8.0, 1111.0, 1, '2026-01-01T00:00:00Z')",
        )
            .execute(&td.db.pool).await.unwrap();

        let state = AppState {
            db: td.db,
            config: test_config(),
        };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/speed?class=local")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
        let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024)
            .await
            .unwrap();
        let html = String::from_utf8(bytes.to_vec()).unwrap();

        assert!(
            html.contains("LocalX"),
            "Local route should appear when filtering by class=local"
        );
        assert!(
            !html.contains("RapidX"),
            "Rapid route should be hidden when filtering by class=local"
        );
    }

    #[tokio::test]
    async fn speed_page_with_hx_request_returns_fragment_not_full_page() {
        let td = test_utils::setup().await;
        let state = AppState {
            db: td.db,
            config: test_config(),
        };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/speed")
                    .header("hx-request", "true")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
        let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024)
            .await
            .unwrap();
        let html = String::from_utf8(bytes.to_vec()).unwrap();
        assert!(
            html.contains(r#"id="speed-content""#),
            "fragment must contain the swap target div"
        );
        assert!(
            !html.contains("<html"),
            "fragment must not contain a full HTML document"
        );
    }

    #[tokio::test]
    async fn speed_page_fragment_includes_htmx_loading_indicator() {
        let td = test_utils::setup().await;
        let state = AppState {
            db: td.db,
            config: test_config(),
        };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/speed")
                    .header("hx-request", "true")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
        let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024)
            .await
            .unwrap();
        let html = String::from_utf8(bytes.to_vec()).unwrap();

        assert!(
            html.contains(r##"hx-indicator="#speed-loading""##),
            "speed content should route HTMX request state to the loading indicator"
        );
        assert!(
            html.contains(r#"id="speed-loading""#),
            "fragment should include a stable loading indicator target"
        );
        assert!(
            html.contains("Loading route speeds"),
            "loading indicator should explain the in-flight request"
        );
    }

    #[tokio::test]
    async fn speed_page_without_hx_request_returns_full_page() {
        let td = test_utils::setup().await;
        let state = AppState {
            db: td.db,
            config: test_config(),
        };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/speed")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
        let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024)
            .await
            .unwrap();
        let html = String::from_utf8(bytes.to_vec()).unwrap();
        assert!(
            html.contains("<html"),
            "full page response must contain an <html> element"
        );
    }

    #[tokio::test]
    async fn route_speed_detail_shows_classification_badge() {
        let td = test_utils::setup().await;
        // Two stops ~1111 m apart → avg spacing 1111 m → Rapid
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '1', 'Route 1', 3)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO trips VALUES ('0', 'T1', 'R1', 'WD', 0, 'Downtown')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S1', 'Main St',  45.50, -73.50)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO stops VALUES ('0', 'S2', 'Downtown', 45.51, -73.50)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S1', 1, '08:00:00', '08:00:00')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO scheduled_stops VALUES ('0', 'T1', 'S2', 2, '08:10:00', '08:10:00')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        let state = AppState {
            db: td.db,
            config: test_config(),
        };
        let app = build_router(state);
        let response = app
            .oneshot(
                Request::builder()
                    .uri("/routes/0/R1/speed")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::OK);
        let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024)
            .await
            .unwrap();
        let html = String::from_utf8(bytes.to_vec()).unwrap();
        assert!(
            html.contains("Rapid"),
            "detail page HTML should contain 'Rapid' badge text"
        );
        assert!(
            html.contains("badge--rapid"),
            "detail page HTML should contain 'badge--rapid' CSS class"
        );
    }

}
