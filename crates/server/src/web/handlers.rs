use askama::Template;
use axum::{
    Form, Json,
    extract::{Query, State},
    http::{HeaderMap, HeaderValue, StatusCode},
    response::{Html, IntoResponse},
};
use serde::Deserialize;

use crate::web::{AppState, SetupState};
use mobilispect_core::db::feeds::{load_feed_options, store_discovered_feeds};
use mobilispect_core::ids::{FeedId, RouteId};
use mobilispect_core::on_time_performance::{RouteSummary, RouteTrend, route_summary, route_trend};
use mobilispect_core::service_frequency::{
    RouteHourlyFrequency, ScheduleGroup, group_by_span, route_headways, route_hourly_frequency,
};
use mobilispect_core::speed_analysis::{
    RouteClass, RouteSpeedCard, RouteSpeedDetailDirection, RouteSpeedSummary, assign_indices,
    build_detail_directions, build_speed_cards, classify_by_spacing, fetch_route_info,
    filter_speed_cards, route_speed_by_day_type, route_speed_summary, route_speed_trend_by_variant,
    route_stop_spacings, sort_speed_cards,
};
use mobilispect_core::transitland::TransitlandClient;

#[derive(Template)]
#[template(path = "pages/route_speed_detail.html")]
struct RouteSpeedDetailTemplate {
    region_name: String,
    short_name: String,
    long_name: String,
    agency_id: FeedId,
    directions: Vec<RouteSpeedDetailDirection>,
    classification: Option<RouteClass>,
}

pub async fn route_speed_detail(
    State(state): State<AppState>,
    axum::extract::Path((feed_id_str, route_id)): axum::extract::Path<(String, String)>,
) -> axum::response::Response {
    use axum::response::IntoResponse;

    let feed_id = feed_id_str
        .parse::<i64>()
        .map(FeedId::from)
        .unwrap_or_else(|_| FeedId::from(0i64));
    let route_id = RouteId::from(route_id);

    let (short_name, long_name) = match fetch_route_info(&state.db, &route_id).await {
        Ok(Some(r)) => r,
        Ok(None) => {
            return (
                axum::http::StatusCode::NOT_FOUND,
                Html("<h1>Not Found</h1>".to_string()),
            )
                .into_response();
        }
        Err(e) => {
            tracing::error!("DB error fetching route {feed_id}/{route_id}: {e}");
            return (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                Html("<h1>Internal Server Error</h1>".to_string()),
            )
                .into_response();
        }
    };
    let (spacings_res, trends_res) = tokio::join!(
        route_stop_spacings(&state.db, feed_id, &route_id),
        route_speed_trend_by_variant(&state.db, feed_id, &route_id, 28),
    );

    let spacings = match spacings_res {
        Ok(s) if s.is_empty() => {
            return (
                axum::http::StatusCode::NOT_FOUND,
                Html("<h1>Not Found</h1>".to_string()),
            )
                .into_response();
        }
        Ok(s) => s,
        Err(e) => {
            tracing::error!("route_stop_spacings failed for {feed_id}/{route_id}: {e}");
            return (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                Html("<h1>Internal Server Error</h1>".to_string()),
            )
                .into_response();
        }
    };
    let trends = trends_res.unwrap_or_else(|e| {
        tracing::error!("route_speed_trend_by_variant failed for {feed_id}/{route_id}: {e}");
        vec![]
    });

    let directions = build_detail_directions(spacings, trends);
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
        region_name: region_name(&state).await,
        short_name,
        long_name,
        agency_id: feed_id,
        directions,
        classification,
    };
    match tmpl.render() {
        Ok(html) => Html(html).into_response(),
        Err(e) => {
            tracing::error!(
                "Template render error for route_speed_detail {feed_id}/{route_id}: {e}"
            );
            (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                Html("<h1>Internal Server Error</h1>".to_string()),
            )
                .into_response()
        }
    }
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
            tracing::error!(days = days, error = %e, "DB error in api_routes");
            (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                axum::Json(serde_json::json!({ "error": e.to_string() })),
            )
        })
}

#[derive(Template)]
#[template(path = "pages/speed.html")]
struct SpeedTemplate {
    region_name: String,
    cards: Vec<RouteSpeedCard>,
    agencies: Vec<(String, String)>,
    active_agency: String,
    active_sort: String,
    active_class: String,
}

#[derive(Template)]
#[template(path = "partials/speed_content.html")]
struct SpeedContentTemplate {
    cards: Vec<RouteSpeedCard>,
    agencies: Vec<(String, String)>,
    active_agency: String,
    active_sort: String,
    active_class: String,
}

