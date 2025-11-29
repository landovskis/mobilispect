# Mobilispect Design System

Derived from the full brand pack in `~/Downloads/mobilispect_full_brand_pack`.
Tokens are checked in at `docs/brand/mobilispect_design_tokens.json`, and shared
assets live in `frontend/web/public/`.

## Source Assets

- Logos: `frontend/web/public/mobilispect_full_logo.svg` (light wordmark),
  `frontend/web/public/mobilispect_logo_dark_mode.svg` (dark surfaces),
  `frontend/web/public/mobilispect_icon_square.svg` (app icon).
- Design tokens: `docs/brand/mobilispect_design_tokens.json`.
- Reference UI snippets (not checked in): `ui/angular`, `ui/web`, `ui/android`,
  `ui/ios` inside the brand pack.

## Colors

- **Primary**: Mobilispect Blue `#0B4F8A`, Mobilispect Cyan `#00A7C4`,
  Dark Navy `#0B3558`.
- **Accent**: Station Yellow `#FFD54F`, Info Blue Light `#E1F3FF`.
- **Neutrals**: Ink `#111827`, Muted `#6B7280`, Border `#D1D5DB`,
  Background `#F9FAFB`.
- **Dark Mode**: Surface `#020617`, Elevated `#0F172A`, Text Primary `#E5F1FF`,
  Text Secondary `#94A3B8`.

Usage:

- Prefer the primary gradient (`#0B4F8A` → `#00A7C4`) for selected states and
  hero headers.
- Use Border/Info Blue Light for dividers, badges, and subtle containers.
- Keep Station Yellow for warnings and attention states only.

## Typography

- Font stack: `system-ui, -apple-system, BlinkMacSystemFont, "SF Pro Text",
  "Segoe UI", sans-serif`.
- Type scale (px): H1 40, H2 32, H3 24, Body 16, Caption 12–13.
- Tone: modern, clear, calm; avoid all-caps except for short labels and
  metadata.

## Spacing

- 4 px baseline grid. Scale: 0, 4, 8, 12, 16, 24, 32, 40, 48.
- Apply 16–24 px page padding; cards use 16–24 px internal padding; buttons use
  16–20 px horizontal padding.

## Logo Usage

- Default wordmark: `mobilispect_full_logo.svg` on light backgrounds (white or
  `#F9FAFB`).
- Dark surfaces: `mobilispect_logo_dark_mode.svg` on colors darker than
  `#0F172A`.
- Small/UI spots: `mobilispect_icon_square.svg` for favicons, avatars, and
  compact badges.
- Maintain clear space equal to the cap height of “M”; minimum width 120 px for
  the full logo.

## Component Guidance

- **Top/App Bar (Angular web)**: Use the light wordmark by default, white
  background, Mobilispect Blue/Cyan accents, and the brand font stack.
  Breadcrumbs and actions align to the right.
- **Navigation**: For selected items, ensure light foreground (`#E5F1FF`) on the
  primary gradient for WCAG contrast; default states use Mobilispect Blue text
  on white with border `#D1D5DB`.
- **Badges/Counts**: Background `#E1F3FF`, text `#0B4F8A`; in dark mode, use
  `rgba(0, 167, 196, 0.18)` with light text.
- **Buttons**: Primary: `#0B4F8A` background, white text; Accent/CTA:
  `#00A7C4` background, `#02131F` text; focus rings `#FFD54F` at 2 px for
  accessibility.
- **Cards/Surfaces**: White on light mode with subtle shadow
  (`0 12px 30px rgba(15, 23, 42, 0.08)`), 16–24 px padding, 12–16 px radius.
  Dark mode uses `#0F172A` with reduced shadow.

## Implementation Notes

- Angular Material: set `color="primary"` to map to Mobilispect Blue; use
  gradient or Cyan for emphasis states. Shared tokens sit in
  `docs/brand/mobilispect_design_tokens.json` for future theme generation.
- KMM/Compose: mirror colors from tokens for `primary`, `secondary` (Cyan), and
  `surface` (`Background`/`Surface` from the dark palette).
- Keep WCAG AA: light text `#E5F1FF` on primary gradient; dark text `#0B3558`
  on white/Background.
