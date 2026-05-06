# Testing Rules

## The Iron Law

No production code without a failing test first. Always.

Red-Green-Refactor

1. RED — Write one failing test. Verify it fails for the right reason.
2. GREEN — Write minimal code to pass. No more.
3. REFACTOR — Clean up while staying green.

## Test Types (applied to this Rust/Axum project)

┌─────────────┬───────────────────────────┬─────────────────────────┐   
│    Type     │           When            │          Notes          │
├─────────────┼───────────────────────────┼─────────────────────────┤   
│ Unit        │ Pure logic (e.g.          │ Real code, no mocks     │
│             │ speed::card computations) │ unless unavoidable      │
├─────────────┼───────────────────────────┼─────────────────────────┤   
│ Integration │ DB queries, GTFS parsing, │ Hit real Postgres via   │
│             │  sqlx interactions        │ testcontainers, not     │
│             │                           │ mocks or external DBs   │
├─────────────┼───────────────────────────┼─────────────────────────┤
│ E2E         │ HTTP handlers end-to-end  │ Use Axum test client    │   
│             │                           │ against real app state  │   
└─────────────┴───────────────────────────┴─────────────────────────┘

## Integration Test Setup (testcontainers)

Use the `testcontainers` crate to spin up a real Postgres instance for each integration test:

```rust
use testcontainers::{runners::AsyncRunner, ImageExt};
use testcontainers_modules::postgres::Postgres;

let container = Postgres::default().start().await.unwrap();
let db_url = format!(
    "postgres://postgres:postgres@127.0.0.1:{}/postgres",
    container.get_host_port_ipv4(5432).await.unwrap()
);
let pool = sqlx::PgPool::connect(&db_url).await.unwrap();
sqlx::migrate!().run(&pool).await.unwrap();
```

- Each test gets its own container — no shared state between tests
- Run migrations with `sqlx::migrate!()` after connecting
- The container is dropped (stopped) when it goes out of scope

## Key Rules

- Write test before implementation — always
- Watch it fail before writing code
- Tests-after prove nothing (you never saw them catch the bug)
- Bug fix? Write a failing test reproducing it first
- "Too simple to test" is a rationalization — 30-second tests prevent   
  regressions

## Verification Checklist (before claiming done)

- Every new function has a test
- Watched each test fail before implementing
- Wrote minimal code to pass
- All tests pass, output pristine
- Edge cases covered

Run tests with cargo test or cargo test <test_name> for a single test.  
                                              
