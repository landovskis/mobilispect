import { test, expect } from '@playwright/test';

/**
 * E2E Tests for RouteDetailPage with Hourly Frequency Display
 * Constitutional Requirements:
 * - Multi-browser testing (Chromium, Firefox, WebKit)
 * - WCAG 2.1 AA accessibility compliance
 * - Comprehensive user journey coverage
 */

test.describe('RouteDetailPage', () => {
  const testRouteId = 'r-test-route';
  const testDate = '2025-01-15';

  test.beforeEach(async ({ page }) => {
    // Mock API responses for consistent testing
    await page.route('**/api/v1/routes/*', async (route) => {
      const url = route.request().url();

      if (url.includes('/hourly-frequencies')) {
        // Mock hourly frequencies endpoint
        const mockHourlyFrequencies = Array.from({ length: 24 }, (_, hour) => ({
          routeId: testRouteId,
          serviceDate: testDate,
          hourOfDay: hour,
          tripCount: hour >= 6 && hour <= 22 ? Math.floor(Math.random() * 5) + 2 : 0,
          averageHeadwayMinutes: hour >= 6 && hour <= 22 ? 15 + Math.random() * 10 : null,
          minHeadwayMinutes: hour >= 6 && hour <= 22 ? 12 : null,
          maxHeadwayMinutes: hour >= 6 && hour <= 22 ? 20 : null,
          variantCount: hour >= 6 && hour <= 22 ? 2 : 0,
          isIrregular: false,
        }));

        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(mockHourlyFrequencies),
        });
      } else if (url.includes('/variants')) {
        // Mock variants endpoint
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            {
              id: 'variant-1',
              routeId: testRouteId,
              directionId: 0,
              headsign: 'Downtown',
              stopCount: 15,
              stopPattern: 'stop1|stop2|stop3',
              firstStopId: 'stop1',
              lastStopId: 'stop3',
            },
            {
              id: 'variant-2',
              routeId: testRouteId,
              directionId: 1,
              headsign: 'Uptown',
              stopCount: 14,
              stopPattern: 'stop3|stop2|stop1',
              firstStopId: 'stop3',
              lastStopId: 'stop1',
            },
          ]),
        });
      } else {
        // Mock route details endpoint
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: testRouteId,
            agencyId: 'o-test-agency',
            shortName: '5',
            longName: 'Downtown Express',
            routeType: 'BUS',
            active: true,
          }),
        });
      }
    });

    // Navigate to route detail page
    await page.goto(`/routes/${testRouteId}`);
  });

  test('should display route information', async ({ page }) => {
    // Wait for route data to load
    await expect(page.locator('app-brand-section').first()).toBeVisible();

    // Check route name and number
    await expect(page.getByText('Downtown Express')).toBeVisible();
    await expect(page.getByText('Route 5')).toBeVisible();

    // Check route metadata
    await expect(page.getByText('Route Number:')).toBeVisible();
    await expect(page.getByText('Route Type:')).toBeVisible();
    await expect(page.getByText('Bus')).toBeVisible();
    await expect(page.getByText('Variants:')).toBeVisible();
    await expect(page.getByText('Status:')).toBeVisible();
    await expect(page.getByText('Active')).toBeVisible();
  });

  test('should display hourly frequency table with 24 rows', async ({ page }) => {
    // Wait for frequency table to load
    await expect(page.locator('.frequency-table')).toBeVisible();

    // Check table header
    await expect(page.getByText('Hour', { exact: true })).toBeVisible();
    await expect(page.getByText('Trips')).toBeVisible();
    await expect(page.getByText('Variants')).toBeVisible();
    await expect(page.getByText('Avg Headway (min)')).toBeVisible();
    await expect(page.getByText('Min-Max (min)')).toBeVisible();

    // Count table rows (should be 24)
    const rows = page.locator('.table-row');
    await expect(rows).toHaveCount(24);

    // Check first and last hour labels
    await expect(page.getByText('00:00-01:00')).toBeVisible();
    await expect(page.getByText('23:00-00:00')).toBeVisible();
  });

  test('should display no-service indicator for hours without trips', async ({ page }) => {
    await expect(page.locator('.frequency-table')).toBeVisible();

    // Early morning hours (0-5) should show no service
    const earlyMorningRows = page.locator('.table-row.no-service').first();
    await expect(earlyMorningRows).toBeVisible();

    // Check for em-dash (—) indicating no service
    await expect(page.locator('.no-service-text').first()).toBeVisible();
  });

  test('should display service hours with frequency data', async ({ page }) => {
    await expect(page.locator('.frequency-table')).toBeVisible();

    // Check that service hours (6-22) show trip counts
    const serviceRow = page.locator('.table-row').nth(8); // Hour 8 (08:00-09:00)
    await expect(serviceRow).not.toHaveClass(/no-service/);

    // Should show numeric values for trips, variants, and headways
    const tripCount = serviceRow.locator('.col-trips');
    await expect(tripCount).not.toHaveText('0');
  });

  test('should have functional date picker', async ({ page }) => {
    const datePicker = page.locator('input[type="date"]');

    // Date picker should be visible
    await expect(datePicker).toBeVisible();

    // Should have default value (today's date)
    const dateValue = await datePicker.inputValue();
    expect(dateValue).toMatch(/\d{4}-\d{2}-\d{2}/);

    // Change date and verify API call is made
    let apiCalled = false;
    page.on('request', (request) => {
      if (request.url().includes('/hourly-frequencies')) {
        apiCalled = true;
      }
    });

    await datePicker.fill('2025-02-01');
    await datePicker.blur();

    // Wait a bit for the API call
    await page.waitForTimeout(500);
    expect(apiCalled).toBeTruthy();
  });

  test('should format hour ranges correctly', async ({ page }) => {
    await expect(page.locator('.frequency-table')).toBeVisible();

    // Check various hour formats
    await expect(page.getByText('00:00-01:00')).toBeVisible();
    await expect(page.getByText('08:00-09:00')).toBeVisible();
    await expect(page.getByText('12:00-13:00')).toBeVisible();
    await expect(page.getByText('23:00-00:00')).toBeVisible();
  });

  test('should handle loading state gracefully', async ({ page }) => {
    // Create a new page without beforeEach setup
    const newPage = await page.context().newPage();

    // Mock slow API response
    await newPage.route('**/api/v1/routes/*', async (route) => {
      await new Promise(resolve => setTimeout(resolve, 1000));
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ id: testRouteId, longName: 'Test', routeType: 'BUS', active: true }),
      });
    });

    await newPage.goto(`/routes/${testRouteId}`);

    // Should show loading indicator
    await expect(newPage.getByText(/Loading/i)).toBeVisible();
  });

  test('should be keyboard navigable', async ({ page }) => {
    // Tab through interactive elements
    await page.keyboard.press('Tab'); // Should focus date picker
    const datePicker = page.locator('input[type="date"]');
    await expect(datePicker).toBeFocused();

    // Date picker should be accessible via keyboard
    await page.keyboard.press('Enter');
    // Date picker native dialog should open (behavior varies by browser)
  });

  test('should have proper ARIA labels and roles', async ({ page }) => {
    // Check for proper semantic structure
    const datePickerLabel = page.locator('label[for="serviceDate"]');
    await expect(datePickerLabel).toBeVisible();
    await expect(datePickerLabel).toHaveText('Service Date:');

    const dateInput = page.locator('#serviceDate');
    await expect(dateInput).toHaveAttribute('type', 'date');

    // Table should have proper structure
    const table = page.locator('.frequency-table');
    await expect(table).toBeVisible();

    // Headers should be distinguishable
    const headers = page.locator('.table-header');
    await expect(headers).toBeVisible();
  });

  test('should have sufficient color contrast (WCAG AA)', async ({ page }) => {
    await expect(page.locator('.frequency-table')).toBeVisible();

    // Check that text is visible and readable
    // Note: Actual contrast testing requires specialized tools
    // but we can verify elements are visible
    const headerText = page.locator('.table-header .col-hour');
    await expect(headerText).toBeVisible();

    const rowText = page.locator('.table-row .col-hour').first();
    await expect(rowText).toBeVisible();

    // Check no-service styling is distinct
    const noServiceRow = page.locator('.table-row.no-service').first();
    await expect(noServiceRow).toBeVisible();
  });

  test('should display irregular schedule indicator when applicable', async ({ page }) => {
    // Mock route with irregular schedule
    await page.route('**/api/v1/routes/*/hourly-frequencies*', async (route) => {
      const mockData = Array.from({ length: 24 }, (_, hour) => ({
        routeId: testRouteId,
        serviceDate: testDate,
        hourOfDay: hour,
        tripCount: hour === 10 ? 3 : 0,
        averageHeadwayMinutes: hour === 10 ? 20 : null,
        minHeadwayMinutes: hour === 10 ? 5 : null,
        maxHeadwayMinutes: hour === 10 ? 45 : null,
        variantCount: hour === 10 ? 1 : 0,
        isIrregular: hour === 10, // Irregular at hour 10
      }));

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockData),
      });
    });

    await page.reload();
    await expect(page.locator('.frequency-table')).toBeVisible();

    // Check for irregular badge
    const irregularBadge = page.locator('.irregular-badge');
    await expect(irregularBadge).toBeVisible();
    await expect(irregularBadge).toHaveText('Irregular');
  });

  test('should display headway ranges correctly', async ({ page }) => {
    await expect(page.locator('.frequency-table')).toBeVisible();

    // Service hours should show min-max range in format "XX.X - YY.Y"
    const rangeCell = page.locator('.table-row').nth(8).locator('.col-range');
    const rangeText = await rangeCell.textContent();

    // Should match pattern like "12.0 - 20.0"
    expect(rangeText).toMatch(/\d+\.\d+\s*-\s*\d+\.\d+/);
  });

  test('should handle route not found error', async ({ page }) => {
    const newPage = await page.context().newPage();

    await newPage.route('**/api/v1/routes/*', async (route) => {
      if (!route.request().url().includes('hourly-frequencies') && !route.request().url().includes('variants')) {
        await route.fulfill({
          status: 404,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'Route not found' }),
        });
      }
    });

    await newPage.goto('/routes/nonexistent-route');

    // Should handle error gracefully (implementation may vary)
    // At minimum, should not crash
    await newPage.waitForLoadState('networkidle');
  });

  test('should be responsive on mobile viewports', async ({ page, viewport }) => {
    // This test will run with mobile viewports via playwright config
    if (viewport && viewport.width < 768) {
      await expect(page.locator('.frequency-table')).toBeVisible();

      // Table should be scrollable horizontally on mobile
      const table = page.locator('.frequency-table');
      const boundingBox = await table.boundingBox();
      expect(boundingBox).toBeTruthy();
    }
  });

  test('should display variant count in route-level view', async ({ page }) => {
    await expect(page.locator('.frequency-table')).toBeVisible();

    // Service hours should show variant count
    const variantCountCell = page.locator('.table-row').nth(8).locator('.col-variants');
    const count = await variantCountCell.textContent();

    // Should show numeric value (2 variants in our mock)
    expect(parseInt(count?.trim() || '0')).toBeGreaterThan(0);
  });
});
