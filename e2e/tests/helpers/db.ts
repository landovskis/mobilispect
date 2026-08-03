import { Client } from 'pg';

/**
 * Direct DB access for E2E test data setup only — the Corridor Builder
 * shell intentionally has no API for seeding regions/corridors (see
 * docs/superpowers/specs/2026-08-02-corridor-builder-wasm-shell-design.md's
 * "Out of Scope": no admin UI for bounding boxes, and corridor creation
 * belongs to the not-yet-built segment-editor). Mirrors how this repo's
 * Rust integration tests seed fixtures with raw SQL directly against the
 * test database.
 */
export async function withDb<T>(fn: (client: Client) => Promise<T>): Promise<T> {
  const client = new Client({
    connectionString:
      process.env.DATABASE_URL ?? 'postgres://mobilispect:mobilispect@localhost:5433/mobilispect',
  });
  await client.connect();
  try {
    return await fn(client);
  } finally {
    await client.end();
  }
}

/** Ensures region id=1 exists, is named "Test Region" (matching the
 * hardcoded label the builder-*.spec.ts specs select in the picker), and has
 * a bounding box, so it appears in the metro-region picker. Owns the fixture
 * outright — inserts the row if it's missing (e.g. a fresh DB where
 * first-launch setup hasn't run) and overwrites name/bbox if it already
 * exists, so the specs never depend on prior manual seeding. */
export async function ensureRegionHasBoundingBox(): Promise<void> {
  await withDb(async (client) => {
    await client.query(
      `INSERT INTO regions (id, name, timezone, min_lat, min_lon, max_lat, max_lon)
       VALUES (1, 'Test Region', 'UTC', 45.40, -73.70, 45.60, -73.50)
       ON CONFLICT (id) DO UPDATE SET
         name = EXCLUDED.name,
         timezone = EXCLUDED.timezone,
         min_lat = EXCLUDED.min_lat,
         min_lon = EXCLUDED.min_lon,
         max_lat = EXCLUDED.max_lat,
         max_lon = EXCLUDED.max_lon`
    );
  });
}
