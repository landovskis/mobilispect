use askama::Template;
use axum::{extract::{Query, State}, response::Html};
use chrono::{NaiveDate, Utc};
use serde::Deserialize;
use serde_json;

use crate::metrics::{compute_route_daily, load_benchmarks, route_summary, route_trend, scorecard_routes, stop_hotspots, Benchmark, RouteSummary, RouteTrend, ScorecardRoute, StopHotspot};
use crate::speed::{compute_route_speed_daily, route_speed_summary, RouteSpeedSummary};
use crate::web::AppState;

#[derive(Deserialize)]
pub struct AgencyFilterParams {
    agency: Option<String>,
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
}

pub async fn dashboard(
    State(state): State<AppState>,
    Query(params): Query<AgencyFilterParams>,
) -> Html<String> {
    let period_days: i64 = 7;
    let active_agency = params.agency.unwrap_or_default();
    let filter = if active_agency.is_empty() { None } else { Some(active_agency.as_str()) };
    let routes = route_summary(&state.db, period_days, filter).await.unwrap_or_default();
    let agencies: Vec<(String, String)> = state.config.agencies.iter()
        .map(|a| (a.slug.clone(), a.name.clone()))
        .collect();
    let agency_names: std::collections::HashMap<String, String> = agencies.iter().cloned().collect();
    let tmpl = DashboardTemplate { routes, period_days, agencies, agency_names, active_agency };
    Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
}

pub async fn report(State(state): State<AppState>) -> Html<String> {
    let period_days: i64 = 7;
    let routes = route_summary(&state.db, period_days, None).await.unwrap_or_default();
    let generated_at = Utc::now().format("%Y-%m-%d %H:%M UTC").to_string();
    let tmpl = ReportTemplate { routes, period_days, generated_at };
    Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
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
    // Use the first configured agency for hotspot UTC offset calculation.
    let agency = &state.config.agencies[0];
    let hotspots = stop_hotspots(&state.db, agency, period_days, 100)
        .await
        .unwrap_or_default();
    let hotspots_json = serde_json::to_string(&hotspots).unwrap_or_default();
    let tmpl = HotspotsTemplate { hotspots, hotspots_json, period_days };
    Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
}

#[derive(Deserialize)]
pub struct ApiRoutesParams {
    days: Option<i64>,
}

/// Return route performance summary as JSON. Accepts optional ?days=N query param (default 7).
pub async fn api_routes(
    State(state): State<AppState>,
    Query(params): Query<ApiRoutesParams>,
) -> Result<axum::Json<Vec<RouteSummary>>, (axum::http::StatusCode, axum::Json<serde_json::Value>)> {
    let days = params.days.unwrap_or(7);
    route_summary(&state.db, days, None).await.map(axum::Json).map_err(|e| {
        (
            axum::http::StatusCode::INTERNAL_SERVER_ERROR,
            axum::Json(serde_json::json!({ "error": e.to_string() })),
        )
    })
}

#[derive(Template)]
#[template(path = "speed.html")]
struct SpeedTemplate {
    speeds: Vec<RouteSpeedSummary>,
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
            let tmpl = RouteDetailTemplate { trend, trend_json, period_days };
            Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
        }
        Ok(None) => Html(format!("<p>Route '{agency_id}/{route_id}' not found or no data yet.</p>")),
        Err(e) => Html(format!("<p>Error: {e}</p>")),
    }
}

pub async fn speed_page(State(state): State<AppState>) -> Html<String> {
    let speeds = route_speed_summary(&state.db, None).await.unwrap_or_default();
    let tmpl = SpeedTemplate { speeds };
    Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
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
    let filter = if active_agency.is_empty() { None } else { Some(active_agency.as_str()) };
    let benchmarks = load_benchmarks(&state.db).await.unwrap_or_default();
    let routes = scorecard_routes(&state.db, period_days, filter).await.unwrap_or_default();

    let floor_pct = benchmarks.first().map(|b| b.on_time_pct).unwrap_or(89.0);
    let floor_speed = benchmarks.first().map(|b| b.speed_vs_scheduled_pct).unwrap_or(3.0);
    let ceiling_pct = benchmarks.last().map(|b| b.on_time_pct).unwrap_or(96.0);
    let floor_city = benchmarks.first().map(|b| b.city.clone()).unwrap_or_else(|| "Helsinki".to_string());
    let ceiling_city = benchmarks.last().map(|b| b.city.clone()).unwrap_or_else(|| "Tokyo".to_string());

    let routes_meeting_floor = routes.iter().filter(|r| {
        r.avg_on_time_pct.map_or(false, |p| p >= floor_pct)
            && r.speed_vs_scheduled_pct.map_or(true, |s| s <= floor_speed)
    }).count();

    let worst_gap = routes.iter()
        .filter_map(|r| r.on_time_gap_vs(floor_pct))
        .reduce(f64::min);

    let generated_at = Utc::now().format("%Y-%m-%d %H:%M UTC").to_string();

    let agencies: Vec<(String, String)> = state.config.agencies.iter()
        .map(|a| (a.slug.clone(), a.name.clone()))
        .collect();
    let agency_names: std::collections::HashMap<String, String> = agencies.iter().cloned().collect();

    let tmpl = ScorecardTemplate {
        routes, benchmarks, floor_pct, ceiling_pct, floor_city, ceiling_city,
        routes_meeting_floor, worst_gap, period_days, generated_at,
        agencies, agency_names, active_agency,
    };
    Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
}

/// Return scheduled average speed per route+direction as JSON.
pub async fn api_route_speed(
    State(state): State<AppState>,
) -> Result<axum::Json<Vec<RouteSpeedSummary>>, (axum::http::StatusCode, axum::Json<serde_json::Value>)> {
    route_speed_summary(&state.db, None).await.map(axum::Json).map_err(|e| {
        (
            axum::http::StatusCode::INTERNAL_SERVER_ERROR,
            axum::Json(serde_json::json!({ "error": e.to_string() })),
        )
    })
}

/// Trigger on-time computation for today (and optionally a past date via ?date=YYYY-MM-DD).
pub async fn compute(
    State(state): State<AppState>,
    axum::extract::Query(params): axum::extract::Query<std::collections::HashMap<String, String>>,
) -> Html<String> {
    let date: NaiveDate = params
        .get("date")
        .and_then(|s| NaiveDate::parse_from_str(s, "%Y-%m-%d").ok())
        .unwrap_or_else(|| Utc::now().date_naive());

    for agency in &state.config.agencies {
        if let Err(e) = compute_route_daily(&state.db, &state.config, agency, date).await {
            return Html(format!("<p>Error computing on-time for {}: {e}</p>", agency.name));
        }
        if let Err(e) = compute_route_speed_daily(&state.db, agency, date).await {
            return Html(format!("<p>Error computing speed for {}: {e}</p>", agency.name));
        }
    }
    Html(format!(
        "<p>✓ Computed on-time performance and speed for <strong>{date}</strong>. \
         <a href='/'>View dashboard</a></p>"
    ))
}
