# Health Check Endpoints — Design

**Date:** 2026-05-24
**Status:** Approved

## Goal

Expose a `GET /health` HTTP endpoint on both the server and the worker so a load balancer can probe liveness and DB connectivity. Returns `200 OK` when the database is reachable, `503 Service Unavailable` otherwise.

## Domain Context

- **Bounded context(s):** Infrastructure / Operations (cross-cutting)
- **Aggregates touched:** None — read-only DB probe
- **New ubiquitous language terms:** None

## Architecture

### Shared: `mobilispect_core::health`

A new module `crates/core/src/health.rs` exposes one function:

```rust
pub async fn db_ping(pool: &PgPool) -> Result<()>
```

Executes `SELECT 1` against the pool. Returns `Ok(())` on success, `Err` on any failure. Exported from `crates/core/src/lib.rs` as `pub mod health`.

This is the only shared piece. Both binaries call it; neither duplicates the DB probe logic.

### Server

- **New route:** `GET /health` added to `build_router` in `crates/server/src/web/mod.rs`
- **New handler:** `health_check` in `crates/server/src/web/handlers.rs`
  - Calls `mobilispect_core::health::db_ping(&state.db.pool)`
  - Returns `(StatusCode::OK, Json({"status":"ok"}))` on success
  - Returns `(StatusCode::SERVICE_UNAVAILABLE, Json({"status":"error","message":"..."}))` on failure
- No new dependencies — the server already has `axum`, `serde`, `serde_json`
- No `AppState` changes — it already holds `db`

### Worker

The worker currently has no HTTP stack. A minimal Axum server is started as a background task before the final `std::future::pending()` call.

- **New module:** `crates/worker/src/health.rs`
  - Builds a minimal `Router` with a single `GET /health` handler
  - Handler calls `mobilispect_core::health::db_ping` and returns the same JSON contract as the server
  - Exposes `pub async fn serve(db: Database, bind_address: String) -> Result<()>`
- **Worker `main.rs`:** `tokio::spawn(health::serve(db.clone(), config.worker_health_bind_address.clone()))`  spawned before `std::future::pending()`
- **New deps in `crates/worker/Cargo.toml`:** `axum`, `serde`, `serde_json` — no tower-http (the health server doesn't need trace middleware)

### Config

New field on `Config` and `TomlConfig`:

| Field | Type | Default | TOML key |
|-------|------|---------|----------|
| `worker_health_bind_address` | `String` | `"0.0.0.0:9090"` | `worker_health_bind_address` (optional) |

Follows the same optional-with-default pattern as `bind_address`. Deserialized via `TomlConfig`, resolved in `Config::from_toml_str_with_env`.

The server health endpoint shares the server's existing `bind_address` — no additional config needed for it.

## Response Contract

Both endpoints return `application/json`:

```json
// 200 OK — DB reachable
{ "status": "ok" }

// 503 Service Unavailable — DB unreachable
{ "status": "error", "message": "db ping failed: connection refused" }
```

## Error Handling

- DB ping failures are logged at `warn` level before returning 503
- The health server start-up error is propagated to the spawned task and logged; it does not crash the worker

## Testing

- **Unit test** for `db_ping` in `crates/core/src/health.rs`: one test with a real Postgres container (testcontainers, same pattern as existing tests)
- **Integration test** for the server health handler in `crates/server/src/web/handlers.rs`: use Axum test client against a real DB, assert 200 and JSON body
- **Integration test** for the worker health server: call `health::serve` in a background task, make an HTTP request with `reqwest`, assert 200

## What Is Not In Scope

- Deep readiness checks (feed freshness, last ingest timestamp)
- Authentication or rate limiting on `/health`
- Separate liveness vs. readiness probes
