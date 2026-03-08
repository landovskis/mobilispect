/**
 * TypeScript models for Metropolitan Region entities
 *
 * These models correspond to the backend entities and OpenAPI specification.
 * Used for type safety and IDE support in Angular components.
 */

/**
 * Region identifier (Transit.land Onestop ID)
 */
export type RegionId = string;

/**
 * Metropolitan Region model
 */
export interface MetropolitanRegion {
  regionOnestopId: RegionId;
  name: string;
  adm0Name?: string | null;
  adm1Name?: string | null;
  autoUpdateEnabled: boolean;
  feedCount: number;
  lastCheckAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Detailed metropolitan region with feeds
 */
export interface MetropolitanRegionDetail extends MetropolitanRegion {
  feeds: Feed[];
}

/**
 * Feed model
 */
export interface Feed {
  feedOnestopId: string;
  regionOnestopId: RegionId;
  name: string;
  specType: FeedSpecType;
  downloadUrl: string;
  currentVersionSha1: string | null;
  lastCheckedAt: string | null;
  lastUpdatedAt: string | null;
  status: FeedStatus;
  hasAuthentication: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * Basic import summary for region models (to avoid circular dependency)
 */
interface BasicImportSummary {
  id: string;
  status: string;
  startedAt: string | null;
  completedAt: string | null;
}

/**
 * Detailed feed with recent imports
 */
export interface FeedDetail extends Feed {
  recentImports: BasicImportSummary[];
}

/**
 * Feed specification types
 */
export enum FeedSpecType {
  GTFS = 'GTFS',
  GTFS_RT = 'GTFS_RT',
}

/**
 * Feed status values
 */
export enum FeedStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
  ERROR = 'ERROR',
}

/**
 * Feed authentication model
 */
export interface FeedAuthentication {
  feedOnestopId: string;
  authType: AuthType;
  hasCredentials: boolean;
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Feed authentication update request
 */
export interface FeedAuthenticationUpdate {
  authType: AuthType;
  credentials?: FeedCredentials;
}

/**
 * Authentication types
 */
export enum AuthType {
  NONE = 'none',
  API_KEY = 'api_key',
  OAUTH2 = 'oauth2',
}

/**
 * Feed credentials (sent to backend, never stored in frontend)
 */
export interface FeedCredentials {
  apiKey?: string;
  clientId?: string;
  clientSecret?: string;
  tokenUrl?: string;
}

/**
 * Region configuration update request
 */
export interface RegionUpdateRequest {
  autoUpdateEnabled?: boolean;
}

/**
 * Response wrapper for regions list
 */
export interface RegionsResponse {
  regions: MetropolitanRegion[];
  total: number;
}

/**
 * Response payload from manual feed discovery trigger
 */
export interface FeedDiscoveryResult {
  regionOnestopId: string;
  feedsDiscovered: number;
  feedsCreated: number;
  feedsUpdated: number;
  errors: string[];
}

/**
 * Response wrapper for feeds list
 */
export interface FeedsResponse {
  feeds: Feed[];
  total: number;
}

/**
 * Utility functions for region models
 */
export class RegionUtils {
  /**
   * Checks if a region has auto-updates enabled
   */
  static hasAutoUpdatesEnabled(region: MetropolitanRegion): boolean {
    return region.autoUpdateEnabled;
  }

  /**
   * Gets display name for a region
   */
  static getDisplayName(region: MetropolitanRegion): string {
    const rawParts = [region.name, region.adm1Name, region.adm0Name]
      .filter((part): part is string => !!part && part.trim().length > 0)
      // Split composite names like "Montréal, Québec, Canada" so we can de-duplicate pieces
      .flatMap((part) =>
        part
          .split(',')
          .map((piece) => piece.trim())
          .filter((piece) => piece.length > 0)
      );

    const seen = new Set<string>();
    const uniqueParts: string[] = [];

    for (const part of rawParts) {
      const key = part.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      uniqueParts.push(part);
    }

    return uniqueParts.join(', ');
  }

  /**
   * Checks if a region has been checked recently (within 24 hours)
   */
  static hasBeenCheckedRecently(region: MetropolitanRegion): boolean {
    if (!region.lastCheckAt) return false;

    const lastCheck = new Date(region.lastCheckAt);
    const twentyFourHoursAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);
    return lastCheck > twentyFourHoursAgo;
  }

  /**
   * Formats the last check timestamp
   */
  static formatLastCheck(region: MetropolitanRegion): string {
    if (!region.lastCheckAt) return 'Never checked';

    const lastCheck = new Date(region.lastCheckAt);
    const now = new Date();
    const diffMs = now.getTime() - lastCheck.getTime();
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));

    if (diffHours < 1) return 'Less than an hour ago';
    if (diffHours < 24) return `${diffHours} hours ago`;

    const diffDays = Math.floor(diffHours / 24);
    return `${diffDays} days ago`;
  }
}

/**
 * Utility functions for feed models
 */
export class FeedUtils {
  /**
   * Checks if a feed is available for import
   */
  static isAvailableForImport(feed: Feed): boolean {
    return feed.status === FeedStatus.ACTIVE;
  }

  /**
   * Checks if a feed is a real-time feed
   */
  static isRealTimeFeed(feed: Feed): boolean {
    return feed.specType === FeedSpecType.GTFS_RT;
  }

  /**
   * Checks if a feed is a static GTFS feed
   */
  static isStaticFeed(feed: Feed): boolean {
    return feed.specType === FeedSpecType.GTFS;
  }

  /**
   * Gets display name for a feed
   */
  static getDisplayName(feed: Feed): string {
    return feed.name;
  }

  /**
   * Gets the feed type display name
   */
  static getSpecTypeDisplayName(specType: FeedSpecType): string {
    switch (specType) {
      case FeedSpecType.GTFS:
        return 'GTFS Static';
      case FeedSpecType.GTFS_RT:
        return 'GTFS Realtime';
      default:
        return specType;
    }
  }

  /**
   * Gets the status display name
   */
  static getStatusDisplayName(status: FeedStatus): string {
    switch (status) {
      case FeedStatus.ACTIVE:
        return 'Active';
      case FeedStatus.INACTIVE:
        return 'Inactive';
      case FeedStatus.ERROR:
        return 'Error';
      default:
        return status;
    }
  }

  /**
   * Gets the status color class for UI
   */
  static getStatusColorClass(status: FeedStatus): string {
    switch (status) {
      case FeedStatus.ACTIVE:
        return 'chip-success';
      case FeedStatus.INACTIVE:
        return 'chip-neutral';
      case FeedStatus.ERROR:
        return 'chip-error';
      default:
        return 'chip-neutral';
    }
  }

  /**
   * Checks if a feed has been updated recently (within 7 days)
   */
  static hasBeenUpdatedRecently(feed: Feed): boolean {
    if (!feed.lastUpdatedAt) return false;

    const lastUpdate = new Date(feed.lastUpdatedAt);
    const sevenDaysAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
    return lastUpdate > sevenDaysAgo;
  }

  /**
   * Formats the last updated timestamp
   */
  static formatLastUpdated(feed: Feed): string {
    if (!feed.lastUpdatedAt) return 'Never updated';

    const lastUpdate = new Date(feed.lastUpdatedAt);
    return lastUpdate.toLocaleDateString();
  }
}

/**
 * Form models for component forms
 */
export interface RegionConfigForm {
  autoUpdateEnabled: boolean;
}

export interface FeedAuthenticationForm {
  authType: AuthType;
  apiKey?: string;
  clientId?: string;
  clientSecret?: string;
  tokenUrl?: string;
}
