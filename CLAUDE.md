# Claude Code Configuration

## Project Constitution

This project follows the Mobilispect Constitution which defines core principles,
standards, and governance for development (v2.2.0).

**Constitution Location**: `.specify/memory/constitution.md`

### Key Constitutional Requirements

Before working on any feature or making architectural decisions, review the full
constitution. Core principles:

1. **Modular Monolith Ownership** — Spring Modulith boundaries; no cross-module
   DB access; ports/events only; extraction requires ADR + migration plan.
2. **Test-Driven Quality (NON-NEGOTIABLE)** — Tests first, fail first;
   unit/contract/integration (Testcontainers) and E2E; ≥80% coverage per
   component via `scripts/validate-coverage.sh`; defects add regression tests.
3. **Observability & Operational Insight** — Structured logs, metrics, traces;
   dashboards/alerts for ingestion and API journeys; trace errors to user paths.
4. **Performance & Reliability Targets** — API p95 ≤200ms, ingestion SLAs,
   60fps UX; load/stress tests; graceful degradation (backpressure, retries with
   jitter, circuit breakers).
5. **Security & Compliance by Default** — Secrets outside VCS; OWASP dependency
   checks; authn/authz and audit logging on sensitive paths; encrypted
   transit/at-rest.
6. **Accessibility & UX Parity** — WCAG 2.1 AA with automated + manual checks;
   light/dark parity across Android/iOS/web with Playwright coverage
   (Chromium/Firefox/WebKit).
7. **Documentation & Traceability** — ADRs for significant decisions; spec →
   plan → tasks chain records assumptions/risks/NFRs; release notes include
   coverage, security, performance, accessibility, observability results.

### Quality Gates

All changes must pass:

- **Pre-Commit**: Formatting applied, linting clean, secret detection
- **Pre-Push**: Tests pass, static analysis, module boundaries, coverage ≥80%, security scan
- **Pre-Merge**: Code review approved, CI/CD passes, performance tests,
  contract tests
- **Pre-Deploy**: E2E tests pass, load testing, security scan,
  DB migration validated

### Git Hook Enforcement

Constitutional quality gates are enforced via **Husky** git hooks.
Checks are divided between pre-commit (fast) and pre-push (thorough).

#### Git Hooks Setup

```bash
# Install Node.js dependencies and initialize Husky hooks
npm install
```

#### Hook Responsibilities

| Stage | Checks | Speed |
|-------|--------|-------|
| **pre-commit** | File hygiene, secret detection, formatting, linting, markdown lint | ~30s |
| **pre-push** | Unit tests, static analysis, module boundaries, coverage (≥80%), security scan | Minutes |

#### pre-commit checks (fast feedback)

- Merge conflict markers, private key detection, large files (>1MB)
- `detect-secrets` baseline scan
- Backend Kotlin: `ktfmtFormat` + `ktlintCheck`
- Web: `prettier --check` + `ng lint`
- Markdown: `markdownlint`

#### pre-push checks (thorough validation)

- Backend: unit tests (`test -x integrationTest`)
- Backend: `detekt` static analysis
- Backend: `verifyModulith` module boundary verification
- Web: `vitest` unit tests
- Coverage validation ≥80% (`scripts/validate-coverage.sh`)
- Security scan / OWASP dependency check (`scripts/security-scan.sh`)

#### Enforcement Mechanism

- Hooks **BLOCK** commits/pushes that fail constitutional requirements
- No bypass allowed without explicit ADR documenting exception
- Failed hooks display specific remediation commands

See platform-specific files for detailed command reference.

### ADR Requirements

All architectural decisions must be documented in `docs/adr/` with format:

- `NNNN-decision-title.md` (e.g., `0001-use-kotlin-for-backend.md`)
- Required sections: Title, Status, Context, Decision, Consequences, Alternatives
- Team review required before acceptance

### Commands Available

This project includes Spec-Kit commands for structured development:

- `/speckit.constitution` - Update project constitution
- `/speckit.specify` - Create feature specifications
- `/speckit.plan` - Generate implementation plans
- `/speckit.tasks` - Break down features into tasks
- `/speckit.checklist` - Generate quality checklists
- `/speckit.analyze` - Cross-artifact consistency analysis
- `/speckit.clarify` - Identify spec ambiguities
- `/speckit.implement` - Execute implementation tasks

### Enforcement

- Constitution violations require explicit justification and team approval
- All PRs must verify constitutional compliance
- Emergency exceptions require immediate follow-up remediation
- ADRs are living documents and must be updated when decisions change

---

## Platform-Specific Configuration

@.claude/backend.md
@.claude/frontend-web.md
@.claude/frontend-mobile.md

---

**Note**: This configuration ensures all Claude Code sessions maintain
consistency with project standards and architectural decisions. Always
reference the full constitution at `.specify/memory/constitution.md` for
complete guidance.