#[derive(Template)]
#[template(path = "pages/route_detail.html")]
struct RouteDetailTemplate {
    region_name: String,
    trend: RouteTrend,
    trend_json: String,
    period_days: i64,
}

pub async fn route_detail(
    State(state): State<AppState>,
    axum::extract::Path((feed_id_str, route_id)): axum::extract::Path<(String, String)>,
) -> axum::response::Response {
    use axum::response::IntoResponse;

    let period_days: i64 = 30;
    let feed_id = feed_id_str
        .parse::<i64>()
        .map(FeedId::from)
        .unwrap_or_else(|_| FeedId::from(0i64));
    let route_id = RouteId::from(route_id);
    match route_trend(&state.db, feed_id, &route_id, period_days).await {
        Ok(Some(trend)) => {
            let trend_json = match serde_json::to_string(&trend.days) {
                Ok(json) => json,
                Err(e) => {
                    tracing::error!("Failed to serialize trend data for {feed_id}/{route_id}: {e}");
                    return (
                        axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                        Html("<h1>Internal Server Error</h1>".to_string()),
                    )
                        .into_response();
                }
            };
            let tmpl = RouteDetailTemplate {
                region_name: region_name(&state).await,
                trend,
                trend_json,
                period_days,
            };
            match tmpl.render() {
                Ok(html) => Html(html).into_response(),
                Err(e) => {
                    tracing::error!(
                        "Template render error for route_detail {feed_id}/{route_id}: {e}"
                    );
                    (
                        axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                        Html("<h1>Internal Server Error</h1>".to_string()),
                    )
                        .into_response()
                }
            }
        }
        Ok(None) => (
            axum::http::StatusCode::NOT_FOUND,
            Html("<h1>Not Found</h1>".to_string()),
        )
            .into_response(),
        Err(e) => {
            tracing::error!("DB error fetching route trend for {feed_id}/{route_id}: {e}");
            (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                Html("<h1>Internal Server Error</h1>".to_string()),
            )
                .into_response()
        }
    }
}

pub async fn speed_page(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(params): Query<SpeedParams>,
) -> axum::response::Response {
    use axum::response::IntoResponse;

    let agencies = load_feed_options(&state.db.pool).await.unwrap_or_default();
    let active_agency = params
        .agency
        .filter(|s| agencies.iter().any(|(id, _)| id == s))
        .unwrap_or_default();
    let active_sort = match params.sort.as_deref() {
        Some("scheduled") => "scheduled",
        Some("actual") => "actual",
        Some("spacing") => "spacing",
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
    let feed_id_filter: Option<FeedId> = if active_agency.is_empty() {
        None
    } else {
        active_agency.parse::<i64>().ok().map(FeedId::from)
    };
    let agency_names: std::collections::HashMap<String, String> =
        agencies.iter().cloned().collect();
    let rows = match route_speed_by_day_type(&state.db, feed_id_filter).await {
        Ok(rows) => rows,
        Err(e) => {
            tracing::error!(error = %e, "DB error in speed_page");
            return (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                Html("<h1>Internal Server Error</h1>".to_string()),
            )
                .into_response();
        }
    };
    let cards = assign_indices(sort_speed_cards(
        filter_speed_cards(build_speed_cards(rows, &agency_names), &active_class),
        &active_sort,
    ));

    if headers.contains_key("hx-request") {
        let tmpl = SpeedContentTemplate {
            cards,
            agencies,
            active_agency,
            active_sort,
            active_class,
        };
        match tmpl.render() {
            Ok(html) => Html(html).into_response(),
            Err(e) => {
                tracing::error!(error = %e, "Template render error in speed_page (content)");
                (
                    axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                    Html("<h1>Internal Server Error</h1>".to_string()),
                )
                    .into_response()
            }
        }
    } else {
        let tmpl = SpeedTemplate {
            region_name: region_name(&state).await,
            cards,
            agencies,
            active_agency,
            active_sort,
            active_class,
        };
        match tmpl.render() {
            Ok(html) => Html(html).into_response(),
            Err(e) => {
                tracing::error!(error = %e, "Template render error in speed_page");
                (
                    axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                    Html("<h1>Internal Server Error</h1>".to_string()),
                )
                    .into_response()
            }
        }
    }
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
            tracing::error!(error = %e, "DB error in api_route_speed");
            (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                axum::Json(serde_json::json!({ "error": e.to_string() })),
            )
        })
}

