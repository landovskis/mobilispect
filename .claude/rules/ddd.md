# DDD Artifacts

DDD artifacts for the Mobilispect domain live in `docs/ddd/`. They are the authoritative reference for domain terms, bounded contexts, aggregates, and ACL boundaries.

## Before Writing Any Feature Spec

1. Read `docs/ddd/bounded-context-canvas.md` — identify which bounded context(s) the feature touches.
2. Read `docs/ddd/aggregate-specs.md` — identify which aggregates are involved.
3. Every feature spec must include a **Domain Context** section:

```markdown
## Domain Context

- **Bounded context(s):** [e.g. Performance, Reporting]
- **Aggregates touched:** [e.g. RouteDailyMetrics, Trip]
- **New ubiquitous language terms:** [list any new terms, or "none"]
```

## Keeping Artifacts Current

- **New domain term** introduced by a feature → add it to `docs/ddd/ubiquitous-language.md` in the same commit as the spec.
- **New or modified aggregate invariant** → update `docs/ddd/aggregate-specs.md` before writing implementation code.
- **New external data source** → document its translation boundary in `docs/ddd/acl.md`.

## ACL Boundary Rule

No file in `crates/core/` or `crates/server/` may import `gtfs_structures::*` or prost-generated protobuf types. Those are exclusively `mobilispect-worker` concerns. See `docs/ddd/acl.md` for the full mapping.
