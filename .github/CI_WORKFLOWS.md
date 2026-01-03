# CI Workflows - Mobilispect

## Overview

The Mobilispect project uses separate, platform-specific CI workflows to
enable parallel execution and faster feedback cycles.

## Workflow Structure

### 1. Backend CI (`ci-backend.yml`)

**Triggers**: Changes to `backend/**`

**Jobs**:

- **Build** - Compile backend with Gradle
- **Tests** - Unit and integration tests with Testcontainers
- **Code Quality** - ktfmt, ktlint, detekt, Spring Modulith verification
- **Coverage** - JaCoCo coverage reports (80% minimum)
- **Security** - OWASP dependency check
- **SonarCloud** - Code quality analysis

**Runner**: `ubuntu-latest`
**Duration**: ~8-12 minutes

---

### 2. Angular CI (`ci-angular.yml`)

**Triggers**: Changes to `frontend/web/**`

**Jobs**:

- **Build** - Development and production builds
- **Tests** - Karma/Jasmine unit tests with coverage
- **E2E** - Playwright tests (Chromium, Firefox, WebKit)
- **Lint** - ESLint and Prettier checks
- **Accessibility** - WCAG 2.1 AA compliance checks
- **Security** - npm audit and dependency checks
- **Coverage** - Test coverage verification (80% minimum)
- **SonarCloud** - Code quality analysis

**Runner**: `ubuntu-latest`
**Duration**: ~6-10 minutes

---

### 3. Android CI (`ci-android.yml`)

**Triggers**: Changes to `frontend/mobile/**` (excluding iOS)

**Jobs**:

- **Build** - Debug and release APK builds
- **Shared Tests** - KMM shared module unit tests
- **Instrumented Tests** - Android emulator tests (API 33)
- **Lint** - Android lint, ktfmt, ktlint
- **Coverage** - JaCoCo coverage reports (80% minimum)
- **Security** - OWASP dependency check
- **SonarCloud** - Code quality analysis

**Runner**: `ubuntu-latest`
**Duration**: ~12-18 minutes

---

### 4. iOS CI (`ci-ios.yml`)

**Triggers**: Changes to `frontend/mobile/**`

**Jobs**:

- **Build** - Debug and release builds for iOS Simulator
- **Shared Tests** - KMM shared module iOS tests
- **Unit Tests** - XCTest unit tests
- **UI Tests** - XCUITest UI tests
- **Lint** - SwiftLint and SwiftFormat checks
- **Coverage** - Xcode coverage reports (80% minimum)
- **Security** - OWASP and CocoaPods dependency checks
- **Accessibility** - iOS accessibility audits

**Runner**: `macos-14`
**Duration**: ~15-20 minutes

---

## Workflow Triggers

All workflows trigger on:

- **Pull Requests**: To any branch when relevant files change
- **Push**: To `main` and `develop` branches
- **Manual**: Via `workflow_dispatch`

### Path Filters

Each workflow only runs when relevant files change:

```yaml
Backend:    backend/**
Angular:    frontend/web/**
Android:    frontend/mobile/** (excluding iosApp/)
iOS:        frontend/mobile/**
```

## Parallel Execution

Workflows run in parallel when changes affect multiple platforms:

**Example**: A PR touching both backend and frontend will run:

- `ci-backend.yml` (8-12 min)
- `ci-angular.yml` (6-10 min)

**Total Time**: ~12 minutes (parallel) vs ~18 minutes (sequential)

## Constitutional Requirements

All workflows enforce constitutional requirements:

### Test-Driven Quality (NON-NEGOTIABLE)

- ✅ Unit tests run on every commit
- ✅ Integration tests (backend, Android)
- ✅ E2E tests (Angular, iOS)
- ✅ 80% minimum coverage threshold

### Code Quality Standards

- ✅ Formatting checks (ktfmt, Prettier, SwiftFormat)
- ✅ Linting (ktlint, ESLint, SwiftLint)
- ✅ Static analysis (detekt, Android lint)
- ✅ Spring Modulith boundary verification

### Security & Compliance

- ✅ OWASP dependency scanning
- ✅ npm/CocoaPods security audits
- ✅ Secret scanning (pre-commit hooks)

### Observability

- ✅ SonarCloud integration
- ✅ Test result artifacts
- ✅ Coverage report uploads

