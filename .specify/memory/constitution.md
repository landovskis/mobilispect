<!--
Sync Impact Report:
Version: 1.10.0 → 1.11.0 (Testcontainers enforced for integration testing)
Modified sections: Testing Standards (stateful integration tests via Testcontainers), Quality Gates (integration environment parity)
Added sections: None
Removed sections: None
Templates requiring updates:
  ✅ .specify/templates/plan-template.md (Constitution Check gate for Testcontainers integration tests)
  ✅ README.md (Backend integration tests note on Testcontainers)
Follow-up TODOs:
  - None
-->

# Mobilispect Constitution

## Core Principles

### I. Code Quality First
All code MUST pass automated quality gates before merge. Every component requires linting, formatting, and static analysis. Technical debt MUST be documented with clear remediation plans. Code reviews are mandatory for all changes with at least one approval required. Code MUST follow DRY (Don't Repeat Yourself), YAGNI (You Aren't Gonna Need It), and SOLID principles. Value classes MUST be used for all entity IDs to ensure type safety and prevent ID mixups across domain boundaries.

**Rationale**: Multi-platform development amplifies quality issues across all platforms. Consistent quality standards prevent platform-specific bugs from propagating. DRY/YAGNI/SOLID principles ensure maintainable, extensible code across all platforms. Value classes prevent runtime errors from ID confusion (e.g., using AgencyId where RouteId expected).

### II. Test-Driven Development (NON-NEGOTIABLE)
Tests MUST be written before implementation. Every feature requires unit tests, integration tests, and platform-specific contract tests. Test coverage MUST exceed 80% for all new code. All tests MUST pass before deployment.

**Rationale**: With Spring backend, Angular frontend, and Android/iOS apps, untested changes create cascading failures across the entire system.

### III. Cross-Platform UX Consistency
User experience MUST be consistent across all platforms while respecting platform conventions. Design systems MUST define shared components, typography, and interaction patterns. Platform-specific implementations MUST maintain functional parity. Light/dark mode support is mandatory across all platforms. All user-facing experiences MUST comply with WCAG 2.1 AA accessibility guidelines; each feature plan MUST document accessibility acceptance criteria, and releases MUST include automated (e.g., axe, Lighthouse) and manual assistive-technology checks for critical flows. Accessibility regressions block deployment.

**Rationale**: Users expect consistent behavior across platforms. Divergent experiences create confusion and support burden. Accessibility parity is foundational to product trust and is legally mandated in several jurisdictions.

### IV. Performance Standards
Backend APIs MUST respond within 200ms p95. Mobile apps MUST maintain 60fps during interactions. Database queries MUST use indexes and avoid N+1 patterns. Performance regressions block deployment.

**Rationale**: Mobile users have zero tolerance for performance issues. API latency directly impacts user experience across all client platforms.

### V. Observability & Monitoring
All services MUST emit structured logs, metrics, and traces using Grafana Cloud as the centralized observability platform. Error tracking is mandatory with alert thresholds. Performance monitoring covers all user journeys. Deployment health checks are required. Dashboards MUST be created for all critical business metrics and system health indicators.

**Rationale**: Distributed systems require comprehensive observability to diagnose issues across platform boundaries. Grafana Cloud provides unified monitoring, alerting, and visualization across all platforms with minimal operational overhead.

### VI. Architecture Decision Records (NON-NEGOTIABLE)
All significant technical decisions MUST be documented as Architecture Decision Records (ADRs). ADRs are required for technology choices, design patterns, architectural changes, and trade-offs. Each ADR MUST include context, decision, consequences, and alternatives considered.

**Rationale**: Complex multi-platform systems require documented decision history to prevent repeated debates, ensure knowledge transfer, and provide context for future changes.

### VII. Modular Monolith Architecture (NON-NEGOTIABLE)
The backend MUST be structured as a modular monolith using Spring Modulith. Domain modules MUST own their data, business logic, and public interfaces. Cross-module communication MUST occur only through well-defined module APIs (events, application services, or explicit dependencies). Direct database access across module boundaries is PROHIBITED. Module boundaries MUST be verified through Spring Modulith's runtime verification. Service extraction from the monolith requires an ADR documenting the extraction rationale, migration plan, and impact analysis.

