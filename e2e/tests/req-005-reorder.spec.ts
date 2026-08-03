import { test, expect } from '@playwright/test';

/**
 * TC-REQ-007-4: Engine-specific drag-and-drop failure during cross-section
 * reorder is surfaced, not silently swallowed
 * (test-plan-corridor-segment-editor.md, REQ-007 Error & Recovery Tests).
 *
 * Test-plan preconditions scope this scenario to a Firefox/Gecko browser
 * context specifically (Firefox's pointer-event/drag timing has
 * historically diverged most from Chromium/WebKit for interrupted-drag
 * handling), so this test skips itself on the chromium/webkit projects
 * rather than asserting the same thing three times — matching
 * IMP-REQ-007-07's validation command note ("Passes on Firefox").
 *
 * Reorder mechanism (SDD REQ-005 Design Approach): up/down buttons are the
 * required, always-present, keyboard-accessible baseline; drag-and-drop via
 * a per-row `aria-hidden` handle ("⠿") is a strictly additive, pointer-only
 * enhancement layered on top. TC-REQ-007-4 is specifically about
 * `pointercancel` handling, which only applies to that pointer-driven drag
 * path — this test exercises the drag handle, not the up/down buttons.
 *
 * PROVISIONAL selectors/data (Loop A): REQ-005's reorder route, drag-handle
 * markup, and cancellation handling do not exist yet (IMP-REQ-007-10, the
 * frontend `pointercancel` handler, and REQ-005's base implementation are
 * both undone — see IMPLEMENTATION_CHECKLIST.md's Loop A note). Selectors
 * are best-guess values based on the SDD's REQ-005 UI Mockup and its
 * Accessibility note (aria-live region announcing position changes). The
 * edit/sequence route shape (`/corridors/{corridor_id}/edit`) is the one
 * confirmed value here — it's stated explicitly in
 * test-plan-corridor-segment-editor.md's TC-REQ-003-4 steps ("Navigate to
 * /corridors/{id}/edit"). CORRIDOR_ID is a placeholder: no corridor-seeding
 * fixture/API exists yet either, so Loop B/QA must replace it with a real
 * seeded 3-cross-section corridor once REQ-001/002/004 land. This test will
 * fail with a navigation/timeout error today; that is the correct Loop A
 * state, not a reason to skip real assertions.
 */

const CORRIDOR_ID = 'seed-corridor-req005-reorder'; // PROVISIONAL — see file header.
const EDIT_PATH = `/corridors/${CORRIDOR_ID}/edit`; // confirmed route shape, see file header.

test.describe('REQ-007 / TC-REQ-007-4: interrupted drag-reorder', () => {
  test('pointercancel mid-drag reverts order and surfaces a message with no uncaught errors', async ({
    page,
    browserName,
  }) => {
    test.skip(
      browserName !== 'firefox',
      'TC-REQ-007-4 preconditions scope this scenario to a Firefox/Gecko browser context specifically, per test-plan-corridor-segment-editor.md.',
    );

    // page.on('pageerror') reliably captures uncaught synchronous
    // exceptions on all engines, but is known to miss some unhandled
    // promise rejections specifically on Firefox (the one engine this test
    // targets) — see https://github.com/microsoft/playwright/issues/14165.
    // A console-message fallback catches the browser's own
    // "Uncaught (in promise) ..." reporting as a second signal so this
    // test doesn't silently pass on a rejection pageerror alone would miss.
    const uncaughtExceptions: string[] = [];
    const unhandledRejections: string[] = [];
    page.on('pageerror', (err) => uncaughtExceptions.push(err.message));
    page.on('console', (msg) => {
      if (msg.type() === 'error' && /uncaught \(in promise\)/i.test(msg.text())) {
        unhandledRejections.push(msg.text());
      }
    });

    await page.goto(EDIT_PATH);

    const rows = page.getByTestId('cross-section-row');
    await expect(rows).toHaveCount(3, { timeout: 10_000 });

    const originalOrder = await rows.evaluateAll((els) =>
      els.map((el) => el.getAttribute('data-cross-section-id')),
    );

    // Begin a drag operation on cross-section 2's handle toward
    // cross-section 1's position, per TC-REQ-007-4's steps.
    const row2Handle = rows.nth(1).getByTestId('drag-handle');
    const row1Box = await rows.nth(0).boundingBox();
    const row2HandleBox = await row2Handle.boundingBox();
    if (!row1Box || !row2HandleBox) {
      throw new Error(
        'Expected bounding boxes for cross-section row 1 and row 2\'s drag handle to be available before dragging',
      );
    }

    await page.mouse.move(
      row2HandleBox.x + row2HandleBox.width / 2,
      row2HandleBox.y + row2HandleBox.height / 2,
    );
    await page.mouse.down();
    await page.mouse.move(row1Box.x + row1Box.width / 2, row1Box.y + row1Box.height / 2, {
      steps: 5,
    });

    // Interrupt the drag mid-gesture with an explicit pointercancel event,
    // simulating an engine-level cancellation (e.g. the OS/browser taking
    // over the gesture) rather than a normal pointerup/drop completion.
    await row2Handle.dispatchEvent('pointercancel', { bubbles: true, cancelable: true });
    await page.mouse.up();

    // The list must revert to its pre-drag order, not settle into an
    // ambiguous/half-moved state.
    const revertedOrder = await rows.evaluateAll((els) =>
      els.map((el) => el.getAttribute('data-cross-section-id')),
    );
    expect(revertedOrder).toEqual(originalOrder);

    // A non-blocking message must indicate the reorder did not complete.
    await expect(page.getByTestId('reorder-error-message')).toBeVisible();

    expect(
      uncaughtExceptions,
      `Uncaught exceptions: ${uncaughtExceptions.join('; ')}`,
    ).toHaveLength(0);
    expect(
      unhandledRejections,
      `Unhandled promise rejections: ${unhandledRejections.join('; ')}`,
    ).toHaveLength(0);
  });
});
