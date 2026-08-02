import { test, expect } from '@playwright/test';
import { ensureRegionHasBoundingBox, withDb } from './helpers/db';

/**
 * Corridor Builder WASM shell — open-remix flow (see design spec's "User
 * Flow" step 3): pick a region, see its remix list, select one, land on
 * its region map.
 */

let seededRemixId: number;
let seededRemixName: string;

// The chromium/firefox/webkit projects run truly in parallel, in separate
// workers, against the same shared dev Postgres (there is no per-test DB
// isolation here — see helpers/db.ts). A fixed fixture name would let two
// projects' `beforeAll`s both insert a row with the same name at once,
// making the `getByRole('link', { name: ... })` lookup below resolve to two
// elements (Playwright strict-mode violation). `testInfo.parallelIndex` is
// guaranteed distinct across workers running concurrently, so folding it
// into the name keeps each project's fixture unique.
test.beforeAll(async ({}, testInfo) => {
  seededRemixName = `Open Flow Test Remix ${testInfo.parallelIndex}`;
  await ensureRegionHasBoundingBox();
  await withDb(async (client) => {
    const result = await client.query(
      `INSERT INTO remixes (name, region_id) VALUES ($1, 1) RETURNING id`,
      [seededRemixName]
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

    const remixLink = page.getByRole('link', { name: seededRemixName });
    await expect(remixLink).toBeVisible();
    await remixLink.click();

    await expect(page).toHaveURL(`/builder/remix/${seededRemixId}`);
    await expect(page.locator('.maplibregl-canvas')).toBeVisible();
  });
});
