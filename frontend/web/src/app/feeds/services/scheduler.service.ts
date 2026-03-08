import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  SchedulerStatus,
  AutoUpdateConfig,
  ManualCheckResult,
  ImportStats,
  FeedVersionInfo,
} from '../models/scheduler.model';

@Injectable({
  providedIn: 'root',
})
export class SchedulerService {
  private readonly apiUrl = `${environment.apiUrl}/feeds/scheduler`;
  private readonly http = inject(HttpClient);

  /**
   * Get current scheduler status
   */
  getSchedulerStatus(): Observable<SchedulerStatus> {
    return this.http.get<SchedulerStatus>(`${this.apiUrl}/status`).pipe(
      map((status) => ({
        ...status,
        lastRunTime: status.lastRunTime ? new Date(status.lastRunTime) : undefined,
      }))
    );
  }

  /**
   * Get automatic update configuration
   */
  getAutoUpdateConfig(): Observable<AutoUpdateConfig> {
    return this.http.get<AutoUpdateConfig>(`${this.apiUrl}/config`);
  }

  /**
   * Update automatic update configuration
   */
  updateAutoUpdateConfig(config: AutoUpdateConfig): Observable<AutoUpdateConfig> {
    return this.http.put<AutoUpdateConfig>(`${this.apiUrl}/config`, config);
  }

  /**
   * Trigger manual feed update check
   */
  triggerManualCheck(): Observable<ManualCheckResult> {
    return this.http.post<ManualCheckResult>(`${this.apiUrl}/manual-check`, {});
  }

  /**
   * Get import statistics
   */
  getImportStats(): Observable<ImportStats> {
    return this.http.get<ImportStats>(`${this.apiUrl}/stats`).pipe(
      map((stats) => ({
        ...stats,
        lastAutomaticImportTime: stats.lastAutomaticImportTime
          ? new Date(stats.lastAutomaticImportTime)
          : undefined,
      }))
    );
  }

  /**
   * Get version information for all feeds
   */
  getAllFeedVersions(): Observable<FeedVersionInfo[]> {
    return this.http.get<FeedVersionInfo[]>(`${this.apiUrl}/versions`).pipe(
      map((versions) =>
        versions.map((version) => ({
          ...version,
          lastCheckedAt: version.lastCheckedAt ? new Date(version.lastCheckedAt) : undefined,
          lastUpdatedAt: version.lastUpdatedAt ? new Date(version.lastUpdatedAt) : undefined,
        }))
      )
    );
  }

  /**
   * Get version information for a specific feed
   */
  getFeedVersion(feedOnestopId: string): Observable<FeedVersionInfo> {
    return this.http.get<FeedVersionInfo>(`${this.apiUrl}/versions/${feedOnestopId}`).pipe(
      map((version) => ({
        ...version,
        lastCheckedAt: version.lastCheckedAt ? new Date(version.lastCheckedAt) : undefined,
        lastUpdatedAt: version.lastUpdatedAt ? new Date(version.lastUpdatedAt) : undefined,
      }))
    );
  }

  /**
   * Force refresh version information for a feed
   */
  refreshFeedVersion(feedOnestopId: string): Observable<FeedVersionInfo> {
    return this.http
      .post<FeedVersionInfo>(`${this.apiUrl}/versions/${feedOnestopId}/refresh`, {})
      .pipe(
        map((version) => ({
          ...version,
          lastCheckedAt: version.lastCheckedAt ? new Date(version.lastCheckedAt) : undefined,
          lastUpdatedAt: version.lastUpdatedAt ? new Date(version.lastUpdatedAt) : undefined,
        }))
      );
  }

  /**
   * Enable automatic updates for a specific feed
   */
  enableFeedAutoUpdate(feedOnestopId: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/feeds/${feedOnestopId}/auto-update/enable`, {});
  }

  /**
   * Disable automatic updates for a specific feed
   */
  disableFeedAutoUpdate(feedOnestopId: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/feeds/${feedOnestopId}/auto-update/disable`, {});
  }

  /**
   * Check if a specific feed has updates available
   */
  checkFeedUpdate(feedOnestopId: string): Observable<boolean> {
    return this.http
      .get<{
        hasUpdate: boolean;
      }>(`${this.apiUrl}/feeds/${feedOnestopId}/check-update`)
      .pipe(map((result) => result.hasUpdate));
  }
}
