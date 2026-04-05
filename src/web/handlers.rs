use askama::Template;
use axum::{extract::{Query, State}, response::Html};
use chrono::{NaiveDate, Utc};
use serde::Deserialize;
use serde_json;

use crate::metrics::{compute_route_daily, route_summary, route_trend, stop_hotspots, RouteSummary, RouteTrend, StopHotspot};
use crate::speed::{compute_route_speed_daily, route_speed_summary, RouteSpeedSummary};
use crate::web::AppState;

#[derive(Template)]
#[template(path = "dashboard.html")]
struct DashboardTemplate {
    routes: Vec<RouteSummary>,
    period_days: i64,
}

#[derive(Template)]
#[template(path = "report.html")]
struct ReportTemplate {
    routes: Vec<RouteSummary>,
    period_days: i64,
    generated_at: String,
}

pub async fn dashboard(State(state): State<AppState>) -> Html<String> {
    let period_days: i64 = 7;
    let routes = route_summary(&state.db, period_days).await.unwrap_or_default();
    let tmpl = DashboardTemplate { routes, period_days };
    Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
}

pub async fn report(State(state): State<AppState>) -> Html<String> {
    let period_days: i64 = 7;
    let routes = route_summary(&state.db, period_days).await.unwrap_or_default();
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
    route_summary(&state.db, days).await.map(axum::Json).map_err(|e| {
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
    axum::extract::Path(route_id): axum::extract::Path<String>,
) -> Html<String> {
    let period_days: i64 = 30;
    match route_trend(&state.db, &route_id, period_days).await {
        Ok(Some(trend)) => {
            let trend_json = serde_json::to_string(&trend.days).unwrap_or_default();
            let tmpl = RouteDetailTemplate { trend, trend_json, period_days };
            Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
        }
        Ok(None) => Html(format!("<p>Route '{route_id}' not found or no data yet.</p>")),
        Err(e) => Html(format!("<p>Error: {e}</p>")),
    }
}

pub async fn speed_page(State(state): State<AppState>) -> Html<String> {
    let speeds = route_speed_summary(&state.db).await.unwrap_or_default();
    let tmpl = SpeedTemplate { speeds };
    Html(tmpl.render().unwrap_or_else(|e| format!("Template error: {e}")))
}

/// Return scheduled average speed per route+direction as JSON.
pub async fn api_route_speed(
    State(state): State<AppState>,
) -> Result<axum::Json<Vec<RouteSpeedSummary>>, (axum::http::StatusCode, axum::Json<serde_json::Value>)> {
    route_speed_summary(&state.db).await.map(axum::Json).map_err(|e| {
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

    // Use the first configured agency for UTC offset and on-time threshold calculations.
    let agency = &state.config.agencies[0];
    if let Err(e) = compute_route_daily(&state.db, &state.config, agency, date).await {
        return Html(format!("<p>Error computing on-time: {e}</p>"));
    }
    if let Err(e) = compute_route_speed_daily(&state.db, date).await {
        return Html(format!("<p>Error computing speed: {e}</p>"));
    }
    Html(format!(
        "<p>✓ Computed on-time performance and speed for <strong>{date}</strong>. \
         <a href='/'>View dashboard</a></p>"
    ))
}
