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
let startIntersectionId: number;
let endIntersectionId: number;
const CORRIDOR_START = { lat: 45.50, lon: -73.60 };
const CORRIDOR_END = { lat: 45.52, lon: -73.58 };

// The chromium/firefox/webkit projects run truly in parallel, in separate
// workers, against the same shared dev Postgres (there is no per-test DB
// isolation here — see helpers/db.ts). This test only ever navigates by the
// seeded remixId/corridorId (never looks a row up by name), but folding
// `testInfo.parallelIndex` (guaranteed distinct across concurrently running
// workers) into the seeded names keeps them unique too, avoiding any
// incidental collision surface between projects' fixture rows.
test.beforeAll(async ({}, testInfo) => {
  await ensureRegionHasBoundingBox();
  await withDb(async (client) => {
    const remixResult = await client.query(
      `INSERT INTO remixes (name, region_id) VALUES ($1, 1) RETURNING id`,
      [`Click Routing Test Remix ${testInfo.parallelIndex}`]
    );
    remixId = remixResult.rows[0].id;

    const corridorResult = await client.query(
      `INSERT INTO corridors (name, geometry_source, remix_id) VALUES ($1, 'manual', $2) RETURNING id`,
      [`Test Corridor ${testInfo.parallelIndex}`, remixId]
    );
    corridorId = corridorResult.rows[0].id;

    // As of migration 028 an endpoint cross-section is only an "intersection"
    // if it carries a real `intersections` FK -- `IntersectionPage` resolves
    // cross_section_id -> intersection_id via GET /api/cross-sections/:id and
    // renders its `LoadState::NotAnIntersection` alert instead of the
    // treatment form when that column is NULL (see
    // crates/corridor_builder_web/src/pages/intersection.rs). The
    // intersection-click test below asserts on that form, so both endpoints
    // get their own seeded Intersection here -- same fixture pattern as
    // builder-intersection-treatment.spec.ts. The interior cross-section
    // deliberately gets none, matching the endpoint-only invariant.
    const startIntersection = await client.query(
      `INSERT INTO intersections (lat, lon) VALUES ($1, $2) RETURNING id`,
      [CORRIDOR_START.lat, CORRIDOR_START.lon]
    );
    startIntersectionId = startIntersection.rows[0].id;
    const endIntersection = await client.query(
      `INSERT INTO intersections (lat, lon) VALUES ($1, $2) RETURNING id`,
      [CORRIDOR_END.lat, CORRIDOR_END.lon]
    );
    endIntersectionId = endIntersection.rows[0].id;

    await client.query(
      `INSERT INTO cross_sections (corridor_id, position, lat, lon, intersection_id) VALUES
         ($1, 0, $2, $3, $8), ($1, 1, $4, $5, NULL), ($1, 2, $6, $7, $9)`,
      [
        corridorId,
        CORRIDOR_START.lat,
        CORRIDOR_START.lon,
        45.51,
        -73.59,
        CORRIDOR_END.lat,
        CORRIDOR_END.lon,
        startIntersectionId,
        endIntersectionId,
      ]
    );
  });
});

test.afterAll(async () => {
  await withDb(async (client) => {
    // FK-ordered teardown: `cross_sections.intersection_id` has no
    // `ON DELETE CASCADE` (see migration 028's comment on that column), so the
    // referencing cross-sections must go before the intersections they point
    // to. Deleting the corridor cascades its cross-sections away, so that
    // DELETE has to come first too.
    await client.query(`DELETE FROM corridors WHERE id = $1`, [corridorId]);
    await client.query(`DELETE FROM intersections WHERE id = ANY($1)`, [
      [startIntersectionId, endIntersectionId],
    ]);
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
    // The corridor page is now the real lane editor (Task 5); no
    // cross-section is selected yet, so it shows the "click to select"
    // prompt rather than the old placeholder text.
    await expect(page.getByText('Click a point on the map to select a cross-section.')).toBeVisible();
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
    // The intersection page is now a real form (bus gate / turn-conflict
    // type), not the old placeholder -- assert on the form instead.
    await expect(page.getByLabel('Bus gate')).toBeVisible();
  });
});
