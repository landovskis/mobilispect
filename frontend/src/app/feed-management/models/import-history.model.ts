/**
 * TypeScript models for Import History and Analytics
 *
 * These models correspond to the backend ImportHistoryService data structures
 * and provide comprehensive import history tracking, statistics, and analytics.
 */

import { FeedImport, ImportLog, LogLevel, ImportStatus, TriggerType } from './import.models';

/**
 * Detailed import information including logs and metrics
 */
export interface ImportDetails {
  import: FeedImport;
  logs: ImportLog[];
  metrics: ImportMetrics;
}

/**
 * Metrics calculated for a specific import
 */
export interface ImportMetrics {
  durationSeconds: number | null;
  logCounts: Record<LogLevel, number>;
  errorCount: number;
  warningCount: number;
  infoCount: number;
}

/**
 * Comprehensive import statistics for a time period
 */
export interface ImportStatistics {
  totalImports: number;
  successfulImports: number;
  failedImports: number;
  cancelledImports: number;
  runningImports: number;
  automaticImports: number;
  manualImports: number;
  averageDurationSeconds: number;
  successRate: number;
  hourlyDistribution: Record<number, number>;
  dailyCounts: Record<string, number>;
  period: ImportPeriod;
}

/**
 * Time period for statistics and analytics
 */
export interface ImportPeriod {
  startDate: string;
  endDate: string;
}

/**
 * Analysis of import failures with patterns and breakdowns
 */
export interface FailureAnalysis {
  totalFailures: number;
  errorPatterns: Record<string, number>;
  failuresByFeed: Record<string, number>;
  failuresByHour: Record<number, number>;
  period: ImportPeriod;
}

/**
 * Import history request parameters for filtering and pagination
 */
export interface ImportHistoryRequest {
  feedOnestopId?: string;
  status?: ImportStatus;
  triggerType?: TriggerType;
  administratorId?: string;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}

/**
 * Import statistics request parameters
 */
export interface ImportStatisticsRequest {
  feedOnestopId?: string;
  startDate?: string;
  endDate?: string;
}

/**
 * Failure analysis request parameters
 */
export interface FailureAnalysisRequest {
  startDate?: string;
  endDate?: string;
}

/**
 * Import history grouped by feed
 */
export interface ImportHistoryByFeed {
  [feedOnestopId: string]: FeedImport[];
}

/**
 * Recent import activity item
 */
export interface RecentImportActivity {
  imports: FeedImport[];
  total: number;
}

/**
 * Import history filter options for UI components
 */
export interface ImportHistoryFilters {
  feedOnestopId: string | null;
  status: ImportStatus | null;
  triggerType: TriggerType | null;
  administratorId: string | null;
  dateRange: {
    startDate: string | null;
    endDate: string | null;
  };
}

/**
 * Sort options for import history
 */
export interface ImportHistorySortOptions {
  field: 'createdAt' | 'startedAt' | 'completedAt' | 'status' | 'triggerType' | 'feedOnestopId';
  direction: 'asc' | 'desc';
}

/**
 * Paginated import history response
 */
