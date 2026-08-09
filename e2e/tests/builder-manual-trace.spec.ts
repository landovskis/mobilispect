import { test, expect } from '@playwright/test';
import { ensureRegionHasBoundingBox, withDb } from './helpers/db';

/**
 * Corridor Design — manual trace flow (see
 * docs/superpowers/specs/2026-08-03-corridor-segment-editor-design.md,
 * "Manual trace (REQ-002)"). Written before the WASM UI for it exists (Task 9),
 * so it fails today for the correct reason, matching this repo's established
 * precedent.
 */

let remixId: number;

test.beforeAll(async () => {
  await ensureRegionHasBoundingBox();
  await withDb(async (client) => {
    const result = await client.query(
      `INSERT INTO remixes (name, region_id) VALUES ('Manual Trace Test Remix', 1) RETURNING id`
    );
    remixId = result.rows[0].id;
  });
});

test.afterAll(async () => {
  await withDb(async (client) => {
    await client.query(`DELETE FROM cross_sections WHERE corridor_id IN (SELECT id FROM corridors WHERE remix_id = $1)`, [remixId]);
    await client.query(`DELETE FROM corridors WHERE remix_id = $1`, [remixId]);
    await client.query(`DELETE FROM remixes WHERE id = $1`, [remixId]);
  });
});

test.describe('Corridor Design: manual trace', () => {
  test('tracing a corridor by clicking the map persists it and navigates to its editor page', async ({
    page,
  }) => {
    await page.goto(`/builder/remix/${remixId}`);
    await page.waitForSelector('.maplibregl-canvas');

    await page.getByRole('button', { name: 'Add corridor' }).click();
    await page.getByRole('button', { name: 'Manual trace' }).click();
    await page.getByLabel('Corridor name').fill('Test Traced Corridor');
    await page.getByRole('button', { name: 'Start tracing' }).click();

    await page.waitForFunction(() => (window as any).__corridorBuilderMap !== undefined);
    const canvas = page.locator('.maplibregl-canvas');
    await canvas.click({ position: { x: 300, y: 200 } });
    await canvas.click({ position: { x: 320, y: 220 } });
    await canvas.click({ position: { x: 340, y: 240 } });

    await expect(
      page.getByText('Click the map to place points (3 placed so far, minimum 2).')
    ).toBeVisible();

    await page.getByRole('button', { name: 'Finish trace' }).click();

    await expect(page).toHaveURL(new RegExp(`/builder/remix/${remixId}/corridor/\\d+$`));
    // The corridor page is now the real lane editor (Task 5); a freshly
    // traced corridor has no cross-sections yet, so it shows the "click to
    // select" prompt rather than the old placeholder text.
    await expect(page.getByText('Click a point on the map to select a cross-section.')).toBeVisible();
  });

  test('finishing a trace with fewer than two points shows an error and stays on the trace screen', async ({
    page,
  }) => {
    await page.goto(`/builder/remix/${remixId}`);
    await page.waitForSelector('.maplibregl-canvas');

    await page.getByRole('button', { name: 'Add corridor' }).click();
    await page.getByRole('button', { name: 'Manual trace' }).click();
    await page.getByLabel('Corridor name').fill('Too Short Corridor');
    await page.getByRole('button', { name: 'Start tracing' }).click();

    await page.waitForFunction(() => (window as any).__corridorBuilderMap !== undefined);
    await page.locator('.maplibregl-canvas').click({ position: { x: 300, y: 200 } });

    await page.getByRole('button', { name: 'Finish trace' }).click();

    await expect(page.getByText('not enough points')).toBeVisible();
  });
});
