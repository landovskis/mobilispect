import { test as base, expect, type Page } from '@playwright/test';
import { ensureRegionHasBoundingBox, withDb } from './helpers/db';

type Fixtures = {
  seededCrossSection: { remixId: number; corridorId: number; crossSectionId: number };
};

const test = base.extend<Fixtures>({
  seededCrossSection: async ({}, use, testInfo) => {
    await ensureRegionHasBoundingBox();
    let remixId = 0;
    let corridorId = 0;
    let crossSectionId = 0;

    await withDb(async (client) => {
      const remixResult = await client.query(
        `INSERT INTO remixes (name, region_id) VALUES ($1, 1) RETURNING id`,
        [`Lane Editor Test Remix ${testInfo.testId}`]
      );
      remixId = remixResult.rows[0].id;

      const corridorResult = await client.query(
        `INSERT INTO corridors (name, geometry_source, remix_id) VALUES ($1, 'manual', $2) RETURNING id`,
        [`Lane Editor Test Corridor ${testInfo.testId}`, remixId]
      );
      corridorId = corridorResult.rows[0].id;

      const crossSectionResult = await client.query(
        `INSERT INTO cross_sections (corridor_id, position, lat, lon, label) VALUES ($1, 0, 45.500, -73.600, 'Main St @ 5th') RETURNING id`,
        [corridorId]
      );
      crossSectionId = crossSectionResult.rows[0].id;

      const laneResult = await client.query(
        `INSERT INTO lanes (cross_section_id, position, lane_type, width_meters, direction) VALUES ($1, 1, 'travel', 3.0, 'forward') RETURNING id`,
        [crossSectionId]
      );
      const laneId = laneResult.rows[0].id;

      // Matches what a freshly-inserted 'travel' lane actually gets via
      // default_access_rule_for(LaneType::Travel) in
      // crates/core/src/corridor_design/lanes.rs: car + emergency, no time
      // window. Without this row, the seeded lane has zero access rules and
      // the "adding and removing an access rule" test's initial
      // toHaveCount(1) assertion below would fail for the wrong reason.
      await client.query(
        `INSERT INTO lane_access_rules (lane_id, days, start_time, end_time, allowed_modes) VALUES ($1, NULL, NULL, NULL, $2)`,
        [laneId, ['car', 'emergency']]
      );
    });

    await use({ remixId, corridorId, crossSectionId });

    await withDb(async (client) => {
      await client.query(
        `DELETE FROM lane_access_rules WHERE lane_id IN (SELECT id FROM lanes WHERE cross_section_id = $1)`,
        [crossSectionId]
      );
      await client.query(`DELETE FROM lanes WHERE cross_section_id = $1`, [crossSectionId]);
      await client.query(`DELETE FROM cross_sections WHERE corridor_id = $1`, [corridorId]);
      await client.query(`DELETE FROM corridors WHERE id = $1`, [corridorId]);
      await client.query(`DELETE FROM remixes WHERE id = $1`, [remixId]);
    });
  },
});

/**
 * Navigates to the corridor page and clicks the one seeded cross-section
 * (at lon -73.6, lat 45.5). Written before the WASM UI for it exists
 * (Task 5), so every test here fails today for the correct reason, matching
 * this repo's established precedent.
 */
async function selectFirstCrossSection(page: Page, corridorId: number, remixId: number) {
  await page.goto(`/builder/remix/${remixId}/corridor/${corridorId}`);
  await page.waitForFunction(() => (window as any).__corridorBuilderMap !== undefined);
  await page.waitForFunction(
    () => (window as any).__corridorBuilderMap.getSource('cross-section-points') !== undefined,
  );
  // The click handler resolves the clicked cross-section via
  // `queryRenderedFeatures` restricted to the 'cross-section-points' layer
  // (see `extract_clicked_cross_section_id` in
  // crates/corridor_builder_web/src/pages/corridor.rs). The source existing
  // is not sufficient -- the layer must have completed at least one render
  // pass before a click will find the feature, so wait for that directly.
  await page.waitForFunction(
    () =>
      (window as any).__corridorBuilderMap.queryRenderedFeatures({
        layers: ['cross-section-points'],
      }).length > 0,
  );
  const px = await page.evaluate(() => {
    const point = (window as any).__corridorBuilderMap.project([-73.6, 45.5]);
    return { x: point.x, y: point.y };
  });
  await page.locator('.maplibregl-canvas').click({ position: px });
}