**Module Ownership Rules**:
- Each module MUST have a clearly defined bounded context
- Modules MUST expose only public APIs (services, events, DTOs)
- Internal implementation details (entities, repositories, domain logic) MUST remain private
- Module dependencies MUST be acyclic and explicitly declared
- Shared kernel concepts MUST be minimal and documented

**Spring Modulith Requirements**:
- Module structure MUST follow Spring Modulith conventions (package-based modules)
- Module boundaries MUST be documented using `@Modulith` annotations
- Integration tests MUST use `@ModuleTest` to verify module isolation
- Application events MUST be used for asynchronous cross-module communication
- Module documentation MUST be auto-generated using Spring Modulith's documentation features

**Rationale**: Modular monoliths provide the development velocity and operational simplicity of monoliths while enforcing architectural boundaries that prevent the "big ball of mud" anti-pattern. Spring Modulith provides runtime verification of module boundaries, event-driven communication patterns, and tooling for eventual service extraction. This architecture supports the team's current size while maintaining a clear path to microservices if needed.

## Cross-Platform Standards

### Technology Stack
- **Backend**: Spring Boot with Kotlin 2.0+, PostgreSQL 17, Redis 8.2, Spring Modulith for modular architecture
- **Frontend**: Angular 19 LTS with TypeScript, RxJS for state management
- **Mobile**: Kotlin Multiplatform Mobile (KMM) with shared business logic
- **Android**: Compose UI with Material Design 3
- **iOS**: SwiftUI with iOS Design Guidelines
- **CI/CD**: GitHub Actions for all automation, testing, and deployment pipelines
- **Observability**: Grafana Cloud for monitoring, alerting, and visualization
- **E2E Testing**: Playwright for cross-browser end-to-end testing
- **Accessibility**: Every feature MUST define WCAG 2.1 AA acceptance criteria, include automated
  accessibility scans in CI, and record manual assistive technology walkthroughs for high-impact flows.

### Testing Standards
All features MUST include comprehensive test coverage across unit, integration, and end-to-end levels. Database-dependent tests MUST execute against PostgreSQL 17 locally and in CI to guarantee compatibility with production storage. Cache-dependent tests MUST execute against Redis 8.2 in development and CI environments.

**Integration Testing with Testcontainers**:
- Stateful integration tests (PostgreSQL, Redis, and any future stateful dependencies) MUST run via Testcontainers with images pinned to production versions.
- Local development and CI MUST share the same container definitions to prevent environment drift.
- Spring Boot integration tests MUST use `spring-boot-testcontainers`/`@Testcontainers` with per-suite lifecycle to avoid shared state between test classes.

**Module Testing**:
- Each module MUST have its own test suite using `@ModuleTest`
- Module integration tests MUST verify module boundaries and contracts
- Cross-module integration tests MUST use published events and public APIs only
- Module tests MUST be independently executable without requiring the full application context

**End-to-End Testing with Playwright**:
- Playwright MUST be used for all cross-browser E2E tests
- Test coverage MUST include Chrome, Firefox, and Safari (WebKit)
- Tests MUST verify complete user journeys from UI interaction to backend data persistence
- Parallel execution MUST be enabled for fast feedback
- Auto-waiting for elements is mandatory (no manual timeouts)
- Visual regression testing SHOULD be included for critical UI flows
- E2E tests MUST run in CI/CD before deployment to staging/production

**Rationale**: Playwright provides modern cross-browser testing with excellent TypeScript support, aligning with Angular frontend technology. Auto-waiting and parallel execution reduce flaky tests and speed up CI/CD pipelines. Multi-browser support ensures consistent behavior across all supported platforms.

### API Contracts
All backend APIs MUST follow OpenAPI 3.0 specification. Contract testing is mandatory between all services. Breaking changes require version increments and deprecation notices.

### Security Requirements
Authentication via OAuth 2.0/OIDC. All data transmission MUST use TLS 1.3+. Client certificates required for production APIs. Regular security audits are mandatory.

### Architecture Decision Records
ADRs MUST be stored in `docs/adr/` directory using numbered format (e.g., `0001-use-kotlin-for-backend.md`). Template MUST include: Title, Status, Context, Decision, Consequences, Alternatives. All ADRs require team review before acceptance.