#[derive(Template)]
#[template(path = "pages/frequency.html")]
struct FrequencyTemplate {
    region_name: String,
    groups: Vec<ScheduleGroup>,
    agencies: Vec<(String, String)>,
    active_agency: String,
}

#[derive(Template)]
#[template(path = "partials/frequency_content.html")]
struct FrequencyContentTemplate {
    groups: Vec<ScheduleGroup>,
    agencies: Vec<(String, String)>,
    active_agency: String,
}

pub async fn frequency_page(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(params): Query<AgencyFilterParams>,
) -> axum::response::Response {
    use axum::response::IntoResponse;

    let agencies = load_feed_options(&state.db.pool).await.unwrap_or_default();
    let active_agency = params
        .agency
        .filter(|s| agencies.iter().any(|(id, _)| id == s))
        .unwrap_or_default();
    let feed_filter: Option<FeedId> = if active_agency.is_empty() {
        None
    } else {
        active_agency.parse::<i64>().ok().map(FeedId::from)
    };
    let rows = match route_headways(&state.db, feed_filter).await {
        Ok(rows) => rows,
        Err(e) => {
            tracing::error!(error = %e, "DB error in frequency_page");
            return (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                Html("<h1>Internal Server Error</h1>".to_string()),
            )
                .into_response();
        }
    };
    let groups = group_by_span(rows);

    if headers.contains_key("hx-request") {
        let tmpl = FrequencyContentTemplate {
            groups,
            agencies,
            active_agency,
        };
        match tmpl.render() {
            Ok(html) => Html(html).into_response(),
            Err(e) => {
                tracing::error!(error = %e, "Template render error in frequency_page (content)");
                (
                    axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                    Html("<h1>Internal Server Error</h1>".to_string()),
                )
                    .into_response()
            }
        }
    } else {
        let tmpl = FrequencyTemplate {
            region_name: region_name(&state).await,
            groups,
            agencies,
            active_agency,
        };
        match tmpl.render() {
            Ok(html) => Html(html).into_response(),
            Err(e) => {
                tracing::error!(error = %e, "Template render error in frequency_page");
                (
                    axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                    Html("<h1>Internal Server Error</h1>".to_string()),
                )
                    .into_response()
            }
        }
    }
}

#[derive(Template)]
#[template(path = "pages/schedule_detail.html")]
struct ScheduleDetailTemplate {
    region_name: String,
    feed_id: FeedId,
    frequency: RouteHourlyFrequency,
}

pub async fn schedule_detail(
    State(state): State<AppState>,
    axum::extract::Path((feed_id_str, route_id_str)): axum::extract::Path<(String, String)>,
) -> axum::response::Response {
    let feed_id = feed_id_str
        .parse::<i64>()
        .map(FeedId::from)
        .unwrap_or_else(|_| FeedId::from(0i64));
    let route_id = RouteId::from(route_id_str);

    let frequency = match route_hourly_frequency(&state.db, feed_id, &route_id).await {
        Ok(Some(f)) => f,
        Ok(None) => {
            return (
                StatusCode::NOT_FOUND,
                Html("<h1>Not Found</h1>".to_string()),
            )
                .into_response();
        }
        Err(e) => {
            tracing::error!(error = %e, "DB error in schedule_detail");
            return (
                StatusCode::INTERNAL_SERVER_ERROR,
                Html("<h1>Internal Server Error</h1>".to_string()),
            )
                .into_response();
        }
    };

    let tmpl = ScheduleDetailTemplate {
        region_name: region_name(&state).await,
        feed_id,
        frequency,
    };
    match tmpl.render() {
        Ok(html) => Html(html).into_response(),
        Err(e) => {
            tracing::error!(error = %e, "Template render error in schedule_detail");
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Html("<h1>Internal Server Error</h1>".to_string()),
            )
                .into_response()
        }
    }
}

pub async fn health_check(State(state): State<AppState>) -> axum::response::Response {
    match mobilispect_core::health::db_ping(&state.db.pool).await {
        Ok(()) => (StatusCode::OK, Json(serde_json::json!({"status": "ok"}))).into_response(),
        Err(e) => (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(serde_json::json!({"status": "error", "message": format!("db ping failed: {e}")})),
        )
            .into_response(),
    }
}

async fn region_name(state: &AppState) -> String {
    state.region.read().await.clone().unwrap_or_default()
}

#[derive(Template)]
#[template(path = "pages/setup.html")]
struct SetupFormTemplate {
    error: Option<String>,
    prefill: String,
}

#[derive(Template)]
#[template(path = "pages/setup_progress.html")]
struct SetupProgressTemplate {
    city: String,
}