export interface ImportHistoryResponse {
  content: FeedImport[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

/**
 * Dashboard summary data combining multiple metrics
 */
export interface ImportDashboardSummary {
  statistics: ImportStatistics;
  recentActivity: FeedImport[];
  failureAnalysis: FailureAnalysis;
  historyByFeed: ImportHistoryByFeed;
}

/**
 * Chart data for import history visualizations
 */
export interface ImportHistoryChartData {
  dailyImports: {
    labels: string[];
    datasets: {
      label: string;
      data: number[];
      backgroundColor?: string;
      borderColor?: string;
    }[];
  };
  statusDistribution: {
    labels: string[];
    data: number[];
    backgroundColor: string[];
  };
  triggerTypeDistribution: {
    labels: string[];
    data: number[];
    backgroundColor: string[];
  };
  hourlyDistribution: {
    labels: string[];
    data: number[];
    backgroundColor?: string;
    borderColor?: string;
  };
}

/**
 * Export options for import history data
 */
export interface ImportHistoryExportOptions {
  format: 'csv' | 'json' | 'xlsx';
  filters: ImportHistoryFilters;
  includeMetrics: boolean;
  includeLogs: boolean;
  dateRange: {
    startDate: string;
    endDate: string;
  };
}

/**
 * Utility functions for import history models
 */
export class ImportHistoryUtils {
  /**
   * Calculates success rate percentage
   */
  static calculateSuccessRate(successful: number, total: number): number {
    if (total === 0) return 0;
    return Math.round((successful / total) * 100 * 100) / 100; // Round to 2 decimal places
  }

  /**
   * Formats duration from seconds to human-readable format
   */
  static formatDuration(durationSeconds: number | null): string {
    if (!durationSeconds || durationSeconds <= 0) return 'Unknown';

    if (durationSeconds < 60) {
      return `${Math.round(durationSeconds)}s`;
    }

    const minutes = Math.floor(durationSeconds / 60);
    const seconds = Math.round(durationSeconds % 60);

    if (minutes < 60) {
      return `${minutes}m ${seconds}s`;
    }

    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    return `${hours}h ${remainingMinutes}m ${seconds}s`;
  }

  /**
   * Converts ImportPeriod to display string
   */
  static formatPeriod(period: ImportPeriod): string {
    const startDate = new Date(period.startDate).toLocaleDateString();
    const endDate = new Date(period.endDate).toLocaleDateString();
    return `${startDate} - ${endDate}`;
  }

  /**
   * Gets the most common error pattern from failure analysis
   */
  static getMostCommonError(failureAnalysis: FailureAnalysis): string | null {
    const patterns = failureAnalysis.errorPatterns;
    if (Object.keys(patterns).length === 0) return null;

    return Object.entries(patterns)
      .sort(([, a], [, b]) => b - a)[0][0];
  }

  /**
   * Gets the feed with most failures
   */
  static getMostFailedFeed(failureAnalysis: FailureAnalysis): string | null {
    const failures = failureAnalysis.failuresByFeed;
    if (Object.keys(failures).length === 0) return null;

    return Object.entries(failures)
      .sort(([, a], [, b]) => b - a)[0][0];
  }

  /**
   * Converts daily counts to chart data
   */
  static convertDailyCountsToChartData(dailyCounts: Record<string, number>): ImportHistoryChartData['dailyImports'] {
    const sortedEntries = Object.entries(dailyCounts)
      .sort(([a], [b]) => new Date(a).getTime() - new Date(b).getTime());

    return {
      labels: sortedEntries.map(([date]) => new Date(date).toLocaleDateString()),
      datasets: [{
        label: 'Daily Imports',
        data: sortedEntries.map(([, count]) => count),
        backgroundColor: 'rgba(59, 130, 246, 0.5)',
        borderColor: 'rgb(59, 130, 246)'
      }]
    };
  }

  /**
   * Converts hourly distribution to chart data
   */
  static convertHourlyDistributionToChartData(hourlyDistribution: Record<number, number>): ImportHistoryChartData['hourlyDistribution'] {
    const hours = Array.from({ length: 24 }, (_, i) => i);
    const data = hours.map(hour => hourlyDistribution[hour] || 0);

    return {
      labels: hours.map(hour => `${hour}:00`),
      data,
      backgroundColor: 'rgba(16, 185, 129, 0.5)',
      borderColor: 'rgb(16, 185, 129)'
    };
  }

  /**
   * Creates status distribution chart data
   */
  static createStatusDistributionChartData(statistics: ImportStatistics): ImportHistoryChartData['statusDistribution'] {
    return {
      labels: ['Successful', 'Failed', 'Cancelled', 'Running'],
      data: [
        statistics.successfulImports,
        statistics.failedImports,
        statistics.cancelledImports,
        statistics.runningImports
      ],
      backgroundColor: [
        'rgb(34, 197, 94)',   // green - successful
        'rgb(239, 68, 68)',   // red - failed
        'rgb(156, 163, 175)', // gray - cancelled
        'rgb(245, 158, 11)'   // yellow - running
      ]
    };
  }

  /**
   * Creates trigger type distribution chart data
   */
  static createTriggerTypeDistributionChartData(statistics: ImportStatistics): ImportHistoryChartData['triggerTypeDistribution'] {
    return {
      labels: ['Automatic', 'Manual'],
      data: [statistics.automaticImports, statistics.manualImports],
      backgroundColor: [
        'rgb(99, 102, 241)',  // indigo - automatic
        'rgb(168, 85, 247)'   // purple - manual
      ]
    };
  }

  /**
   * Checks if import is within time period
   */
  static isImportInPeriod(importRecord: FeedImport, period: ImportPeriod): boolean {
    const importDate = new Date(importRecord.createdAt).getTime();
    const startDate = new Date(period.startDate).getTime();
    const endDate = new Date(period.endDate).getTime();
    return importDate >= startDate && importDate <= endDate;
  }

  /**
   * Groups imports by status
   */
  static groupImportsByStatus(imports: FeedImport[]): Record<ImportStatus, FeedImport[]> {
    return imports.reduce((groups, import_) => {
      const status = import_.status;
      if (!groups[status]) {
        groups[status] = [];
      }
      groups[status].push(import_);
      return groups;
    }, {} as Record<ImportStatus, FeedImport[]>);
  }

  /**
   * Gets import trend (increasing/decreasing/stable)
   */
  static getImportTrend(dailyCounts: Record<string, number>): 'increasing' | 'decreasing' | 'stable' {
    const values = Object.values(dailyCounts);
    if (values.length < 2) return 'stable';

    const firstHalf = values.slice(0, Math.floor(values.length / 2));
    const secondHalf = values.slice(Math.floor(values.length / 2));

    const firstAvg = firstHalf.reduce((sum, val) => sum + val, 0) / firstHalf.length;
    const secondAvg = secondHalf.reduce((sum, val) => sum + val, 0) / secondHalf.length;

    const threshold = 0.1; // 10% change threshold
    const changeRatio = (secondAvg - firstAvg) / (firstAvg || 1);

    if (changeRatio > threshold) return 'increasing';
    if (changeRatio < -threshold) return 'decreasing';
    return 'stable';
  }

  /**
   * Validates import history filters
   */
  static validateFilters(filters: ImportHistoryFilters): string[] {
    const errors: string[] = [];

    if (filters.dateRange.startDate && filters.dateRange.endDate) {
      const start = new Date(filters.dateRange.startDate);
      const end = new Date(filters.dateRange.endDate);
      if (start > end) {
        errors.push('Start date must be before end date');
      }
    }

    return errors;
  }

  /**
   * Creates default filter values
   */
  static createDefaultFilters(): ImportHistoryFilters {
    return {
      feedOnestopId: null,
      status: null,
      triggerType: null,
      administratorId: null,
      dateRange: {
        startDate: null,
        endDate: null
      }
    };
  }

  /**
   * Creates default sort options
   */
  static createDefaultSortOptions(): ImportHistorySortOptions {
    return {
      field: 'createdAt',
      direction: 'desc'
    };
  }
}

/**
 * Constants for import history components
 */
export const IMPORT_HISTORY_CONSTANTS = {
  DEFAULT_PAGE_SIZE: 20,
  MAX_PAGE_SIZE: 100,
  DEFAULT_RECENT_LIMIT: 10,
  MAX_RECENT_LIMIT: 50,
  CHART_COLORS: {
    PRIMARY: 'rgb(59, 130, 246)',
    SUCCESS: 'rgb(34, 197, 94)',
    ERROR: 'rgb(239, 68, 68)',
    WARNING: 'rgb(245, 158, 11)',
    INFO: 'rgb(99, 102, 241)',
    NEUTRAL: 'rgb(156, 163, 175)'
  },
  DATE_FORMATS: {
    DISPLAY: 'MMM dd, yyyy',
    CHART: 'MM/dd',
    FULL: 'yyyy-MM-dd HH:mm:ss'
  }
} as const;