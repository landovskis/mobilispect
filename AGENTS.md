# AGENTS.md - Mobilispect

Quick-reference facts that agents often gets wrong:

## Running the app

```bash
# ALL commands require dotenvx to load secrets (not optional)
dotenvx run -- cargo run --bin mobilispect-server
dotenvx run -- cargo run --bin mobilispect-worker

# Dev mode (auto-restart + Postgres in Docker)
./dev.sh
```

## Dev workflow

- **Postgres runs on port 5433** (not default 5432) - configured in dev.sh
- Database URL must be set: `MOBILISPECT_DATABASE_URL=postgres://mobilispect:mobilispect@localhost:5433/mobilispect`
- dev.sh manages Docker container `mobilispect-pg` - run `docker start mobilispect-pg` to resume

## Build/code generation

- **Protobuf compiled at build time**: build.rs runs prost-build on `proto/gtfs-realtime.proto`
- Rebuild required after proto changes: `cargo build` reruns build.rs
- **SQLX_OFFLINE enabled**: queries checked at compile time via `.cargo/config.toml` - do NOT disable

## Commands

```bash
cargo fm          # format (not cargo fmt)
cargo clippy     # lint
cargo test <name> # single test
```

## Architecture

- Two binaries: `mobilispect-server` (HTTP), `mobilispect-worker` (GTFS ingestion)
- Server reads config from `config.toml` (agencies, thresholds, retention)
- Env vars for secrets: API keys loaded via dotenvx (not hardcoded)