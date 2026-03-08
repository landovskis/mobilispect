import { Feed, FeedSpecType, FeedStatus } from './region.models';

/**
 * Agency Feed Group Model
 *
 * Represents a grouping of feeds belonging to the same transit agency.
 * Multiple feeds (GTFS static, GTFS-RT, etc.) from the same agency are
 * combined into a single card for cleaner display.
 */
export interface AgencyFeedGroup {
  /** Agency name (derived from feed name) */
  agencyName: string;

  /** Base agency identifier extracted from feedOnestopId */
  agencyId: string;

  /** All feeds belonging to this agency */
  feeds: Feed[];

  /** Primary feed (usually GTFS static) */
  primaryFeed: Feed;

  /** Whether any feed in this agency is active */
  hasActiveFeeds: boolean;

  /** Whether any feed has authentication configured */
  hasAuthentication: boolean;

  /** Latest update timestamp across all feeds */
  lastUpdatedAt: string | null;

  /** Count of feeds by spec type */
  feedsByType: {
    gtfs: number;
    gtfsRt: number;
  };
}

/**
 * Utility class for grouping feeds by agency
 */
export class FeedGroupingUtils {
  /**
   * Extracts agency identifier from feedOnestopId
   *
   * Example: "f-9q5-bart" -> "bart"
   * Example: "f-9q8-actransit" -> "actransit"
   * Example: "f-dr5-nyct~rt" -> "nyct"
   */
  static extractAgencyId(feedOnestopId: string): string {
    const parts = feedOnestopId.split('-');
    if (parts.length < 3) return feedOnestopId;

    // Get the last part and remove ~rt suffix if present
    const lastPart = parts[parts.length - 1];
    return lastPart.replace(/~rt$/i, '');
  }

  /**
   * Extracts agency name from feed name
   *
   * Removes common suffixes like "GTFS", "Realtime", etc.
   */
  static extractAgencyName(feedName: string): string {
    return feedName
      .replace(/\s*(GTFS|gtfs)(\s*(static|Static|Realtime|realtime|RT|rt))?$/i, '')
      .replace(/\s*-\s*(static|Static|Realtime|realtime|RT|rt)$/i, '')
      .trim();
  }

  /**
   * Groups feeds by agency
   */
  static groupFeedsByAgency(feeds: Feed[]): AgencyFeedGroup[] {
    const groupMap = new Map<string, Feed[]>();

    // Group feeds by agency ID
    feeds.forEach((feed) => {
      const agencyId = this.extractAgencyId(feed.feedOnestopId);
      if (!groupMap.has(agencyId)) {
        groupMap.set(agencyId, []);
      }
      groupMap.get(agencyId)!.push(feed);
    });

    // Convert to AgencyFeedGroup array
    return Array.from(groupMap.entries()).map(([agencyId, agencyFeeds]) => {
      // Sort feeds: GTFS first, then GTFS-RT
      const sortedFeeds = agencyFeeds.sort((a, b) => {
        if (a.specType === FeedSpecType.GTFS && b.specType !== FeedSpecType.GTFS) return -1;
        if (a.specType !== FeedSpecType.GTFS && b.specType === FeedSpecType.GTFS) return 1;
        return a.name.localeCompare(b.name);
      });

      const primaryFeed = sortedFeeds[0];
      const agencyName = this.extractAgencyName(primaryFeed.name);

      // Check if any feed is active
      const hasActiveFeeds = agencyFeeds.some((f) => f.status === FeedStatus.ACTIVE);

      // Check if any feed has authentication
      const hasAuthentication = agencyFeeds.some((f) => f.hasAuthentication);

      // Find latest update time
      const lastUpdatedAt =
        agencyFeeds
          .map((f) => f.lastUpdatedAt)
          .filter((date) => date !== null)
          .sort()
          .reverse()[0] || null;

      // Count feeds by type
      const feedsByType = {
        gtfs: agencyFeeds.filter((f) => f.specType === FeedSpecType.GTFS).length,
        gtfsRt: agencyFeeds.filter((f) => f.specType === FeedSpecType.GTFS_RT).length,
      };

      return {
        agencyName,
        agencyId,
        feeds: sortedFeeds,
        primaryFeed,
        hasActiveFeeds,
        hasAuthentication,
        lastUpdatedAt,
        feedsByType,
      };
    });
  }

  /**
   * Sorts agency groups alphabetically by name
   */
  static sortAgencyGroups(groups: AgencyFeedGroup[]): AgencyFeedGroup[] {
    return groups.sort((a, b) => a.agencyName.localeCompare(b.agencyName));
  }

  /**
   * Gets display label for feed type
   */
  static getFeedTypeLabel(specType: FeedSpecType): string {
    switch (specType) {
      case FeedSpecType.GTFS:
        return 'Static';
      case FeedSpecType.GTFS_RT:
        return 'Realtime';
      default:
        return specType;
    }
  }

  /**
   * Gets icon for feed type
   */
  static getFeedTypeIcon(specType: FeedSpecType): string {
    switch (specType) {
      case FeedSpecType.GTFS:
        return 'directions_transit';
      case FeedSpecType.GTFS_RT:
        return 'real_time_tracking';
      default:
        return 'feed';
    }
  }

  /**
   * Gets color class for feed type
   */
  static getFeedTypeColorClass(specType: FeedSpecType): string {
    switch (specType) {
      case FeedSpecType.GTFS:
        return 'feed-type-gtfs';
      case FeedSpecType.GTFS_RT:
        return 'feed-type-gtfs-rt';
      default:
        return 'feed-type-default';
    }
  }
}
