/**
 * TypeScript models for Feed Import entities
 *
 * These models correspond to the backend entities and OpenAPI specification.
 * Used for import operations, progress tracking, and history management.
 */

/**
 * Feed Import model
 */
export interface FeedImport {
  id: string;
  feedOnestopId: string;
  administratorId: string | null;
  administratorUsername: string | null;
  triggerType: TriggerType;
  status: ImportStatus;
  versionSha1: string | null;
  startedAt: string | null;
  completedAt: string | null;
  fileSizeBytes: number | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Detailed feed import with additional context
 */
export interface FeedImportDetail extends FeedImport {
  feedName: string;
  regionName: string;
  progress: ImportProgress | null;
}

/**
 * Feed import summary for lists and dashboards
 */
export interface FeedImportSummary {
  id: string;
  feedOnestopId: string;
  feedName: string;
  regionName: string;
  status: ImportStatus;
  triggerType: TriggerType;
  startedAt: string | null;
  completedAt: string | null;
  fileSizeBytes: number | null;
  errorMessage: string | null;
  progress: ImportProgress | null;
  currentStep?: string; // For backward compatibility
}

/**
 * Import progress information
 */
export interface ImportProgress {
  progressPercentage: number;
  totalSteps: number;
  currentStep: string;
  estimatedTimeRemainingSeconds: number | null;
}

/**
 * Import status values
 */
export enum ImportStatus {
  PENDING = 'pending',
  RUNNING = 'running',
  COMPLETED = 'completed',
  FAILED = 'failed',
  CANCELLED = 'cancelled',
}

/**
 * Import trigger types
 */
export enum TriggerType {
  MANUAL = 'manual',
  AUTOMATIC = 'automatic',
}

/**
 * Import request for starting new imports
 */
export interface ImportRequest {
  force?: boolean;
}

/**
 * Pagination information
 */
export interface PageInfo {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

/**
 * Response wrapper for imports list
 */
export interface ImportsResponse {
  imports: FeedImportDetail[];
  page: PageInfo;
}

/**
 * Response wrapper for active imports
 */
export interface ActiveImportsResponse {
  imports: FeedImportSummary[];
  total: number;
}

/**
 * WebSocket progress update message
 */
export interface ProgressUpdateMessage {
  importId: string;
  feedOnestopId: string;
  progressPercentage: number;
  currentStep: string;
  totalSteps: number;
  estimatedTimeRemainingSeconds: number | null;
  startedAt: number;
  lastUpdated: number;
  status: string;
}

/**
 * WebSocket status message
 */
export interface ImportStatusMessage {
  importId: string;
  feedOnestopId: string;
  feedName: string | null;
  regionName: string | null;
  status: string;
  message: string;
  timestamp: number;
  details: Record<string, unknown> | null;
}

/**
 * System alert message
 */
export interface SystemAlertMessage {
  type: 'info' | 'warning' | 'error';
  title: string;
  message: string;
  timestamp: number;
  autoClose: boolean;
  duration: number;
}

/**
 * Utility functions for import models
 */
export class ImportUtils {
  /**
   * Checks if an import is currently active
   */
  static isActive(importRecord: FeedImport): boolean {
    return (
      importRecord.status === ImportStatus.PENDING ||
      importRecord.status === ImportStatus.RUNNING
    );
  }

  /**
   * Checks if an import is completed (successfully or failed)
   */
  static isCompleted(importRecord: FeedImport): boolean {
    return (
      importRecord.status === ImportStatus.COMPLETED ||
      importRecord.status === ImportStatus.FAILED ||
      importRecord.status === ImportStatus.CANCELLED
    );
  }

  /**
   * Checks if an import was successful
   */
  static isSuccessful(importRecord: FeedImport): boolean {
    return importRecord.status === ImportStatus.COMPLETED;
  }

  /**
   * Checks if an import can be cancelled
   */
  static isCancellable(importRecord: FeedImport): boolean {
    return (
      importRecord.status === ImportStatus.PENDING ||
      importRecord.status === ImportStatus.RUNNING
    );
  }

