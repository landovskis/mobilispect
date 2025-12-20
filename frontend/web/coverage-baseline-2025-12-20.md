# Angular 21 Migration - Test Coverage Baseline

**Date:** 2025-12-20
**Branch:** adopt-angular-21
**Purpose:** Baseline coverage before Angular 21 recommendation adoption

## Summary

Current test coverage is **CRITICALLY BELOW** the constitutional requirement of ≥80%.

## Coverage Metrics

| Metric | Current | Required | Status |
|--------|---------|----------|--------|
| Statements | 49.41% (168/340) | ≥80% | ❌ FAIL |
| Branches | 32.38% (34/105) | ≥80% | ❌ FAIL |
| Functions | 37.71% (43/114) | ≥80% | ❌ FAIL |
| Lines | 50.79% (160/315) | ≥80% | ❌ FAIL |

## Test Suite Status

- **Total Tests:** 20
- **Passing:** 20
- **Failing:** 0
- **Test Files:** 6

### Existing Test Files

1. `app.spec.ts` - App component ✅
2. `core/services/theme.service.spec.ts` - Theme service ✅
3. `feeds/models/region.models.spec.ts` - Region models ✅
4. `regions/pages/region-detail.component.spec.ts` - Region detail component ✅
5. `regions/resolvers/region-breadcrumb.resolver.spec.ts` - Region breadcrumb
   resolver ✅
6. `transit-frequency/pages/route-detail/route-detail-page.component.spec.ts`
   - Route detail page ✅

## Files Needing Tests

### Services (14 files - NO TESTS)

**CRITICAL (Security/Complex):**

1. `feeds/services/auth.service.ts` - Authentication logic
2. `feeds/services/feed-authentication.service.ts` - Feed authentication
3. `feeds/services/region.service.ts` - 278 lines, complex caching
4. `feeds/services/websocket.service.ts` - Real-time WebSocket
5. `feeds/services/progress-websocket.service.ts` - Progress tracking

**HIGH (Core Business Logic):**
6. `feeds/services/import.service.ts` - Import operations
7. `feeds/services/scheduler.service.ts` - Job scheduling
8. `transit-frequency/services/frequency.service.ts` - Core frequency logic
9. `agencies/services/agency.service.ts` - Agency management
10. `transit-frequency/services/agency.service.ts` - Transit agency logic
11. `transit-frequency/services/region.service.ts` - Transit regions

**MEDIUM:**
12. `feeds/services/feeds-metrics.service.ts` - Metrics tracking
13. `feeds/services/feeds-events.service.ts` - Event handling
14. `transit-frequency/services/common-section.service.ts` - Common sections

### Components (26+ files - NO TESTS)

**CRITICAL (Primary User Workflows):**

1. `feeds/pages/discover-feeds.page.ts` - Main feed discovery
2. `feeds/pages/feed-imports.page.ts` - Import workflow
3. `regions/pages/region-list.component.ts` - Region navigation
4. `transit-frequency/pages/route-frequency/route-frequency.component.ts` -
   Route frequency
5. `agencies/pages/agency-page.component.ts` - Agency details

**HIGH (Layout/Navigation/Theme):**
6. `shared/components/app-shell.component.ts` - Main layout
7. `shared/components/app-bar.component.ts` - Navigation bar
8. `shared/components/theme-toggle.component.ts` - Theme switching (constitutional requirement)
9. `shared/components/app-breadcrumbs.component.ts` - Breadcrumb
   navigation

**MEDIUM (Reusable Components):**
10. `shared/components/brand-button.component.ts` - Button component (uses ngClass)
11. `shared/components/brand-badge.component.ts` - Badge component (uses ngClass)
12. `feeds/components/progress-monitor.component.ts` - Progress monitor
    (uses ngClass)
13-26. Additional feed, region, and transit-frequency components

### Resolvers/Guards

1. `agencies/resolvers/agency-breadcrumb.resolver.ts` - Status unknown

## Gap Analysis

**Coverage Gap:** ~30% to reach 80% minimum

**Estimated Work:**

- **Services:** 14 files × 0.5 days = 7 days
- **Components:** 26+ files × 0.3 days = 8 days
- **Resolvers:** 1-2 files × 0.5 days = 1 day
- **Total:** ~16-18 days for test authoring

## Next Steps (Phase 1 Plan)

1. **Configure coverage thresholds** in test configuration
2. **Write service tests** - Start with critical services (auth, region, websocket)
3. **Write component tests** - Start with critical workflows and theme components
4. **Write resolver/guard tests** - Complete coverage for routing logic
5. **Validate ≥80% coverage** - Run validation script and fix gaps
6. **Create ADR** - Document baseline and migration approach

## Constitutional Compliance

**Status:** ❌ FAILING

**Requirements:**

- ≥80% test coverage MANDATORY (currently 49.41%)
- Pre-commit hooks enforce test passage (currently would BLOCK commits)
- Test-Driven Quality: tests first, fail first
- WCAG 2.1 AA accessibility compliance must be tested
- Multi-browser E2E testing required

**Impact:** Cannot proceed with Angular 21 migrations until coverage baseline
is established.

## Coverage Report Location

Full HTML coverage report: `frontend/web/coverage/web/index.html`

## Test Execution

Run tests with coverage:

```bash
cd frontend/web
npm test -- --code-coverage --no-watch
```

View coverage report:

```bash
open coverage/web/index.html
```

---

**Generated:** 2025-12-20
**Tool:** Angular CLI with Karma + Istanbul coverage
**Next Migration:** Vitest (Phase 2)
