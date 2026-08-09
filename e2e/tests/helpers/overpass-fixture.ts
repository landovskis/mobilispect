import { createServer, Server } from 'http';

/**
 * A minimal local stand-in for the Overpass API, so
 * builder-import-osm.spec.ts can exercise the real import flow without
 * depending on the live overpass-api.de endpoint (which E2E runs should
 * never hit — see
 * docs/superpowers/specs/2026-08-06-corridor-osm-import-design.md).
 *
 * Binds to a fixed port rather than an ephemeral one, since
 * `mobilispect-server` is started as a separate process this test suite does
 * not control — its `OVERPASS_BASE_URL` env var must be set to
 * `http://localhost:19999` *before* that process starts, matching this
 * repo's existing convention of fixed, documented ports for test
 * infrastructure (e.g. `mobilispect-pg` on 5433).
 *
 * Always responds with the same three fixture ways, regardless of the
 * actual query body — sufficient to exercise this app's own code paths
 * without needing to parse/validate Overpass QL. Way 9001001 and 9001002
 * share an endpoint node (90012) and form one contiguous street; way
 * 9001003 is ~400m from both (no shared endpoint, but still comfortably
 * within the import page's default zoom-16 viewport so it actually renders
 * and is clickable — see clickWayAt below), for
 * exercising the disconnected-selection error path.
 */
export const FIXTURE_PORT = 19999;

const FIXTURE_RESPONSE = {
  version: 0.6,
  elements: [
    {
      type: 'way',
      id: 9001001,
      nodes: [90011, 90012],
      geometry: [
        { lat: 45.5, lon: -73.58 },
        { lat: 45.501, lon: -73.579 },
      ],
      tags: { highway: 'residential', name: 'Fixture Test Street' },
    },
    {
      type: 'way',
      id: 9001002,
      nodes: [90012, 90013],
      geometry: [
        { lat: 45.501, lon: -73.579 },
        { lat: 45.502, lon: -73.578 },
      ],
      tags: { highway: 'residential', name: 'Fixture Test Street' },
    },
    {
      type: 'way',
      id: 9001003,
      nodes: [90021, 90022],
      geometry: [
        { lat: 45.505, lon: -73.575 },
        { lat: 45.506, lon: -73.574 },
      ],
      tags: { highway: 'residential', name: 'Disconnected Fixture Street' },
    },
  ],
};

let server: Server | undefined;

export function startOverpassFixture(): Promise<void> {
  return new Promise((resolve, reject) => {
    server = createServer((_req, res) => {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(FIXTURE_RESPONSE));
    });
    server.on('error', reject);
    server.listen(FIXTURE_PORT, () => resolve());
  });
}

export function stopOverpassFixture(): Promise<void> {
  return new Promise((resolve) => {
    if (!server) {
      resolve();
      return;
    }
    server.close(() => resolve());
    server = undefined;
  });
}
