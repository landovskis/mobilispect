# Transitland Bulk Stop Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-stop Transitland requests during static ingestion with paginated feed-wide stop resolution.

**Architecture:** `TransitlandClient` fetches all stops for a feed by following Transitland pagination cursors and returns a map keyed by GTFS stop ID. The worker resolves its parsed GTFS stops from that map while preserving existing database writes and warnings.

**Tech Stack:** Rust 2024, Tokio, reqwest, serde, wiremock, cargo test

---

### Task 1: Add Paginated Feed Stop Resolution

**Files:**
- Modify: `crates/core/src/transitland/mod.rs`
- Test: `crates/core/src/transitland/mod.rs`

- [ ] **Step 1: Write a failing single-page mapping test**

Add a wiremock test that returns two stop records from `/stops.json`, including their
`stop_id`, `onestop_id`, and an optional `parent`. Call `resolve_stops_for_feed` and assert
that both GTFS IDs map to the expected canonical IDs.

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
dotenvx run -- cargo test -p mobilispect-core resolve_stops_for_feed_returns_map
```

Expected: compilation fails because `resolve_stops_for_feed` does not exist.

- [ ] **Step 3: Implement the minimal single-page method**

Add `stop_id` and pagination metadata to the private serde models. Implement
`resolve_stops_for_feed` with `feed_onestop_id` and `limit=1000`, converting returned
records into a `HashMap<String, (StopId, Option<StationId>)>`.

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
dotenvx run -- cargo test -p mobilispect-core resolve_stops_for_feed_returns_map
```

Expected: one test passes.

- [ ] **Step 5: Write a failing pagination test**

Configure page one to return `meta.after = 42` and page two to require `after=42`.
Assert records from both pages are returned and each mock is called once.

- [ ] **Step 6: Run the pagination test and verify it fails**

Run:

```bash
dotenvx run -- cargo test -p mobilispect-core resolve_stops_for_feed_follows_pagination
```

Expected: failure because the client only requests the first page.

- [ ] **Step 7: Implement cursor pagination**

Loop requests while `meta.after` is present. Add the opaque cursor as an `after` query
parameter on subsequent requests and merge each page into the result map.

- [ ] **Step 8: Run Transitland client tests**

Run:

```bash
dotenvx run -- cargo test -p mobilispect-core transitland::tests
```

Expected: all Transitland tests pass.

### Task 2: Use Bulk Resolution During Static Ingestion

**Files:**
- Modify: `crates/worker/src/feed_ingestion/static_feed.rs`

- [ ] **Step 1: Replace per-stop calls with one feed-wide request**

At the start of `resolve_stops`, call:

```rust
let resolved = transitland.resolve_stops_for_feed(tl_feed_id).await?;
```

Inside the GTFS stop loop, replace the async `resolve_stop` match with a lookup in
`resolved`. Preserve the existing upsert behavior and missing-match warning.

- [ ] **Step 2: Run worker feed ingestion tests**

Run:

```bash
dotenvx run -- cargo test -p mobilispect-worker feed_ingestion
```

Expected: all feed ingestion tests pass.

### Task 3: Verify the Workspace

**Files:**
- Verify: `crates/core/src/transitland/mod.rs`
- Verify: `crates/worker/src/feed_ingestion/static_feed.rs`

- [ ] **Step 1: Format**

Run:

```bash
cargo fm
```

Expected: formatting succeeds.

- [ ] **Step 2: Run focused package tests**

Run:

```bash
dotenvx run -- cargo test -p mobilispect-core transitland::tests
dotenvx run -- cargo test -p mobilispect-worker feed_ingestion
```

Expected: all tests pass.

- [ ] **Step 3: Run lint**

Run:

```bash
dotenvx run -- cargo clippy --workspace --all-targets -- -D warnings
```

Expected: no warnings or errors.

