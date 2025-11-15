# Claude Code Configuration

## Project Constitution

This project follows the Mobilispect Constitution which defines core
principles, standards, and governance for development.

**Constitution Location**: `.specify/memory/constitution.md`

### Key Constitutional Requirements

Before working on any feature or making architectural decisions, review the
full constitution. Key principles include:

1. **Code Quality First** - DRY, YAGNI, SOLID principles mandatory
2. **Test-Driven Development** - NON-NEGOTIABLE, 80%+ coverage required
3. **Cross-Platform UX Consistency** - Light/dark mode mandatory across all platforms
4. **Performance Standards** - 200ms API response, 60fps mobile UI
5. **Observability & Monitoring** - Structured logging, metrics, traces required
6. **Architecture Decision Records** - NON-NEGOTIABLE for all significant
   technical decisions
7. **Accessibility & WCAG** - Every user-facing change MUST satisfy WCAG 2.1 AA
   (automated scans + manual assistive-technology walkthroughs) with evidence in
   feature plans and release notes

### Technology Stack (Constitutional)

- **Backend**: Spring Boot with Kotlin 2.0+, PostgreSQL 17, Redis 8.2
- **Frontend**: Angular 19 LTS with TypeScript, RxJS for state management
- **Mobile**: Kotlin Multiplatform Mobile (KMM) with shared business logic
- **Android**: Compose UI with Material Design 3
- **iOS**: SwiftUI with iOS Design Guidelines

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

#### Multi-Platform Hook Configuration (`.pre-commit-config.yaml`)

- **Kotlin Backend**: ktlint formatting, detekt static analysis, test execution
- **Angular Frontend**: Prettier formatting, ESLint linting, Jest tests, ng lint
- **Android**: ktlint, Android lint, unit tests
- **iOS**: SwiftFormat, SwiftLint, XCTest execution
- **Security**: OWASP dependency check, secret scanning
- **Cross-Platform**: Test coverage validation (80%+ threshold)

#### Enforcement Mechanism

- Hooks **BLOCK** commits that fail constitutional requirements
- No bypass allowed without explicit ADR documenting exception
- Failed hooks display specific remediation commands
- Developers can run `pre-commit run --all-files` for full validation

#### IDE Integration

- VS Code: Pre-commit extension with real-time validation
- IntelliJ/Android Studio: Pre-commit plugin configuration
- Xcode: Build phases for SwiftLint/SwiftFormat integration

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

**Note**: This configuration ensures all Claude Code sessions maintain
consistency with project standards and architectural decisions. Always
reference the full constitution at `.specify/memory/constitution.md` for
complete guidance.
