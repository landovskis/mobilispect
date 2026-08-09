import { test, expect } from '@playwright/test';
import { ensureRegionHasBoundingBox } from './helpers/db';

/**
 * Corridor Builder WASM shell — create-remix flow (see
 * docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md,
 * "User Flow" steps 1-2).
 */

test.beforeAll(async () => {
  await ensureRegionHasBoundingBox();
});

test.describe('Corridor Builder: create remix', () => {
  test('creating a remix navigates to its region map', async ({ page }) => {
    await page.goto('/builder');

    await page.getByRole('button', { name: 'Create remix' }).click();
    await page.getByLabel('Metro region').selectOption({ label: 'Test Region' });
    await page.getByLabel('Remix name').fill('Downtown bike lane proposal');
    await page.getByRole('button', { name: 'Create' }).click();

    await expect(page).toHaveURL(/\/builder\/remix\/\d+$/);
    await expect(page.locator('.maplibregl-canvas')).toBeVisible();
  });

  test('blank remix name is rejected without navigating', async ({ page }) => {
    await page.goto('/builder');

    await page.getByRole('button', { name: 'Create remix' }).click();
    await page.getByLabel('Metro region').selectOption({ label: 'Test Region' });
    await page.getByRole('button', { name: 'Create' }).click();

    await expect(page).toHaveURL('/builder');
    await expect(page.getByText('name must not be blank')).toBeVisible();
  });
});
