# Transitland Bulk Stop Resolution

**Date:** 2026-06-06
**Status:** Approved

## Problem

Static feed ingestion currently calls Transitland once for every GTFS stop. Large feeds
therefore make hundreds or thousands of REST requests and exceed Transitland's Free Tier
rate limit. Each `429 Too Many Requests` response causes the current stop to be skipped,
leaving incomplete canonical stop and route-variant data.

## Design

Add a feed-wide stop lookup to `TransitlandClient`:

```rust
pub async fn resolve_stops_for_feed(
    &self,
    feed_onestop_id: &str,
) -> anyhow::Result<HashMap<String, (StopId, Option<StationId>)>>
```

The method requests `/stops.json` with `feed_onestop_id` and a page limit. It follows the
opaque `meta.after` cursor until no next cursor remains. Each response record supplies the
GTFS `stop_id`, canonical stop `onestop_id`, and optional parent station `onestop_id`.

Static ingestion calls this method once before iterating its GTFS stops. The existing loop
then performs local lookups by GTFS stop ID and retains its current database writes and
missing-match warnings.

The existing single-stop `resolve_stop` method remains unchanged for compatibility.

## Response Model

The private stop response model gains:

```rust
struct StopsResponse {
    stops: Vec<StopRecord>,
    meta: Option<PaginationMeta>,
}

struct StopRecord {
    stop_id: String,
    onestop_id: String,
    parent: Option<StationRecord>,
}

struct PaginationMeta {
    after: Option<i64>,
}
```

Transitland's current v2 response names the parent relationship `parent`. Deserialization
accepts the legacy `parent_station` name as an alias so existing fixtures and compatible
responses continue to work.

## Error Handling

- Any network or non-success HTTP response aborts feed-wide stop resolution.
- Static ingestion propagates that error instead of treating every remaining stop as an
  independent miss.
- Empty pages and missing `meta.after` values terminate pagination.
- Duplicate GTFS stop IDs use the last record returned by Transitland.

## Testing

Wiremock tests cover:

1. A single-page feed response mapped by GTFS stop ID.
2. Multiple pages linked by `meta.after`.
3. Parent station mapping.
4. HTTP errors propagated from any page.

Existing Transitland and worker tests continue to pass.

