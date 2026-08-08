import { test, expect, type Page } from '@playwright/test';
import { ensureRegionHasBoundingBox, withDb } from './helpers/db';

/**
 * Corridor Design — OSM import flow (see
 * docs/superpowers/specs/2026-08-06-corridor-osm-import-design.md).
 * Written before the WASM UI for it exists (Task 5), so it fails today for
 * the correct reason, matching this repo's established precedent. Uses
 * `window.__corridorBuilderMap.project()` (see
 * builder-click-routing.spec.ts) to compute exact click pixel coordinates
 * for the fixture ways below, since MapLibre's pan/zoom means a rendered
 * way's screen position isn't otherwise predictable from outside the page.
 *
 * The Overpass fixture server this test's OSM data comes from is started
 * once for the whole suite by `../global-setup.ts` (not per-file
 * `beforeAll`) -- see that file's doc comment for why. Requires
 * `mobilispect-server` to have been started with
 * `OVERPASS_BASE_URL=http://localhost:19999` so its Overpass calls hit that
 * fixture server instead of the real overpass-api.de.
 */

const WAY_A_MIDPOINT = { lat: 45.5005, lon: -73.5795 }; // fixture way 9001001
const WAY_B_MIDPOINT = { lat: 45.5015, lon: -73.5785 }; // fixture way 9001002, contiguous with A
const WAY_C_MIDPOINT = { lat: 45.5055, lon: -73.5745 }; // fixture way 9001003, disconnected

let remixId: number;

test.beforeAll(async ({}, testInfo) => {
  await ensureRegionHasBoundingBox();
  await withDb(async (client) => {
    const result = await client.query(
      `INSERT INTO remixes (name, region_id) VALUES ($1, 1) RETURNING id`,
      [`OSM Import Test Remix ${testInfo.parallelIndex}`]
    );
    remixId = result.rows[0].id;
  });
});

test.afterAll(async () => {
  await withDb(async (client) => {
    await client.query(
      `DELETE FROM lanes WHERE cross_section_id IN (
         SELECT id FROM cross_sections WHERE corridor_id IN (
           SELECT id FROM corridors WHERE remix_id = $1))`,
      [remixId]
    );
    await client.query(
      `DELETE FROM cross_sections WHERE corridor_id IN (SELECT id FROM corridors WHERE remix_id = $1)`,
      [remixId]
    );
    await client.query(`DELETE FROM corridors WHERE remix_id = $1`, [remixId]);
    await client.query(`DELETE FROM remixes WHERE id = $1`, [remixId]);
  });
});

async function clickWayAt(page: Page, lonLat: { lat: number; lon: number }) {
  const px = await page.evaluate(
    ({ lat, lon }) => {
      const point = (window as any).__corridorBuilderMap.project([lon, lat]);
      return { x: point.x, y: point.y };
    },
    lonLat
  );
  await page.locator('.maplibregl-canvas').click({ position: px });
}

test.describe('Corridor Design: OSM import', () => {
  test('loading streets, selecting two contiguous ways, and importing persists a corridor and navigates to its editor page', async ({
    page,
  }) => {
    await page.goto(`/builder/remix/${remixId}`);
    await page.waitForSelector('.maplibregl-canvas');

    await page.getByRole('button', { name: 'Add corridor' }).click();
    await page.getByRole('button', { name: 'Import from OSM' }).click();

    await page.waitForFunction(() => (window as any).__corridorBuilderMap !== undefined);
    await page.getByRole('button', { name: 'Load streets' }).click();

    // The ways layer (and the camera fit_bounds to it) only exist once
    // search_streets's response has been processed -- waiting for the
    // GeoJSON source to exist avoids a race where a click computed via
    // map.project() lands before the camera has actually moved to show it.
    await page.waitForFunction(() => (window as any).__corridorBuilderMap.getSource('osm-ways') !== undefined);

    await clickWayAt(page, WAY_A_MIDPOINT);
    await clickWayAt(page, WAY_B_MIDPOINT);

    await expect(page.getByLabel('Corridor name')).toHaveValue('Fixture Test Street');
    await page.getByRole('button', { name: 'Import' }).click();

    await expect(page).toHaveURL(new RegExp(`/builder/remix/${remixId}/corridor/\\d+$`));
    await expect(page.getByText('editor coming soon')).toBeVisible();
  });

  test('selecting two disconnected ways shows a disconnected error and stays on the import screen', async ({
    page,
  }) => {
    await page.goto(`/builder/remix/${remixId}`);
    await page.waitForSelector('.maplibregl-canvas');

    await page.getByRole('button', { name: 'Add corridor' }).click();
    await page.getByRole('button', { name: 'Import from OSM' }).click();

    await page.waitForFunction(() => (window as any).__corridorBuilderMap !== undefined);
    await page.getByRole('button', { name: 'Load streets' }).click();

    // The ways layer (and the camera fit_bounds to it) only exist once
    // search_streets's response has been processed -- waiting for the
    // GeoJSON source to exist avoids a race where a click computed via
    // map.project() lands before the camera has actually moved to show it.
    await page.waitForFunction(() => (window as any).__corridorBuilderMap.getSource('osm-ways') !== undefined);

    await clickWayAt(page, WAY_A_MIDPOINT);
    await clickWayAt(page, WAY_C_MIDPOINT);

    await page.getByRole('button', { name: 'Import' }).click();

    await expect(page.getByText('not connected')).toBeVisible();
  });
});
