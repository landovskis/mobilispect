# Health Check Endpoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `GET /health` HTTP endpoint to both the server and worker that returns 200 with `{"status":"ok"}` when the database is reachable, and 503 with an error message otherwise.

**Architecture:** A shared `db_ping` function lives in `mobilispect_core::health` — the only shared piece. The server adds a `/health` route to its existing Axum router. The worker gains a minimal new Axum server (a single background task) wired to a configurable bind address from `config.toml`.

**Tech Stack:** Rust, Axum 0.7, sqlx PgPool, serde_json, reqwest (worker test), testcontainers

**Spec:** `docs/superpowers/specs/2026-05-24-health-check-design.md`

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `crates/core/src/health.rs` | `db_ping(pool)` — DB probe logic |
| Modify | `crates/core/src/lib.rs` | Export `pub mod health` |
| Modify | `crates/core/src/config.rs` | Add `worker_health_bind_address` field |
| Modify | `config.toml` | Document new optional field |
| Modify | `crates/server/src/web/handlers.rs` | `health_check` handler + test, update `test_config()` |
| Modify | `crates/server/src/web/mod.rs` | Register `GET /health` route |
| Modify | `crates/worker/Cargo.toml` | Add `axum`, `serde`, `serde_json` deps |
| Create | `crates/worker/src/health.rs` | Minimal Axum router + `serve` function |
| Modify | `crates/worker/src/main.rs` | Declare `mod health`, spawn health server task |

---

## Task 1: `db_ping` in `mobilispect_core`

**Files:**
- Create: `crates/core/src/health.rs`
- Modify: `crates/core/src/lib.rs`

- [ ] **Step 1: Write the failing test**

Add a new file `crates/core/src/health.rs` with this content:

```rust
use anyhow::Result;
use sqlx::PgPool;

pub async fn db_ping(pool: &PgPool) -> Result<()> {
    todo!()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::test_utils;

    #[tokio::test]
    async fn db_ping_succeeds_with_live_db() {
        let td = test_utils::setup().await;
        assert!(db_ping(&td.db.pool).await.is_ok());
    }
}
```

Then add one line to `crates/core/src/lib.rs` (after the existing `pub mod db;` line):

```rust
pub mod health;
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
cargo nextest run db_ping_succeeds_with_live_db
```

Expected: FAIL — `not yet implemented` panic from `todo!()`

- [ ] **Step 3: Write minimal implementation**

Replace the `todo!()` body in `crates/core/src/health.rs`:

```rust
pub async fn db_ping(pool: &PgPool) -> Result<()> {
    pool.acquire().await?;
    Ok(())
}
```

- [ ] **Step 4: Run the test to confirm it passes**

```bash
cargo nextest run db_ping_succeeds_with_live_db
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add crates/core/src/health.rs crates/core/src/lib.rs
git commit -m "feat(core): add health::db_ping"
```

---

## Task 2: `worker_health_bind_address` in Config

**Files:**
- Modify: `crates/core/src/config.rs`
- Modify: `config.toml`
- Modify: `crates/server/src/web/handlers.rs` (update `test_config()`)

- [ ] **Step 1: Write the failing test**

In `crates/core/src/config.rs`, find the test `applies_defaults_for_optional_toml_fields` and add one assertion at the end:

```rust
assert_eq!(config.worker_health_bind_address, "0.0.0.0:9090");
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
cargo nextest run applies_defaults_for_optional_toml_fields
```

Expected: FAIL — `no field 'worker_health_bind_address' on type 'Config'`

- [ ] **Step 3: Add the field to `Config`, `TomlConfig`, and the builder**

In `crates/core/src/config.rs`:

**3a.** Add to the `Config` struct (after `retention_days`):

```rust
pub worker_health_bind_address: String,
```

**3b.** Add to the `TomlConfig` struct (after `retention_days`):

```rust
worker_health_bind_address: Option<String>,
```

**3c.** Add to the `Ok(Self { ... })` block in `from_toml_str_with_env` (after `retention_days`):

```rust
worker_health_bind_address: file
    .worker_health_bind_address
    .unwrap_or_else(|| "0.0.0.0:9090".to_string()),
```

- [ ] **Step 4: Fix the broken `test_config()` in the server**

In `crates/server/src/web/handlers.rs`, find `fn test_config() -> Config` and add the new field to the struct literal (after `retention_days: 30`):

```rust
worker_health_bind_address: "0.0.0.0:9090".to_string(),
```

- [ ] **Step 5: Document the field in `config.toml`**

Add a comment line after `bind_address = "0.0.0.0:3000"`:

```toml
# worker_health_bind_address = "0.0.0.0:9090"
```

- [ ] **Step 6: Run the test to confirm it passes**

```bash
cargo nextest run applies_defaults_for_optional_toml_fields
```

Expected: PASS

- [ ] **Step 7: Run all config + server tests to confirm no regressions**

```bash
cargo nextest run --package mobilispect-core --package mobilispect-server
```

Expected: all PASS

- [ ] **Step 8: Commit**

