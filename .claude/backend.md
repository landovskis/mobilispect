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
full test runs for pre-push validation.

## Development Workflow

Follow these steps for every backend change (TDD is constitutional requirement):

### Step 1: Create Your Test First

```bash
# Navigate to test directory
cd backend/src/test/kotlin/com/mobilispect/backend/<module>

# Create your test file (if new): YourFeatureTest.kt
# Write a failing test that describes the behavior you want
```

### Step 2: Run Test to Verify It Fails

```bash
./backend/gradlew -p backend test --tests 'YourFeatureTest'
```

Expected: Test fails (red) ✗

### Step 3: Write Minimum Implementation

```bash
# Navigate to source directory
cd backend/src/main/kotlin/com/mobilispect/backend/<module>

# Write just enough code to make the test pass
```

### Step 4: Run Test to Verify It Passes

```bash
./backend/gradlew -p backend test --tests 'YourFeatureTest'
```

Expected: Test passes (green) ✓

### Step 5: Format Your Code

```bash
# Auto-format with ktlint
./backend/gradlew ktlintFormat
```

### Step 6: Run Static Analysis

```bash
# Check for code quality issues
./backend/gradlew detekt
```

Fix any violations reported, then re-run until clean.

### Step 7: Run All Unit Tests

```bash
# Run unit tests only (fast)
./backend/gradlew -p backend test -x integrationTest
```

All tests must pass ✓

### Step 8: Verify Coverage

```bash
# Check coverage meets 80% threshold
./scripts/validate-coverage.sh backend
```

If below 80%, add more tests and repeat from Step 1.

### Step 9: Commit Your Changes

```bash
git add .
git commit -m "feat: your feature description"
```

The pre-commit Husky hook runs automatically and checks formatting and linting.
If it fails, fix issues and retry.

### Step 10: Push Your Changes

```bash
git push
```

The pre-push Husky hook runs automatically and checks tests, static analysis,
coverage (≥80%), and security scan. If it fails, fix issues and retry.

## Git Hooks (Husky)

Backend checks are divided between hooks:

**pre-commit** (runs on `git commit` — fast):

- `ktfmtFormat` — auto-formats Kotlin code
- `ktlintCheck` — Kotlin linting

**pre-push** (runs on `git push` — thorough):

- `test -x integrationTest` — unit tests must pass
- `detekt` — static analysis
- `verifyModulith` — Spring Modulith module boundary verification
- `scripts/validate-coverage.sh` — ≥80% coverage requirement
- `scripts/security-scan.sh` — OWASP dependency check

## Quick Reference Commands

```bash
# Format code
./backend/gradlew ktlintFormat

# Run static analysis
./backend/gradlew detekt

# Run unit tests only (fast)
./backend/gradlew -p backend test -x integrationTest

# Run specific test
./backend/gradlew -p backend test --tests 'YourTestClass'

# Check coverage
./scripts/validate-coverage.sh backend

# Run integration tests (slow - Testcontainers)
./backend/gradlew integrationTest
```

## IDE Integration

- **IntelliJ IDEA / Android Studio**: Pre-commit plugin configuration
- Enable ktlint and detekt plugins for real-time feedback
- Configure "Reformat Code" to use ktlint settings
- Enable "Optimize imports on the fly"

## Backend-Specific Requirements

See [CLAUDE.md](../CLAUDE.md) for constitutional requirements on modular monolith architecture, performance targets, and quality standards. This section covers backend-specific implementation details.

### Database Migrations

- All schema changes via versioned migrations
- Migration validation required before deployment
- Never modify applied migrations

### Backend-Specific Implementation Notes

- Implement backpressure for ingestion endpoints
- Use retries with exponential backoff and jitter for external service calls
- Circuit breakers for external dependencies (e.g., third-party APIs)