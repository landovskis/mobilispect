# Mobilispect Civic Ledger Design System

## Context

Mobilispect is a real-time public transit performance monitoring product for municipalities, transit agencies, and advocacy organizations. The current UI has a partial `base.html` design foundation plus several page-local styles in dashboard, scorecard, speed, hotspot, route detail, and report templates.

The approved brand direction is **Civic Ledger**: a public accountability system that feels credible, transparent, report-ready, and usable for dense daily metrics.

## Goals

- Give Mobilispect a distinctive logo that communicates route inspection, evidence, and public performance accountability.
- Replace the mixed page-local styling with a shared design system in the base template.
- Make the product credible for public reports while still working as an operational dashboard.
- Keep the interface dense, calm, and scannable for tables, route metrics, filters, and scorecards.

## Non-Goals

- Do not build a marketing landing page.
- Do not introduce a JavaScript-heavy component framework.
- Do not redesign backend routes, metrics, database schema, or GTFS ingestion behavior.
- Do not make the product look like a generic dark monitoring terminal or a government seal.

## Brand Position

Mobilispect should feel like an independent transit accountability instrument. The product tone is:

- **Evidence-first:** numbers, routes, and benchmarks are visually prioritized.
- **Civic:** suitable for agency staff, public reports, and advocates.
- **Calm:** uses color to communicate state, not decoration.
- **Practical:** compact layouts support repeated use and comparison.

## Logo

The logo is an inspection-sheet mark with a route line rising through it and one highlighted node.

Meaning:

- The sheet represents inspection, reports, and public records.
- The route line represents transit performance over geography and time.
- The highlighted node represents the point where Mobilispect detects or verifies a condition.

Required variants:

- Full header lockup: mark plus `Mobilispect`.
- Square mark for favicon, app icon, and compact navigation.
- Monochrome mark for print reports.

Implementation constraints:

- Use inline SVG in `templates/base.html` first. A separate asset file can be added later if needed.
- Keep the mark legible at 24px.
- Avoid animation in the primary logo; accountability should feel stable, not flashy.

## Color System

Core tokens:

- `--ink-900: #17202c` for primary text, header, and logo mark.
- `--ink-700: #334155` for secondary headings and table emphasis.
- `--ink-500: #627083` for muted labels.
- `--paper: #f7f9fb` for app background.
- `--surface: #ffffff` for cards, tables, and panels.
- `--line: #d8dee8` for strong borders.
- `--line-soft: #edf0f5` for row dividers.
- `--civic-green: #2d8f67` for meeting standard.
- `--civic-amber: #f0b84b` for watch conditions.
- `--civic-red: #c9483f` for below-standard conditions.
- `--link-blue: #2f6f9f` for links and selected filters.

Usage rules:

- Green, amber, and red are reserved for performance status.
- Blue is for navigation, links, selected controls, and chart accents.
- The UI should be mostly paper, surface, ink, and line colors. State colors should appear sparingly.
- Avoid purple-blue gradients, decorative blobs, or one-hue dashboard palettes.

## Typography

Use the current font choices:

- Inter for UI text.
- JetBrains Mono for route IDs, timestamps, numeric metrics, feed identifiers, and compact technical values.

Scale:

- Page title: 24-28px, 700.
- Section title: 16-18px, 700.
- Body/table text: 14px.
- Dense metadata and labels: 11-12px, 700, uppercase only for short labels.
- Large stat values: 28-36px, 700, tabular where possible.

Rules:

- Letter spacing is `0` for normal text.
- Uppercase labels may use slight positive letter spacing.
- Do not use viewport-scaled font sizes.

## Shared Components

The base template should define reusable classes for:

- App shell: sticky header, constrained content region, footer.
- Brand lockup: logo mark plus wordmark.
- Navigation links with active state.
- Metric summary blocks.
- Cards and panels.
- Tables with sticky or clear headers where useful.
- Status badges: `status-good`, `status-watch`, `status-bad`, `status-none`.
- Filter controls as segmented/pill controls.
- Route identifiers using the mono font.
- Empty states for pages without computed data.
- Print-friendly report styles.

Cards should use an 8px radius or less. Page sections should not be nested cards; reserve cards for repeated items, metrics, and framed tools.

## Page Applications

Dashboard:

- Use the shared app shell.
- Lead with route monitoring summary metrics.
- Keep the route table dense and readable.
- Agency filters should use the shared filter control.

Scorecard:

- Use the most report-like expression of the system.
- Benchmark columns should remain clear, but not use a bright blue header fill.
- Status badges should map directly to public-standard outcomes.

Route Speeds:

- Preserve chart utility.
- Restyle cards, route labels, controls, and badges through shared classes.
- Use chart colors that match civic state and link tokens.

Hotspots:

- Keep the map/task layout operational.
- Restyle header, sidebar, table, and legend with shared tokens.
- Delay colors should use the civic status palette.

Route Detail:

- Use shared callout, chart, and section styles.
- Keep route history focused on trend evidence, not decoration.

Print Report:

- Use the monochrome logo variant where possible.
- Keep serif body typography only if it improves printed readability; otherwise align with the system.
- Ensure reports still print cleanly without navigation.

## Accessibility

- Status must not rely on color alone; badges need text labels.
- Link and control focus states must be visible.
- Text contrast must meet WCAG AA for normal UI text.
- Tables should preserve semantic table markup.
- The logo should include accessible text through surrounding brand text, not duplicate SVG labels.

## Testing And Verification

- Run `cargo test` after template changes because Askama templates compile with Rust.
- Start the server with `dotenvx run -- cargo run --bin mobilispect-server` for manual browser checks.
- Verify desktop and mobile widths for dashboard, speed, scorecard, hotspots, route detail, and report.
- Confirm print report output still hides navigation and uses readable contrast.

## Rollout Plan

1. Update `templates/base.html` with Civic Ledger tokens, logo, shell, and shared component classes.
2. Migrate each page template to extend or visually align with the base system.
3. Replace repeated page-local status colors and filter styles with shared classes.
4. Verify template compilation and browser rendering.
5. Commit the design-system implementation separately from this design spec.