#[derive(Deserialize)]
pub struct SetupForm {
    city_name: String,
}

pub async fn setup_page() -> impl IntoResponse {
    Html(
        SetupFormTemplate {
            error: None,
            prefill: String::new(),
        }
        .render()
        .unwrap_or_else(|e| {
            tracing::warn!("Template render failed: {e}");
            String::new()
        }),
    )
}

pub async fn setup_submit(
    State(state): State<AppState>,
    Form(form): Form<SetupForm>,
) -> impl IntoResponse {
    let city = form.city_name.trim().to_string();

    {
        let mut setup = state.setup_state.lock().await;
        if matches!(*setup, SetupState::Running) {
            return Html(
                SetupProgressTemplate { city: city.clone() }
                    .render()
                    .unwrap_or_else(|e| {
                        tracing::warn!("Template render failed: {e}");
                        String::new()
                    }),
            )
            .into_response();
        }
        *setup = SetupState::Running;
    }

    let pool = state.db.pool.clone();
    let api_key = state.config.transitland_api_key.clone();
    let setup_state = state.setup_state.clone();
    let city_clone = city.clone();
    tokio::spawn(async move {
        let client = TransitlandClient::new(api_key);
        let result = async {
            let feeds = client.discover_feeds_for_city(&city_clone).await?;
            if feeds.is_empty() {
                anyhow::bail!("No feeds found for '{city_clone}' — try a different city name");
            }
            store_discovered_feeds(&pool, &city_clone, &feeds).await?;
            anyhow::Ok(city_clone.clone())
        }
        .await;

        let mut setup = setup_state.lock().await;
        *setup = match result {
            Ok(city) => SetupState::Done { city },
            Err(e) => SetupState::Failed {
                message: e.to_string(),
                city: city_clone.clone(),
            },
        };
    });

    Html(SetupProgressTemplate { city }.render().unwrap_or_else(|e| {
        tracing::warn!("Template render failed: {e}");
        String::new()
    }))
    .into_response()
}

pub async fn setup_status(State(state): State<AppState>) -> impl IntoResponse {
    let terminal = {
        let mut setup = state.setup_state.lock().await;
        if !matches!(*setup, SetupState::Done { .. } | SetupState::Failed { .. }) {
            // Not terminal yet — return spinner and keep the lock short
            return Html(
                r#"<div class="setup-card"
                                 hx-get="/setup/status"
                                 hx-trigger="every 1s"
                                 hx-target="this"
                                 hx-swap="outerHTML">
                               <h1 class="setup-title">Searching Transitland…</h1>
                               <div class="spinner"></div>
                               <p class="setup-sub">Discovering transit feeds for your city.</p>
                            </div>"#,
            )
            .into_response();
        }
        std::mem::replace(&mut *setup, SetupState::Idle)
    };

    match terminal {
        SetupState::Done { city } => {
            *state.region.write().await = Some(city);
            let mut headers = HeaderMap::new();
            headers.insert("HX-Redirect", HeaderValue::from_static("/"));
            (StatusCode::OK, headers, Html(String::new())).into_response()
        }
        SetupState::Failed { message, city } => Html(
            SetupFormTemplate {
                error: Some(message),
                prefill: city,
            }
            .render()
            .unwrap_or_else(|e| {
                tracing::warn!("Template render failed: {e}");
                String::new()
            }),
        )
        .into_response(),
        _ => {
            // Should not be reached — state was terminal when we checked
            Html(String::new()).into_response()
        }
    }
}

#[cfg(test)]
mod e2e_tests {
    use super::*;
    use crate::web::{SetupState, build_router};
    use axum::body::Body;
    use axum::http::{Request, StatusCode};
    use mobilispect_core::config::Config;
    use mobilispect_core::db::test_utils;
    use std::sync::Arc;
    use tokio::sync::RwLock;
    use tower::ServiceExt;

    fn test_config() -> Config {
        Config {
            database_url: String::new(),
            poll_interval_secs: 30,
            bind_address: "0.0.0.0:3000".to_string(),
            on_time_early_threshold_secs: -60,
            on_time_late_threshold_secs: 300,
            retention_days: 30,
            worker_health_bind_address: "0.0.0.0:9090".to_string(),
            transitland_api_key: None,
        }
    }

