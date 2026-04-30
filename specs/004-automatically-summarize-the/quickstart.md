# Quickstart: Automated Montreal Council Meeting Summarization

**Feature**: 004-automatically-summarize-the

---

## Prerequisites

- JDK 21+
- Docker (for PostgreSQL via Testcontainers in integration tests)
- An Anthropic API key with access to `claude-sonnet-4-6`

---

## Environment Setup

Add the following to your `application-local.yml` (never commit this file):

```yaml
app:
  anthropic:
    api-key: sk-ant-...your-key-here...
  council-meeting:
    scheduler:
      enabled: true
      cron: "0 0 6 * * *"   # daily at 06:00 server time
```

Or set environment variables:
```bash
export APP_ANTHROPIC_API_KEY=sk-ant-...
```

---

## Running the Service

```bash
# Start the backend (from repo root)
./backend/gradlew -p backend bootRun

# Trigger a manual summarization run (once scheduler is implemented)
# The scheduler runs automatically at 06:00 daily; no manual trigger endpoint in v1.
```

---

## Key API Endpoints

All endpoints are public (no authentication required).

### List council bodies
```bash
GET /api/council-bodies
```

### Latest summary (French)
```bash
GET /api/council-bodies/CM/summaries/latest?language=FR
```

### Latest summary (English)
```bash
GET /api/council-bodies/CA_Pmr/summaries/latest?language=EN
```

### Summaries by date range
```bash
GET /api/council-bodies/CM/summaries?language=FR&from=2026-01-01&to=2026-04-30
```

### Example response
```json
{
  "councilBodyCode": "CM",
  "councilBodyName": "Conseil municipal de Montréal",
  "meetingDate": "2026-04-28",
  "language": "EN",
  "keyDecisions": "- Approved the 2026 capital works budget amendment of $42M\n- Adopted bylaw 26-042 on heritage building maintenance standards",
  "motionsAndVotes": "- Motion to approve budget amendment: 42 for, 3 against, 2 abstentions\n- Motion on bylaw 26-042: unanimous",
  "mainTopics": "The council focused primarily on infrastructure investment priorities for 2026...",
  "sourceUrl": "https://ville.montreal.qc.ca/documents/Adi_Public/CM/CM_PV_ORDI_2026-04-28_13h00_FR.pdf",
  "generatedAt": "2026-04-29T06:12:34Z"
}
```

---

## Running Tests

```bash
# Unit tests only (fast)
./backend/gradlew -p backend test --tests '*councilmeeting*' -x integrationTest

# All unit tests
./backend/gradlew -p backend test -x integrationTest

# Integration tests (requires Docker)
./backend/gradlew -p backend integrationTest

# Coverage check
./scripts/validate-coverage.sh backend
```

---

## Module Location

```
backend/src/main/kotlin/com/mobilispect/backend/councilmeeting/
backend/src/test/kotlin/com/mobilispect/backend/councilmeeting/
backend/src/main/resources/db/migration/V064__create_council_meeting_tables.sql
backend/src/main/resources/db/migration/V065__seed_council_bodies.sql
```

---

## Adding a New Borough Code

If a new borough is added or a code needs correction:

1. Update `V065__seed_council_bodies.sql` — **do not edit applied migrations**.
   Create a new migration `V0NN__update_council_body_code.sql` with an UPDATE statement.
2. Update `CouncilBodyRegistry.kt` if the code affects URL construction logic.
3. Add/update unit tests in `CouncilBodyRegistryTest`.

---

## Disabling the Scheduler (e.g., in tests)

```yaml
app:
  council-meeting:
    scheduler:
      enabled: false
```

Or via system property: `-Dapp.council-meeting.scheduler.enabled=false`
