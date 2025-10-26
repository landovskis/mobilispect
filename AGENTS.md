# Repository Guidelines

## Project Structure & Module Organization

- `backend`: Spring Boot service in Kotlin; code in `src/main/kotlin`, tests in
  `src/test/kotlin`, configs in `config/`, and seed data in `data/`.
- `frontend/mobile`: Kotlin Multiplatform app; shared logic in
  `shared/src/<platform>`, Android UI in `androidApp`, iOS scaffold in `iosApp`,
  Room schemas in `shared/schemas`.
- `frontend/web`: Angular workspace; feature modules in `src/app`, shared
  utilities in `src/app/core`.
- Supporting assets: `docs` for architecture and ADRs, `scripts` for automation
  gates, and `specs` for constitutional product definitions.

## Build, Test, and Development Commands

- `cd backend && ./gradlew build` – compile the backend and verify dependency
  wiring.
- `cd backend && ./gradlew test jacocoTestReport ktlintCheck detekt` – run JUnit
  tests, produce coverage, and enforce lint plus static analysis.
- `cd frontend/mobile && ./gradlew shared:assembleDebug shared:testDebugUnitTest`
  – build KMM artifacts and run JVM tests; add
  `shared:koverXmlReport` when coverage is required.
- `cd frontend/web && npm install && npm run start` – launch the Angular dev
  server; swap `start` with `test` or `build` for Karma runs or production
  bundles.
- `./scripts/pre-merge-gates.sh` – run the full constitutional gate suite
  (coverage, security, backend/mobile quality) before opening a PR.

## Coding Style & Naming Conventions

- Kotlin uses ktlint defaults (4-space indent, trailing commas) and Detekt rules
  from `backend/config/detekt/detekt.yml`; packages stay under
  `com.mobilispect.<feature>`.
- KMM modules follow `commonMain`, `androidMain`, `iosMain`; name suspend
  services `<Feature>Service` and DTOs `<Feature>Dto`.
- Angular uses 2-space indent, `kebab-case` filenames such as
  `feed-management.component.ts`, and `PascalCase` class symbols.

## Testing Guidelines

- Backend tests live in `src/test/kotlin`; use JUnit 5 with Testcontainers for
  data-intensive flows.
- Mobile tests belong in `shared/src/commonTest` plus `androidTest` or `iosTest`;
  rely on Ktor HTTP mocks and Truth assertions.
- Web unit tests remain as `*.spec.ts`; mock HTTP via Angular
  `HttpTestingController`.
- `scripts/validate-coverage.sh` enforces ≥80% coverage per component; review
  JaCoCo and Kover artifacts before merge.

## Commit & Pull Request Guidelines

- Follow conventional commits (`feat:`, `fix:`, `chore:`, `docs:`) as shown in
  history, keeping each change focused.
- PRs link issues, note architectural impacts, attach UI screenshots, and call
  out any manual steps.
- Share key outputs from `./scripts/pre-merge-gates.sh` in the PR description to
  streamline review.

## Quality & Security

- Run `./scripts/security-scan.sh` before tagging releases or promoting builds.
- Keep secrets out of VCS; store them in Spring profiles (`application-*.yml`)
  and Android Gradle local properties.
