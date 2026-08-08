import type { FullConfig } from '@playwright/test';
import { stopOverpassFixture } from './tests/helpers/overpass-fixture';

async function globalTeardown(_config: FullConfig) {
  await stopOverpassFixture();
}

export default globalTeardown;
