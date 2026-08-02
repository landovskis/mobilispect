import { test, expect } from '@playwright/test';
import { ensureRegionHasBoundingBox } from './helpers/db';

/**
 * Corridor Builder WASM shell — WebGL graceful degradation (see design
 * spec's Error Handling table). Scoped to what this shell's map actually
 * depends on (WebGL, for MapLibre GL JS), distinct from the existing
 * canvas/Pointer-Events feature-detection pattern in feature-detection.spec.ts
 * (which covers the separate, canvas-based corridor segment editor).
 */

test.beforeAll(async () => {
  await ensureRegionHasBoundingBox();
});

test.describe('Corridor Builder: graceful degradation', () => {
  test('shows a fallback message when WebGL is unavailable', async ({ browser }) => {
    const context = await browser.newContext();
    await context.addInitScript(() => {
      // Force HTMLCanvasElement.getContext('webgl') to return null, simulating
      // a browser/GPU without WebGL support.
      const original = HTMLCanvasElement.prototype.getContext;
      // @ts-expect-error overriding for test purposes
      HTMLCanvasElement.prototype.getContext = function (type: string, ...rest: unknown[]) {
        if (type === 'webgl' || type === 'webgl2') {
          return null;
        }
        return original.apply(this, [type, ...rest]);
      };
    });
    const page = await context.newPage();

    await page.goto('/builder');
    await page.getByRole('button', { name: 'Create remix' }).click();
    await page.getByLabel('Metro region').selectOption({ label: 'Test Region' });
    await page.getByLabel('Remix name').fill('WebGL fallback test');
    await page.getByRole('button', { name: 'Create' }).click();

    await expect(page.getByText("doesn't support WebGL")).toBeVisible();
    await expect(page.locator('.maplibregl-canvas')).toHaveCount(0);

    await context.close();
  });
});
