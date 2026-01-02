/**
 * Material Design window size classes for adaptive layouts.
 *
 * Window Size Classes:
 * - Compact (< 600px): Bottom navigation (phones in portrait)
 * - Medium (600-840px): Navigation rail (tablets in portrait, foldables)
 * - Expanded (≥ 840px): Full sidenav (tablets in landscape, desktops)
 *
 * Reference: https://m3.material.io/foundations/layout/applying-layout/window-size-classes
 */
export enum WindowSizeClass {
  COMPACT = 'COMPACT',
  MEDIUM = 'MEDIUM',
  EXPANDED = 'EXPANDED',
}
