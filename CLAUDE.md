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

```
src/
  bin/
    server.rs      # HTTP server entry point
    worker.rs      # Background ingestion entry point
  config.rs        # Config + AgencyConfig from config.toml plus dotenvx env refs
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

Where new code goes:
- New metric or analysis: add a module under `metrics/` or a new top-level slice
- New web page: handler in `web/handlers.rs` + template in `templates/`
- New GTFS feed type: extend `gtfs/`
- Shared DB logic: `db/`
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
- No mocks in tests — use real Postgres via testcontainers (see `.claude/rules/testing.md`)
- Prefer explicit error types over `unwrap()` in production paths
- sqlx queries must be compile-time checked (`query!` / `query_as!`)
- Askama templates are type-safe — keep logic out of templates
- Config lives in `config.toml`; secrets via dotenvx env refs only

## UI & Design System

All UI must use the **Lumina design system** — see `frontend/design-system.html` for the full reference.

**Typography**
- Display/headings: `font-family: 'Cormorant', serif` — use for `h1`–`h3`, stat numbers
- Body/UI: `font-family: 'Jost', sans-serif` — default for all UI text
- Code/labels: `font-family: 'Fira Code', monospace` — section labels, data values

**Colour palette (Tailwind custom tokens)**
- `cream` — neutral scale; use for backgrounds, borders, text hierarchy
- `cinnabar` — primary accent (action, active states); `cinnabar-500` = `#C8463A`
- `oxford` — secondary accent (info, links); `oxford-500` = `#1D4E89`
- `saffron` — warning; `saffron-500` = `#E8A020`
- Sage (`#3D9A6B`) — success (CSS var only, no Tailwind token)

**CSS custom properties (semantic tokens)**
Use these instead of raw hex values:
- `--bg`, `--bg-subtle`, `--surface` — background layers
- `--ink`, `--secondary`, `--muted`, `--dim` — text hierarchy
- `--border`, `--border-light` — borders
- `--cinn`, `--ox`, `--saff`, `--sage` — accent colours
- Badge variants: `--b-cinn-bg/fg`, `--b-ox-bg/fg`, `--b-saff-bg/fg`, `--b-neu-bg/fg`
- Alert variants: `--al-info-*`, `--al-ok-*`, `--al-warn-*`, `--al-err-*`

**Dark mode:** toggle via `html.dark` class; all CSS vars switch automatically.

**Key component classes**
- `.btn` — base button; primary = `bg-cinnabar-500 text-cream-50 hover:bg-cinnabar-600`
- `.field` — text input with focus ring in cinnabar
- `.card` — `border-radius: 12px`, hover lift (`translateY(-2px)`)
- `.badge` — small label with variant bg/fg vars
- `.alert` — info/ok/warn/err with matching vars
- `.stat-num` — Cormorant 3rem weight-300 for metric display numbers
- `.section-label` — Fira Code uppercase tracking, cinnabar colour

Do not introduce other component libraries (Bootstrap, shadcn, etc.) unless explicitly requested.

## Safety Rules

- Do not modify `migrations/` schema without calling it out explicitly
- Do not change GTFS parsing logic without verifying against a real feed
- Do not alter `config.rs` AgencyConfig fields without updating `config.toml`
- Preserve existing metric definitions — changes affect historical comparisons
