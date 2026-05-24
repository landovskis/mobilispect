# Context Map

How the four bounded contexts relate to each other and to external systems.

```
GTFS Provider ──(ACL)──► Feed Ingestion ──(Conformist)──► Schedule
                                                               │
                                                     (Shared Kernel: ID types)
                                                               │
                                                               ▼
                                                          Performance ──(Customer/Supplier)──► Reporting
```

## Relationships

### GTFS Provider → Feed Ingestion: ACL (Anti-Corruption Layer)

GTFS is an external standard with its own model (raw strings, CSV conventions, protobuf schema). Feed Ingestion translates everything at the boundary — no GTFS-native types enter the domain. See `acl.md` for the translation rules.

### Feed Ingestion → Schedule: Conformist (schema level)

The database schema for schedule data mirrors GTFS structure intentionally (routes, trips, stops, scheduled_stops). We accept this constraint rather than fighting it. The benefit: GTFS → DB upserts are straightforward. The cost: the schema is coupled to the GTFS spec version.

### Schedule → Performance: Shared Kernel

`RouteId`, `TripId`, `StopId`, and other ID types are defined once in `crates/core/src/ids.rs` and used across both Schedule and Performance. Neither context owns these IDs exclusively — they are the shared kernel.

### Performance → Reporting: Customer/Supplier

Reporting is the downstream consumer. Performance is the upstream supplier. Performance exposes query functions (in `metrics/`, `speed/`, `frequency/`) that Reporting calls. Reporting has no write access to Performance's tables.
