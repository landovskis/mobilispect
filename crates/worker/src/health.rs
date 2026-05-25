use anyhow::Result;
use axum::{Json, Router, extract::State, http::StatusCode, response::IntoResponse, routing::get};
use mobilispect_core::db::Database;

pub fn router(db: Database) -> Router {
    Router::new()
        .route("/health", get(health_check))
        .with_state(db)
}

pub async fn serve(db: Database, bind_address: &str) -> Result<()> {
    let listener = tokio::net::TcpListener::bind(bind_address).await?;
    axum::serve(listener, router(db)).await?;
    Ok(())
}

async fn health_check(State(db): State<Database>) -> impl IntoResponse {
    match mobilispect_core::health::db_ping(&db.pool).await {
        Ok(()) => (StatusCode::OK, Json(serde_json::json!({"status": "ok"}))).into_response(),
        Err(e) => (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(serde_json::json!({"status": "error", "message": format!("db ping failed: {e}")})),
        )
            .into_response(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use mobilispect_core::db::test_utils;

    #[tokio::test]
    async fn health_check_returns_200_with_db_up() {
        let td = test_utils::setup().await;
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();

        let db = td.db.clone();
        tokio::spawn(async move {
            axum::serve(listener, router(db)).await.unwrap();
        });

        let response = reqwest::get(format!("http://{addr}/health")).await.unwrap();

        assert_eq!(response.status().as_u16(), 200u16);
        let json: serde_json::Value = response.json().await.unwrap();
        assert_eq!(json["status"], "ok");
    }
}
