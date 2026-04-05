# Montreal Metropolitan Area Coverage — Design Spec

**Date:** 2026-04-05
**Status:** Approved

## Overview

Extend Mobilispect from single-agency (STM only) to the full Montreal metropolitan transit network: STM, RTL, STL, and all exo networks (trains + 11 bus sectors). 15 agency entries total. Add agency labels inline with route numbers and per-agency filtering to all list views.

## Section 1: Configuration

Complete `.env.example` with all exo bus sector entries. The file already documents indices 0–4 (STM, RTL, STL, exo-trains, exo-citso). Add indices 5–14 for the remaining 10 exo networks:

| Index | Slug         | Name             |
|-------|------------- |------------------|
| 5     | exo-citla    | exo (Laval)      |
| 6     | exo-citpi    | exo (Presqu'île) |
| 7     | exo-citsv    | exo (Sorel-Varennes) |
| 8     | exo-citvr    | exo (Vallée-du-Richelieu) |
| 9     | exo-citcrc   | exo (Chambly-Richelieu-Carignan) |
| 10    | exo-citrous  | exo (Roussillon) |
| 11    | exo-citlr    | exo (Le Richelain) |
| 12    | exo-mrclm    | exo (Laurentides-Mirabel) |
| 13    | exo-mrclasso | exo (Assomption) |
| 14    | exo-lrrs     | exo (Haut-Saint-Laurent) |

All exo bus sectors share the same GTFS-RT URLs as exo-trains:
- Vehicle positions: `https://opendata.exo.quebec/ServiceGTFSR/VehiclePosition.pb`
- Trip updates: `https://opendata.exo.quebec/ServiceGTFSR/TripUpdate.pb`
- Static GTFS: `https://exo.quebec/xdata/{network}/google_transit.zip`
- UTC offset: `-04:00`
- API key: none required

## Section 2: Agency Labels in Route Tables

`RouteSummary`, `ScorecardRoute`, and `RouteSpeedSummary` already carry `agency_id` (the slug). No struct or DB changes are needed.

Each handler that renders a route list builds a `HashMap<String, String>` (slug → display name) from `config.agencies` and passes it to the template as `agency_names`. Templates render the route identifier inline as:

```
{{ agency_names[route.agency_id] }} {{ route.short_name }}
```

e.g. "STM 15", "RTL 10", "exo (trains) 1".

Affected templates: `dashboard.html`, `scorecard.html`, `speed.html`, `report.html`.
Affected handlers: `dashboard`, `scorecard`, `speed_page`, `report` in `handlers.rs`.

## Section 3: Agency Filter

All list pages (dashboard, scorecard, speed) accept an optional `?agency=<slug>` query param.

**Handler changes:**
- Add a `agency: Option<String>` field to each page's query params struct.
- Pass it as an optional filter to the corresponding DB query function.
- Pass `config.agencies` (as a slice or vec of `(slug, name)` pairs) and the active `agency` slug to the template.

**DB query changes:**
- `route_summary`, `scorecard_routes`, and `route_speed_summary` each gain an `agency_filter: Option<&str>` parameter.
- When `Some(slug)`, append `AND agency_id = ?` to the existing query.

**Template changes:**
- Add a filter bar above the route table:
  - "All" link → current page URL with no `?agency` param (active when `agency` is `None`)
  - One link per agency → `?agency={slug}` (active when slug matches current filter)
  - Active link is visually distinguished (e.g. bold or highlighted).

**`hotspots` handler:** uses `agencies[0]` for UTC offset only. All Montreal agencies share the same timezone, so this remains correct with no change.

## Out of Scope

- Grouping exo networks under a single "exo" umbrella in the UI — each network is a separate entry.
- Per-agency hotspot pages.
- Agency management UI (agencies are configured via env vars only).
