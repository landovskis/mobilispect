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
      process.env.DATABASE_URL ?? 'postgres://postgres:postgres@localhost:5432/mobilispect',
  });
  await client.connect();
  try {
    return await fn(client);
  } finally {
    await client.end();
  }
}

/** Ensures region id=1 (the single region this repo's first-launch setup
 * creates) has a bounding box, so it appears in the metro-region picker. */
export async function ensureRegionHasBoundingBox(): Promise<void> {
  await withDb(async (client) => {
    await client.query(
      `UPDATE regions SET min_lat = 45.40, min_lon = -73.70, max_lat = 45.60, max_lon = -73.50
       WHERE id = 1`
    );
  });
}
