import { test, expect } from '@playwright/test';

/**
 * REQ-007 feature-detection smoke tests (sdd-corridor-segment-editor.md,
 * REQ-007 "Testing" section, feature-detection checks 1-3). These verify
 * that each of Playwright's three configured engines
 * (chromium/firefox/webkit — see ../playwright.config.ts) exposes the three
 * browser APIs the corridor editor's canvas/pointer-driven flows depend on:
 * a canvas 2D rendering context, the Pointer Events API (including pointer
 * capture), and the fetch API.
 *
 * Deliberately independent of the corridor editor's own pages/markup: these
 * assert engine-level capability, not application behavior, using a blank
 * page rather than a real corridor-editor route. That means they can run
 * and give real, meaningful signal *today* even though REQ-001–006's routes
 * and templates don't exist yet in this repository (see the Loop A note in
 * IMPLEMENTATION_CHECKLIST.md). Once Loop B ships the real editor,
 * IMP-REQ-007-09's client-side feature-detection *guard* — the app code
 * that reads these same APIs and renders the Lumina `.alert` fallback when
 * they're missing — is exercised separately by
 * graceful-degradation.spec.ts.
 *
 * Playwright's project matrix (../playwright.config.ts) runs this file
 * once per configured engine automatically; no per-engine duplication is
 * needed in this file.
 */

test.describe('REQ-007 feature detection', () => {
  test('canvas 2D context is available', async ({ page }) => {
    await page.goto('about:blank');

    const hasWorkingCanvas2dContext = await page.evaluate(() => {
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d');
      return (
        ctx !== null &&
        typeof ctx.beginPath === 'function' &&
        typeof ctx.arc === 'function' &&
        typeof ctx.stroke === 'function'
      );
    });

    expect(hasWorkingCanvas2dContext).toBe(true);
  });

  test('Pointer Events API and pointer capture are available', async ({ page }) => {
    await page.goto('about:blank');

    const pointerSupport = await page.evaluate(() => {
      const el = document.createElement('div');
      return {
        hasPointerEvent: typeof window.PointerEvent === 'function',
        hasSetPointerCapture: typeof el.setPointerCapture === 'function',
        hasReleasePointerCapture: typeof el.releasePointerCapture === 'function',
        hasHasPointerCapture: typeof el.hasPointerCapture === 'function',
      };
    });

    expect(pointerSupport.hasPointerEvent).toBe(true);
    expect(pointerSupport.hasSetPointerCapture).toBe(true);
    expect(pointerSupport.hasReleasePointerCapture).toBe(true);
    expect(pointerSupport.hasHasPointerCapture).toBe(true);
  });

  test('fetch API is available', async ({ page }) => {
    await page.goto('about:blank');

    const fetchSupport = await page.evaluate(() => ({
      hasFetch: typeof window.fetch === 'function',
      hasRequest: typeof window.Request === 'function',
      hasResponse: typeof window.Response === 'function',
    }));

    expect(fetchSupport.hasFetch).toBe(true);
    expect(fetchSupport.hasRequest).toBe(true);
    expect(fetchSupport.hasResponse).toBe(true);
  });
});
