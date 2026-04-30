# Implementation Plan: Automated Montreal Council Meeting Summarization

**Branch**: `004-automatically-summarize-the` | **Date**: 2026-04-30 | **Spec**: `specs/004-automatically-summarize-the/spec.md`

---

## Summary

Build a new `councilmeeting` Spring Modulith module that daily discovers, fetches, and summarizes Montreal city and borough council meeting minutes PDFs from `ville.montreal.qc.ca`. Each meeting produces two AI-generated summaries (French and English) via the Claude API (`claude-sonnet-4-6`), stored in PostgreSQL and served via three public REST endpoints. No dependencies on existing backend modules.

---

## Technical Context

**Language/Version**: Kotlin 2.0+, JVM 21, Spring Boot 4.0
**Primary Dependencies**:
- Spring Modulith (module boundary enforcement)
- Spring Web MVC (REST controllers)
- Spring Data JPA + Hibernate (persistence)
- Spring Scheduler (daily job, `@EnableScheduling` already configured)
- Apache PDFBox 3.0.3 (PDF text extraction)
- `com.anthropic:anthropic-java-client-okhttp:2.5.0` (Claude API)
- Resilience4j (retry on transient fetch failures)
- Flyway (DB migrations V064, V065)

**Storage**: PostgreSQL 18 — 5 new tables: `council_body`, `council_meeting`, `meeting_summary`, `summarization_attempt`
**Testing**: JUnit 5, MockK, Testcontainers (PostgreSQL), WireMock (HTTP stubs for Claude API and ville.montreal.qc.ca)
**Target Platform**: Linux server (Spring Boot application, same deployment unit as existing backend)
**Project Type**: web-service (modular monolith addition)
**Performance Goals**: API p95 ≤200ms (constitutional requirement); summarization job is async/offline, no latency SLA
**Constraints**:
- Spring Modulith: no cross-module DB access; `councilmeeting` has no `allowedDependencies`
- Anthropic API key stored outside VCS via `@ConfigurationProperties`
- ≥80% test coverage per module (constitutional requirement)
- TDD: tests written before implementation (constitutional requirement)

**Scale/Scope**: 20 council bodies × ~10 meetings/year = ~200 documents/year; trivial data volume

---

## Constitution Check

*GATE: Must pass before implementation begins.*

| Principle | Status | Notes |
|-----------|--------|-------|
| Modular Monolith | ✅ Pass | New standalone `councilmeeting` module; no cross-module DB access; no `allowedDependencies` |
| Test-Driven Quality | ✅ Pass | TDD required; Testcontainers for integration tests; ≥80% coverage enforced by pre-push hook |
| Observability | ✅ Pass | `SummarizationAttempt` entity captures attempt outcomes; structured logging in services |
| Performance | ✅ Pass | API endpoints read from DB only (no external calls on GET); p95 ≤200ms achievable |
| Security | ✅ Pass | API key in `@ConfigurationProperties` only, never in VCS; endpoints are public read-only (per spec FR-015) |
| Accessibility | ✅ N/A | Backend-only feature; no UI |
| Documentation | ⚠️ Requires ADR | Claude API integration is a significant architectural decision — ADR required before merge |

**ADR Required**: `docs/adr/0002-use-claude-api-for-council-meeting-summarization.md`

---

## Project Structure

### Documentation (this feature)

```text
specs/004-automatically-summarize-the/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
├── contracts/
│   └── openapi.yaml     ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit.tasks — not yet created)
```

### Source Code

```text
backend/src/main/kotlin/com/mobilispect/backend/councilmeeting/
├── CouncilmeetingModule.kt                  # @ApplicationModule declaration
├── api/
│   ├── package-info.java                    # Public API surface
│   ├── CouncilBodyDTO.kt
│   ├── MeetingSummaryDTO.kt
│   └── SummaryListResponse.kt
├── controller/
│   └── CouncilMeetingSummaryController.kt   # GET /api/council-bodies/**
├── data/
│   ├── package-info.java                    # @NamedInterface("internal")
│   ├── entity/
│   │   ├── CouncilBodyEntity.kt
│   │   ├── CouncilMeetingEntity.kt
│   │   ├── MeetingSummaryEntity.kt
│   │   └── SummarizationAttemptEntity.kt
│   └── repository/
│       ├── CouncilBodyJpaRepository.kt
│       ├── CouncilMeetingJpaRepository.kt
│       ├── MeetingSummaryJpaRepository.kt
│       └── SummarizationAttemptJpaRepository.kt
├── domain/
│   ├── package-info.java                    # @NamedInterface("internal")
│   ├── model/
│   │   ├── CouncilBody.kt
│   │   ├── CouncilMeeting.kt
│   │   ├── MeetingSummary.kt
│   │   └── IngestionStatus.kt
│   └── repository/
│       ├── CouncilBodyRepository.kt
│       ├── CouncilMeetingRepository.kt
│       └── MeetingSummaryRepository.kt
├── internal/
│   ├── package-info.java                    # @NamedInterface("internal")
│   ├── scraper/
│   │   ├── MontrealDocumentScraper.kt       # HTTP HEAD probing + PDF download
│   │   └── CouncilBodyRegistry.kt           # Hardcoded URL patterns per borough code
│   ├── extractor/
│   │   └── PdfTextExtractor.kt              # PDFBox wrapper
│   └── summarizer/
│       ├── ClaudeApiSummarizer.kt           # Anthropic SDK call + response parsing
│       └── AnthropicProperties.kt           # @ConfigurationProperties
└── service/
    ├── CouncilMeetingDiscoveryService.kt    # Orchestrates scraping + summarization
    ├── CouncilMeetingSummaryQueryService.kt # Read-side for REST endpoints
    └── CouncilMeetingScheduler.kt          # @Scheduled daily job

backend/src/test/kotlin/com/mobilispect/backend/councilmeeting/
├── controller/
│   └── CouncilMeetingSummaryControllerTest.kt
├── service/
│   ├── CouncilMeetingDiscoveryServiceTest.kt
│   └── CouncilMeetingSummaryQueryServiceTest.kt
├── internal/
│   ├── scraper/
│   │   ├── MontrealDocumentScraperTest.kt
│   │   └── CouncilBodyRegistryTest.kt
│   ├── extractor/
│   │   └── PdfTextExtractorTest.kt
│   └── summarizer/
│       └── ClaudeApiSummarizerTest.kt
└── CouncilmeetingModuleTest.kt             # Spring Modulith boundary verification

backend/src/integrationTest/kotlin/com/mobilispect/backend/councilmeeting/
└── CouncilMeetingDiscoveryIntegrationTest.kt  # Full pipeline with Testcontainers

backend/src/main/resources/db/migration/
├── V064__create_council_meeting_tables.sql
└── V065__seed_council_bodies.sql

docs/adr/
└── 0002-use-claude-api-for-council-meeting-summarization.md
```

