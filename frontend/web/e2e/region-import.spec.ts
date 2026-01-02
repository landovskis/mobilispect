import { test, expect } from '@playwright/test';

test.describe('Discover Regions import', () => {
  const regionId = 'r-1';
  const feedId = 'f-1';
  const regionName = 'Test Region';

  test.beforeEach(async ({ page }) => {
    await page.route(`**/api/feeds/regions/${regionId}/feeds**`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          feeds: [
            {
              feedOnestopId: feedId,
              regionOnestopId: regionId,
              name: 'Test Feed',
              specType: 'GTFS',
              downloadUrl: 'https://example.com/gtfs.zip',
              currentVersionSha1: null,
              lastCheckedAt: null,
              lastUpdatedAt: null,
              status: 'ACTIVE',
              hasAuthentication: false,
              createdAt: '2024-01-01T00:00:00Z',
              updatedAt: '2024-01-01T00:00:00Z',
            },
          ],
          total: 1,
        }),
      });
    });

    await page.route('**/api/feeds/regions**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          regions: [
            {
              regionOnestopId: regionId,
              name: regionName,
              adm0Name: 'United States',
              adm1Name: 'California',
              autoUpdateEnabled: false,
              feedCount: 1,
              lastCheckAt: null,
              createdAt: '2024-01-01T00:00:00Z',
              updatedAt: '2024-01-01T00:00:00Z',
            },
          ],
          total: 1,
        }),
      });
    });

    await page.route(`**/api/feeds/${feedId}/import`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'imp-1',
          feedOnestopId: feedId,
          administratorId: null,
          administratorUsername: null,
          triggerType: 'manual',
          status: 'pending',
          versionSha1: null,
          startedAt: '2024-01-01T00:00:00Z',
          completedAt: null,
          fileSizeBytes: null,
          errorMessage: null,
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
        }),
      });
    });

    await page.route('**/api/feeds/imports**', async (route) => {
      const url = route.request().url();
      if (url.includes('/active')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ imports: [] }),
        });
        return;
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          imports: [],
          page: {
            page: 0,
            size: 20,
            totalElements: 0,
            totalPages: 0,
            hasNext: false,
            hasPrevious: false,
          },
        }),
      });
    });
  });

  test('imports a region and navigates to the imports view', async ({ page }) => {
    await page.goto('/regions/discover');

    const regionButton = page.getByRole('button', { name: /Test Region/ });
    await regionButton.click();

    await expect(
      page.getByRole('button', { name: /Import Region/ }),
    ).toBeVisible();

    const importRequest = page.waitForRequest((request) => {
      return (
        request.method() === 'POST' &&
        request.url().includes(`/api/feeds/${feedId}/import`)
      );
    });

    await page.getByRole('button', { name: /Import Region/ }).click();
    await importRequest;
    await page.waitForURL('**/feeds/imports');

    await expect(page.getByText('Import History')).toBeVisible();
  });
});
