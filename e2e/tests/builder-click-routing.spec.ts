import { test, expect } from '@playwright/test';
import { ensureRegionHasBoundingBox, withDb } from './helpers/db';

/**
 * Corridor Builder WASM shell — click-routing flow (see design spec's User
 * Flow steps 5-6). Seeds a remix with one corridor directly via SQL, since
 * this shell has no API for creating corridors (that's the segment-editor
 * follow-up spec's job). Uses `window.__corridorBuilderMap.project()` (a
 * test-only hook exposed by the app — see region_map.rs) to compute exact
 * click pixel coordinates, since MapLibre's pan/zoom-to-fit means a
 * corridor's screen position isn't otherwise predictable from outside the
 * page.
 */

let remixId: number;
let corridorId: number;
const CORRIDOR_START = { lat: 45.50, lon: -73.60 };
const CORRIDOR_END = { lat: 45.52, lon: -73.58 };

test.beforeAll(async () => {
  await ensureRegionHasBoundingBox();
  await withDb(async (client) => {
    const remixResult = await client.query(
      `INSERT INTO remixes (name, region_id) VALUES ('Click Routing Test Remix', 1) RETURNING id`
    );
    remixId = remixResult.rows[0].id;

    const corridorResult = await client.query(
      `INSERT INTO corridors (name, geometry_source, remix_id) VALUES ('Test Corridor', 'manual', $1) RETURNING id`,
      [remixId]
    );
    corridorId = corridorResult.rows[0].id;

    await client.query(
      `INSERT INTO cross_sections (corridor_id, position, lat, lon) VALUES
         ($1, 0, $2, $3), ($1, 1, $4, $5), ($1, 2, $6, $7)`,
      [
        corridorId,
        CORRIDOR_START.lat,
        CORRIDOR_START.lon,
        45.51,
        -73.59,
        CORRIDOR_END.lat,
        CORRIDOR_END.lon,
      ]
    );
  });
});

test.afterAll(async () => {
  await withDb(async (client) => {
    await client.query(`DELETE FROM corridors WHERE id = $1`, [corridorId]);
    await client.query(`DELETE FROM remixes WHERE id = $1`, [remixId]);
  });
});

test.describe('Corridor Builder: click routing', () => {
  test('clicking a corridor line navigates to its placeholder page', async ({ page }) => {
    await page.goto(`/builder/remix/${remixId}`);
    await page.waitForFunction(() => (window as any).__corridorBuilderMap !== undefined);

    const midpoint = {
      lat: (CORRIDOR_START.lat + CORRIDOR_END.lat) / 2,
      lon: (CORRIDOR_START.lon + CORRIDOR_END.lon) / 2,
    };
    const px = await page.evaluate(({ lat, lon }) => {
      const point = (window as any).__corridorBuilderMap.project([lon, lat]);
      return { x: point.x, y: point.y };
    }, midpoint);

    await page.locator('.maplibregl-canvas').click({ position: px });

    await expect(page).toHaveURL(`/builder/remix/${remixId}/corridor/${corridorId}`);
    await expect(page.getByText('editor coming soon')).toBeVisible();
  });

  test('clicking an intersection point navigates to its placeholder page', async ({ page }) => {
    await page.goto(`/builder/remix/${remixId}`);
    await page.waitForFunction(() => (window as any).__corridorBuilderMap !== undefined);

    const px = await page.evaluate((lonLat) => {
      const point = (window as any).__corridorBuilderMap.project(lonLat);
      return { x: point.x, y: point.y };
    }, [CORRIDOR_START.lon, CORRIDOR_START.lat]);

    await page.locator('.maplibregl-canvas').click({ position: px });

    await expect(page).toHaveURL(new RegExp(`/builder/remix/${remixId}/intersection/\\d+$`));
    await expect(page.getByText('editor coming soon')).toBeVisible();
  });
});
