import { test, expect } from '@playwright/test';

/**
 * TC-REQ-007-3: Editor degrades gracefully when the Pointer Events API is
 * unavailable (test-plan-corridor-segment-editor.md, REQ-007 Functional
 * Tests). Maps to sdd-corridor-segment-editor.md's REQ-007 Error Handling
 * table: a missing required browser API (canvas 2D context, Pointer
 * Events, fetch) must render a Lumina `.alert` warning variant in place of
 * the canvas editor, with the message "Your browser doesn't support a
 * feature this tool needs. Please use a current version of Chrome,
 * Firefox, Edge, or Safari." — not a silent blank canvas, and no unhandled
 * JS exception.
 *
 * PROVISIONAL route (Loop A): as of this writing, IMP-REQ-007-09's
 * client-side feature-detection guard, and the corridor-editor route it
 * lives on, do not exist yet — REQ-001/002's routes are not registered in
 * the Axum router (see IMPLEMENTATION_CHECKLIST.md's Loop A note). TRACE_PATH
 * below is a best guess based on the SDD's REQ-002 UI Mockup: the
 * manual-trace screen is the most canvas/pointer-dependent screen named in
 * REQ-007's own risk analysis ("REQ-001's import flow and REQ-002's manual
 * click-to-trace flow both depend on canvas ... and pointer-event
 * handling"). Until Loop B lands, this test is EXPECTED to fail with a
 * navigation/timeout error (404 or connection refused) rather than an
 * assertion failure — that failure mode is itself the correct Loop A
 * signal that production code doesn't exist yet, not a reason to skip
 * writing the real assertions below. Loop B must reconcile TRACE_PATH and
 * the data-testid values against its actual templates.
 */

const TRACE_PATH = '/corridors/new'; // PROVISIONAL — see file header.

test.describe('REQ-007 / TC-REQ-007-3: graceful degradation without Pointer Events', () => {
  test('missing window.PointerEvent renders a warning alert instead of the canvas editor', async ({
    page,
  }) => {
    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];

    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        consoleErrors.push(msg.text());
      }
    });
    page.on('pageerror', (err) => {
      pageErrors.push(err.message);
    });

    // Delete window.PointerEvent before any page script runs, simulating a
    // browser/engine that lacks the Pointer Events API — per the test
    // plan's exact precondition.
    await page.addInitScript(() => {
      // @ts-expect-error - intentionally deleting a standard global to
      // simulate an unsupported browser for this test case.
      delete window.PointerEvent;
    });

    await page.goto(TRACE_PATH);

    // Per the SDD's REQ-007 Error Handling table: CLIENT_FEATURE_UNSUPPORTED
    // -> a Lumina `.alert` warning variant rendered in place of the canvas.
    const warningAlert = page.locator('.alert').filter({
      hasText: "doesn't support a feature this tool needs",
    });
    await expect(warningAlert).toBeVisible();

    // The canvas editor itself must not attempt to render — "no blank
    // canvas is shown" per the test plan's expected result.
    const canvasEditor = page.getByTestId('trace-map-canvas');
    await expect(canvasEditor).toHaveCount(0);

    expect(pageErrors, `Unhandled JS exceptions: ${pageErrors.join('; ')}`).toHaveLength(0);
    expect(consoleErrors, `Console errors: ${consoleErrors.join('; ')}`).toHaveLength(0);
  });
});
