import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for REQ-007 (Corridor Segment Editor
 * cross-browser compatibility — see sdd-corridor-segment-editor.md and
 * test-plan-corridor-segment-editor.md). Runs the suite under `./tests`
 * against three engines:
 *   - chromium — stands in for both Chrome and Edge (SDD REQ-007 Design
 *     Approach: "Chromium (covering both Chrome and Edge)")
 *   - firefox  — Gecko
 *   - webkit   — an approximation of Safari, not identical (see the SDD's
 *     REQ-007 Open Question 3 and the Test Plan's Risks table: real-Safari
 *     spot-checks remain a separate, manual, periodic activity)
 *
 * Browser version pinning (SDD REQ-007 Open Question 1/2): this config
 * deliberately does NOT pin exact external browser version numbers (e.g.
 * "Chrome 124.0.6367.91"). Playwright ships and manages its own bundled
 * browser binaries per `@playwright/test` release (installed via
 * `npx playwright install`), and keeping those binaries in step with each
 * vendor's "current release" is Playwright's own responsibility per its own
 * release cadence — that's the "current release" pin this suite relies on.
 * Advancing the pin over time means deliberately bumping the
 * `@playwright/test` devDependency version (see package.json) and re-running
 * `npx playwright install`; ownership of that recurring bump is unresolved
 * (SDD Open Question 2 — single-developer resourcing).
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',

  use: {
    // Assumption (SDD REQ-007 has no stated deployment URL for CI): the
    // corridor editor is served by mobilispect-server, whose configured
    // default bind address is 0.0.0.0:3000 per this repo's config.toml
    // (`bind_address = "0.0.0.0:3000"`). If that default ever changes,
    // update baseURL to match.
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
  ],
});
