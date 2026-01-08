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

- **Pre-Commit**: Tests pass, formatting applied, linting clean, security scan
- **Pre-Merge**: Code review approved, CI/CD passes, performance tests,
  contract tests
- **Pre-Deploy**: E2E tests pass, load testing, security scan,
  DB migration validated

### Pre-Commit Hook Enforcement

Constitutional pre-commit checks are enforced via:

#### Git Hooks Setup

```bash
# Install pre-commit framework
pip install pre-commit

# Initialize hooks from .pre-commit-config.yaml
pre-commit install
```

#### Enforcement Mechanism

- Hooks **BLOCK** commits that fail constitutional requirements
- No bypass allowed without explicit ADR documenting exception
- Failed hooks display specific remediation commands
- Developers can run `pre-commit run --all-files` for full validation

See platform-specific files for detailed hook configuration.

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

### Development Workflow

1. **Feature Planning**: Use `/speckit.specify` → `/speckit.plan` → `/speckit.tasks`
2. **Quality Assurance**: Use `/speckit.checklist` and `/speckit.analyze`
3. **Implementation**: Follow TDD, maintain 80%+ test coverage
4. **Documentation**: Create ADRs for all architectural decisions
5. **Review**: Ensure constitutional compliance in all code reviews

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
