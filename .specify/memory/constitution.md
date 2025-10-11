<!--
Sync Impact Report:
Version: 1.5.0 (Consolidated Constitution Updates v1.3.0-1.5.0)
Added in v1.3.0: Version Management and Governance requirements
Added in v1.4.0: Value classes for entity IDs, PlantUML for architectural diagrams
Added in v1.5.0: Grafana Cloud for observability, GitHub Actions for CI/CD
Modified sections: Governance (versioning), Code Quality First (value classes), Observability & Monitoring (Grafana Cloud), Technology Stack (CI/CD and observability), Quality Gates (GitHub Actions), CI/CD Standards (new section)
Templates requiring updates: ✅ All templates align with constitution structure
Follow-up TODOs: Configure Grafana Cloud integration, migrate existing CI/CD to GitHub Actions, update ID implementations to use value classes
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
User experience MUST be consistent across all platforms while respecting platform conventions. Design systems MUST define shared components, typography, and interaction patterns. Platform-specific implementations MUST maintain functional parity. Light/dark mode support is mandatory across all platforms.

**Rationale**: Users expect consistent behavior across platforms. Divergent experiences create confusion and support burden.

### IV. Performance Standards
Backend APIs MUST respond within 200ms p95. Mobile apps MUST maintain 60fps during interactions. Database queries MUST use indexes and avoid N+1 patterns. Performance regressions block deployment.

**Rationale**: Mobile users have zero tolerance for performance issues. API latency directly impacts user experience across all client platforms.

### V. Observability & Monitoring
All services MUST emit structured logs, metrics, and traces using Grafana Cloud as the centralized observability platform. Error tracking is mandatory with alert thresholds. Performance monitoring covers all user journeys. Deployment health checks are required. Dashboards MUST be created for all critical business metrics and system health indicators.

**Rationale**: Distributed systems require comprehensive observability to diagnose issues across platform boundaries. Grafana Cloud provides unified monitoring, alerting, and visualization across all platforms with minimal operational overhead.

### VI. Architecture Decision Records (NON-NEGOTIABLE)
All significant technical decisions MUST be documented as Architecture Decision Records (ADRs). ADRs are required for technology choices, design patterns, architectural changes, and trade-offs. Each ADR MUST include context, decision, consequences, and alternatives considered.

**Rationale**: Complex multi-platform systems require documented decision history to prevent repeated debates, ensure knowledge transfer, and provide context for future changes.

## Cross-Platform Standards

### Technology Stack
- **Backend**: Spring Boot with Kotlin 2.0+, PostgreSQL, Redis
- **Frontend**: Angular 19 LTS with TypeScript, RxJS for state management
- **Mobile**: Kotlin Multiplatform Mobile (KMM) with shared business logic
- **Android**: Compose UI with Material Design 3
- **iOS**: SwiftUI with iOS Design Guidelines
- **CI/CD**: GitHub Actions for all automation, testing, and deployment pipelines
- **Observability**: Grafana Cloud for monitoring, alerting, and visualization

### API Contracts
All backend APIs MUST follow OpenAPI 3.0 specification. Contract testing is mandatory between all services. Breaking changes require version increments and deprecation notices.

### Security Requirements
Authentication via OAuth 2.0/OIDC. All data transmission MUST use TLS 1.3+. Client certificates required for production APIs. Regular security audits are mandatory.

### Architecture Decision Records
ADRs MUST be stored in `docs/adr/` directory using numbered format (e.g., `0001-use-kotlin-for-backend.md`). Template MUST include: Title, Status, Context, Decision, Consequences, Alternatives. All ADRs require team review before acceptance.

### Documentation Standards
All architectural diagrams MUST use PlantUML for consistency and version control compatibility. Diagrams MUST be stored as `.puml` files alongside their rendered outputs. Entity relationship diagrams, sequence diagrams, and component diagrams are required for all major features.

### CI/CD Standards
All automation MUST use GitHub Actions workflows. Separate workflows are required for each platform (backend, frontend, mobile). Matrix builds MUST cover all supported platform versions. Deployment pipelines MUST include staging validation before production. All workflows MUST integrate with Grafana Cloud for build and deployment metrics.
## Quality Gates

### Pre-Commit Gates
- [ ] All tests pass locally
- [ ] Code formatting applied (Prettier, ktlint, SwiftFormat)
- [ ] Linting violations resolved (ESLint, ktlint, SwiftLint)
- [ ] Security scan passes (SonarQube, OWASP)

### Pre-Merge Gates
- [ ] Code review approved by platform expert
- [ ] GitHub Actions CI/CD pipeline passes completely
- [ ] Performance tests show no regressions
- [ ] Contract tests verify API compatibility

### Pre-Deploy Gates
- [ ] End-to-end tests pass in staging
- [ ] Load testing confirms performance targets
- [ ] Security scan shows no critical issues
- [ ] Database migration validated

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

**Version**: 1.5.0 | **Ratified**: 2025-10-07 | **Last Amended**: 2025-10-11
