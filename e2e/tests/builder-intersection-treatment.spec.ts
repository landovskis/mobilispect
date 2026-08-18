import { test as base, expect } from '@playwright/test';
import { ensureRegionHasBoundingBox, withDb } from './helpers/db';

type Fixtures = {
  seededCrossSection: {
    remixId: number;
    corridorId: number;
    crossSectionId: number;
    intersectionId: number;
  };
};

const test = base.extend<Fixtures>({
  seededCrossSection: async ({}, use, testInfo) => {
    await ensureRegionHasBoundingBox();
    let remixId = 0;
    let corridorId = 0;
    let crossSectionId = 0;
    let intersectionId = 0;

    await withDb(async (client) => {
      const remixResult = await client.query(
        `INSERT INTO remixes (name, region_id) VALUES ($1, 1) RETURNING id`,
        [`Intersection Test Remix ${testInfo.testId}`]
      );
      remixId = remixResult.rows[0].id;

      const corridorResult = await client.query(
        `INSERT INTO corridors (name, geometry_source, remix_id) VALUES ($1, 'manual', $2) RETURNING id`,
        [`Intersection Test Corridor ${testInfo.testId}`, remixId]
      );
      corridorId = corridorResult.rows[0].id;

      // As of migration 028, treatment fields (bus_gate/turn_conflict/bus_stop)
      // live on a real `intersections` row, not on the cross-section itself --
      // a cross-section only carries a nullable `intersection_id` FK to it.
      // The page under test resolves cross_section_id -> intersection_id via
      // GET /api/cross-sections/:id before it will render the treatment form
      // at all (see `LoadState::NotAnIntersection` in
      // crates/corridor_builder_web/src/pages/intersection.rs), so a
      // cross-section seeded WITHOUT an `intersection_id` renders the "not an
      // intersection endpoint" alert instead of the form under test here.
      const intersectionResult = await client.query(
        `INSERT INTO intersections (lat, lon) VALUES (45.500, -73.600) RETURNING id`
      );
      intersectionId = intersectionResult.rows[0].id;

      const crossSectionResult = await client.query(
        `INSERT INTO cross_sections (corridor_id, position, lat, lon, intersection_id) VALUES ($1, 0, 45.500, -73.600, $2) RETURNING id`,
        [corridorId, intersectionId]
      );
      crossSectionId = crossSectionResult.rows[0].id;
    });

    await use({ remixId, corridorId, crossSectionId, intersectionId });

    await withDb(async (client) => {
      // `turn_movements` and `intersection_osm_nodes` both
      // `ON DELETE CASCADE` from `intersections`, so no separate delete is
      // needed for them. `cross_sections.intersection_id` has no
      // `ON DELETE CASCADE` (see migration 028's comment on that column), so
      // the referencing cross-section must go before the intersection it
      // points to, or the intersection DELETE below fails its FK check.
      await client.query(`DELETE FROM cross_sections WHERE corridor_id = $1`, [corridorId]);
      await client.query(`DELETE FROM intersections WHERE id = $1`, [intersectionId]);
      await client.query(`DELETE FROM corridors WHERE id = $1`, [corridorId]);
      await client.query(`DELETE FROM remixes WHERE id = $1`, [remixId]);
    });
  },
});

