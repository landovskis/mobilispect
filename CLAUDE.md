# Mobilispect: Transit Performance Monitoring

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

- **Language:** Rust (2024 edition)
- **Async runtime:** Tokio
- **Web framework:** Axum 0.7
- **Database:** PostgreSQL via sqlx 0.8 (compile-time checked queries, migrations)
- **Templates:** Askama (type-safe HTML, compiled at build time)
- **GTFS parsing:** gtfs-structures (static), prost (RT protobuf)
- **HTTP client:** reqwest
- **Logging/tracing:** tracing + tracing-subscriber

## Structure

```
src/
  bin/
    server.rs      # HTTP server entry point
    worker.rs      # Background ingestion entry point
  config.rs        # Config + AgencyConfig from env vars
  db/              # Database wrapper (sqlx PgPool)
  gtfs/
    static_feed.rs # GTFS zip download + upsert
    realtime.rs    # GTFS-RT protobuf polling
  metrics/         # Query functions (on-time %, delays, hotspots, scorecard)
  speed/
    mod.rs         # Scheduled/actual speed computation
    card.rs        # RouteSpeedCard builder
  web/
    mod.rs         # Axum router + AppState
    handlers.rs    # HTTP handlers → Askama templates
migrations/        # SQL schema (001_schema.sql)
templates/         # Askama HTML templates
```

## Commands

```bash
# Build
cargo build

# Run server (requires DATABASE_URL and agency env vars)
cargo run --bin mobilispect-server

# Run worker
cargo run --bin mobilispect-worker

# Dev mode (auto-restart on changes, starts Postgres via Docker)
./dev.sh

# Lint
cargo clippy

# Format
cargo fm

# Tests
cargo test
cargo test <test_name>   # run a single test
```

## Conventions

- Use Vertical Slice Architecture
