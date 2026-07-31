import { test, expect } from '@playwright/test';

/**
 * TC-REQ-007-1: Corridor import flow succeeds identically across Chromium,
 * Firefox, and WebKit (test-plan-corridor-segment-editor.md, REQ-007
 * Functional Tests).
 *
 * PROVISIONAL selectors/route (Loop A): REQ-001's routes are implemented in
 * `crates/server/src/web/corridor_import.rs` but not yet registered in the
 * Axum router, and no template/client-side JS exists for the "New
 * Corridor" entry point (see IMPLEMENTATION_CHECKLIST.md's Loop A note).
 * This spec is written against the intended UI described in the SDD's
 * REQ-001 UI Mockup — a "New Corridor" card/modal with a corridor-name
 * field, an address/place search field, a schematic map region, a
 * way-segment-count badge, and an Import button — using best-guess
 * `data-testid` attributes. It will fail with a navigation/timeout error
 * today; that is the correct Loop A state, not a reason to skip real
 * assertions. Loop B must reconcile these selectors against its actual
 * templates.
 *
 * Cross-engine "identical output" strategy: Playwright runs each configured
 * project (chromium/firefox/webkit — ../playwright.config.ts) as an
 * isolated worker/process, so there's no direct mechanism for one worker to
 * compare its result against another's within a single spec run. Instead,
 * this test mocks the network response for `fetch-preview` with a fixed,
 * deterministic GeoJSON payload (consistent with the Test Plan's
 * Environment Requirements, which call for a mocked Overpass API in REQ-001
 * tests generally) and asserts the resulting corridor geometry and
 * attribution visibility against a FIXED expected value. Because every
 * engine receives byte-identical input, each engine independently matching
 * the same expected output *is* the "identical across all three engines"
 * property the test plan asks for, without requiring shared state between
 * isolated per-project test workers.
 */

const NEW_CORRIDOR_PATH = '/corridors/new'; // PROVISIONAL — see file header.

// Fixed, deterministic single-way path standing in for a real Overpass
// fetch-preview response (analogous in shape to the backend's
// `valid_connected_path.geojson` fixture used by TC-REQ-001-1, but a
// smaller 3-point path is sufficient here since this test exercises UI
// parity across engines, not import-geometry validation itself).
const MOCK_FETCH_PREVIEW_RESPONSE = {
  source_geometry: {
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        properties: { osm_way_id: 111 },
        geometry: {
          type: 'LineString',
          coordinates: [
            [-73.5878, 45.5088],
            [-73.5865, 45.5091],
            [-73.5851, 45.5094],
          ],
        },
      },
    ],
  },
  way_segment_count: 1,
};

const EXPECTED_COORDINATES = [
  { lat: '45.5088', lon: '-73.5878' },
  { lat: '45.5091', lon: '-73.5865' },
  { lat: '45.5094', lon: '-73.5851' },
];

test.describe('REQ-007 / TC-REQ-007-1: import flow cross-engine parity', () => {
  test('import flow produces identical corridor geometry and visible attribution', async ({
    page,
  }) => {
    const jsErrors: string[] = [];
    page.on('pageerror', (err) => jsErrors.push(err.message));

    await page.route('**/api/corridors/import/fetch-preview', async (route) => {
      await route.fulfill({ json: MOCK_FETCH_PREVIEW_RESPONSE });
    });

    await page.goto(NEW_CORRIDOR_PATH);

    await page.getByTestId('corridor-name-input').fill('Test Corridor A');
    await page.getByTestId('corridor-search-input').fill('Peel St, Montreal');
    // Triggers the fetch-preview request per the SDD's REQ-001 flow diagram
    // (A: bounding-box/address search -> B: POST fetch-preview).
    await page.getByTestId('corridor-search-input').press('Enter');

    await expect(page.getByTestId('way-segments-badge')).toHaveText('1 way segment selected');

    const importButton = page.getByTestId('import-button');
    await expect(importButton).toBeEnabled();
    await importButton.click();

    // REQ-003: OSM attribution must be present and visible after import
    // completes, per TC-REQ-007-1's expected result.
    await expect(page.locator('.osm-attribution')).toBeVisible();

    // Final corridor geometry, captured from the editor's rendered
    // cross-section sequence (one row per cross_sections row, data-lat/
    // data-lon attributes exposing the persisted coordinates), must match
    // the fixed expected coordinates exactly and in order.
    const rows = page.getByTestId('cross-section-row');
    await expect(rows).toHaveCount(EXPECTED_COORDINATES.length);

    for (let i = 0; i < EXPECTED_COORDINATES.length; i++) {
      const row = rows.nth(i);
      await expect(row).toHaveAttribute('data-lat', EXPECTED_COORDINATES[i].lat);
      await expect(row).toHaveAttribute('data-lon', EXPECTED_COORDINATES[i].lon);
    }

    expect(jsErrors, `Unhandled JS exceptions: ${jsErrors.join('; ')}`).toHaveLength(0);
  });
});
