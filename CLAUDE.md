# Mobilispect: Mobility Performance Monitoring

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Mobilispect monitors mobility performance for transit agencies and active mobility networks.
It ingests GTFS static schedules and real-time feeds, computes metrics
(on-time %, speed, delays, hotspots), and presents them via a web dashboard.

Current focus: public transit. Planned expansion: active mobility (cycling, walking, micromobility).

Primary users: transit analysts and agency operations staff.

Optimizes for: data accuracy, clear metric definitions, and fast feed ingestion.
Avoid over-abstraction — prefer clear domain logic over clever generics.

## Stack

- **Language:** Rust (2024 edition)
- **Async runtime:** Tokio
- **Web framework:** Axum 0.7
- **Database:** PostgreSQL via sqlx 0.8 (compile-time checked queries, migrations)
- **Templates:** Askama (type-safe HTML, compiled at build time)
- **GTFS parsing:** gtfs-structures (static), prost (RT protobuf)
- **HTTP client:** reqwest
- **CSS:** Tailwind CSS + Lumina design system (`frontend/design-system.html`)
- **Logging/tracing:** tracing + tracing-subscriber

## Structure

Cargo workspace with three crates:

```
crates/
  core/            # mobilispect-core (library)
    src/
      config.rs    # Config + AgencyConfig from config.toml plus dotenvx env refs
      db/          # Database wrapper (sqlx PgPool)
      metrics/     # Query functions (on-time %, delays, hotspots, scorecard)
      speed/
        mod.rs     # Scheduled/actual speed computation
        card.rs    # RouteSpeedCard builder
      frequency/   # Headway computation
    migrations/    # SQL schema migrations
  server/          # mobilispect-server (binary)
    src/
      main.rs      # HTTP server entry point
      web/
        mod.rs     # Axum router + AppState
        handlers.rs # HTTP handlers → Askama templates
    templates/     # Askama HTML templates
  worker/          # mobilispect-worker (binary)
    src/
      main.rs      # Background ingestion entry point
      gtfs/
        static_feed.rs # GTFS zip download + upsert
        realtime.rs    # GTFS-RT protobuf polling
      maintenance/     # Data retention cleanup
    proto/         # GTFS-RT .proto definition
    build.rs       # prost protobuf compilation
```

Where new code goes:
- New metric or analysis: add a module under `crates/core/src/metrics/` or a new slice in core
- New web page: handler in `crates/server/src/web/handlers.rs` + template in `crates/server/templates/`
- New GTFS feed type: extend `crates/worker/src/gtfs/`
- Shared DB logic: `crates/core/src/db/`
- Each vertical slice owns its query, computation, and presentation layer

## Commands

```bash
# Build
cargo build

# Run server (requires config.toml and dotenvx-provided secret env vars)
dotenvx run -- cargo run --bin mobilispect-server

# Run worker
dotenvx run -- cargo run --bin mobilispect-worker

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

- Vertical Slice Architecture: each feature owns its DB query, computation, and handler
- **Functional Core / Imperative Shell is mandatory:** pure domain logic (computation, validation, transformations) must be in functions that take plain values and return plain values — no I/O, no side effects. I/O (DB, HTTP, file system) belongs only in the shell layer (handlers, workers, feed ingestors) that calls into the core. Never mix the two.
- No mocks in tests — use real Postgres via testcontainers (see `.claude/rules/testing.md`)
- Prefer explicit error types over `unwrap()` in production paths
- sqlx queries must be compile-time checked (`query!` / `query_as!`)
- Askama templates are type-safe — keep logic out of templates
- Config lives in `config.toml`; secrets via dotenvx env refs only

## UI & Design System

This project uses a design system defined in @DESIGN.md.
Follow strictly the rules defined in @DESIGN.md for all UI generation.
Do not invent colors, fonts, or spacing values outside the design system.
Match component states (hover, focus, active, disabled) to patterns in @DESIGN.md.

## Safety Rules

- Do not modify `migrations/` schema without calling it out explicitly
- Do not change GTFS parsing logic without verifying against a real feed
- Do not alter `config.rs` AgencyConfig fields without updating `config.toml`
- Preserve existing metric definitions — changes affect historical comparisons
