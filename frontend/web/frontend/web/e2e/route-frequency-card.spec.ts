import { test, expect } from '@playwright/test';

test.describe('RouteFrequencyCard', () => {
  const testRouteId = 'r-card-route';
  const testVariantId = 'variant-1';
  const testDate = '2025-03-10';
  const testSectionId = 'section-1';

  test.beforeEach(async ({ page }) => {
    await page.route(
      '**/api/v1/routes/variants/*/frequencies*',
      async (route) => {
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
              {
                id: 'freq-2',
                variantId: testVariantId,
                serviceDate: testDate,
                timePeriod: 'WEEKDAY_PM_PEAK',
                averageHeadwayMinutes: 15,
                minHeadwayMinutes: 12,
                maxHeadwayMinutes: 18,
                tripCount: 6,
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
      },
    );

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
            stopCount: 12,
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
          agencyId: 'agency-1',
          shortName: '10',
          longName: 'Riverfront Express',
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

    await page.route(
      '**/api/v1/common-sections/*/frequency*',
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            commonSectionId: testSectionId,
            timePeriod: 'WEEKDAY_AM_PEAK',
            averageHeadwayMinutes: 10,
            tripCount: 12,
            isIrregular: false,
            contributions: [
              {
                routeId: testRouteId,
                averageHeadwayMinutes: 10,
                tripCount: 12,
                isIrregular: false,
              },
            ],
          }),
        });
      },
    );

    await page.goto(`/regions/routes/${testRouteId}`);
  });

  test('renders route card details and frequency content', async ({ page }) => {
    await expect(page.getByText('Riverfront Express')).toBeVisible();
    await expect(
      page.locator('.card-subtitle', { hasText: '10' }),
    ).toBeVisible();

    const datePicker = page.locator('input[type="date"]');
    await expect(datePicker).toBeVisible();

    await page.getByRole('button', { name: 'View frequencies' }).click();

    await expect(page.getByText('Stops: 3')).toBeVisible();
    await expect(page.getByText('Variants: 2')).toBeVisible();
    await expect(page.getByText('10 min avg')).toBeVisible();
  });
});
