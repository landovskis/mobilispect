# GitHub Actions CI Design

**Date:** 2026-05-24
**Status:** Approved

## Overview

Two-workflow CI/CD pipeline for the Mobilispect Rust workspace. `ci.yml` gates every PR and push to `main` with five parallel quality checks. `deploy.yml` triggers a blocking Railway deployment on every push to `main`. Branch protection ensures nothing reaches `main` without passing CI.

## Domain Context

- **Bounded context(s):** Tooling / Infrastructure (cross-cutting)
- **Aggregates touched:** None — pure CI/CD configuration
- **New ubiquitous language terms:** None

---

## File Structure

```
.github/
  workflows/
    ci.yml       # Runs on pull_request + push to main
    deploy.yml   # Runs on push to main
```

---

## `ci.yml` — Continuous Integration

### Triggers

```yaml
on:
  pull_request:
  push:
    branches: [main]
```

### Shared Setup (all jobs)

- Runner: `ubuntu-latest`
- `actions/checkout`
- `dtolnay/rust-toolchain@stable` with components `rustfmt` and `clippy`
- `Swatinem/rust-cache` — caches `~/.cargo/registry` and `target/` between runs
- `SQLX_OFFLINE: "true"` — compile-time query verification uses committed `.sqlx/` cache; no live DB needed at compile time

### Jobs (all parallel)

#### `lint`

1. `apt-get install -y pkg-config libssl-dev protobuf-compiler` — same native deps as the Dockerfile
2. `cargo fmt --check`
3. `cargo clippy --all-targets --all-features -- -D warnings`

Estimated time (warm cache): ~1–2 min.

#### `test`

1. `apt-get install -y pkg-config libssl-dev protobuf-compiler`
2. Install `cargo-nextest` via `taiki-e/install-action` (pre-built binary, ~5s)
3. `cargo nextest run --profile ci`

`ubuntu-latest` runners have a live Docker daemon; testcontainers spins up real Postgres containers with no extra configuration. The `[profile.ci]` in `.config/nextest.toml` sets `fail-fast = true` and a 60s slow-test threshold.

Estimated time (warm cache): ~3–5 min.

#### `audit`

Uses `rustsec/audit-check@v2`, which wraps `cargo audit`. Fails if any dependency has a known CVE in the RustSec advisory database. No Rust compilation required.

Estimated time: ~30s.

#### `docker-build`

1. `docker/setup-buildx-action`
2. `docker/build-push-action` with `push: false` and `cache-from/cache-to: type=gha`

Verifies the multi-stage Dockerfile compiles correctly. Docker layer cache (`type=gha`) keeps repeat builds fast. Does not publish an image.

Estimated time (warm cache): ~2–3 min.

#### `mutants`

**Runs on `pull_request` only** — skipped on push to main (the PR already gated it).

1. `actions/checkout` with `fetch-depth: 0` (full history needed for accurate diff)
2. `dtolnay/rust-toolchain@stable`
3. `Swatinem/rust-cache` — avoids recompiling the full workspace per mutation run
4. `apt-get install -y pkg-config libssl-dev protobuf-compiler`
5. Install `cargo-mutants` via `cargo install cargo-mutants --locked`
6. Compute PR diff: `git diff origin/${{ github.base_ref }}...HEAD > /tmp/pr.diff`
7. `cargo mutants --in-diff /tmp/pr.diff --timeout 120`
8. Upload `mutants.out/` as a workflow artifact

`--in-diff` limits mutation to lines changed in the PR — keeps the job proportional to change size and avoids running mutations on untouched code. `--timeout 120` caps each mutation's test run at 120s (testcontainers Postgres startup is ~5–10s, leaving ~110s for actual tests).

**Blocking** — a surviving mutant in changed lines means the new code lacks test coverage. The job fails and the PR cannot merge.

Estimated time: proportional to diff size; typically 2–10 min for a normal PR.

---

## `deploy.yml` — Railway Deployment

### Trigger

```yaml
on:
  push:
    branches: [main]
```

### Job: `deploy`

Runs **without** `--detach` — the CLI streams Railway build logs and exits non-zero if the deployment fails. The GitHub Actions job goes red on a failed deploy, not just a failed trigger.

Steps:
1. `actions/checkout`
2. `npm install -g @railway/cli`
3. `railway up --service ${{ secrets.RAILWAY_SERVICE_ID_SERVER }}` — deploys the server binary; blocks until Railway confirms success or failure
4. `railway up --service ${{ secrets.RAILWAY_SERVICE_ID_WORKER }}` — deploys the worker binary; only runs if server deploy succeeded

Sequential ordering is intentional: a broken server deploy stops the worker deploy from running.

### Required Secrets

| Secret | Description |
|--------|-------------|
| `RAILWAY_TOKEN` | Railway service account token |
| `RAILWAY_SERVICE_ID_SERVER` | Railway service ID for `mobilispect-server` |
| `RAILWAY_SERVICE_ID_WORKER` | Railway service ID for `mobilispect-worker` |

---

## Branch Protection (Recommended Configuration)

Require the following status checks to pass before merging to `main`:

- `lint`
- `test`
- `audit`
- `docker-build`
- `mutants`

`mutants` only runs on PRs, so it is always present before a merge. The `deploy` job is not a branch protection check — deployment status is visible in the Actions tab.

---

## Caching Strategy

| Layer | Mechanism | Covers |
|-------|-----------|--------|
| Cargo registry + target dir | `Swatinem/rust-cache` | Compiled dependencies, incremental rustc output |
| Docker layers | `type=gha` cache backend | Multi-stage Docker build layers |
| sqlx query metadata | Committed `.sqlx/` directory | Compile-time query verification without a live DB |

---

## Native Dependencies

All Rust jobs require the same three packages the Dockerfile installs:

```bash
apt-get install -y pkg-config libssl-dev protobuf-compiler
```

- `pkg-config` + `libssl-dev` — OpenSSL headers for `reqwest` TLS support
- `protobuf-compiler` — `protoc` required by `prost-build` in the worker's `build.rs`