```bash
git add crates/core/src/config.rs config.toml crates/server/src/web/handlers.rs
git commit -m "feat(config): add worker_health_bind_address"
```

---

## Task 3: Server `GET /health` handler

**Files:**
- Modify: `crates/server/src/web/handlers.rs`
- Modify: `crates/server/src/web/mod.rs`

- [ ] **Step 1: Write the failing test**

In `crates/server/src/web/handlers.rs`, add the following test inside the existing `#[cfg(test)]` module (at the end, before the closing `}`):

```rust
#[tokio::test]
async fn health_check_returns_200_with_db_up() {
    let td = test_utils::setup().await;
    let state = AppState {
        db: td.db,
        config: test_config(),
    };
    let app = build_router(state);

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
    let body = axum::body::to_bytes(response.into_body(), 1024).await.unwrap();
    let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
    assert_eq!(json["status"], "ok");
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
cargo nextest run health_check_returns_200_with_db_up
```

Expected: FAIL — no `/health` route, gets 404

- [ ] **Step 3: Add the `health_check` handler**

Append the following to `crates/server/src/web/handlers.rs` (before the `#[cfg(test)]` block):

```rust
pub async fn health_check(State(state): State<AppState>) -> axum::response::Response {
    use axum::http::StatusCode;
    use axum::response::IntoResponse;
    use axum::Json;

    match mobilispect_core::health::db_ping(&state.db.pool).await {
        Ok(()) => (StatusCode::OK, Json(serde_json::json!({"status": "ok"}))).into_response(),
        Err(e) => (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(serde_json::json!({"status": "error", "message": format!("db ping failed: {e}")})),
        )
            .into_response(),
    }
}
```

- [ ] **Step 4: Register the route**

In `crates/server/src/web/mod.rs`, add a new route to `build_router` (before `.layer(TraceLayer::new_for_http())`):

```rust
.route("/health", get(handlers::health_check))
```

- [ ] **Step 5: Run the test to confirm it passes**

```bash
cargo nextest run health_check_returns_200_with_db_up
```

Expected: PASS

- [ ] **Step 6: Run the full server test suite**

```bash
cargo nextest run --package mobilispect-server
```

Expected: all PASS

- [ ] **Step 7: Commit**

```bash
git add crates/server/src/web/handlers.rs crates/server/src/web/mod.rs
git commit -m "feat(server): add GET /health endpoint"
```

---

## Task 4: Worker health server

**Files:**
- Modify: `crates/worker/Cargo.toml`
- Create: `crates/worker/src/health.rs`
- Modify: `crates/worker/src/main.rs`

- [ ] **Step 1: Add Axum dependencies to the worker**

In `crates/worker/Cargo.toml`, add after the existing `anyhow = "1"` line:

```toml
axum = "0.7"
serde = { version = "1", features = ["derive"] }
serde_json = "1"
```

Also add `tower` as a dev-dependency (needed for the test's `.oneshot` call is NOT used here — skip it):

```toml
# no change to [dev-dependencies] needed
```

- [ ] **Step 2: Write the failing test**

Create `crates/worker/src/health.rs` with this content:

```rust
use anyhow::Result;
use axum::{
    Router,
    extract::State,
    http::StatusCode,
    response::IntoResponse,
    routing::get,
    Json,
};
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
    todo!()
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

        let response = reqwest::get(format!("http://{addr}/health"))
            .await
            .unwrap();

        assert_eq!(response.status().as_u16(), 200u16);
        let json: serde_json::Value = response.json().await.unwrap();
        assert_eq!(json["status"], "ok");
    }
}
```

Declare the module in `crates/worker/src/main.rs` by adding after the existing `mod pipeline;` line:

```rust
mod health;
```

- [ ] **Step 3: Run the test to confirm it fails**

```bash
cargo nextest run --package mobilispect-worker health_check_returns_200_with_db_up
```

Expected: FAIL — `not yet implemented` panic from `todo!()`

- [ ] **Step 4: Implement the handler**

Replace the `todo!()` body in `health_check`:

```rust
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
```

- [ ] **Step 5: Run the test to confirm it passes**

```bash
cargo nextest run --package mobilispect-worker health_check_returns_200_with_db_up
```

Expected: PASS

- [ ] **Step 6: Spawn the health server in worker `main.rs`**

In `crates/worker/src/main.rs`, add the following block before the `std::future::pending::<()>().await;` line:

```rust
let db_health = db.clone();
let health_addr = config.worker_health_bind_address.clone();
tokio::spawn(async move {
    if let Err(e) = health::serve(db_health, &health_addr).await {
        warn!("Health server failed: {e:#}");
    }
});
```

- [ ] **Step 7: Run the full worker test suite**

```bash
cargo nextest run --package mobilispect-worker
```

Expected: all PASS

- [ ] **Step 8: Run the full workspace test suite**

```bash
cargo nextest run
```

Expected: all PASS

- [ ] **Step 9: Commit**

```bash
git add crates/worker/Cargo.toml crates/worker/src/health.rs crates/worker/src/main.rs
git commit -m "feat(worker): add GET /health endpoint"
```
