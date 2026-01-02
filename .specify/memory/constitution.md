<!--
Sync Impact Report
- Version change: 2.2.0 -> 2.3.0
- Modified principles: Accessibility & UX Parity -> Accessibility & UX Parity (adaptive layouts added)
- Added sections: UI & Frontend Standards; Development Workflow & Quality Gates
- Removed sections: None
- Templates requiring updates:
  - ✅ .specify/templates/plan-template.md
  - ✅ .specify/templates/spec-template.md
  - ✅ .specify/templates/tasks-template.md
  - ⚠ .specify/templates/commands/*.md (directory not found)
- Follow-up TODOs: TODO(RATIFICATION_DATE) (original adoption date not found)
-->
# Mobilispect Constitution

## Core Principles

### Principle I: Modular Monolith Ownership
Spring Modulith boundaries are mandatory. Modules own their data and MAY NOT
perform cross-module database access. Communication must flow through module
ports and published events only.

### Principle II: Test-Driven Quality (NON-NEGOTIABLE)
Tests must be written first, fail first, then implementation follows. Each
component must maintain at least 80% coverage. Contract and integration tests
are required for data-intensive flows using Testcontainers.

### Principle III: Observability
All new flows must emit structured logs, metrics, and traces with dashboards
and alerts updated to match the new behavior. Observability is required before
feature completion.

### Principle IV: Performance & Reliability
APIs must meet p95 <= 200ms, ingestion flows must meet defined SLAs, and UX
must sustain 60fps with graceful degradation when limits are exceeded.

### Principle V: Security by Default
Secrets must stay out of VCS and live in environment-specific configuration.
OWASP dependency checks, authentication/authorization, and audit logging are
mandatory for sensitive paths.

### Principle VI: Accessibility & UX Parity
Meet WCAG 2.1 AA and provide light/dark parity across Android, iOS, and web
(Chromium/Firefox/WebKit). All frontends must ship adaptive layouts for
mobile, tablet, and desktop viewports.

### Principle VII: Documentation & Traceability
Significant decisions require ADRs. Work must follow the spec -> plan -> tasks
chain with explicit assumptions recorded.

## UI & Frontend Standards

- Use Tailwind CSS for layout and spacing, and Angular Material for UI
  controls.
- Avoid overriding Angular Material component internals with Tailwind
  utilities.
- Implement responsive layouts across mobile, tablet, and desktop breakpoints
  with functional parity and accessibility preserved.

## Development Workflow & Quality Gates

- Run `./scripts/pre-merge-gates.sh` before opening a PR.
- Backend: `cd backend && ./gradlew test jacocoTestReport ktlintCheck detekt`.
- Mobile: `cd frontend/mobile && ./gradlew shared:assembleDebug shared:testDebugUnitTest`.
- Web: `cd frontend/web && npm install && npm run test`.
- Run `./scripts/security-scan.sh` before tagging releases or promoting builds.

## Governance

- This constitution supersedes other guidance. Exceptions require an ADR and a
  plan to return to compliance.
- Amendments must update this document, record rationale, and follow semantic
  versioning (MAJOR for breaking governance changes, MINOR for new requirements,
  PATCH for clarifications).
- Plans and reviews must explicitly verify constitutional compliance; any
  violations must be documented with mitigations.

**Version**: 2.3.0 | **Ratified**: TODO(RATIFICATION_DATE): original adoption
unknown | **Last Amended**: 2026-01-02
