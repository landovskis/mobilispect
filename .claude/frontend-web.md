# Frontend Web Configuration

## Technology Stack

- **Framework**: Angular 21 LTS with TypeScript
- **State Management**: RxJS for reactive state management
- **Testing**: Playwright (Chromium/Firefox/WebKit)

## Pre-Commit Hooks

Web-specific hooks enforced via `.pre-commit-config.yaml`:

- **Prettier**: Code formatting
- **ESLint**: Linting and code quality
- **Jest**: Unit tests execution
- **ng lint**: Angular-specific linting
- **Coverage validation**: ≥80% threshold

## IDE Integration

- **VS Code**: Pre-commit extension with real-time validation
- Enable ESLint and Prettier extensions
- Configure Angular Language Service

## Web-Specific Requirements

### Accessibility (WCAG 2.1 AA)

- Automated accessibility checks in CI
- Manual testing required for complex interactions
- Light/dark theme parity required
- Keyboard navigation support

### Performance Targets

- 60fps UI rendering
- Core Web Vitals compliance
- Lazy loading for routes and modules
- Optimize bundle size

### Testing Requirements

- **Unit tests**: Jest with ≥80% coverage
- **E2E tests**: Playwright across all browsers (Chromium/Firefox/WebKit)
- **Visual regression**: Screenshots for critical user flows
- Test both light and dark themes

### RxJS Best Practices

- Always unsubscribe or use async pipe
- Avoid nested subscriptions
- Use appropriate operators (switchMap, mergeMap, etc.)
- Handle errors in observable streams