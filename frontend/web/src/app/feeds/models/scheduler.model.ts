export interface SchedulerStatus {
  enabled: boolean;
  totalActiveFeeds: number;
  feedsCheckedInLast24Hours: number;
  nextScheduledRun: string;
  lastRunTime?: Date;
}

export interface AutoUpdateConfig {
  globalAutoUpdateEnabled: boolean;
  defaultCheckIntervalHours: number;
  maxConcurrentImports: number;
  notifyOnFailures: boolean;
  retryFailedImports: number;
}

export interface ManualCheckResult {
  success: boolean;
  checkedCount: number;
  updatesTriggered: number;
  errorCount: number;
  errors: string[];
  message: string;
}

export interface ImportStats {
  totalAutomaticImportsLast24h: number;
  successfulImportsLast24h: number;
  failedImportsLast24h: number;
  currentlyRunningAutoImports: number;
  lastAutomaticImportTime?: Date;
}

export interface FeedVersionInfo {
  feedOnestopId: string;
  currentVersionSha1?: string;
  latestVersionSha1?: string;
  hasUpdate: boolean;
  lastCheckedAt?: Date;
  lastUpdatedAt?: Date;
  status: 'available' | 'not_found' | 'api_unavailable' | 'error';
  error?: string;
}
