---
title: Lumina Design System
version: "1.0"
description: Warm, editorial design system for Mobilispect built on Tailwind CSS.
---

# Lumina Design System

> A warm, editorial system for applications that value clarity, craft, and considered aesthetics.
> Every token, every component — deliberate.

**Version:** 1.0 · Tailwind CSS
**Reference file:** `docs/design/design-system.html`

---

## Table of Contents

- [Overview](#overview)
- [Foundation](#foundation)
  - [Colors](#colors)
  - [Typography](#typography)
- [CSS Custom Properties](#css-custom-properties)
- [Components](#components)
  - [Buttons](#buttons)
  - [Form Controls](#form-controls)
  - [Cards](#cards)
  - [Badges](#badges)
  - [Alerts](#alerts)
  - [Tables](#tables)
- [Patterns](#patterns)
  - [Stats & Data](#stats--data)
  - [Charts & Graphs](#charts--graphs)
- [Dark Mode](#dark-mode)
- [Tailwind Configuration](#tailwind-configuration)
- [Quick-Start Snippets](#quick-start-snippets)

---

## Overview

Lumina is a warm, grounded design system built on Tailwind CSS with a three-typeface palette drawn from editorial print design. It pairs structured data presentation with considered aesthetics — suited for transit analytics dashboards where legibility and information density matter.

**Design goals:**
- Warm neutrals instead of cold greys
- Three clearly differentiated typefaces with non-overlapping roles
- Semantic colour tokens that adapt automatically to dark mode
- Components that make numeric data legible at a glance

---

## Foundation

### Colors

#### Neutral Scale — `cream`

The primary neutral. Warm off-whites through near-black.

| Step | Hex | Usage |
|------|-----|-------|
| `cream-50` | `#FAFAF7` | Page background |
| `cream-100` | `#F4F4EF` | Subtle background, sidebar |
| `cream-200` | `#ECEAE4` | Border light, dividers |
| `cream-300` | `#E0DDD6` | Border, input strokes |
| `cream-400` | `#C8C4BC` | Stone — placeholder, disabled |
| `cream-500` | `#A8A49B` | Dim — de-emphasised text |
| `cream-600` | `#888480` | Muted — secondary labels |
| `cream-700` | `#645F5A` | Secondary body text |
| `cream-800` | `#3D3935` | Mid — primary UI text |
| `cream-900` | `#1A1814` | Ink — highest contrast text |

#### Accent Palettes

**Cinnabar** — primary action, error states, active indicators

| Step | Hex |
|------|-----|
| 200 | `#F2B5AF` |
| 500 | `#C8463A` |
| 600 | `#A83530` |
| 700 | `#872828` |

**Oxford** — secondary accent, info states, links, filter chips

| Step | Hex |
|------|-----|
| 200 | `#9DBDE6` |
| 500 | `#1D4E89` |
| 600 | `#163A67` |
| 700 | `#102D52` |

**Saffron** — warnings, pending states

| Step | Hex |
|------|-----|
| 200 | `#F9DC98` |
| 500 | `#E8A020` |
| 600 | `#C88018` |
| 700 | `#9E6012` |

#### Semantic Status Colours

| Semantic | Hex | Usage |
|----------|-----|-------|
| Success / Sage | `#3D9A6B` | On-time, healthy metrics |
| Warning / Saffron | `#E8A020` | Minor delays, pending |
| Error / Cinnabar | `#C8463A` | Failures, significant delays |
| Info / Oxford | `#1D4E89` | Informational alerts |

> **Note:** Sage (`#3D9A6B`) has no Tailwind token — use the CSS variable `--sage` or inline `style` attribute.

---

### Typography

Three typefaces, each with a distinct role. Never mix roles.

| Role | Family | Use for |
|------|--------|---------|
| Display | Cormorant (serif) | `h1`–`h3`, page titles, stat numbers |
| UI Sans | Jost (sans-serif) | All body text, labels, buttons, inputs |
| Monospace | Fira Code (monospace) | Section labels, data values, code, table headers |

**Google Fonts import:**

```html
<link href="https://fonts.googleapis.com/css2?family=Cormorant:ital,wght@0,300;0,400;0,600;0,700;1,300;1,400;1,600&family=Jost:wght@300;400;500;600&family=Fira+Code:wght@400;500&display=swap" rel="stylesheet">
```

#### Display Scale (Cormorant)

| Token | Size | Weight | Use |
|-------|------|--------|-----|
| D1 / 5xl | `4.5rem` | 300 | Hero/page headlines |
| D2 / 4xl | `3rem` | 400 | Section titles |
| H1 / 3xl | `2.25rem` | 500 | Card headings |
| H2 / 2xl | `1.75rem` | 600 | Sub-section headings |

#### UI Scale (Jost)

| Token | Size | Colour role |
|-------|------|-------------|
| lg | `18px / 1.125rem` | `--ink` |
| base | `16px / 1rem` | `--ink` |
| sm | `14px / 0.875rem` | `--secondary` |
| xs | `12px / 0.75rem` | `--muted` |

#### Special: Section Label

```css
font-family: 'Fira Code', monospace;
font-size: 0.68rem;
letter-spacing: 0.22em;
text-transform: uppercase;
color: var(--cinn);
```

Tailwind class: `.section-label` (defined in the design system CSS).

#### Special: Stat Number

```css
font-family: 'Cormorant', serif;
font-size: 3rem;
font-weight: 300;
line-height: 1;
```

Tailwind class: `.stat-num`.

---

## CSS Custom Properties

All semantic tokens are exposed as CSS variables. Use these in preference to raw Tailwind colour tokens — they switch automatically in dark mode.

### Surface & Background

| Variable | Light | Dark |
|----------|-------|------|
| `--bg` | `#FAFAF7` | `#0D0B09` |
| `--bg-subtle` | `#F4F4EF` | `#161310` |
| `--surface` | `#FFFFFF` | `#1E1A16` |

### Text Hierarchy

| Variable | Light | Dark | Use |
|----------|-------|------|-----|
| `--ink` | `#1A1814` | `#EDE8E0` | Primary text |
| `--secondary` | `#645F5A` | `#B5AFA6` | Secondary text |
| `--muted` | `#888480` | `#7A7470` | De-emphasised text |
| `--dim` | `#A8A49B` | `#4E4844` | Labels, placeholders |
| `--mid` | `#3D3935` | `#CCC7C0` | Input text |

### Borders

| Variable | Light | Dark |
|----------|-------|------|
| `--border` | `#E0DDD6` | `#352C22` |
| `--border-light` | `#ECEAE4` | `#272018` |

### Accent Colours

| Variable | Light | Dark |
|----------|-------|------|
| `--cinn` | `#C8463A` | `#D95045` |
| `--cinn-hover` | `#A83530` | `#E87060` |
| `--ox` | `#1D4E89` | `#5A94CC` |
| `--saff` | `#E8A020` | `#E8A82A` |
| `--sage` | `#3D9A6B` | `#4DAA7B` |

### Badge Semantic Tints

| Variable | Purpose |
|----------|---------|
| `--b-cinn-bg` / `--b-cinn-fg` | Cinnabar badge (error, delay) |
| `--b-ox-bg` / `--b-ox-fg` | Oxford badge (info) |
| `--b-saff-bg` / `--b-saff-fg` | Saffron badge (warning, pending) |
| `--b-sage-bg` / `--b-sage-fg` | Sage badge (success, on-time) |
| `--b-neu-bg` / `--b-neu-fg` | Neutral badge |
| `--b-drk-bg` / `--b-drk-fg` | Dark badge (inverted) |

### Alert Semantic Tints

Each alert variant exposes three variables: `-bg`, `-bd` (border), `-title`, `-body`.

| Prefix | Variant |
|--------|---------|
| `--al-info-*` | Informational (oxford) |
| `--al-ok-*` | Success (sage) |
| `--al-warn-*` | Warning (saffron) |
| `--al-err-*` | Error (cinnabar) |

### Chart Grid

| Variable | Light | Dark |
|----------|-------|------|
| `--chart-grid` | `#ECEAE4` | `#272018` |

---

## Components

### Buttons

Base class `.btn` applies `font-family: Jost`, `font-weight: 500`, `letter-spacing: 0.04em`, and flex alignment. Always combine with a variant and size.

#### Variants

| Variant | Classes / Style |
|---------|----------------|
| **Primary** | `bg-cinnabar-500 text-cream-50 hover:bg-cinnabar-600` + `box-shadow: 0 2px 6px rgba(200,70,58,0.25)` |
| **Secondary** | `text-cinnabar-500 hover:bg-cinnabar-50` + `border: 1.5px solid #C8463A` |
| **Ghost** | `text-cream-700 hover:bg-cream-100` (no border) |
| **Oxford** | `bg-oxford-500 text-cream-50 hover:bg-oxford-600` + oxford shadow |
| **Disabled** | Add `disabled` attribute → opacity 0.45, cursor not-allowed |
| **Loading** | Add spinner SVG + `gap: 8px` inside primary button |

#### Sizes

| Size | Classes |
|------|---------|
| XSmall | `rounded text-[0.7rem] px-3 py-[5px]` |
| Small | `rounded-md text-xs px-4 py-2` |
| Medium | `rounded-md text-sm px-5 py-2.5` |
| Large | `rounded-lg text-base px-6 py-3` |
| X-Large | `rounded-lg px-8 py-4` + Cormorant italic `1.3rem` |

#### Icon Buttons

Add `gap: 8px` and a 16×16 SVG icon inside the button. For icon-only buttons: `padding: 10px` with no text.

---

### Form Controls

Base class `.field` provides:
- Full width, Jost `0.875rem`
- `border: 1.5px solid var(--border)`, `border-radius: 6px`, `padding: 0.6rem 0.875rem`
- Focus: cinnabar border + `box-shadow: 0 0 0 3px rgba(200,70,58,0.12)`
- Success state: `.field.success` — sage border + sage shadow
- Error state: `.field.error` — cinnabar border + cinnabar shadow

Applies to: `<input>`, `<textarea>`, `<select>`.

**Labels:** `font-size: 0.72rem`, `font-weight: 500`, `color: var(--muted)`, `letter-spacing: 0.05em`, `margin-bottom: 6px`.

**Helper/error text:** `font-size: 0.72rem`, cinnabar for errors, sage for success.

#### Toggle

```html
<div class="toggle [on]">
  <div class="toggle-thumb"></div>
</div>
```

Toggle `.on` class to activate (background becomes `--cinn`). Thumb slides 20px right.

**Checkboxes / Radios:** Use native elements with `accent-color: #C8463A`.

---

### Cards

Base class `.card`:

```css
background: var(--surface);
border: 1px solid var(--border-light);
border-radius: 12px;
box-shadow: 0 1px 4px rgba(0,0,0,0.05);
/* hover */
transform: translateY(-2px);
box-shadow: 0 6px 24px rgba(0,0,0,0.1);
```

Always add `padding: 20px` (or `24px` for data cards) inside the card.

#### Card Variants

| Variant | Description |
|---------|-------------|
| **Feature card** | Icon (34×34 cinnabar tinted bg) + title + description |
| **Media card** | Full-width colour gradient header + badge + content below |
| **Profile card** | Avatar circle (gradient bg, initials) + name + stat rows |
| **Action card** | Title + description + feature grid + CTA button |
| **Stat card** | Fira Code label + `.stat-num` + trend badge + progress bar |

---

### Badges

Base class `.badge`:

```css
display: inline-flex;
align-items: center;
gap: 5px;
font-family: 'Jost', sans-serif;
font-size: 0.7rem;
font-weight: 500;
letter-spacing: 0.04em;
border-radius: 4px;
padding: 2px 8px;
white-space: nowrap;
```

#### Colour Variants

Apply via inline style using semantic badge vars:

| Variant | Style |
|---------|-------|
| Cinnabar | `background: var(--b-cinn-bg); color: var(--b-cinn-fg)` |
| Oxford | `background: var(--b-ox-bg); color: var(--b-ox-fg)` |
| Saffron | `background: var(--b-saff-bg); color: var(--b-saff-fg)` |
| Sage (success) | `background: var(--b-sage-bg); color: var(--b-sage-fg)` |
| Neutral | `background: var(--border-light); color: var(--mid)` |
| Dark | `background: var(--b-drk-bg); color: var(--b-drk-fg)` |

#### Status Dot

Add a 6×6 circle inside the badge to indicate live status:

```html
<span class="badge" style="background:var(--b-sage-bg);color:var(--b-sage-fg);">
  <span style="width:6px;height:6px;border-radius:50%;background:#3D9A6B;display:inline-block;"></span>
  Active
</span>
```

---

### Alerts

Base class `.alert`:

```css
display: flex;
gap: 0.75rem;
padding: 1rem;
border-radius: 8px;
border: 1px solid;
```

Structure: `icon (18×18, flex-shrink:0) + div(title + body)`.

| Variant | Background | Border | Title colour | Body colour |
|---------|-----------|--------|-------------|-------------|
| Info | `--al-info-bg` | `--al-info-bd` | `--al-info-title` | `--al-info-body` |
| Success | `--al-ok-bg` | `--al-ok-bd` | `--al-ok-title` | `--al-ok-body` |
| Warning | `--al-warn-bg` | `--al-warn-bd` | `--al-warn-title` | `--al-warn-body` |
| Error | `--al-err-bg` | `--al-err-bd` | `--al-err-title` | `--al-err-body` |

Title: `font-size: 0.875rem`, `font-weight: 500`. Body: `font-size: 0.82rem`.

Optional action button: `font-size: 0.8rem`, `font-weight: 500`, `color: --cinn`, `flex-shrink: 0`, `white-space: nowrap`.

---

### Tables

Wrap tables in a `.card` container with `overflow: hidden`.

**Table header row:**
```css
font-family: 'Fira Code', monospace;
font-size: 0.68rem;
letter-spacing: 0.12em;
text-transform: uppercase;
color: var(--dim);
padding: 0.75rem 1rem;
border-bottom: 1px solid var(--border);
```

**Table cells:**
```css
padding: 0.875rem 1rem;
font-size: 0.875rem;
border-bottom: 1px solid var(--border-light);
```

**Row hover:** `background: var(--bg-subtle)` on `tbody tr:hover td`.

**Numeric data cells:** Use `font-family: 'Fira Code', monospace` and colour-code by severity (sage → good, saffron → minor issue, cinnabar → significant issue).

**Table toolbar pattern:** `display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid var(--bg-subtle)` — contains title + ghost action buttons.

---

## Patterns

### Stats & Data

Use `.stat-num` for the primary metric display:

```html
<p class="font-mono text-xs" style="color:var(--dim);">On-Time Rate</p>
<div class="stat-num" style="color:#3D9A6B;">87<span style="font-size:1.5rem;color:var(--dim);">%</span></div>
<p style="font-size:0.72rem;color:#3D9A6B;">↑ 2.1% vs last week</p>
```

The trend line below the stat inherits the same accent colour as the number. Unit suffixes (%, m, k) use a smaller Cormorant size (`1.5rem`) in `--dim`.

**Progress bars:**

```html
<div class="progress">
  <div class="progress-fill" style="width:87%;background:#3D9A6B;"></div>
</div>
```

`.progress` = `height: 4px; background: var(--border); border-radius: 2px; overflow: hidden`.
`.progress-fill` = `height: 100%; border-radius: 2px; transition: width 0.8s ease`.

Colour-code fill: sage ≥ ~80%, saffron ~65–80%, cinnabar < ~65%.

---

### Charts & Graphs

Charts are raw SVG — no charting library. All chart colours are read from CSS variables at draw time via `getComputedStyle`, so they respond to dark mode automatically.

#### Chart conventions

- Grid lines: `stroke: var(--chart-grid)`, `stroke-width: 1`
- Axis labels: Fira Code `9px`, `fill: var(--dim)`
- Baseline: `stroke: var(--border)`, `stroke-width: 1`
- Animated entry (lines): stroke-dashoffset draw animation, 1.3s cubic-bezier
- Animated entry (bars): grow from baseline, cubic-ease, staggered by 80ms per bar
- Animated entry (donut): scale(0) → scale(1), spring cubic-bezier, staggered by 80ms

#### Line / Area Chart

Smooth curves via Catmull-Rom → cubic bezier spline. Area fill uses a vertical `linearGradient` from 15% → 1% opacity of the stroke colour. Dots (r=3.5) fade in staggered after line draws.

#### Bar Chart

Bars grow upward from baseline. Value labels appear above bars after growth completes. Bar colour driven by `--sage`, `--saff`, or `--cinn` per severity.

#### Donut Chart

Segments scale from centre origin. Centre label shows primary stat in Cormorant 22px weight-300. Legend to the right uses Jost for labels, Fira Code for values.

---

## Dark Mode

Toggle by adding/removing the `dark` class on `<html>`:

```js
document.documentElement.classList.toggle('dark');
```

All CSS custom properties redefine automatically under `html.dark { }`. Chart colours re-read CSS vars on redraw. Theme preference stored in `localStorage` under key `lumina-theme`.

**Transition:** All elements animate `background-color`, `border-color`, `color` over `0.25s ease` (except `pre` blocks and the grain overlay).

**Dark mode card shadows:** Elevated from `0 1px 4px rgba(0,0,0,0.3)` to `0 6px 24px rgba(0,0,0,0.4)` on hover.

---

## Tailwind Configuration

Add to your `tailwind.config`:

```js
tailwind.config = {
  theme: {
    extend: {
      fontFamily: {
        display: ['Cormorant', 'serif'],
        sans:    ['Jost', 'sans-serif'],
        mono:    ['Fira Code', 'monospace'],
      },
      colors: {
        cream: {
          50: '#FAFAF7', 100: '#F4F4EF', 200: '#ECEAE4',
          300: '#E0DDD6', 400: '#C8C4BC', 500: '#A8A49B',
          600: '#888480', 700: '#645F5A', 800: '#3D3935', 900: '#1A1814',
        },
        cinnabar: {
          50: '#FDF2F1', 100: '#F9DDD9', 200: '#F2B5AF',
          300: '#E88880', 400: '#DC5E54', 500: '#C8463A',
          600: '#A83530', 700: '#872828', 800: '#641E1E', 900: '#411414',
        },
        oxford: {
          50: '#EEF3FA', 100: '#D0E0F3', 200: '#9DBDE6',
          300: '#6498D3', 400: '#3574BC', 500: '#1D4E89',
          600: '#163A67', 700: '#102D52', 800: '#0A1D37', 900: '#060E1C',
        },
        saffron: {
          50: '#FEF8EC', 100: '#FDF0D0', 200: '#F9DC98',
          300: '#F4C460', 400: '#EDB030', 500: '#E8A020',
          600: '#C88018', 700: '#9E6012', 800: '#74420C', 900: '#4A2506',
        },
      },
    }
  }
}
```

---

## Quick-Start Snippets

### Primary Button

```html
<button class="btn bg-cinnabar-500 text-cream-50 text-sm px-5 py-2.5 rounded-md hover:bg-cinnabar-600"
        style="box-shadow:0 2px 8px rgba(200,70,58,0.3);">
  Get Started
</button>
```

### Display Heading

```html
<h1 style="font-family:'Cormorant',serif;font-size:4.5rem;font-weight:300;color:var(--ink);">
  Built with <em style="color:var(--cinn);">intention</em>
</h1>
```

### Section Label

```html
<p class="section-label">01 — Foundation</p>
<div class="section-rule"></div>
```

### Stat Card

```html
<div class="card" style="padding:20px;">
  <p style="font-family:'Fira Code',monospace;font-size:0.65rem;color:var(--dim);margin-bottom:6px;">On-Time Rate</p>
  <div class="stat-num" style="color:#3D9A6B;">
    87<span style="font-size:1.5rem;color:var(--dim);">%</span>
  </div>
  <p style="font-size:0.72rem;color:#3D9A6B;margin-top:4px;">↑ 2.1% vs last week</p>
</div>
```

### Badge

```html
<span class="badge" style="background:var(--b-sage-bg);color:var(--b-sage-fg);">On Track</span>
<span class="badge" style="background:var(--b-saff-bg);color:var(--b-saff-fg);">Minor Delay</span>
<span class="badge" style="background:var(--b-cinn-bg);color:var(--b-cinn-fg);">Significant Delay</span>
```

### Alert

```html
<div class="alert" style="background:var(--al-warn-bg);border-color:var(--al-warn-bd);">
  <!-- warning icon SVG -->
  <div>
    <p style="font-size:0.875rem;font-weight:500;color:var(--al-warn-title);">High Delay Alert</p>
    <p style="font-size:0.82rem;color:var(--al-warn-body);">Message text here.</p>
  </div>
</div>
```

### Progress Bar (colour-coded)

```html
<!-- ≥80%: sage -->
<div class="progress"><div class="progress-fill" style="width:87%;background:#3D9A6B;"></div></div>
<!-- 65–80%: saffron -->
<div class="progress"><div class="progress-fill" style="width:71%;background:#E8A020;"></div></div>
<!-- <65%: cinnabar -->
<div class="progress"><div class="progress-fill" style="width:58%;background:#C8463A;"></div></div>
```

### Chip (Filter Button)

```html
<a class="chip">All Routes</a>
<a class="chip active">Delayed</a>
```

`.chip` = bordered button, hover → oxford, `.active` → oxford fill.
