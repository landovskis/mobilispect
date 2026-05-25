use askama::Template;
use axum::{
    extract::{Query, State},
    http::HeaderMap,
    response::Html,
};
use serde::Deserialize;

use crate::web::AppState;
use mobilispect_core::ids::{AgencyId, RouteId};
use mobilispect_core::on_time_performance::{RouteSummary, RouteTrend, route_summary, route_trend};
use mobilispect_core::service_frequency::{RouteHeadwayRow, route_headways};
use mobilispect_core::speed_analysis::{
    RouteClass, RouteSpeedCard, RouteSpeedDetailDirection, RouteSpeedSummary, assign_indices,
    build_detail_directions, build_speed_cards, classify_by_spacing, fetch_route_info,
    filter_speed_cards, route_speed_by_day_type, route_speed_summary, route_speed_trend_by_variant,
    route_stop_spacings, sort_speed_cards,
};

#[derive(Template)]
#[template(path = "route_speed_detail.html")]
struct RouteSpeedDetailTemplate {
    region_name: String,
    short_name: String,
    long_name: String,
    agency_id: AgencyId,
    directions: Vec<RouteSpeedDetailDirection>,
    classification: Option<RouteClass>,
}

pub async fn route_speed_detail(
    State(state): State<AppState>,
    axum::extract::Path((agency_id, route_id)): axum::extract::Path<(String, String)>,
) -> axum::response::Response {
    use axum::response::IntoResponse;

    let agency_id = AgencyId::from(agency_id);
    let route_id = RouteId::from(route_id);

    let (short_name, long_name) = match fetch_route_info(&state.db, &agency_id, &route_id).await {
        Ok(Some(r)) => r,
        Ok(None) => {
            return (
                axum::http::StatusCode::NOT_FOUND,
                Html("<h1>Not Found</h1>".to_string()),
            )
                .into_response();
        }
        Err(e) => {
            tracing::error!("DB error fetching route {agency_id}/{route_id}: {e}");
            return (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                Html("<h1>Internal Server Error</h1>".to_string()),
            )
                .into_response();
        }
    };
    let (spacings_res, trends_res) = tokio::join!(
        route_stop_spacings(&state.db, &agency_id, &route_id),
        route_speed_trend_by_variant(&state.db, &agency_id, &route_id, 28),
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
            tracing::error!("route_stop_spacings failed for {agency_id}/{route_id}: {e}");
            return (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                Html("<h1>Internal Server Error</h1>".to_string()),
            )
                .into_response();
        }
    };
    let trends = trends_res.unwrap_or_else(|e| {
        tracing::error!("route_speed_trend_by_variant failed for {agency_id}/{route_id}: {e}");
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
        region_name: state.config.region.name.clone(),
        short_name,
        long_name,
        agency_id: agency_id.clone(),
        directions,
        classification,
    };
    match tmpl.render() {
        Ok(html) => Html(html).into_response(),
        Err(e) => {
            tracing::error!(
                "Template render error for route_speed_detail {agency_id}/{route_id}: {e}"
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
) -> axum::response::Response {
    use axum::response::IntoResponse;

    let period_days: i64 = 30;
    let agency_id = AgencyId::from(agency_id);
    let route_id = RouteId::from(route_id);
    match route_trend(&state.db, &agency_id, &route_id, period_days).await {
        Ok(Some(trend)) => {
            let trend_json = match serde_json::to_string(&trend.days) {
                Ok(json) => json,
                Err(e) => {
                    tracing::error!(
                        "Failed to serialize trend data for {agency_id}/{route_id}: {e}"
                    );
                    return (
                        axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                        Html("<h1>Internal Server Error</h1>".to_string()),
                    )
                        .into_response();
                }
            };
            let tmpl = RouteDetailTemplate {
                region_name: state.config.region.name.clone(),
                trend,
                trend_json,
                period_days,
            };
            match tmpl.render() {
                Ok(html) => Html(html).into_response(),
                Err(e) => {
                    tracing::error!(
                        "Template render error for route_detail {agency_id}/{route_id}: {e}"
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
            tracing::error!("DB error fetching route trend for {agency_id}/{route_id}: {e}");
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
    let filter_agency_id = if active_agency.is_empty() {
        None
    } else {
        Some(AgencyId::from(active_agency.as_str()))
    };
    let filter = filter_agency_id.as_ref();
    let agency_names: std::collections::HashMap<String, String> =
        agencies.iter().cloned().collect();
    let rows = match route_speed_by_day_type(&state.db, filter).await {
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
            region_name: state.config.region.name.clone(),
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
#[template(path = "frequency.html")]
struct FrequencyTemplate {
    region_name: String,
    rows: Vec<RouteHeadwayRow>,
    agencies: Vec<(String, String)>,
    active_agency: String,
}

#[derive(Template)]
#[template(path = "frequency_content.html")]
struct FrequencyContentTemplate {
    rows: Vec<RouteHeadwayRow>,
    agencies: Vec<(String, String)>,
    active_agency: String,
}

pub async fn frequency_page(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(params): Query<AgencyFilterParams>,
) -> axum::response::Response {
    use axum::response::IntoResponse;

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
    let agency_filter: Option<AgencyId> = if active_agency.is_empty() {
        None
    } else {
        Some(AgencyId::from(active_agency.clone()))
    };
    let rows = match route_headways(&state.db, agency_filter.as_ref()).await {
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

    if headers.contains_key("hx-request") {
        let tmpl = FrequencyContentTemplate {
            rows,
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
            region_name: state.config.region.name.clone(),
            rows,
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

#[cfg(test)]
mod e2e_tests {
    use super::*;
    use crate::web::build_router;
    use axum::body::Body;
    use axum::http::{Request, StatusCode};
    use mobilispect_core::config::{AgencyConfig, Config, RegionConfig};
    use mobilispect_core::db::test_utils;
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
        sqlx::query(
            "INSERT INTO route_variants (agency_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary)
             VALUES ('0', 'VAR1', 'R1', 0, 2, 1, true)",
        )
        .execute(&td.db.pool).await.unwrap();
        sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 1, 'S1')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 2, 'S2')")
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
            html.contains("badge--neutral"),
            "speed page HTML should contain 'badge--neutral' CSS class for route classification"
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
        sqlx::query(
            "INSERT INTO route_variants (agency_id, variant_id, route_id, direction_id, stop_count, trip_count, is_primary)
             VALUES ('0', 'VAR1', 'R1', 0, 2, 1, true)",
        )
        .execute(&td.db.pool).await.unwrap();
        sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 1, 'S1')")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO route_variant_stops VALUES ('0', 'VAR1', 2, 'S2')")
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
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '10', 'Route 10', 3)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO calendar VALUES ('0', 'WD', true, true, true, true, true, false, false)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        for (trip_id, dep_time, arr_time) in [
            ("T1", "06:00:00", "06:30:00"),
            ("T2", "06:10:00", "06:40:00"),
            ("T3", "06:30:00", "07:00:00"),
        ] {
            sqlx::query("INSERT INTO trips VALUES ('0', $1, 'R1', 'WD', 0, 'Downtown')")
                .bind(trip_id)
                .execute(&td.db.pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('0', $1, 'S1', 1, $2, $2)")
                .bind(trip_id)
                .bind(dep_time)
                .execute(&td.db.pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('0', $1, 'S2', 2, $2, $2)")
                .bind(trip_id)
                .bind(arr_time)
                .execute(&td.db.pool)
                .await
                .unwrap();
        }

        let state = AppState {
            db: td.db,
            config: test_config(),
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
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '10', 'Route 10', 3)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO calendar VALUES ('0', 'WD', true, true, true, true, true, false, false)",
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
            sqlx::query("INSERT INTO trips VALUES ('0', $1, 'R1', 'WD', 0, 'Downtown')")
                .bind(&trip_id)
                .execute(&td.db.pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('0', $1, 'S1', 1, $2, $2)")
                .bind(&trip_id)
                .bind(&dep_time)
                .execute(&td.db.pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('0', $1, 'S2', 2, $2, $2)")
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
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '10', 'Route 10', 3)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        for service_id in ["WD1", "WD2"] {
            sqlx::query(
                "INSERT INTO calendar VALUES ('0', $1, true, true, true, true, true, false, false)",
            )
            .bind(service_id)
            .execute(&td.db.pool)
            .await
            .unwrap();
        }

        for (trip_id, service_id, dep_time, arr_time) in [
            ("T1", "WD1", "06:00:00", "06:30:00"),
            ("T2", "WD1", "06:10:00", "06:40:00"),
            ("T3", "WD1", "06:20:00", "06:50:00"),
            ("T4", "WD2", "06:01:00", "06:31:00"),
            ("T5", "WD2", "06:11:00", "06:41:00"),
            ("T6", "WD2", "06:21:00", "06:51:00"),
        ] {
            sqlx::query("INSERT INTO trips VALUES ('0', $1, 'R1', $2, 0, 'Downtown')")
                .bind(trip_id)
                .bind(service_id)
                .execute(&td.db.pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('0', $1, 'S1', 1, $2, $2)")
                .bind(trip_id)
                .bind(dep_time)
                .execute(&td.db.pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('0', $1, 'S2', 2, $2, $2)")
                .bind(trip_id)
                .bind(arr_time)
                .execute(&td.db.pool)
                .await
                .unwrap();
        }

        let state = AppState {
            db: td.db,
            config: test_config(),
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
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '10', 'Route 10', 3)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO calendar VALUES ('0', 'WD', true, true, true, true, true, false, false)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        for (direction_id, trip_id, dep_time, arr_time) in [
            (0, "T1", "06:00:00", "06:30:00"),
            (0, "T2", "06:10:00", "06:40:00"),
            (1, "T3", "06:05:00", "06:35:00"),
            (1, "T4", "06:15:00", "06:45:00"),
        ] {
            sqlx::query("INSERT INTO trips VALUES ('0', $1, 'R1', 'WD', $2, 'Downtown')")
                .bind(trip_id)
                .bind(direction_id)
                .execute(&td.db.pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('0', $1, 'S1', 1, $2, $2)")
                .bind(trip_id)
                .bind(dep_time)
                .execute(&td.db.pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('0', $1, 'S2', 2, $2, $2)")
                .bind(trip_id)
                .bind(arr_time)
                .execute(&td.db.pool)
                .await
                .unwrap();
        }

        let state = AppState {
            db: td.db,
            config: test_config(),
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
        sqlx::query("INSERT INTO routes VALUES ('0', 'R1', '10', 'Route 10', 3)")
            .execute(&td.db.pool)
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO calendar VALUES ('0', 'WD', true, true, true, true, true, false, false)",
        )
        .execute(&td.db.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO calendar VALUES ('0', 'SAT', false, false, false, false, false, true, false)",
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
            sqlx::query("INSERT INTO trips VALUES ('0', $1, 'R1', $2, 0, 'Downtown')")
                .bind(trip_id)
                .bind(service_id)
                .execute(&td.db.pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('0', $1, 'S1', 1, $2, $2)")
                .bind(trip_id)
                .bind(dep_time)
                .execute(&td.db.pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO scheduled_stops VALUES ('0', $1, 'S2', 2, $2, $2)")
                .bind(trip_id)
                .bind(arr_time)
                .execute(&td.db.pool)
                .await
                .unwrap();
        }

        let state = AppState {
            db: td.db,
            config: test_config(),
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
}