test.describe('Corridor Design: lane editor', () => {
  test('selecting a cross-section shows its label and lane diagram, editing the label persists', async ({
    page,
    seededCrossSection,
  }) => {
    const { remixId, corridorId } = seededCrossSection;
    await selectFirstCrossSection(page, corridorId, remixId);

    await expect(page.getByLabel('Cross-section label')).toHaveValue('Main St @ 5th');
    await expect(page.getByText('Travel')).toBeVisible();

    await page.getByLabel('Cross-section label').fill('Main St @ 5th Ave');
    await page.getByLabel('Cross-section label').blur();
    await expect(page.getByLabel('Cross-section label')).toHaveValue('Main St @ 5th Ave');

    // Reload to confirm the edit actually persisted server-side, not just in local state.
    await selectFirstCrossSection(page, corridorId, remixId);
    await expect(page.getByLabel('Cross-section label')).toHaveValue('Main St @ 5th Ave');
  });

  test('clicking a lane opens its edit panel; editing width, type, and direction persist', async ({
    page,
    seededCrossSection,
  }) => {
    const { remixId, corridorId } = seededCrossSection;
    await selectFirstCrossSection(page, corridorId, remixId);

    await page.getByText('Travel').click();
    await expect(page.getByLabel('Width (meters)')).toHaveValue('3');

    await page.getByLabel('Width (meters)').fill('3.5');
    await page.getByLabel('Width (meters)').blur();
    await expect(page.getByLabel('Width (meters)')).toHaveValue('3.5');

    await page.getByLabel('Lane type').selectOption('turn');
    // Scoped to `.xs-lane`: the lane-type <select> also has an <option>
    // labeled "Turn" (all lane types are always present as options), so an
    // unscoped `getByText('Turn')` is ambiguous once the edit panel is open.
    await expect(page.locator('.xs-lane').getByText('Turn')).toBeVisible();

    await page.getByLabel('Direction').selectOption('backward');
    await expect(page.getByLabel('Direction')).toHaveValue('backward');
  });

  test('inserting a lane via a gap control adds it to the diagram', async ({ page, seededCrossSection }) => {
    const { remixId, corridorId } = seededCrossSection;
    await selectFirstCrossSection(page, corridorId, remixId);

    await expect(page.locator('.xs-lane')).toHaveCount(1);
    await page.getByLabel('Add lane at start').click();
    await expect(page.locator('.xs-lane')).toHaveCount(2);
  });

  test('removing a lane deletes it from the diagram', async ({ page, seededCrossSection }) => {
    const { remixId, corridorId } = seededCrossSection;
    await selectFirstCrossSection(page, corridorId, remixId);

    await page.getByText('Travel').click();
    await page.getByLabel('Remove lane').click();
    await expect(page.locator('.xs-lane')).toHaveCount(0);
  });

  test('adding and removing an access rule persists', async ({ page, seededCrossSection }) => {
    const { remixId, corridorId } = seededCrossSection;
    await selectFirstCrossSection(page, corridorId, remixId);
    await page.getByText('Travel').click();

    await expect(page.getByLabel('Allowed modes')).toHaveCount(1);
    await page.getByText('+ Add time window').click();
    await expect(page.getByLabel('Allowed modes')).toHaveCount(2);

    await page.getByLabel('Allowed modes').nth(1).fill('transit,emergency');
    await page.getByLabel('Allowed modes').nth(1).blur();
    await expect(page.getByLabel('Allowed modes').nth(1)).toHaveValue('transit,emergency');

    await page.getByLabel('Remove access rule').nth(1).click();
    await expect(page.getByLabel('Allowed modes')).toHaveCount(1);
  });
});
