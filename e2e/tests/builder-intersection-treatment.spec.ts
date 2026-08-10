import { test as base, expect } from '@playwright/test';
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
        [`Intersection Test Remix ${testInfo.testId}`]
      );
      remixId = remixResult.rows[0].id;

      const corridorResult = await client.query(
        `INSERT INTO corridors (name, geometry_source, remix_id) VALUES ($1, 'manual', $2) RETURNING id`,
        [`Intersection Test Corridor ${testInfo.testId}`, remixId]
      );
      corridorId = corridorResult.rows[0].id;

      const crossSectionResult = await client.query(
        `INSERT INTO cross_sections (corridor_id, position, lat, lon) VALUES ($1, 0, 45.500, -73.600) RETURNING id`,
        [corridorId]
      );
      crossSectionId = crossSectionResult.rows[0].id;
    });

    await use({ remixId, corridorId, crossSectionId });

    await withDb(async (client) => {
      await client.query(`DELETE FROM intersection_treatments WHERE cross_section_id = $1`, [crossSectionId]);
      await client.query(`DELETE FROM cross_sections WHERE corridor_id = $1`, [corridorId]);
      await client.query(`DELETE FROM corridors WHERE id = $1`, [corridorId]);
      await client.query(`DELETE FROM remixes WHERE id = $1`, [remixId]);
    });
  },
});

test.describe('Corridor Design: intersection treatment editor', () => {
  test('setting bus gate and turn-conflict type persists across reload', async ({
    page,
    seededCrossSection,
  }) => {
    const { remixId, crossSectionId } = seededCrossSection;
    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);

    await expect(page.getByLabel('Bus gate')).toHaveValue('');
    await expect(page.getByLabel('Turn-conflict type')).toHaveValue('');

    await page.getByLabel('Bus gate').selectOption('signal_controlled');
    await expect(page.getByLabel('Bus gate')).toHaveValue('signal_controlled');

    await page.getByLabel('Turn-conflict type').selectOption('right_in_right_out');
    await expect(page.getByLabel('Turn-conflict type')).toHaveValue('right_in_right_out');

    // Reload to confirm both edits persisted server-side, not just in local state.
    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);
    await expect(page.getByLabel('Bus gate')).toHaveValue('signal_controlled');
    await expect(page.getByLabel('Turn-conflict type')).toHaveValue('right_in_right_out');
  });

  test('clearing a previously-set field back to None persists', async ({ page, seededCrossSection }) => {
    const { remixId, crossSectionId } = seededCrossSection;
    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);

    await page.getByLabel('Bus gate').selectOption('yield_controlled');
    await expect(page.getByLabel('Bus gate')).toHaveValue('yield_controlled');

    await page.getByLabel('Bus gate').selectOption('');
    await expect(page.getByLabel('Bus gate')).toHaveValue('');

    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);
    await expect(page.getByLabel('Bus gate')).toHaveValue('');
  });

  test('back to map link returns to the region map', async ({ page, seededCrossSection }) => {
    const { remixId, crossSectionId } = seededCrossSection;
    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);

    await page.getByText('Back to map').click();
    await expect(page).toHaveURL(`/builder/remix/${remixId}`);
  });
});
