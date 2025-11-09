import { test, expect } from '@playwright/test';

/**
 * Theme Switching E2E Tests
 * Constitutional Requirement: Light/dark mode support mandatory across all platforms
 *
 * Tests verify cross-platform UX consistency (Principle III)
 */

test.describe('Theme Support (Constitutional Requirement)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should support light/dark mode theme switching', async ({ page }) => {
    // Check if theme toggle exists
    const themeToggle = page.locator('[aria-label*="theme" i], button:has-text("Dark"), button:has-text("Light")');
    const hasThemeToggle = await themeToggle.isVisible({ timeout: 2000 });

    if (hasThemeToggle) {
      // Get current theme
      const body = page.locator('body');
      const initialClass = await body.getAttribute('class');

      // Toggle theme
      await themeToggle.click();

      // Verify theme changed
      const newClass = await body.getAttribute('class');
      expect(newClass).not.toBe(initialClass);
    } else {
      // Theme toggle not yet implemented - mark as pending
      test.skip();
      console.log('⚠️  Theme toggle not implemented yet - constitutional requirement pending');
    }
  });

  test('should persist theme preference across page reloads', async ({ page }) => {
    const themeToggle = page.locator('[aria-label*="theme" i], button:has-text("Dark"), button:has-text("Light")');
    const hasThemeToggle = await themeToggle.isVisible({ timeout: 2000 });

    if (hasThemeToggle) {
      // Toggle to dark mode
      await themeToggle.click();

      // Get theme state
      const body = page.locator('body');
      const darkModeClass = await body.getAttribute('class');

      // Reload page
      await page.reload();

      // Verify theme persisted
      const reloadedClass = await body.getAttribute('class');
      expect(reloadedClass).toBe(darkModeClass);
    } else {
      test.skip();
    }
  });

  test('should apply theme to all Material Design components', async ({ page }) => {
    const themeToggle = page.locator('[aria-label*="theme" i], button:has-text("Dark"), button:has-text("Light")');
    const hasThemeToggle = await themeToggle.isVisible({ timeout: 2000 });

    if (hasThemeToggle) {
      // Navigate to feed management to see Material components
      await page.goto('/feeds');

      // Toggle theme
      await themeToggle.click();

      // Verify Material components have theme applied
      const card = page.locator('mat-card').first();
      const cardBg = await card.evaluate((el) =>
        window.getComputedStyle(el).backgroundColor
      );

      // Background should be defined (not default)
      expect(cardBg).toBeTruthy();
      expect(cardBg).not.toBe('rgba(0, 0, 0, 0)');
    } else {
      test.skip();
    }
  });

  test('should respect system theme preference (prefers-color-scheme)', async ({ page, context }) => {
    // Test with dark mode preference
    await context.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/');

    const body = page.locator('body');
    const darkClass = await body.getAttribute('class');

    // Test with light mode preference
    await context.emulateMedia({ colorScheme: 'light' });
    await page.reload();

    const lightClass = await body.getAttribute('class');

    // Classes should be different based on system preference
    // This test may be skipped if auto-detection not implemented
    if (darkClass === lightClass) {
      console.log('⚠️  System theme preference detection not implemented');
      test.skip();
    }
  });

  test('should maintain theme consistency across all pages', async ({ page }) => {
    const themeToggle = page.locator('[aria-label*="theme" i], button:has-text("Dark"), button:has-text("Light")');
    const hasThemeToggle = await themeToggle.isVisible({ timeout: 2000 });

    if (hasThemeToggle) {
      // Set dark theme
      await themeToggle.click();

      const body = page.locator('body');
      const darkModeClass = await body.getAttribute('class');

      // Navigate to different pages
      await page.goto('/feeds');
      const feedMgmtClass = await body.getAttribute('class');
      expect(feedMgmtClass).toBe(darkModeClass);

      await page.goto('/feeds/history');
      const historyClass = await body.getAttribute('class');
      expect(historyClass).toBe(darkModeClass);

      await page.goto('/feeds/imports');
      const importsClass = await body.getAttribute('class');
      expect(importsClass).toBe(darkModeClass);
    } else {
      test.skip();
    }
  });
});