  /**
   * Gets the status display name
   */
  static getStatusDisplayName(status: ImportStatus): string {
    switch (status) {
      case ImportStatus.PENDING:
        return 'Pending';
      case ImportStatus.RUNNING:
        return 'Running';
      case ImportStatus.COMPLETED:
        return 'Completed';
      case ImportStatus.FAILED:
        return 'Failed';
      case ImportStatus.CANCELLED:
        return 'Cancelled';
      default:
        return status;
    }
  }

  /**
   * Gets the status color class for UI
   */
  static getStatusColorClass(status: ImportStatus): string {
    switch (status) {
      case ImportStatus.PENDING:
        return 'chip-neutral';
      case ImportStatus.RUNNING:
        return 'chip-warning';
      case ImportStatus.COMPLETED:
        return 'chip-success';
      case ImportStatus.FAILED:
        return 'chip-error';
      case ImportStatus.CANCELLED:
        return 'chip-neutral';
      default:
        return 'chip-neutral';
    }
  }

  /**
   * Gets the trigger type display name
   */
  static getTriggerTypeDisplayName(triggerType: TriggerType): string {
    switch (triggerType) {
      case TriggerType.MANUAL:
        return 'Manual';
      case TriggerType.AUTOMATIC:
        return 'Automatic';
      default:
        return triggerType;
    }
  }

  /**
   * Formats file size in human-readable format
   */
  static formatFileSize(bytes: number | null): string {
    if (!bytes) return 'Unknown';

    const units = ['B', 'KB', 'MB', 'GB'];
    let size = bytes;
    let unitIndex = 0;

    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }

    return `${size.toFixed(1)} ${units[unitIndex]}`;
  }

  /**
   * Calculates import duration
   */
  static getDuration(importRecord: FeedImport): string | null {
    if (!importRecord.startedAt) return null;

    const startTime = new Date(importRecord.startedAt).getTime();
    const endTime = importRecord.completedAt
      ? new Date(importRecord.completedAt).getTime()
      : Date.now();

    const durationMs = endTime - startTime;
    const durationSeconds = Math.floor(durationMs / 1000);

    if (durationSeconds < 60) {
      return `${durationSeconds}s`;
    }

    const minutes = Math.floor(durationSeconds / 60);
    const seconds = durationSeconds % 60;

    if (minutes < 60) {
      return `${minutes}m ${seconds}s`;
    }

    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    return `${hours}h ${remainingMinutes}m`;
  }

  /**
   * Formats estimated time remaining
   */
  static formatEstimatedTimeRemaining(seconds: number | null): string {
    if (!seconds || seconds <= 0) return 'Unknown';

    if (seconds < 60) {
      return `${seconds}s`;
    }

    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;

    if (minutes < 60) {
      return `${minutes}m ${remainingSeconds}s`;
    }

    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    return `${hours}h ${remainingMinutes}m`;
  }

  /**
   * Formats timestamp for display
   */
  static formatTimestamp(timestamp: string): string {
    const date = new Date(timestamp);
    return date.toLocaleString();
  }

  /**
   * Formats relative time (e.g., "2 hours ago")
   */
  static formatRelativeTime(timestamp: string): string {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMinutes = Math.floor(diffMs / (1000 * 60));

    if (diffMinutes < 1) return 'Just now';
    if (diffMinutes < 60) return `${diffMinutes}m ago`;

    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) return `${diffHours}h ago`;

    const diffDays = Math.floor(diffHours / 24);
    return `${diffDays}d ago`;
  }
}

/**
 * Progress tracking utilities
 */
export class ProgressUtils {
  /**
   * Calculates progress percentage for display
   */
  static getProgressPercentage(progress: ImportProgress | null): number {
    if (!progress) return 0;
    return Math.max(0, Math.min(100, progress.progressPercentage));
  }

  /**
   * Checks if progress indicates failure
   */
  static isProgressFailed(progress: ImportProgress | null): boolean {
    return progress?.progressPercentage === -1;
  }

  /**
   * Gets progress color class based on percentage
   */
  static getProgressColorClass(progress: ImportProgress | null): string {
    if (!progress) return 'bg-gray-200';

    if (progress.progressPercentage === -1) return 'bg-red-500';
    if (progress.progressPercentage === 100) return 'bg-green-500';
    if (progress.progressPercentage >= 75) return 'bg-blue-500';
    if (progress.progressPercentage >= 50) return 'bg-yellow-500';
    return 'bg-gray-400';
  }
}
