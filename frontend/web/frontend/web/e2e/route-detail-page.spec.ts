import { test, expect } from '@playwright/test';

test.describe('RouteDetailPage', () => {
  const testRouteId = 'r-test-route';
  const testVariantId = 'variant-1';
  const testDate = '2025-03-10';
  const testSectionId = 'section-1';

  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/routes/variants/*/frequencies*', async (route) => {
      const url = route.request().url();
      if (url.includes(testVariantId)) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            {
              id: 'freq-1',
              variantId: testVariantId,
              serviceDate: testDate,
              timePeriod: 'WEEKDAY_AM_PEAK',
              averageHeadwayMinutes: 12,
              minHeadwayMinutes: 10,
              maxHeadwayMinutes: 15,
              tripCount: 8,
              isIrregular: false,
            },
          ]),
        });
        return;
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route('**/api/v1/routes/*/variants', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: testVariantId,
            routeId: testRouteId,
            directionId: 0,
            headsign: 'Downtown',
            stopCount: 15,
            stopPattern: 'stop1|stop2|stop3',
            firstStopId: 'stop1',
            lastStopId: 'stop3',
          },
        ]),
      });
    });

    await page.route('**/api/v1/routes/*', async (route) => {
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
    });

    await page.route('**/api/v1/common-sections/routes/*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: testSectionId,
            stopPattern: 'stop1|stop2|stop3',
            stopCount: 3,
            firstStopId: 'stop1',
            lastStopId: 'stop3',
            variants: ['A', 'B'],
          },
        ]),
      });
    });

    await page.route('**/api/v1/common-sections/*/frequency*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          commonSectionId: testSectionId,
          timePeriod: 'WEEKDAY_AM_PEAK',
          averageHeadwayMinutes: 10,
          tripCount: 12,
          isIrregular: false,
        }),
      });
    });

    await page.goto(`/routes/${testRouteId}`);
  });

  test('should display route information', async ({ page }) => {
    await expect(page.locator('app-brand-section').first()).toBeVisible();

    await expect(page.getByText('Downtown Express')).toBeVisible();
    await expect(page.getByText('Route 5')).toBeVisible();

    await expect(page.getByText('Route Number:')).toBeVisible();
    await expect(page.getByText('Route Type:')).toBeVisible();
    await expect(page.getByText('Bus')).toBeVisible();
    await expect(page.getByText('Variants:')).toBeVisible();
    await expect(page.getByText('Status:')).toBeVisible();
    await expect(page.getByText('Active')).toBeVisible();
  });

  test('should render route frequency card content', async ({ page }) => {
    await page.getByRole('button', { name: 'View frequencies' }).click();

    await expect(page.getByText('Stops: 3')).toBeVisible();
    await expect(page.getByText('Variants: 2')).toBeVisible();
    await expect(page.getByText('10 min avg')).toBeVisible();
  });

  test('should request frequencies for date changes', async ({ page }) => {
    await page.getByRole('button', { name: 'View frequencies' }).click();

    const datePicker = page.locator('input[type="date"]');
    await expect(datePicker).toBeVisible();

    const responsePromise = page.waitForResponse((response) => {
      return (
        response.url().includes(`/api/v1/routes/variants/${testVariantId}/frequencies`) &&
        response.url().includes('date=2025-04-01')
      );
    });

    await datePicker.fill('2025-04-01');
    await datePicker.blur();

    await responsePromise;
  });
});
