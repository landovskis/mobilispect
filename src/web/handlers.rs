use askama::Template;
use axum::{extract::State, response::Html};
use chrono::{NaiveDate, Utc};

use crate::metrics::{compute_route_daily, route_summary, RouteSummary};
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

/// Trigger on-time computation for today (and optionally a past date via ?date=YYYY-MM-DD).
pub async fn compute(
    State(state): State<AppState>,
    axum::extract::Query(params): axum::extract::Query<std::collections::HashMap<String, String>>,
) -> Html<String> {
    let date: NaiveDate = params
        .get("date")
        .and_then(|s| NaiveDate::parse_from_str(s, "%Y-%m-%d").ok())
        .unwrap_or_else(|| Utc::now().date_naive());

    match compute_route_daily(&state.db, &state.config, date).await {
        Ok(()) => Html(format!(
            "<p>✓ Computed on-time performance for <strong>{date}</strong>. \
             <a href='/'>View dashboard</a></p>"
        )),
        Err(e) => Html(format!("<p>Error: {e}</p>")),
    }
}
