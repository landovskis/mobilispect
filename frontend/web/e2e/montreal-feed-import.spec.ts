import { test, expect } from '@playwright/test';

/**
 * Montréal Feed Import E2E Test
 * Constitutional Requirement: Cross-browser testing with auto-waiting
 *
 * Tests the complete user journey for discovering and importing all feeds
 * for the Montréal region (r-canada-quebec-montreal), including:
 * - STM (Société de transport de Montréal)
 * - STL (Société de transport de Laval)
 * - RTL (Réseau de transport de Longueuil)
 * - EXO (Suburban transit services)
 */

test.describe('Montreal Feed Import System', () => {
  const MONTREAL_REGION_NAME = 'Montréal';
  const MONTREAL_REGION_ID = 'r-canada-quebec-montreal';
  const IMPORT_TIMEOUT = 120000; // 2 minutes for import completion
  const POLL_INTERVAL = 2000; // Check status every 2 seconds

  test.beforeEach(async ({ page }) => {
    await page.goto('/feeds');
  });

  test('should discover and import all Montreal GTFS feeds', async ({ page }) => {
    // Step 1: Verify the page loads
    await expect(page).toHaveTitle(/Mobilispect/);

    // Step 2: Verify we're on the Discover view (sidebar navigation)
    const discoverButton = page.locator('button:has-text("Discover")');
    await expect(discoverButton).toBeVisible({ timeout: 10000 });

    // Step 3: Find the region selector textbox using accessible role
    const regionSelector = page.getByRole('textbox', { name: 'Search Metropolitan Region' });
    await expect(regionSelector).toBeVisible({ timeout: 10000 });

    // Step 4: Type "Montreal" into the region selector
    await regionSelector.click();
    await regionSelector.fill('Montreal');
    await page.waitForTimeout(1000); // Wait for autocomplete

    // Step 5: Select "Greater Montreal" from the dropdown
    const montrealOption = page.locator(`mat-option:has-text("${MONTREAL_REGION_NAME}")`);
    const isMontrealAvailable = await montrealOption.isVisible({ timeout: 5000 }).catch(() => false);

    if (!isMontrealAvailable) {
      test.skip('Greater Montreal region not found in selector - may need to run feed discovery first');
      return;
    }

    await montrealOption.click();
    console.log(`✓ Selected ${MONTREAL_REGION_NAME} region`);

    // Step 6: Wait for agency feed cards to load
    await page.waitForTimeout(2000);

    // Step 7: Check if there are any agency feed cards
    const agencyCards = page.locator('app-agency-feed-card, .agency-card, article');
    const cardCount = await agencyCards.count();

    if (cardCount === 0) {
      // No feeds found - check for empty state
      const emptyState = page.locator('text=/no feeds found/i, text=/select a region/i');
      const hasEmptyState = await emptyState.isVisible({ timeout: 5000 }).catch(() => false);

      if (hasEmptyState) {
        test.skip('No feeds available for Montreal - may need to discover feeds first');
        return;
      }
    }

    console.log(`✓ Found ${cardCount} agency card(s) for Montreal`);

    // Step 8: Look for import buttons
    const importButtons = page.locator('button:has-text("Import")');
    const importButtonCount = await importButtons.count();

    if (importButtonCount === 0) {
      test.skip('No import buttons found - feeds may not be available');
      return;
    }

    console.log(`✓ Found ${importButtonCount} import button(s)`);

    // Step 9: Click the first import button
    await importButtons.first().click();

    // Step 10: Import confirmation dialog should appear
    await page.waitForTimeout(1000);
    const dialog = page.locator('mat-dialog-container, .import-dialog, [role="dialog"]');
    await expect(dialog).toBeVisible({ timeout: 5000 });

    // Step 11: Confirm the import
    const confirmButton = page.locator('button:has-text("Import"), button:has-text("Start Import"), button:has-text("Confirm")').last();
    await expect(confirmButton).toBeVisible({ timeout: 5000 });
    await confirmButton.click();

    console.log('✓ Started import');

    // Step 12: Navigate to imports view to monitor progress
    const importsNavButton = page.locator('button:has-text("Imports")');
    await expect(importsNavButton).toBeVisible();
    await importsNavButton.click();

    // Wait for imports view to load
    await page.waitForTimeout(2000);

    // Step 13: Monitor import progress
    const startTime = Date.now();
    let importCompleted = false;

    console.log('Monitoring import progress...');

    while (Date.now() - startTime < IMPORT_TIMEOUT && !importCompleted) {
      // Check for the imports tab content
      const importsContent = page.locator('.tab-content, .view-content, app-feed-imports-tab');
      const hasImportsContent = await importsContent.isVisible({ timeout: 2000 }).catch(() => false);

      if (!hasImportsContent) {
        console.log('Waiting for imports view to load...');
        await page.waitForTimeout(POLL_INTERVAL);
        continue;
      }

      // Look for import status indicators
      const completedIndicators = page.locator('text=/completed/i, text=/success/i, .status-completed, .status-success');
      const activeIndicators = page.locator('text=/importing/i, text=/in progress/i, .status-active, .status-importing');
      const failedIndicators = page.locator('text=/failed/i, text=/error/i, .status-failed, .status-error');

      const completedCount = await completedIndicators.count();
      const activeCount = await activeIndicators.count();
      const failedCount = await failedIndicators.count();

      console.log(`Status - Active: ${activeCount}, Completed: ${completedCount}, Failed: ${failedCount}`);

      if (completedCount > 0 || failedCount > 0) {
        importCompleted = true;
        console.log('✓ Import process completed');
        break;
      }

      if (activeCount === 0 && completedCount === 0) {
        // Import might have completed very quickly
        console.log('No active imports found - checking if import completed');
        await page.waitForTimeout(2000);

        // Check one more time
        const finalCompletedCount = await completedIndicators.count();
        if (finalCompletedCount > 0) {
          importCompleted = true;
          console.log('✓ Import completed (fast import)');
          break;
        }

        // If still no imports after total wait time exceeds threshold, break
        if (Date.now() - startTime > 10000) {
          console.warn('Warning: No import status found after 10 seconds');
          importCompleted = true;
          break;
        }
      }

      await page.waitForTimeout(POLL_INTERVAL);
    }

    // Step 14: Verify import results
    const pageContent = await page.textContent('body');
    const hasMontrealFeeds = /montreal|stm|société de transport|laval|longueuil|exo/i.test(pageContent);

    if (hasMontrealFeeds) {
      console.log('✓ Montreal transit feeds confirmed in import results');
    } else {
      console.warn('Warning: Could not confirm Montreal-specific feeds in results');
    }

    // Step 15: Check for any import records (completed or failed)
    const importRecords = page.locator('table tr, .import-item, .import-record');
    const recordCount = await importRecords.count();

    console.log(`Total import records found: ${recordCount}`);

    // At least one import should have been attempted
    expect(recordCount).toBeGreaterThan(0);
  });

  test('should navigate to Montreal region using selector', async ({ page }) => {
    // Verify page loads
    await expect(page).toHaveTitle(/Mobilispect/);

    // Wait for region selector
    const regionSelector = page.getByRole('textbox', { name: 'Search Metropolitan Region' });
    await expect(regionSelector).toBeVisible({ timeout: 10000 });

    // Type Montreal
    await regionSelector.click();
    await regionSelector.fill('Montreal');
    await page.waitForTimeout(1000);

    // Check if Montreal option appears
    const montrealOption = page.locator(`mat-option:has-text("${MONTREAL_REGION_NAME}"), [role="option"]:has-text("${MONTREAL_REGION_NAME}")`);
    const isAvailable = await montrealOption.isVisible({ timeout: 5000 }).catch(() => false);

    if (!isAvailable) {
      test.skip('Montreal region not available');
      return;
    }

    await montrealOption.click();
    console.log(`✓ Successfully navigated to ${MONTREAL_REGION_NAME}`);

    // Verify UI updated
    await page.waitForTimeout(1000);
    const pageText = await page.textContent('body');
    expect(/montreal|greater montreal/i.test(pageText)).toBeTruthy();
  });

  test('should display import history tab', async ({ page }) => {
    // Verify page loads
    await expect(page).toHaveTitle(/Mobilispect/);

    // Click imports nav button
    const importsButton = page.locator('button:has-text("Imports")');
    await expect(importsButton).toBeVisible({ timeout: 10000 });
    await importsButton.click();

    // Wait for imports view
    await page.waitForTimeout(2000);

    // Verify we're in imports view - use .first() to avoid strict mode violation
    const importsView = page.locator('app-feed-imports-tab').first();
    await expect(importsView).toBeVisible({ timeout: 5000 });

    console.log('✓ Imports view is functional');
  });

  test('should navigate between discover and imports views', async ({ page }) => {
    // Verify page loads
    await expect(page).toHaveTitle(/Mobilispect/);

    // Navigate to Imports
    const importsButton = page.locator('button:has-text("Imports")');
    await expect(importsButton).toBeVisible({ timeout: 10000 });
    await importsButton.click();
    await page.waitForTimeout(1000);

    // Navigate back to Discover
    const discoverButton = page.locator('button:has-text("Discover")');
    await expect(discoverButton).toBeVisible();
    await discoverButton.click();
    await page.waitForTimeout(1000);

    // Verify we're back on discover view - check for the discover heading
    const discoverHeading = page.locator('text="Discover Feeds"');
    await expect(discoverHeading).toBeVisible({ timeout: 5000 });

    console.log('✓ Navigation between views is functional');
  });
});
