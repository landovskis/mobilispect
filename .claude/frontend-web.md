# Frontend Web Configuration

## Technology Stack

- **Framework**: Angular 21 LTS with TypeScript
- **State Management**: RxJS for reactive state management
- **Testing**: Playwright (Chromium/Firefox/WebKit)

## Development Workflow

Follow these steps for every frontend web change (TDD is constitutional requirement):

### Step 1: Create Your Test First

```bash
# Navigate to component/service directory
cd frontend/web/src/app/<feature>

# Create your spec file (if new): your-feature.component.spec.ts
# Write a failing test that describes the behavior you want
```

### Step 2: Run Test to Verify It Fails

```bash
# Run specific test in watch mode
npm test -- --testNamePattern="YourFeature" --watch
```

Expected: Test fails (red) ✗

### Step 3: Write Minimum Implementation

```bash
# Edit your component/service file: your-feature.component.ts
# Write just enough code to make the test pass
```

### Step 4: Test Auto-Runs and Passes

With Jest in watch mode, it automatically re-runs.

Expected: Test passes (green) ✓

### Step 5: Format Your Code

```bash
# Auto-format with Prettier
npm run format
```

### Step 6: Run ESLint

```bash
# Check and auto-fix linting issues
npm run lint -- --fix
```

Fix any remaining violations manually.

### Step 7: Run Angular Linting

```bash
# Check Angular-specific rules
npm run ng lint
```

### Step 8: Run All Tests with Coverage

```bash
# Stop watch mode (Ctrl+C), then run all tests
npm test -- --coverage --watchAll=false
```

All tests must pass ✓

### Step 9: Verify Coverage

```bash
# Coverage report is displayed in terminal
# Should show ≥80% for all metrics

# View detailed HTML report
open frontend/web/coverage/lcov-report/index.html
```

If below 80%, add more tests and repeat from Step 1.

### Step 10: Commit Your Changes

```bash
git add .
git commit -m "feat: your feature description"
```

The pre-commit Husky hook runs automatically and checks formatting and linting.
If it fails, fix issues and retry.

### Step 11: Push Your Changes

```bash
git push
```

The pre-push Husky hook runs automatically and checks tests, coverage (≥80%),
and security scan. If it fails, fix issues and retry.

## Git Hooks (Husky)

Web checks are divided between hooks:

**pre-commit** (runs on `git commit` — fast):

- `prettier --check` — formatting validation
- `ng lint` — ESLint + Angular-specific linting

**pre-push** (runs on `git push` — thorough):

- `npm test` — vitest unit tests
- `scripts/validate-coverage.sh` — ≥80% coverage requirement

## Quick Reference Commands

```bash
# Start dev server
npm start

# Format code
npm run format

# Lint and fix
npm run lint -- --fix

# Angular lint
npm run ng lint

# Run tests in watch mode
npm run test:watch

# Run tests with coverage
npm run test:coverage

# Build for production
npm run build

# Run E2E tests
npm run e2e
```

## IDE Integration

- **VS Code**: Pre-commit extension with real-time validation
- Enable ESLint and Prettier extensions
- Configure Angular Language Service
- Set "Format on Save" with Prettier as default formatter

## Web-Specific Requirements

See [CLAUDE.md](../CLAUDE.md) for constitutional requirements on accessibility (WCAG 2.1 AA), performance targets (60fps), and testing standards. This section covers web-specific implementation details.

### Web Accessibility Implementation

- Automated accessibility checks in CI (constitutional requirement)
- Manual testing required for complex interactions
- Keyboard navigation support for all interactive elements

### Web Performance Implementation

- Core Web Vitals compliance (LCP, FID, CLS)
- Lazy loading for routes and modules
- Optimize bundle size via code splitting

### Web Testing Requirements

- **Unit tests**: Jest with ≥80% coverage (constitutional requirement)
- **E2E tests**: Playwright across all browsers (Chromium/Firefox/WebKit)
- **Visual regression**: Screenshots for critical user flows
- Test both light and dark themes (constitutional requirement)

### RxJS Best Practices

- Always unsubscribe or use async pipe
- Avoid nested subscriptions
- Use appropriate operators (switchMap, mergeMap, etc.)
- Handle errors in observable streams