    #[tokio::test]
    async fn route_speed_detail_returns_200_with_direction_name() {
        let td = test_utils::setup().await;
        sqlx::query("INSERT INTO feeds (id, gtfs_static_url) VALUES (0, 'http://test')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO agencies (onestop_id, name) VALUES ('a', 'Agency')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('R1', 'a', '1', 'Route 1', 3)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S1', 'Main St', 45.50, -73.50)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S2', 'Downtown', 45.51, -73.50)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_variants (feed_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary) VALUES (0, 'VAR1', 'R1', 0, 2, 1, true)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_variant_stops (feed_id, variant_id, stop_sequence, stop_id) VALUES (0, 'VAR1', 1, 'S1')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_variant_stops (feed_id, variant_id, stop_sequence, stop_id) VALUES (0, 'VAR1', 2, 'S2')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
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
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
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
        // R1: avg_stop_spacing_m = 1111 m → Rapid.
        sqlx::query("INSERT INTO feeds (id, gtfs_static_url) VALUES (0, 'http://test')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO agencies (onestop_id, name) VALUES ('a', 'Agency')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('R1', 'a', '1', 'Route 1', 3)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_speed (feed_id, route_id, variant_id, scheduled_speed_mps, avg_stop_spacing_m, trip_count, computed_at) VALUES (0, 'R1', 'VAR1', 8.0, 1111.0, 1, NOW())",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
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
            html.contains("badge--neutral"),
            "speed page HTML should contain 'badge--neutral' CSS class for route classification"
        );
    }

    #[tokio::test]
    async fn speed_page_filters_by_class_local() {
        let td = test_utils::setup().await;

        sqlx::query("INSERT INTO feeds (id, gtfs_static_url) VALUES (0, 'http://test')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO agencies (onestop_id, name) VALUES ('a', 'Agency')")
            .execute(&td.db.pool)
            .await
            .unwrap();

        // Local route: avg_stop_spacing_m = 333 m → Local
        sqlx::query(
            "INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('RL', 'a', 'LocalX', 'Local Route', 3)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_speed (feed_id, route_id, variant_id, scheduled_speed_mps, avg_stop_spacing_m, trip_count, computed_at) VALUES (0, 'RL', 'VARL', 5.0, 333.0, 1, NOW())",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        // Rapid route: avg_stop_spacing_m = 1111 m → Rapid
        sqlx::query(
            "INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('RR', 'a', 'RapidX', 'Rapid Route', 3)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_speed (feed_id, route_id, variant_id, scheduled_speed_mps, avg_stop_spacing_m, trip_count, computed_at) VALUES (0, 'RR', 'VARR', 8.0, 1111.0, 1, NOW())",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
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
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
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
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
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
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
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
        sqlx::query("INSERT INTO feeds (id, gtfs_static_url) VALUES (0, 'http://test')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO agencies (onestop_id, name) VALUES ('a', 'Agency')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('R1', 'a', '1', 'Route 1', 3)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S1', 'Main St', 45.50, -73.50)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S2', 'Downtown', 45.51, -73.50)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_variants (feed_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary) VALUES (0, 'VAR1', 'R1', 0, 2, 1, true)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_variant_stops (feed_id, variant_id, stop_sequence, stop_id) VALUES (0, 'VAR1', 1, 'S1')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_variant_stops (feed_id, variant_id, stop_sequence, stop_id) VALUES (0, 'VAR1', 2, 'S2')",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
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
            html.contains("badge--neutral"),
            "detail page HTML should contain 'badge--neutral' CSS class for route classification"
        );
    }

    #[tokio::test]
    async fn schedule_page_returns_full_html() {
        let td = test_utils::setup().await;
        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
        };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/frequency")
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
        assert!(
            html.contains("Route Schedule"),
            "full page response must contain the renamed page title text"
        );
        assert!(
            html.contains(r#"id="freq-content""#),
            "full page response must contain the freq-content swap target"
        );
    }

    #[tokio::test]
    async fn schedule_page_with_hx_request_returns_fragment() {
        let td = test_utils::setup().await;
        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
        };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/frequency")
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
            html.contains(r#"id="freq-content""#),
            "fragment must contain the freq-content swap target div"
        );
        assert!(
            !html.contains("<!DOCTYPE html"),
            "fragment must not contain a full HTML document"
        );
        assert!(
            !html.contains("<html"),
            "fragment must not contain an <html> element"
        );
    }

    #[tokio::test]
    async fn schedule_page_renders_route_schedule_cards() {
        let td = test_utils::setup().await;
        sqlx::query("INSERT INTO feeds (id, gtfs_static_url) VALUES (0, 'http://test')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO agencies (onestop_id, name) VALUES ('a', 'Agency')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('R1', 'a', '10', 'Route 10', 3)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO services (feed_id, service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday) VALUES (0, 'WD', true, true, true, true, true, false, false)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_variants (feed_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary) VALUES (0, 'VAR1', 'R1', 0, 2, 3, true)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S1', 'Stop 1', 45.50, -73.50)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S2', 'Stop 2', 45.51, -73.50)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        for (trip_id, dep_time, arr_time) in [
            ("T1", "06:00:00", "06:30:00"),
            ("T2", "06:10:00", "06:40:00"),
            ("T3", "06:30:00", "07:00:00"),
        ] {
            sqlx::query(
                "INSERT INTO trips (feed_id, trip_id, variant_id, service_id) VALUES (0, $1, 'VAR1', 'WD')",
            )
            .bind(trip_id)
            .execute(&td.db.pool)
            .await
            .unwrap();
            sqlx::query(
                "INSERT INTO scheduled_stops (feed_id, trip_id, stop_sequence, stop_id, arrival_time, departure_time) VALUES (0, $1, 1, 'S1', $2, $2)",
            )
            .bind(trip_id)
            .bind(dep_time)
            .execute(&td.db.pool)
            .await
            .unwrap();
            sqlx::query(
                "INSERT INTO scheduled_stops (feed_id, trip_id, stop_sequence, stop_id, arrival_time, departure_time) VALUES (0, $1, 2, 'S2', $2, $2)",
            )
            .bind(trip_id)
            .bind(arr_time)
            .execute(&td.db.pool)
            .await
            .unwrap();
        }

        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
        };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/frequency")
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
        assert!(html.contains("schedule-card"));
        assert!(html.contains("Top 10%"));
        assert!(html.contains("Max"));
        assert!(html.contains("Span"));
        assert!(html.contains("06:00-07:00"));
        assert!(html.contains("11.0 min"));
        assert!(html.contains("20.0 min"));
    }

    #[tokio::test]
    async fn schedule_page_uses_top_decile_headway_instead_of_minimum() {
        let td = test_utils::setup().await;
        sqlx::query("INSERT INTO feeds (id, gtfs_static_url) VALUES (0, 'http://test')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO agencies (onestop_id, name) VALUES ('a', 'Agency')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('R1', 'a', '10', 'Route 10', 3)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO services (feed_id, service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday) VALUES (0, 'WD', true, true, true, true, true, false, false)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_variants (feed_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary) VALUES (0, 'VAR1', 'R1', 0, 2, 21, true)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S1', 'Stop 1', 45.50, -73.50)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S2', 'Stop 2', 45.51, -73.50)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        let mut departure_secs = 6 * 3600;
        for i in 0..21 {
            let trip_id = format!("T{i}");
            let dep_time = format!(
                "{:02}:{:02}:00",
                departure_secs / 3600,
                (departure_secs % 3600) / 60
            );
            let arr_secs = departure_secs + 30 * 60;
            let arr_time = format!("{:02}:{:02}:00", arr_secs / 3600, (arr_secs % 3600) / 60);
            sqlx::query(
                "INSERT INTO trips (feed_id, trip_id, variant_id, service_id) VALUES (0, $1, 'VAR1', 'WD')",
            )
            .bind(&trip_id)
            .execute(&td.db.pool)
            .await
            .unwrap();
            sqlx::query(
                "INSERT INTO scheduled_stops (feed_id, trip_id, stop_sequence, stop_id, arrival_time, departure_time) VALUES (0, $1, 1, 'S1', $2, $2)",
            )
            .bind(&trip_id)
            .bind(&dep_time)
            .execute(&td.db.pool)
            .await
            .unwrap();
            sqlx::query(
                "INSERT INTO scheduled_stops (feed_id, trip_id, stop_sequence, stop_id, arrival_time, departure_time) VALUES (0, $1, 2, 'S2', $2, $2)",
            )
            .bind(&trip_id)
            .bind(&arr_time)
            .execute(&td.db.pool)
            .await
            .unwrap();

            departure_secs += if i == 0 { 60 } else { 10 * 60 };
        }

        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
        };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/schedule")
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
        assert!(html.contains("Top 10%"));
        assert!(
            html.contains("10.0 min"),
            "top decile should ignore the single 1-minute minimum gap"
        );
        assert!(
            !html.contains("1.0 min"),
            "raw minimum gap should not be displayed as the best headway"
        );
    }

    #[tokio::test]
    async fn schedule_page_computes_headways_within_each_service_id() {
        let td = test_utils::setup().await;
        sqlx::query("INSERT INTO feeds (id, gtfs_static_url) VALUES (0, 'http://test')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO agencies (onestop_id, name) VALUES ('a', 'Agency')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('R1', 'a', '10', 'Route 10', 3)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        for service_id in ["WD1", "WD2"] {
            sqlx::query(
                "INSERT INTO services (feed_id, service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday) VALUES (0, $1, true, true, true, true, true, false, false)",
            )
            .bind(service_id)
            .execute(&td.db.pool)
            .await
            .unwrap();
        }
        sqlx::query(
            "INSERT INTO route_variants (feed_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary) VALUES (0, 'VAR1', 'R1', 0, 2, 6, true)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S1', 'Stop 1', 45.50, -73.50)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S2', 'Stop 2', 45.51, -73.50)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        for (trip_id, service_id, dep_time, arr_time) in [
            ("T1", "WD1", "06:00:00", "06:30:00"),
            ("T2", "WD1", "06:10:00", "06:40:00"),
            ("T3", "WD1", "06:20:00", "06:50:00"),
            ("T4", "WD2", "06:01:00", "06:31:00"),
            ("T5", "WD2", "06:11:00", "06:41:00"),
            ("T6", "WD2", "06:21:00", "06:51:00"),
        ] {
            sqlx::query(
                "INSERT INTO trips (feed_id, trip_id, variant_id, service_id) VALUES (0, $1, 'VAR1', $2)",
            )
            .bind(trip_id)
            .bind(service_id)
            .execute(&td.db.pool)
            .await
            .unwrap();
            sqlx::query(
                "INSERT INTO scheduled_stops (feed_id, trip_id, stop_sequence, stop_id, arrival_time, departure_time) VALUES (0, $1, 1, 'S1', $2, $2)",
            )
            .bind(trip_id)
            .bind(dep_time)
            .execute(&td.db.pool)
            .await
            .unwrap();
            sqlx::query(
                "INSERT INTO scheduled_stops (feed_id, trip_id, stop_sequence, stop_id, arrival_time, departure_time) VALUES (0, $1, 2, 'S2', $2, $2)",
            )
            .bind(trip_id)
            .bind(arr_time)
            .execute(&td.db.pool)
            .await
            .unwrap();
        }

        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
        };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/schedule")
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
        assert!(html.contains("Top 10%"));
        assert!(
            html.contains("10.0 min"),
            "overlapping service calendars should not create 1-minute headways"
        );
        assert!(!html.contains("1.0 min"));
    }

