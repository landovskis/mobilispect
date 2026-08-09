import type { FullConfig } from '@playwright/test';
import { startOverpassFixture } from './tests/helpers/overpass-fixture';

/**
 * Starts the Overpass fixture server exactly once, before any test
 * worker/project starts running tests. `globalSetup`/`globalTeardown` run
 * once for the entire suite regardless of worker or project count, unlike a
 * per-spec `beforeAll`/`afterAll` (which runs once *per worker process* --
 * with `fullyParallel: true` across three browser projects, that would mean
 * multiple workers racing to bind the fixture's one fixed port, and racing
 * on which worker's `afterAll` tears the shared server down first). See
 * `tests/helpers/overpass-fixture.ts` for the fixture itself.
 */
async function globalSetup(_config: FullConfig) {
  await startOverpassFixture();
}

export default globalSetup;
