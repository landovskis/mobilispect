/**
 * Feed Management Models Index
 *
 * Central export point for all feed management TypeScript models.
 * Provides clean imports for components and services.
 */

// Region models
export * from './region.models';

// Import models
export * from './import.models';

// Import types for TypeGuards
import { MetropolitanRegion, Feed, FeedSpecType, FeedStatus } from './region.models';
import { FeedImport, ImportProgress, ProgressUpdateMessage, SystemAlertMessage, ImportStatus, TriggerType } from './import.models';

// Re-export commonly used types for convenience
export type {
  // Core entities from region models
  MetropolitanRegion,
  MetropolitanRegionDetail,
  Feed,
  FeedDetail,
  FeedAuthentication,
  RegionId,
  RegionsResponse,
  FeedsResponse,
  RegionConfigForm,
  FeedAuthenticationForm
} from './region.models';

export type {
  // Import-related entities
  FeedImport,
  FeedImportDetail,
  FeedImportSummary,
  ImportProgress,
  ImportsResponse,
  ActiveImportsResponse,
  ProgressUpdateMessage,
  ImportStatusMessage,
  SystemAlertMessage,
  ImportRequest,
  PageInfo
} from './import.models';

export {
  // Enums from region models
  FeedSpecType,
  FeedStatus,
  AuthType,

  // Utility classes from region models
  RegionUtils,
  FeedUtils
} from './region.models';

export {
  // Enums from import models
  ImportStatus,
  TriggerType,

  // Utility classes from import models
  ImportUtils,
  ProgressUtils
} from './import.models';

/**
 * Common type guards for runtime type checking
 */
export class TypeGuards {
  /**
   * Checks if an object is a valid MetropolitanRegion
   */
  static isMetropolitanRegion(obj: unknown): obj is MetropolitanRegion {
    if (!obj || typeof obj !== 'object') return false;
    const candidate = obj as Record<string, unknown>;
    return typeof candidate.regionOnestopId === 'string' &&
           typeof candidate.name === 'string' &&
           typeof candidate.autoUpdateEnabled === 'boolean' &&
           typeof candidate.feedCount === 'number';
  }

  /**
   * Checks if an object is a valid Feed
   */
  static isFeed(obj: unknown): obj is Feed {
    if (!obj || typeof obj !== 'object') return false;
    const candidate = obj as Record<string, unknown>;
    return typeof candidate.feedOnestopId === 'string' &&
           typeof candidate.regionOnestopId === 'string' &&
           typeof candidate.name === 'string' &&
           Object.values(FeedSpecType).includes(candidate.specType as FeedSpecType) &&
           Object.values(FeedStatus).includes(candidate.status as FeedStatus);
  }

  /**
   * Checks if an object is a valid FeedImport
   */
  static isFeedImport(obj: unknown): obj is FeedImport {
    if (!obj || typeof obj !== 'object') return false;
    const candidate = obj as Record<string, unknown>;
    return typeof candidate.id === 'string' &&
           typeof candidate.feedOnestopId === 'string' &&
           Object.values(ImportStatus).includes(candidate.status as ImportStatus) &&
           Object.values(TriggerType).includes(candidate.triggerType as TriggerType);
  }

  /**
   * Checks if an object is a valid ImportProgress
   */
  static isImportProgress(obj: unknown): obj is ImportProgress {
    if (!obj || typeof obj !== 'object') return false;
    const candidate = obj as Record<string, unknown>;
    return typeof candidate.progressPercentage === 'number' &&
           typeof candidate.totalSteps === 'number' &&
           typeof candidate.currentStep === 'string';
  }

  /**
   * Checks if an object is a valid ProgressUpdateMessage
   */
  static isProgressUpdateMessage(obj: unknown): obj is ProgressUpdateMessage {
    if (!obj || typeof obj !== 'object') return false;
    const candidate = obj as Record<string, unknown>;
    return typeof candidate.importId === 'string' &&
           typeof candidate.feedOnestopId === 'string' &&
           typeof candidate.progressPercentage === 'number' &&
           typeof candidate.currentStep === 'string';
  }

  /**
   * Checks if an object is a valid SystemAlertMessage
   */
  static isSystemAlertMessage(obj: unknown): obj is SystemAlertMessage {
    if (!obj || typeof obj !== 'object') return false;
    const candidate = obj as Record<string, unknown>;
    return ['info', 'warning', 'error'].includes(candidate.type as string) &&
           typeof candidate.title === 'string' &&
           typeof candidate.message === 'string';
  }
}

/**
 * Constants for the feed management module
 */
export const FEED_MANAGEMENT_CONSTANTS = {
  // WebSocket topics
  WEBSOCKET_TOPICS: {
    IMPORT_PROGRESS: '/topic/import/progress',
    IMPORT_STATUS: '/topic/import/status',
    SYSTEM_ALERTS: '/topic/system/alerts'
  },

  // API endpoints
  API_ENDPOINTS: {
    REGIONS: '/api/feeds/regions',
    FEEDS: '/api/feeds/feeds',
    IMPORTS: '/api/feeds/imports',
    AUTHENTICATION: '/api/feeds/feeds/{feedId}/authentication'
  },

  // UI constants
  UI: {
    DEFAULT_PAGE_SIZE: 20,
    MAX_PAGE_SIZE: 100,
    PROGRESS_UPDATE_INTERVAL: 1000, // ms
    TOAST_DURATION: 5000, // ms
    WEBSOCKET_RECONNECT_DELAY: 5000 // ms
  },

  // Validation constants
  VALIDATION: {
    MAX_REGION_NAME_LENGTH: 255,
    MAX_FEED_NAME_LENGTH: 255,
    MIN_PASSWORD_LENGTH: 8,
    ONESTOP_ID_PATTERN: {
      REGION: /^r-[0-9a-z]+-[a-z0-9-]+$/,
      FEED: /^f-[0-9a-z]+(~[a-z]+)?-[a-z0-9-]+$/
    }
  }
} as const;