    #[tokio::test]
    async fn schedule_page_combines_directions_into_one_route_card() {
        let td = test_utils::setup().await;
        sqlx::query("INSERT INTO feeds (id, gtfs_static_url) VALUES (0, 'http://test')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO agencies (onestop_id, name) VALUES ('a', 'Agency')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('R1', 'a', '10', 'Route 10', 3)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO services (feed_id, service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday) VALUES (0, 'WD', true, true, true, true, true, false, false)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        // Two variants with different direction_ids for the same route
        sqlx::query(
            "INSERT INTO route_variants (feed_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary) VALUES (0, 'VARA', 'R1', 0, 2, 2, true)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_variants (feed_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary) VALUES (0, 'VARB', 'R1', 1, 2, 2, false)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S1', 'Stop 1', 45.50, -73.50)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S2', 'Stop 2', 45.51, -73.50)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        for (variant_id, trip_id, dep_time, arr_time) in [
            ("VARA", "T1", "06:00:00", "06:30:00"),
            ("VARA", "T2", "06:10:00", "06:40:00"),
            ("VARB", "T3", "06:05:00", "06:35:00"),
            ("VARB", "T4", "06:15:00", "06:45:00"),
        ] {
            sqlx::query(
                "INSERT INTO trips (feed_id, trip_id, variant_id, service_id) VALUES (0, $1, $2, 'WD')",
            )
            .bind(trip_id)
            .bind(variant_id)
            .execute(&td.db.pool)
            .await
            .unwrap();
            sqlx::query(
                "INSERT INTO scheduled_stops (feed_id, trip_id, stop_sequence, stop_id, arrival_time, departure_time) VALUES (0, $1, 1, 'S1', $2, $2)",
            )
            .bind(trip_id)
            .bind(dep_time)
            .execute(&td.db.pool)
            .await
            .unwrap();
            sqlx::query(
                "INSERT INTO scheduled_stops (feed_id, trip_id, stop_sequence, stop_id, arrival_time, departure_time) VALUES (0, $1, 2, 'S2', $2, $2)",
            )
            .bind(trip_id)
            .bind(arr_time)
            .execute(&td.db.pool)
            .await
            .unwrap();
        }

        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
        };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/schedule")
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
        assert_eq!(html.matches("card schedule-card").count(), 1);
        assert!(!html.contains("Outbound"));
        assert!(!html.contains("Inbound"));
    }

