import { test, expect } from '@playwright/test';

/**
 * TC-REQ-007-2: Editor functions correctly on the minimum-supported
 * (current-release) version of each target browser
 * (test-plan-corridor-segment-editor.md, REQ-007 Functional Tests).
 *
 * This test exercises REQ-002's manual click-to-trace flow (4 points) via
 * Playwright's chromium/firefox/webkit project matrix
 * (../playwright.config.ts) — standing in for Chrome/Firefox/Edge per the
 * SDD's REQ-007 Design Approach (Chromium covers both Chrome and Edge).
 * Real Safari is NOT covered by this automated spec: per the SDD's REQ-007
 * Open Question 3 and the Test Plan's Risks table, WebKit-via-Playwright is
 * only an approximation of Safari, and a periodic manual spot-check on real
 * Safari is required separately and is not automatable here.
 *
 * PROVISIONAL selectors/route (Loop A): REQ-002's manual-trace route,
 * template, and client-side JS do not exist in the repository yet (its
 * handlers are implemented in `crates/server/src/web/corridor_design.rs`
 * but not registered in the Axum router — see IMPLEMENTATION_CHECKLIST.md's
 * Loop A note). Selectors below are best-guess values based on the SDD's
 * REQ-002 UI Mockup: an entry card with a "Trace Manually" CTA, a map
 * region that accepts clicks to place points, a "Points placed: N" counter,
 * and a "Finish Trace" button (disabled below the 2-point minimum). This
 * test will fail with a navigation/timeout error today; that is the
 * correct Loop A state, not a reason to skip real assertions. Loop B must
 * reconcile these selectors against its actual templates.
 */

const NEW_CORRIDOR_PATH = '/corridors/new'; // PROVISIONAL — see file header.

// Four points forming a simple, non-self-intersecting path on the
// schematic map canvas. These are canvas pixel coordinates (not lat/lon),
// since this test exercises the click-to-trace UI interaction directly
// rather than validating persisted geometry (that's covered by the
// backend's own TC-REQ-002 integration tests).
const TRACE_CLICK_POINTS = [
  { x: 120, y: 200 },
  { x: 220, y: 180 },
  { x: 320, y: 210 },
  { x: 420, y: 190 },
];

test.describe('REQ-007 / TC-REQ-007-2: manual trace on pinned browser versions', () => {
  test('4-point manual trace renders incrementally and submits successfully', async ({ page }) => {
    await page.goto(NEW_CORRIDOR_PATH);
    await page.getByTestId('trace-manually-button').click();

    const canvas = page.getByTestId('trace-map-canvas');
    await expect(canvas).toBeVisible();

    // Before the first click: 0 points placed, Finish Trace disabled below
    // the 2-point minimum, per the SDD's REQ-002 UI Mockup empty state.
    await expect(page.getByTestId('points-placed-count')).toHaveText('Points placed: 0');
    await expect(page.getByTestId('finish-trace-button')).toBeDisabled();

    for (let i = 0; i < TRACE_CLICK_POINTS.length; i++) {
      const point = TRACE_CLICK_POINTS[i];
      await canvas.click({ position: point });

      // The canvas must render incrementally after each click, not only at
      // the end — verified via the "Points placed: N" counter (per the
      // SDD's REQ-002 UI Mockup) rather than pixel inspection, since raw
      // canvas pixel content isn't reliably assertable across engines.
      await expect(page.getByTestId('points-placed-count')).toHaveText(
        `Points placed: ${i + 1}`,
      );
    }

    const finishButton = page.getByTestId('finish-trace-button');
    await expect(finishButton).toBeEnabled();

    const [response] = await Promise.all([
      page.waitForResponse(
        (res) => res.url().includes('/finish') && res.request().method() === 'POST',
      ),
      finishButton.click(),
    ]);

    expect(
      response.status(),
      `Expected a 2xx response for the completed trace submission, got ${response.status()}`,
    ).toBeGreaterThanOrEqual(200);
    expect(response.status()).toBeLessThan(300);
  });
});
