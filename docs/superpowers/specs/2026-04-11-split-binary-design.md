# Split Binary: HTTP Server and Worker

**Date:** 2026-04-11  
**Goal:** Separate the single `mobilispect` binary into `mobilispect-server` and `mobilispect-worker` for operational fault isolation — a crashing worker does not take down the HTTP server and vice versa.

---

## Motivation

The current binary runs all concerns in one process: GTFS loading, speed computation, realtime polling, maintenance, and the HTTP server. A panic or unrecoverable error in any background task can crash the entire process including the web UI. Separating into two OS-level processes achieves isolation with minimal structural change.

---

## Approach

Two `[[bin]]` entries in `Cargo.toml` backed by a shared library crate (`src/lib.rs`). No workspace restructuring needed.

---

## File Structure

```
src/
  lib.rs           ← new: declares all shared modules
  bin/
    server.rs      ← renamed from main.rs; HTTP server entry point only
    worker.rs      ← new: GTFS init, RT polling, maintenance entry point
  config.rs        ← unchanged
  db/              ← unchanged
  gtfs/            ← unchanged
  maintenance/     ← unchanged
  metrics/         ← unchanged
  speed/           ← unchanged
  web/             ← unchanged
```

---

## Cargo.toml Changes

Remove the existing `[[bin]]` entry and replace with two:

```toml
[[bin]]
name = "mobilispect-server"
path = "src/bin/server.rs"

[[bin]]
name = "mobilispect-worker"
path = "src/bin/worker.rs"
```

---

## `src/lib.rs`

Declares all existing modules as public:

```rust
pub mod config;
pub mod db;
pub mod gtfs;
pub mod maintenance;
pub mod metrics;
pub mod speed;
pub mod web;
```

No changes to any module's internal visibility.

---

## Binary Responsibilities

### `mobilispect-server`

1. Init tracing
2. `Config::from_env()`
3. `Database::connect()` + `db.migrate()`
4. `web::serve(&db, &config).await` — blocks until shutdown

### `mobilispect-worker`

1. Init tracing
2. `Config::from_env()`
3. `Database::connect()` + `db.migrate()`
4. Load static GTFS + compute speeds for all agencies in parallel (same logic as today)
5. Spawn RT poll loops for successfully loaded agencies (same restart-on-exit logic as today)
6. Spawn maintenance/retention loop (same restart-on-exit logic as today)
7. Block forever: `std::future::pending::<()>().await`

Both binaries read the full `Config` from the same env vars. No config changes needed.

---

## What Does Not Change

- All module internals (`gtfs`, `speed`, `maintenance`, `web`, `db`, `config`)
- Environment variable interface
- Database migrations (worker runs them; server also runs them for safety on independent deploys)
- The `Dockerfile` — for now, both binaries are compiled in the same image; deployment split can follow separately

---

## Out of Scope

- Splitting into separate Docker images
- Independent scaling of server replicas
- Removing `axum`/web deps from the worker compile unit (they're in the shared lib; acceptable for now)
