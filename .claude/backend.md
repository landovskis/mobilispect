# Backend Configuration

## Technology Stack

- **Runtime**: Spring Boot with Kotlin 2.0+
- **Database**: PostgreSQL 18
- **Cache**: Redis 8.2
- **Architecture**: Spring Modulith with strict module boundaries

## Testing Commands

**IMPORTANT**: Run unit tests only during development to avoid long test
suite execution times.

```bash
# Run unit tests only (fast)
./backend/gradlew -p backend test --tests '*Test' --tests '*Tests'

# Skip integration tests (use this for quick verification)
./backend/gradlew -p backend test -x integrationTest

# Run specific test class
./backend/gradlew -p backend test --tests 'com.mobilispect.backend.agency.application.AgencyQueryServiceTest'

# Full test suite (includes integration tests with Testcontainers - SLOW)
./backend/gradlew -p backend test
```

Integration tests use Testcontainers and can take several minutes. Reserve
full test runs for pre-commit/pre-push validation.

## Pre-Commit Hooks

Backend-specific hooks enforced via `.pre-commit-config.yaml`:

- **ktlint**: Kotlin code formatting
- **detekt**: Static analysis
- **Test execution**: Unit tests must pass
- **Coverage validation**: ≥80% threshold via `scripts/validate-coverage.sh`

## IDE Integration

- **IntelliJ IDEA / Android Studio**: Pre-commit plugin configuration
- Enable ktlint and detekt plugins for real-time feedback

## Backend-Specific Requirements

### Modular Monolith

- Respect Spring Modulith boundaries
- No cross-module database access
- Use ports and events for inter-module communication
- Module extraction requires ADR + migration plan

### Performance Targets

- API p95 latency ≤200ms
- Implement backpressure for ingestion
- Use retries with exponential backoff and jitter
- Circuit breakers for external dependencies

### Database Migrations

- All schema changes via versioned migrations
- Migration validation required before deployment
- Never modify applied migrations