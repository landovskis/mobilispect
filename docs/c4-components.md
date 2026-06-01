<!-- architecture: auto-generated — edit the Mermaid block only, keep other text -->
# Mobilispect – Component Diagrams

> Last updated: 2026-05-26

Component-level detail for the two deployable binaries. The shared `mobilispect-core` library is shown as an external container in each diagram because it is statically linked into both binaries rather than deployed independently.

## mobilispect-server

The server is a thin Axum application. Handlers query the core library for domain data and render it through Askama templates. HTMX requests receive HTML fragments; plain requests receive full pages. The router also exposes two JSON endpoints for JavaScript chart data.

```mermaid
C4Component
  title mobilispect-server – Component Diagram

  Container_Boundary(server, "mobilispect-server") {
    Component(router, "Router", "Axum 0.7", "HTTP route table; owns AppState (db + config); applies TraceLayer middleware")
    Component(handlers, "Handlers", "Rust / Askama", "Speed, frequency, route-detail, and JSON API handlers; detects hx-request header to return fragment vs. full page")
    Component(templates, "Askama Templates", "Askama 0.15 / HTML", "Compiled type-safe HTML: speed.html, frequency.html, route_detail.html, route_speed_detail.html, and their *_content.html fragment twins")
  }

  Container_Ext(core, "mobilispect-core", "Rust library", "Domain queries and computations: on_time_performance, speed_analysis, service_frequency, ids, config, db")
  ContainerDb(postgres, "PostgreSQL", "PostgreSQL 16", "")
  Person(analyst, "Transit Analyst", "")

  Rel(analyst, router, "HTTP request", "HTTPS")
  Rel(router, handlers, "Dispatches to handler fn")
  Rel(handlers, core, "Calls route_summary, route_trend, route_headways, route_speed_by_day_type, etc.")
  Rel(handlers, templates, "Renders HTML via Template::render()")
  Rel(core, postgres, "Executes compile-time checked SQL queries", "sqlx")
```

## mobilispect-worker

The worker spawns three long-running Tokio task groups per configured agency: a one-shot static import, a polling realtime ingestor, and a daily maintenance loop. A lightweight pipeline module wires static and realtime events into metric computation hooks in `mobilispect-core`.

```mermaid
C4Component
  title mobilispect-worker – Component Diagram

  Container_Boundary(worker, "mobilispect-worker") {
    Component(static_feed, "Static Feed Ingestor", "Rust / gtfs-structures 0.26", "Downloads GTFS schedule ZIP; skips if already downloaded today or feed version unchanged; bulk-inserts routes, trips, stops, scheduled_stops, calendar, and route_variants in a single transaction")
    Component(realtime, "Realtime Poller", "Rust / prost 0.13 / reqwest 0.12", "Polls GTFS-RT endpoints on a configurable interval; decodes Protobuf; stores vehicle positions and stop time events")
    Component(pipeline, "Post-Import Pipeline", "Rust", "Calls core hooks: on_static_loaded after static import, on_realtime_polled after each RT poll")
    Component(maintenance, "Maintenance Loop", "Rust / Tokio", "Runs daily: deletes stop_time_events and vehicle_positions beyond retention window; computes daily on-time and speed metrics for each agency")
  }

  Container_Ext(core, "mobilispect-core", "Rust library", "on_static_loaded, on_realtime_polled, compute_route_daily, compute_route_speed_daily, speed_analysis, on_time_performance, db, config")
  ContainerDb(postgres, "PostgreSQL", "PostgreSQL 16", "")
  System_Ext(gtfs_static, "GTFS Static Feed", "")
  System_Ext(gtfs_rt, "GTFS-RT Feed", "")

  Rel(static_feed, gtfs_static, "Downloads ZIP", "HTTPS")
  Rel(static_feed, postgres, "Bulk-inserts schedule data in transaction", "sqlx")
  Rel(static_feed, pipeline, "Triggers after successful load")
  Rel(realtime, gtfs_rt, "Polls feed", "HTTPS / Protobuf")
  Rel(realtime, postgres, "Inserts vehicle positions + stop time events", "sqlx")
  Rel(realtime, pipeline, "Triggers after each successful poll")
  Rel(pipeline, core, "Calls on_static_loaded / on_realtime_polled")
  Rel(maintenance, postgres, "DELETEs old rows from event tables", "sqlx")
  Rel(maintenance, core, "compute_route_daily, compute_route_speed_daily")
```

<!-- manually maintained: add notes, decisions, or ADRs below this line -->