    #[tokio::test]
    async fn schedule_page_renders_saturday_column_when_saturday_service_exists() {
        let td = test_utils::setup().await;
        sqlx::query("INSERT INTO feeds (id, gtfs_static_url) VALUES (0, 'http://test')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO agencies (onestop_id, name) VALUES ('a', 'Agency')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO routes (onestop_id, agency_id, short_name, long_name, route_type) VALUES ('R1', 'a', '10', 'Route 10', 3)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO services (feed_id, service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday) VALUES (0, 'WD', true, true, true, true, true, false, false)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO services (feed_id, service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday) VALUES (0, 'SAT', false, false, false, false, false, true, false)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO route_variants (feed_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary) VALUES (0, 'VAR1', 'R1', 0, 2, 4, true)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S1', 'Stop 1', 45.50, -73.50)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO stops (onestop_id, name, lat, lon) VALUES ('S2', 'Stop 2', 45.51, -73.50)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();

        for (trip_id, service_id, dep_time, arr_time) in [
            ("T1", "WD", "06:00:00", "06:30:00"),
            ("T2", "WD", "06:10:00", "06:40:00"),
            ("T3", "SAT", "09:00:00", "09:30:00"),
            ("T4", "SAT", "09:20:00", "09:50:00"),
        ] {
            sqlx::query(
                "INSERT INTO trips (feed_id, trip_id, variant_id, service_id) VALUES (0, $1, 'VAR1', $2)",
            )
            .bind(trip_id)
            .bind(service_id)
            .execute(&td.db.pool)
            .await
            .unwrap();
            sqlx::query(
                "INSERT INTO scheduled_stops (feed_id, trip_id, stop_sequence, stop_id, arrival_time, departure_time) VALUES (0, $1, 1, 'S1', $2, $2)",
            )
            .bind(trip_id)
            .bind(dep_time)
            .execute(&td.db.pool)
            .await
            .unwrap();
            sqlx::query(
                "INSERT INTO scheduled_stops (feed_id, trip_id, stop_sequence, stop_id, arrival_time, departure_time) VALUES (0, $1, 2, 'S2', $2, $2)",
            )
            .bind(trip_id)
            .bind(arr_time)
            .execute(&td.db.pool)
            .await
            .unwrap();
        }

        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
        };
        let app = build_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/schedule")
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
        assert!(html.contains("Weekday"), "weekday column should render");
        assert!(html.contains("Saturday"), "saturday column should render");
        assert!(!html.contains("Sunday"), "sunday column should not render");
        assert!(
            html.contains("09:00-09:50"),
            "saturday service span should be 09:00-09:50"
        );
    }