test.describe('Corridor Design: intersection treatment editor', () => {
  test('setting bus gate and turn-conflict type persists across reload', async ({
    page,
    seededCrossSection,
  }) => {
    const { remixId, crossSectionId } = seededCrossSection;
    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);

    await expect(page.getByLabel('Bus gate')).toHaveValue('');
    await expect(page.getByLabel('Turn-conflict type')).toHaveValue('');

    await page.getByLabel('Bus gate').selectOption('signal_controlled');
    await expect(page.getByLabel('Bus gate')).toHaveValue('signal_controlled');

    await page.getByLabel('Turn-conflict type').selectOption('right_in_right_out');
    await expect(page.getByLabel('Turn-conflict type')).toHaveValue('right_in_right_out');

    // Reload to confirm both edits persisted server-side, not just in local state.
    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);
    await expect(page.getByLabel('Bus gate')).toHaveValue('signal_controlled');
    await expect(page.getByLabel('Turn-conflict type')).toHaveValue('right_in_right_out');
  });

  test('clearing a previously-set field back to None persists', async ({ page, seededCrossSection }) => {
    const { remixId, crossSectionId } = seededCrossSection;
    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);

    await page.getByLabel('Bus gate').selectOption('yield_controlled');
    await expect(page.getByLabel('Bus gate')).toHaveValue('yield_controlled');

    await page.getByLabel('Bus gate').selectOption('');
    await expect(page.getByLabel('Bus gate')).toHaveValue('');

    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);
    await expect(page.getByLabel('Bus gate')).toHaveValue('');
  });

  test('back to map link returns to the region map', async ({ page, seededCrossSection }) => {
    const { remixId, crossSectionId } = seededCrossSection;
    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);

    await page.getByText('Back to map').click();
    await expect(page).toHaveURL(`/builder/remix/${remixId}`);
  });

  test('changing bus gate and turn-conflict type in the same tick both persist', async ({
    page,
    seededCrossSection,
  }) => {
    const { remixId, crossSectionId, intersectionId } = seededCrossSection;
    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);

    await expect(page.getByLabel('Bus gate')).toHaveValue('');
    await expect(page.getByLabel('Turn-conflict type')).toHaveValue('');

    // Count completed PUTs to the intersection endpoint,
    // `/api/intersections/:id` (Task 9's replacement for the old, dropped
    // `/api/cross-sections/:id/intersection-treatment` route -- see
    // `set_intersection_treatment` in
    // crates/server/src/web/intersection_api.rs and
    // `set_intersection_treatment` in
    // crates/corridor_builder_web/src/api.rs). Matched by exact path (not a
    // substring) so this doesn't also pick up PUTs the turn-movement or
    // split-corridor endpoints under the same `/api/intersections/` prefix
    // might fire. Registered BEFORE the same-tick dispatch below so no
    // response is missed, and polled (rather than `page.waitForResponse`'d
    // twice) because two concurrent `waitForResponse` calls with the same
    // predicate can both resolve off the SAME first matching response
    // instead of each capturing a distinct one.
    let putResponseCount = 0;
    const intersectionPutPath = `/api/intersections/${intersectionId}`;
    page.on('response', (response) => {
      if (
        response.request().method() === 'PUT' &&
        new URL(response.url()).pathname === intersectionPutPath
      ) {
        putResponseCount += 1;
      }
    });

    // Fire both selects' native `change` events inside a SINGLE browser task
    // (mirroring `builder-lane-editor.spec.ts`'s "two access-rule edits fired
    // in the same tick both persist" test), so the two PUTs to
    // /api/intersections/:id are queued before either response can come
    // back. Driving this through two separate Playwright actions would leave
    // enough time between them for the first PUT to complete first, which is
    // exactly the case the sequential test above already covers -- it can't
    // exercise the completion-order hazard the write queue in
    // `pages/intersection.rs` exists to close.
    await page.evaluate(() => {
      const busGate = document.getElementById('bus-gate') as HTMLSelectElement;
      const turnConflict = document.getElementById('turn-conflict') as HTMLSelectElement;
      busGate.value = 'signal_controlled';
      busGate.dispatchEvent(new Event('change', { bubbles: true }));
      turnConflict.value = 'right_in_right_out';
      turnConflict.dispatchEvent(new Event('change', { bubbles: true }));
    });

    await expect(page.getByLabel('Bus gate')).toHaveValue('signal_controlled');
    await expect(page.getByLabel('Turn-conflict type')).toHaveValue('right_in_right_out');

    // Wait for BOTH queued PUTs to actually complete before reloading --
    // reloading (a real navigation) can abort an in-flight `fetch`, which
    // would silently drop a write for a reason that has nothing to do with
    // the hazard this test exists to catch. The assertions above only prove
    // the optimistic client-side state; this is what makes the reload below
    // a meaningful check of server-persisted state.
    await expect.poll(() => putResponseCount).toBe(2);

    // Reload: BOTH edits must be in the database, not just whichever PUT the
    // server happened to finish last.
    await page.goto(`/builder/remix/${remixId}/intersection/${crossSectionId}`);
    await expect(page.getByLabel('Bus gate')).toHaveValue('signal_controlled');
    await expect(page.getByLabel('Turn-conflict type')).toHaveValue('right_in_right_out');
  });
});
