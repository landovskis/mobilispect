import { test, expect } from '@playwright/test';
import { ensureRegionHasBoundingBox, withDb } from './helpers/db';

/**
 * Corridor Builder WASM shell — open-remix flow (see design spec's "User
 * Flow" step 3): pick a region, see its remix list, select one, land on
 * its region map.
 */

let seededRemixId: number;

test.beforeAll(async () => {
  await ensureRegionHasBoundingBox();
  await withDb(async (client) => {
    const result = await client.query(
      `INSERT INTO remixes (name, region_id) VALUES ('Open Flow Test Remix', 1) RETURNING id`
    );
    seededRemixId = result.rows[0].id;
  });
});

test.afterAll(async () => {
  await withDb(async (client) => {
    await client.query(`DELETE FROM remixes WHERE id = $1`, [seededRemixId]);
  });
});

test.describe('Corridor Builder: open remix', () => {
  test('selecting a region lists its remixes, and opening one loads its map', async ({
    page,
  }) => {
    await page.goto('/builder');

    await page.getByRole('button', { name: 'Open remix' }).click();
    await page.getByLabel('Metro region').selectOption({ label: 'Test Region' });

    const remixLink = page.getByRole('link', { name: 'Open Flow Test Remix' });
    await expect(remixLink).toBeVisible();
    await remixLink.click();

    await expect(page).toHaveURL(`/builder/remix/${seededRemixId}`);
    await expect(page.locator('.maplibregl-canvas')).toBeVisible();
  });
});
