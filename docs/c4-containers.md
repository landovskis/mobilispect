<!-- architecture: auto-generated — edit the Mermaid block only, keep other text -->
# Mobilispect – Container Diagram

> Last updated: 2026-05-26

Three deployable units make up Mobilispect: a web server binary, a background worker binary, and a PostgreSQL database. Both binaries link against `mobilispect-core`, a Rust library crate that holds all shared domain logic (queries, computations, configuration, ID types). The server and worker never communicate with each other directly — the database is their only shared state.

```mermaid
C4Container
  title Mobilispect – Container Diagram

  Person(analyst, "Transit Analyst", "Monitors route performance via dashboard")

  System_Boundary(mobilispect, "Mobilispect") {
    Container(server, "mobilispect-server", "Rust / Axum 0.7", "Serves the web dashboard: speed overview, route detail, frequency schedule pages, and JSON API endpoints")
    Container(worker, "mobilispect-worker", "Rust / Tokio", "Ingests GTFS static schedules and polls GTFS-RT real-time feeds; triggers post-import metric computation; runs daily data retention")
    ContainerDb(postgres, "PostgreSQL", "PostgreSQL 16", "Stores GTFS schedule data (routes, trips, stops, stop times, calendar), real-time events (vehicle positions, stop time events), computed metrics, and feed metadata")
  }

  System_Ext(gtfs_static, "GTFS Static Feed", "Per-agency schedule ZIP archives")
  System_Ext(gtfs_rt, "GTFS-RT Feed", "Per-agency real-time Protobuf streams")

  Rel(analyst, server, "Views dashboards and drills into routes", "HTTPS / HTML + HTMX")
  Rel(server, postgres, "Queries schedule and metric data", "sqlx / SQL")
  Rel(worker, postgres, "Writes schedule data, real-time events, computed metrics", "sqlx / SQL")
  Rel(worker, gtfs_static, "Downloads schedule ZIP on startup or feed version change", "HTTPS")
  Rel(worker, gtfs_rt, "Polls vehicle positions and trip updates every N seconds", "HTTPS / Protobuf")
```

<!-- manually maintained: add notes, decisions, or ADRs below this line -->
