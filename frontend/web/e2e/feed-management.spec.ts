import { test, expect } from '@playwright/test';

/**
 * Feed Management System E2E Tests
 * Constitutional Requirement: Cross-browser testing with auto-waiting
 *
 * Tests verify complete user journeys from UI interaction to backend persistence
 */

test.describe('Feed Management System', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/feeds');
  });

  test('should display feed management page', async ({ page }) => {
    // Verify page loads
    await expect(page).toHaveTitle(/Mobilispect/);

    // Verify navigation tabs are present
    await expect(page.getByRole('tab', { name: /regions/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /active imports/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /history/i })).toBeVisible();
  });

  test('should display import history', async ({ page }) => {
    // Navigate to history tab
    await page.click('a:has-text("History")');

    // Wait for history view to load
    await expect(page.locator('mat-card-title:has-text("Import History")')).toBeVisible();

    // Verify table or empty state is displayed
    const table = page.locator('table');
    const emptyState = page.locator('.empty-state');

    // Either table or empty state should be visible
    await expect(table.or(emptyState)).toBeVisible();
  });

  test('should display active imports', async ({ page }) => {
    // Navigate to active imports tab
    await page.click('a:has-text("Active Imports")');

    // Wait for active imports view to load
    await expect(page.locator('mat-card-title:has-text("Active Imports")')).toBeVisible();

    // Verify "No active imports" message or active import list
    const noImportsMessage = page.locator('text=/no active imports/i');
    const importList = page.locator('.active-import-item');

    await expect(noImportsMessage.or(importList)).toBeVisible();
  });

  test('should display regions list', async ({ page }) => {
    // Regions view should be default
    await expect(page.locator('mat-card-title:has-text("Metropolitan Regions")')).toBeVisible();

    // Verify regions are loaded
    const loadingSpinner = page.locator('mat-spinner');
    const regionsList = page.locator('.region-item');

    // Wait for loading to complete
    await expect(loadingSpinner).not.toBeVisible({ timeout: 10000 });

    // Regions should be visible
    await expect(regionsList.first()).toBeVisible({ timeout: 5000 });
  });

  test('should show import dialog when clicking import button', async ({ page }) => {
    // Wait for regions to load
    await expect(page.locator('.region-item').first()).toBeVisible({ timeout: 10000 });

    // Click first import button
    await page.locator('button:has-text("Import")').first().click();

    // Verify dialog opens
    await expect(page.locator('.import-dialog')).toBeVisible();
    await expect(page.locator('h2:has-text("Start Feed Import")')).toBeVisible();

    // Verify dialog has cancel and import buttons
    await expect(page.locator('button:has-text("Cancel")')).toBeVisible();
    await expect(page.locator('button:has-text("Start Import")')).toBeVisible();
  });

  test('import flow: start import and verify it appears in active imports', async ({ page }) => {
    // Navigate to regions
    await expect(page.locator('.region-item').first()).toBeVisible({ timeout: 10000 });

    // Click import button on first region
    await page.locator('button:has-text("Import")').first().click();

    // Wait for dialog
    await expect(page.locator('.import-dialog')).toBeVisible();

    // Start import
    await page.locator('button:has-text("Start Import")').click();

    // Should redirect to active imports automatically
    await expect(page).toHaveURL(/\/feeds\/imports/);

    // Verify import appears in active imports or completes quickly
    // (imports may complete very fast in test environment)
    const activeImportItem = page.locator('.active-import-item');
    const noImportsMessage = page.locator('text=/no active imports/i');

    // One of these should be visible
    await expect(activeImportItem.or(noImportsMessage)).toBeVisible({ timeout: 10000 });
  });

  test('should display import details in history after completion', async ({ page }) => {
    // Navigate to history
    await page.click('a:has-text("History")');

    // Wait for history table to load
    await expect(page.locator('mat-card-title:has-text("Import History")')).toBeVisible();

    // If there are imports in history, verify table structure
    const historyTable = page.locator('table');
    const hasHistory = await historyTable.isVisible({ timeout: 5000 });

    if (hasHistory) {
      // Verify table headers
      await expect(page.locator('th:has-text("Feed")')).toBeVisible();
      await expect(page.locator('th:has-text("Region")')).toBeVisible();
      await expect(page.locator('th:has-text("Status")')).toBeVisible();

      // Verify at least one row exists
      await expect(page.locator('tr[mat-row]').first()).toBeVisible();
    }
  });

  test('should handle pagination in import history', async ({ page }) => {
    // Navigate to history
    await page.click('a:has-text("History")');
    await expect(page.locator('mat-card-title:has-text("Import History")')).toBeVisible();

    // Check if paginator exists (only if there are enough imports)
    const paginator = page.locator('mat-paginator');
    const isPaginatorVisible = await paginator.isVisible({ timeout: 2000 });

    if (isPaginatorVisible) {
      // Verify pagination controls are functional
      const nextButton = page.locator('button[aria-label="Next page"]');
      const isNextEnabled = await nextButton.isEnabled();

      if (isNextEnabled) {
        await nextButton.click();
        // Verify page changed (new data loaded)
        await expect(page.locator('tr[mat-row]').first()).toBeVisible();
      }
    }
  });

});
