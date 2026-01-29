import { FeedImportSummary, ImportStatus } from './import.models';

/**
 * Represents a group of feed imports for a specific region.
 *
 * Groups multiple feed imports by their region, providing aggregate
 * statistics and metadata for displaying region-level import cards.
 */
export interface RegionImportGroup {
  /** Region's Onestop ID (e.g., 'r-san-francisco-bay-area') */
  regionOnestopId: string;

  /** Human-readable region name (e.g., 'San Francisco Bay Area') */
  regionName: string;

  /** All feed imports within this region */
  feedImports: FeedImportSummary[];

  /** Total number of feeds being imported in this region */
  totalFeeds: number;

  /** Average progress across all feeds in this region (0-100) */
  averageProgress: number;

  /** Whether any feed in this region has failed */
  hasFailures: boolean;

  /** Whether all feeds in this region have completed */
  allCompleted: boolean;
}

/**
 * Utility functions for grouping and managing region import groups.
 *
 * Provides static methods for grouping feed imports by region,
 * calculating aggregate statistics, and sorting groups.
 */
export class RegionImportGroupingUtils {
  /**
   * Groups feed imports by region.
   *
   * Takes a flat list of feed imports and groups them by their region,
   * calculating aggregate statistics for each region group.
   *
   * Feeds with no region are grouped under a special "Unknown Region" group.
   *
   * @param imports - List of feed import summaries to group
   * @returns Array of region import groups
   */
  static groupImportsByRegion(
    imports: FeedImportSummary[],
  ): RegionImportGroup[] {
    if (imports.length === 0) {
      return [];
    }

    // Group imports by region ID
    const groupMap = new Map<string, FeedImportSummary[]>();

    for (const feedImport of imports) {
      // Handle feeds with no region by using a special key
      const regionId = feedImport.regionOnestopId || 'unknown-region';

      if (!groupMap.has(regionId)) {
        groupMap.set(regionId, []);
      }

      groupMap.get(regionId)!.push(feedImport);
    }

    // Convert map to array of region groups
    const groups: RegionImportGroup[] = [];

    groupMap.forEach((feedImports, regionId) => {
      // Use the region name from the first import in the group
      // (all imports in a group have the same region)
      const firstImport = feedImports[0];
      const regionName = firstImport.regionName || 'Unknown Region';

      const averageProgress = this.calculateAverageProgress(feedImports);
      const hasFailures = feedImports.some(
        (imp) => imp.status === ImportStatus.FAILED,
      );
      const allCompleted = feedImports.every(
        (imp) => imp.status === ImportStatus.COMPLETED,
      );

      groups.push({
        regionOnestopId: regionId,
        regionName: regionName,
        feedImports: feedImports,
        totalFeeds: feedImports.length,
        averageProgress: averageProgress,
        hasFailures: hasFailures,
        allCompleted: allCompleted,
      });
    });

    return groups;
  }

  /**
   * Calculates the average progress across multiple feed imports.
   *
   * Only includes imports that have progress data (progress is not null).
   * Returns 0 if no imports have progress data.
   *
   * @param imports - List of feed imports
   * @returns Average progress percentage (0-100), rounded to 2 decimal places
   */
  static calculateAverageProgress(imports: FeedImportSummary[]): number {
    // Filter imports that have progress data
    const importsWithProgress = imports.filter(
      (imp) => imp.progress !== null && imp.progress !== undefined,
    );

    if (importsWithProgress.length === 0) {
      return 0;
    }

    // Sum all progress percentages
    const totalProgress = importsWithProgress.reduce(
      (sum, imp) => sum + (imp.progress?.progressPercentage || 0),
      0,
    );

    // Calculate average and round to 2 decimal places
    const average = totalProgress / importsWithProgress.length;
    return Math.round(average * 100) / 100;
  }

  /**
   * Sorts region groups alphabetically by region name.
   *
   * @param groups - Array of region import groups to sort
   * @returns Sorted array of region groups (does not mutate input)
   */
  static sortRegionGroups(groups: RegionImportGroup[]): RegionImportGroup[] {
    return [...groups].sort((a, b) => a.regionName.localeCompare(b.regionName));
  }
}
