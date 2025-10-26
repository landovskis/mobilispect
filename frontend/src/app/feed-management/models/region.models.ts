/**
 * TypeScript models for Metropolitan Region entities
 *
 * These models correspond to the backend entities and OpenAPI specification.
 * Used for type safety and IDE support in Angular components.
 */

/**
 * Metropolitan Region model
 */
export interface MetropolitanRegion {
  regionOnestopId: string;
  name: string;
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
  regionOnestopId: string;
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
 * Detailed feed with recent imports
 */
export interface FeedDetail extends Feed {
  recentImports: FeedImportSummary[];
}

/**
 * Feed specification types
 */
export enum FeedSpecType {
  GTFS = 'gtfs',
  GTFS_RT = 'gtfs-rt'
}

/**
 * Feed status values
 */
export enum FeedStatus {
  ACTIVE = 'active',
  INACTIVE = 'inactive',
  ERROR = 'error'
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
  OAUTH2 = 'oauth2'
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
    return region.name;
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
        return 'text-green-600';
      case FeedStatus.INACTIVE:
        return 'text-gray-500';
      case FeedStatus.ERROR:
        return 'text-red-600';
      default:
        return 'text-gray-500';
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