# Remove Hotspots Feature

## Summary

Delete the stop delay hotspots feature entirely. No replacement. The feature had zero tests, the handler silently swallowed DB errors, and it used a raw `agencies[0]` index that would panic on an empty config.

## Scope

**Delete:**
- `crates/core/src/metrics/hotspots.rs`
- `crates/server/templates/hotspots.html`

**Edit:**
- `crates/core/src/metrics/mod.rs` — remove `mod hotspots;` and `pub use hotspots::*;`
- `crates/server/src/web/mod.rs` — remove `.route("/hotspots", get(handlers::hotspots))`
- `crates/server/src/web/handlers.rs` — remove `HotspotsTemplate`, `hotspots` handler, and `StopHotspot` / `stop_hotspots` from imports

## Out of scope

- No migrations — hotspots queried `stop_time_events` (shared table, retained by other features)
- No nav changes — no `/hotspots` link exists in `base.html`

## Verification

`cargo build` must pass with no references to `StopHotspot`, `stop_hotspots`, or `hotspots.html` remaining.