### Accessibility

- ✅ WCAG 2.1 AA compliance checks
- ✅ iOS accessibility audits

## Caching Strategy

All workflows use aggressive caching to speed up builds:

### Backend & Android

```yaml
~/.gradle/caches
~/.gradle/wrapper
```

### Angular

```yaml
node_modules (via npm cache)
```

### iOS

```yaml
~/.gradle/caches
frontend/mobile/iosApp/Pods
Android emulator AVD snapshots
```

## Artifacts

Each workflow uploads artifacts for debugging:

| Workflow | Artifacts |
|----------|-----------|
| Backend | Test results, coverage reports, security scans |
| Angular | Build output, test results, E2E reports, coverage |
| Android | APKs, test results, lint reports, coverage |
| iOS | App builds, test results, coverage, lint reports |

**Retention**: 30 days (GitHub default)

## SonarCloud Integration

Conditional SonarCloud scans run on:

- Pull requests to any branch
- Pushes to `main` branch

**Requirements**:

- `SONAR_TOKEN` secret configured
- `GITHUB_TOKEN` (automatic)

**Projects**:

- `mobilispect_backend`
- `mobilispect_angular`
- `mobilispect_android`

## Status Checks

Required status checks for PRs (configured in branch protection):

### Backend PRs

- ✅ Backend Build
- ✅ Backend Tests
- ✅ Backend Code Quality

### Frontend Web PRs

- ✅ Angular Build
- ✅ Angular Tests
- ✅ Angular Lint

### Mobile PRs

- ✅ Android Build
- ✅ Android Shared Tests
- ✅ iOS Build
- ✅ iOS Shared Tests

## Workflow Commands

### Run workflows manually

```bash
# Via GitHub CLI
gh workflow run ci-backend.yml
gh workflow run ci-angular.yml
gh workflow run ci-android.yml
gh workflow run ci-ios.yml
```

### View workflow status

```bash
gh run list --workflow=ci-backend.yml
gh run list --workflow=ci-angular.yml
gh run list --workflow=ci-android.yml
gh run list --workflow=ci-ios.yml
```

### Download artifacts

```bash
gh run download <run-id>
```

## Performance Optimization

### Current Performance

- Backend: ~8-12 minutes
- Angular: ~6-10 minutes
- Android: ~12-18 minutes
- iOS: ~15-20 minutes

### Optimization Strategies

1. ✅ Gradle dependency caching
2. ✅ npm cache
3. ✅ CocoaPods cache
4. ✅ AVD snapshot caching
5. ✅ Parallel job execution
6. ✅ Path-based triggers
7. 🔄 Matrix builds (future)

## Debugging Failed Workflows

### View logs

```bash
gh run view <run-id> --log
```

### Re-run failed jobs

```bash
gh run rerun <run-id> --failed
```

### Download failure artifacts

```bash
gh run download <run-id>
```

## Migration Notes

### Removed Workflows

- ❌ `constitutional-enforcement.yml` - Replaced by platform-specific workflows

### Retained Workflows

- ✅ `deployment-enforcement.yml` - Production deployment gates

### Benefits

1. **Faster feedback** - Parallel execution reduces wait time
2. **Better caching** - Platform-specific cache keys
3. **Clearer failures** - Isolated platform failures
4. **Resource efficiency** - Only run what changed
5. **Easier maintenance** - Single responsibility per workflow

## Future Enhancements

### Planned

- [ ] Matrix builds for multiple Node.js/JDK versions
- [ ] Performance benchmarking jobs
- [ ] Visual regression testing
- [ ] Load testing integration
- [ ] Dependency vulnerability alerts
- [ ] Auto-merge for passing Renovate PRs

### Under Consideration

- [ ] Nightly full test runs
- [ ] Weekly security scans
- [ ] Monthly dependency updates
- [ ] Canary deployments

## Support

For CI/CD issues:

1. Check workflow logs in GitHub Actions
2. Review artifact uploads for detailed reports
3. Consult platform-specific documentation
4. Contact DevOps team for infrastructure issues

## References

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Constitutional Requirements](.specify/memory/constitution.md)
- [Branch Protection Config](.github/branch-protection-config.json)
- [Pre-commit Hooks](.pre-commit-config.yaml)