**Modular Monolith ADRs**:
- Module boundary decisions MUST be documented with ADRs
- Cross-module dependency introductions MUST be documented with ADRs
- Service extraction decisions MUST include migration plans and impact analysis

### Documentation Standards
All architectural diagrams MUST use PlantUML with C4 model notation for consistency and version control compatibility. Diagrams MUST be stored as `.puml` files alongside their rendered outputs in `docs/architecture/`.

**C4 Model Requirements**:
- **Context Diagrams** (Level 1): Show system boundaries and external actors/systems
- **Container Diagrams** (Level 2): Show high-level technology choices and communication patterns
- **Component Diagrams** (Level 3): Show internal structure of containers and module boundaries
- **Code Diagrams** (Level 4): Use when critical implementation details need visualization

All major features MUST include at minimum a Container diagram (C4 Level 2). Complex features MUST include Component diagrams (C4 Level 3) for critical subsystems. Sequence diagrams and entity relationship diagrams are required for data flows and persistence layers respectively. Module interaction diagrams are required for cross-module features.

**Rationale**: C4 model provides a standardized hierarchy for architectural documentation, ensuring consistent abstraction levels across all documentation. PlantUML enables version control, diff tracking, and automated diagram generation in CI/CD pipelines.

### CI/CD Standards
All automation MUST use GitHub Actions workflows. Separate workflows are required for each platform (backend, frontend, mobile). Matrix builds MUST cover all supported platform versions. Deployment pipelines MUST include staging validation before production. All workflows MUST integrate with Grafana Cloud for build and deployment metrics.

**Module Verification in CI**:
- CI pipelines MUST execute Spring Modulith's module structure verification
- Module dependency violations MUST fail the build
- Module documentation MUST be generated and published as part of the build

## Quality Gates

### Pre-Commit Gates
- [ ] All tests pass locally
- [ ] Code formatting applied (Prettier, ktlint, SwiftFormat)
- [ ] Linting violations resolved (ESLint, ktlint, SwiftLint)
- [ ] Security scan passes (SonarQube, OWASP)
- [ ] Module structure verification passes (Spring Modulith)
- [ ] Stateful integration tests use Testcontainers (PostgreSQL 17, Redis 8.2) with no reliance on host services

### Pre-Merge Gates
- [ ] Code review approved by platform expert
- [ ] GitHub Actions CI/CD pipeline passes completely
- [ ] Performance tests show no regressions
- [ ] Contract tests verify API compatibility
- [ ] Module boundaries verified (no circular dependencies, proper encapsulation)
- [ ] CI executes Testcontainers-backed integration suites for all stateful components

### Pre-Deploy Gates
- [ ] End-to-end tests pass in staging (Playwright multi-browser)
- [ ] Load testing confirms performance targets
- [ ] Security scan shows no critical issues
- [ ] Database migration validated
- [ ] Module integration tests pass

## Governance

### Version Management
- **Semantic Versioning**: All components follow MAJOR.MINOR.PATCH format
- **Breaking Changes**: MAJOR version increments require migration documentation
- **Changelog**: All changes documented in CHANGELOG.md following Keep a Changelog format
- **Release Coordination**: Multi-platform releases must maintain version synchronization

### Amendment Process
- Constitution changes require version bump and changelog entry
- All PRs/reviews must verify compliance with versioning requirements
- Release approval requires changelog review and version validation

**Authority**: This constitution supersedes all other development practices and coding standards. Violations require explicit justification and team approval.

**Amendment Process**: Constitution changes require documentation, team consensus, and migration plan for existing code. All amendments must increment version number.

**Compliance**: All pull requests MUST verify constitutional compliance. Code reviews MUST validate adherence to principles. Complexity that violates principles requires architectural justification.

**Exception Handling**: Principle violations require explicit documentation in code comments and Architecture Decision Records (ADRs). Emergency exceptions require immediate follow-up remediation.

**ADR Requirements**: All architectural changes, technology selections, and design pattern choices MUST be documented as ADRs before implementation. ADRs are living documents that MUST be updated when decisions change.

**Version**: 1.11.0 | **Ratified**: 2025-10-07 | **Last Amended**: 2025-11-29
