use axum::{
    body::Body,
    extract::State,
    http::Request,
    middleware::Next,
    response::{IntoResponse, Redirect, Response},
};

use crate::web::AppState;

pub async fn require_region_configured(
    State(state): State<AppState>,
    request: Request<Body>,
    next: Next,
) -> Response {
    let configured = state.region.read().await.is_some();
    if !configured {
        return Redirect::to("/setup").into_response();
    }
    next.run(request).await
}
