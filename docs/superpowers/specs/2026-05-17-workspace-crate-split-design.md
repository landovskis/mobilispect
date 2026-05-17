# Workspace Crate Split: server + worker

**Date:** 2026-05-17
**Status:** Approved

## Goal

Split the single `mobilispect` package into a Cargo workspace with three crates so that:
- server and worker build and deploy as independent artifacts
- changing a template or handler doesn't recompile worker code, and vice versa
- each binary's `Cargo.toml` only declares the dependencies it actually uses

## Workspace Layout

```
mobilispect/
  Cargo.toml                    # workspace manifest — members: core, server, worker
  crates/
    core/
      Cargo.toml                # lib: mobilispect-core
      src/
        lib.rs                  # pub mod config, db, speed, metrics, frequency
        config.rs
        db/
        speed/
        metrics/
        frequency/
      migrations/               # moved from repo root
    server/
      Cargo.toml                # bin: mobilispect-server
      src/
        main.rs                 # was src/bin/server.rs
        web/
          mod.rs
          handlers.rs
      templates/                # moved from repo root (Askama uses CARGO_MANIFEST_DIR)
    worker/
      Cargo.toml                # bin: mobilispect-worker
      src/
        main.rs                 # was src/bin/worker.rs
        gtfs/
          mod.rs
          static_feed.rs
          realtime.rs
        maintenance/
          mod.rs
      proto/                    # moved from repo root
      build.rs                  # moved from repo root (prost compile)
  config.toml                   # stays at root (runtime)
  frontend/, package.json       # stays at root (Tailwind)
  Dockerfile, dev.sh            # stays at root, minor updates
```

## Dependencies per Crate

### mobilispect-core
```toml
tokio = { version = "1", features = ["full"] }
sqlx = { version = "0.8", features = ["runtime-tokio", "postgres", "chrono", "migrate"] }
chrono = { version = "0.4", features = ["serde"] }
serde = { version = "1", features = ["derive"] }
serde_json = "1"
toml = "0.8"
tracing = "0.1"
anyhow = "1"
thiserror = "2"
bytes = "1"

[dev-dependencies]
testcontainers = "0.21"
testcontainers-modules = { version = "0.9", features = ["postgres"] }
tokio = { version = "1", features = ["full"] }
```

### mobilispect-server
```toml
mobilispect-core = { path = "../core" }
tokio = { version = "1", features = ["full"] }
axum = { version = "0.7", features = ["macros"] }
tower = "0.5"
tower-http = { version = "0.6", features = ["fs", "trace"] }
askama = { version = "0.15", features = ["derive"] }
tracing = "0.1"
tracing-subscriber = { version = "0.3", features = ["env-filter"] }
anyhow = "1"
```

### mobilispect-worker
```toml
mobilispect-core = { path = "../core" }
tokio = { version = "1", features = ["full"] }
reqwest = { version = "0.12", features = ["gzip", "json"] }
gtfs-structures = "0.26"
prost = "0.13"
sha2 = "0.10"
hex = "0.4"
tracing = "0.1"
tracing-subscriber = { version = "0.3", features = ["env-filter"] }
anyhow = "1"
bytes = "1"

[build-dependencies]
prost-build = "0.13"
```

## Code Changes

### `use` paths
Both `main.rs` entrypoints replace `use mobilispect::` with `use mobilispect_core::`.
All `use crate::` references inside `web/`, `gtfs/`, `maintenance/` are unchanged — each module stays within its own crate.

### `sqlx::migrate!`
`db/mod.rs` keeps `sqlx::migrate!("./migrations")` unchanged. sqlx resolves paths from `CARGO_MANIFEST_DIR`, which becomes `crates/core/` after the move.

### Askama templates
Askama also resolves `templates/` from `CARGO_MANIFEST_DIR`. Moving `templates/` into `crates/server/templates/` requires no config change.

### `build.rs`
Moves verbatim to `crates/worker/build.rs`. Content unchanged; `proto/` moves alongside it.

### Dockerfile
`cargo build --release` becomes two invocations:
```
cargo build --release -p mobilispect-server
cargo build --release -p mobilispect-worker
```

### `cargo sqlx prepare`
Run from repo root:
```
cargo sqlx prepare --workspace
```

### Tests
All integration and unit tests live in `mobilispect-core` (speed, gtfs, db modules). No test rewrites needed. `cargo test` at workspace root runs all crates.

## What Doesn't Change
- Binary names (`mobilispect-server`, `mobilispect-worker`)
- `cargo run --bin` invocations in `dev.sh`
- `config.toml` location and structure
- All module-internal logic
- Migration content
- Template content