---

## Implementation Phases

### Phase 1: Persistence Layer (TDD)

1. Write `CouncilmeetingModuleTest` (Spring Modulith boundary test) — fails first.
2. Create module skeleton: `CouncilmeetingModule.kt`, package-info files for `data`, `domain`, `internal`.
3. Write Flyway migration `V064__create_council_meeting_tables.sql` (council_body, council_meeting, meeting_summary, summarization_attempt tables + indexes).
4. Write Flyway migration `V065__seed_council_bodies.sql` (20 council bodies).
5. Write entity classes and JPA repositories (test with Testcontainers).
6. Write domain model classes and repository interfaces.

### Phase 2: Document Scraper (TDD)

1. Write `CouncilBodyRegistryTest` — verify URL construction for each borough code.
2. Implement `CouncilBodyRegistry` — URL pattern logic for all 20 council bodies.
3. Write `MontrealDocumentScraperTest` using WireMock.
4. Implement `MontrealDocumentScraper`:
   - HTTP HEAD probing for candidate URLs (date + time-slot combinations for last 45 days)
   - PDF download on hit
   - Resilience4j retry on transient failures (3 retries, exponential backoff)
5. Write `PdfTextExtractorTest` with a real sample PDF.
6. Implement `PdfTextExtractor` (PDFBox).

### Phase 3: Summarization (TDD)

1. Write `ClaudeApiSummarizerTest` using WireMock to stub Claude API.
2. Implement `AnthropicProperties` (`@ConfigurationProperties(prefix = "app.anthropic")`).
3. Implement `ClaudeApiSummarizer`:
   - Construct message with cached system prompt (1h TTL)
   - Call `claude-sonnet-4-6`
   - Parse FR and EN sections from response
   - Return `Pair<MeetingSummary, MeetingSummary>`

### Phase 4: Orchestration & Scheduling (TDD)

1. Write `CouncilMeetingDiscoveryServiceTest`.
2. Implement `CouncilMeetingDiscoveryService`:
   - For each active `CouncilBody`: scrape → extract → summarize → persist
   - Skip if `CouncilMeeting` already has `SUMMARIZED` status (FR-008)
   - Persist `SummarizationAttempt` on each attempt (FR-009)
3. Implement `CouncilMeetingScheduler` (`@Scheduled`, `@ConditionalOnProperty`).

### Phase 5: REST API (TDD)

1. Write `CouncilMeetingSummaryControllerTest`.
2. Implement `CouncilMeetingSummaryQueryService` (read-side, `@Transactional(readOnly=true)`).
3. Implement `CouncilMeetingSummaryController`:
   - `GET /api/council-bodies`
   - `GET /api/council-bodies/{code}/summaries/latest?language=FR|EN`
   - `GET /api/council-bodies/{code}/summaries?language=&from=&to=&page=&size=`

### Phase 6: ADR + Coverage + Pre-push

1. Write `docs/adr/0002-use-claude-api-for-council-meeting-summarization.md`.
2. Run `./scripts/validate-coverage.sh backend` — achieve ≥80%.
3. Run `./backend/gradlew detekt` — resolve any violations.
4. Run `./backend/gradlew verifyModulith` — confirm no boundary violations.
5. Run `./backend/gradlew -p backend test -x integrationTest` — all green.

---

## Complexity Tracking

No constitution violations. The `councilmeeting` module is a clean addition with no cross-module dependencies and follows all established patterns.

---

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| PDF extraction | PDFBox 3.x | Apache license, no native deps, sufficient for digitally-produced PDFs |
| Claude SDK | `anthropic-java-client-okhttp` | Official SDK, idiomatic Kotlin, OkHttp backend |
| Bilingual output | Single prompt, two sections | Lower cost, consistent translation decisions |
| Prompt caching | System prompt, 1h TTL | 90% cost reduction on cache hits for batch of 20 bodies |
| Document discovery | URL probing (HEAD) | No stable index/RSS feed available; direct URL pattern is reliable |
| Module dependencies | None | Standalone capability; maximises extractability |
| Scheduling | Spring `@Scheduled` | Already configured; no external dependency needed |
