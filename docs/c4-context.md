<!-- architecture: auto-generated — edit the Mermaid block only, keep other text -->
# Mobilispect – System Context

> Last updated: 2026-05-26

Mobilispect is a transit performance monitoring system. Transit analysts and agency operations staff use its web dashboard to track on-time performance, route speed, and service frequency. The system ingests two external data sources: GTFS static schedule feeds (downloaded as ZIP archives from agency servers) and GTFS-RT real-time feeds (polled as Protobuf streams). Both are consumed entirely within Mobilispect — external consumers receive nothing from this system.

```mermaid
C4Context
  title Mobilispect – System Context

  Person(analyst, "Transit Analyst", "Agency operations staff monitoring route performance and service quality")

  System(mobilispect, "Mobilispect", "Ingests GTFS static and real-time feeds, computes on-time %, speed, and headway metrics, and presents them via a web dashboard")

  System_Ext(gtfs_static, "GTFS Static Feed", "Per-agency schedule ZIP files (routes, trips, stops, stop times, calendar)")
  System_Ext(gtfs_rt, "GTFS-RT Feed", "Per-agency real-time Protobuf feeds: vehicle positions and trip updates")

  Rel(analyst, mobilispect, "Views route dashboards and drills into per-route metrics", "HTTPS / HTML + HTMX")
  Rel(mobilispect, gtfs_static, "Downloads schedule ZIP on startup or when feed version changes", "HTTPS")
  Rel(mobilispect, gtfs_rt, "Polls vehicle positions and trip updates every N seconds", "HTTPS / Protobuf")
```

<!-- manually maintained: add notes, decisions, or ADRs below this line -->