    #[tokio::test]
    async fn health_check_returns_200_with_db_up() {
        let td = test_utils::setup().await;
        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(Some("Test Region".to_string()))),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
        };
        let app = build_router(state.clone()).merge(
            axum::Router::new()
                .route("/health", axum::routing::get(health_check))
                .with_state(state),
        );

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/health")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
        let body = axum::body::to_bytes(response.into_body(), 1024)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(json["status"], "ok");
    }

    /// Build a router that includes setup routes (mirrors what `serve()` does, minus the
    /// health router, for testing setup handlers without starting the TCP listener).
    fn build_setup_router(state: AppState) -> axum::Router {
        use axum::routing::get;
        let setup_router = axum::Router::new()
            .route("/setup", get(setup_page).post(setup_submit))
            .route("/setup/status", get(setup_status))
            .with_state(state.clone());
        build_router(state).merge(setup_router)
    }

    #[tokio::test]
    async fn setup_page_returns_200_with_welcome_text() {
        let td = test_utils::setup().await;
        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(None)),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
        };
        let app = build_setup_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/setup")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        assert!(
            std::str::from_utf8(&body).unwrap().contains("Welcome"),
            "setup page should contain 'Welcome'"
        );
    }

    #[tokio::test]
    async fn setup_status_returns_spinner_when_idle() {
        let td = test_utils::setup().await;
        let state = AppState {
            db: td.db,
            config: test_config(),
            region: Arc::new(RwLock::new(None)),
            setup_state: Arc::new(tokio::sync::Mutex::new(SetupState::Idle)),
        };
        let app = build_setup_router(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/setup/status")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        assert!(
            std::str::from_utf8(&body)
                .unwrap()
                .contains("/setup/status"),
            "idle status response should contain polling fragment referencing /setup/status"
        );
    }
}
