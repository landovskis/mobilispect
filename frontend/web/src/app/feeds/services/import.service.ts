import { Injectable, OnDestroy, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, BehaviorSubject, timer, of, merge } from 'rxjs';
import { map, tap, switchMap, takeUntil, distinctUntilChanged, catchError, startWith, filter } from 'rxjs/operators';
import { WebSocketService } from './websocket.service';
import {
  FeedImport,
  FeedImportDetail,
  ImportProgress,
  ImportRequest,
  ImportStatus,
  TriggerType,
  ImportsResponse,
  ActiveImportsResponse,
  FeedImportSummary
} from '../models/import.models';
import { environment } from '../../../environments/environment';

/**
 * Import Service
 *
 * Handles API calls for feed import operations including starting,
 * monitoring, and managing import processes.
 *
 * Constitutional Compliance:
 * - Performance: Efficient polling and caching strategies
 * - Real-time: WebSocket integration for live progress updates
 * - Observability: Comprehensive error handling and metrics
 * - UX: Loading states and progress indicators
 */
@Injectable({
  providedIn: 'root'
})
export class ImportService implements OnDestroy {
  private readonly apiUrl = `${environment.apiUrl}/feeds`;
  private readonly http = inject(HttpClient);
  private readonly webSocketService = inject(WebSocketService);

  // Active imports cache for real-time updates
  private activeImports$ = new BehaviorSubject<FeedImportSummary[]>([]);
  private pollingInterval = 5000; // 5 seconds
  private isPolling = false;

  /**
   * Initializes WebSocket connection for real-time updates
   */
  initializeWebSocket(): void {
    this.webSocketService.connect();
    this.webSocketService.startHeartbeat();
  }

  /**
   * Disconnects WebSocket
   */
  disconnectWebSocket(): void {
    this.webSocketService.disconnect();
  }

  /**
   * Starts a new feed import
   */
  startImport(feedId: string, request?: ImportRequest): Observable<FeedImport> {
    const body = request || { force: false };
    return this.http.post<FeedImport>(`${this.apiUrl}/${feedId}/import`, body).pipe(
      tap(() => {
        // Start polling for active imports to update UI
        this.startPollingActiveImports();
      }),
      catchError(error => {
        console.error('Import API error:', error);
        // Re-throw the error with enhanced information
        throw {
          ...error,
          message: this.getErrorMessage(error),
          isBackendError: true
        };
      })
    );
  }

  private getErrorMessage(error: unknown): string {
    const candidate = error as {
      status?: number;
      statusText?: string;
      error?: { message?: string };
    };
    if (candidate.status === 0) {
      return 'Cannot connect to backend server. Please check if the backend is running.';
    } else if (candidate.status === 403) {
      return 'Authentication required. Please log in to perform imports.';
    } else if (candidate.status === 404) {
      return 'Feed not found or import endpoint not available.';
    } else if (candidate.status === 503) {
      return 'Backend service is temporarily unavailable. Database connection issues detected.';
    } else if (candidate.error?.message) {
      return candidate.error.message;
    } else {
      return `Backend error (${candidate.status ?? 'unknown'}): ${candidate.statusText || 'Unknown error'}`;
    }
  }

  /**
   * Gets import history for a feed
   */
  getFeedImportHistory(
    feedOnestopId: string,
    options?: {
      page?: number;
      size?: number;
      status?: ImportStatus;
    }
  ): Observable<{ imports: FeedImport[]; totalElements: number; totalPages: number }> {
    let params = new HttpParams();
    if (options?.page !== undefined) {
      params = params.set('page', options.page.toString());
    }
    if (options?.size !== undefined) {
      params = params.set('size', options.size.toString());
    }
    if (options?.status) {
      params = params.set('status', options.status);
    }

    return this.http.get<ImportsResponse>(`${this.apiUrl}/${feedOnestopId}/imports`, { params }).pipe(
      map(response => ({
        imports: response.imports,
        totalElements: response.page.totalElements,
        totalPages: response.page.totalPages
      }))
    );
  }

  /**
   * Gets detailed information about a specific import
   */
  getImport(importId: string): Observable<FeedImportDetail> {
    return this.http.get<FeedImportDetail>(`${this.apiUrl}/imports/${importId}`);
  }

  /**
   * Cancels a running import
   */
  cancelImport(importId: string): Observable<FeedImport> {
    return this.http.delete<FeedImport>(`${this.apiUrl}/imports/${importId}`).pipe(
      tap(() => {
        // Refresh active imports after cancellation
        this.refreshActiveImports();
      })
    );
  }

  /**
   * Gets import progress for a specific import
   */
  getImportProgress(importId: string): Observable<ImportProgress> {
    return this.http.get<ImportProgress>(`${this.apiUrl}/imports/${importId}/progress`);
  }

  /**
   * Gets all active imports
   */
  getActiveImports(): Observable<FeedImportSummary[]> {
    return this.http.get<ActiveImportsResponse>(`${this.apiUrl}/imports/active`).pipe(
      map(response => response.imports),
      tap(imports => {
        this.activeImports$.next(imports);
      })
    );
  }

  /**
   * Gets active imports as an observable (cached)
   */
  getActiveImportsObservable(): Observable<FeedImportSummary[]> {
    return this.activeImports$.asObservable();
  }

  /**
   * Starts polling for active imports (for real-time updates)
   */
  startPollingActiveImports(): void {
    if (this.isPolling) return;

    this.isPolling = true;
    timer(0, this.pollingInterval).pipe(
      switchMap(() => this.getActiveImports()),
      distinctUntilChanged((prev, curr) => JSON.stringify(prev) === JSON.stringify(curr)),
    ).subscribe();
  }


  /**
   * Stops polling for active imports
   */
  stopPollingActiveImports(): void {
    this.isPolling = false;
  }

  /**
   * Manually refreshes active imports
   */
  refreshActiveImports(): void {
    this.getActiveImports().subscribe();
  }

  /**
   * Monitors import progress with hybrid WebSocket + HTTP polling approach
   */
  monitorImportProgress(importId: string, stopSignal?: Observable<void>): Observable<ImportProgress> {
    // HTTP polling as fallback
    const polling$ = timer(0, 5000).pipe(
      switchMap(() => this.getImportProgress(importId)),
      distinctUntilChanged((prev, curr) =>
        prev.progressPercentage === curr.progressPercentage &&
        prev.currentStep === curr.currentStep
      ),
      catchError(error => {
        console.warn('HTTP polling failed, continuing...', error);
        return of(null);
      })
    );

    // WebSocket real-time updates (STOMP protocol)
    const webSocket$ = this.webSocketService.subscribeToImportProgress(importId).pipe(
      map(msg => {
        // Backend sends ProgressUpdate: { progress?: ImportProgress, completed?: boolean, error?: string }
        if (!msg.progress) {
          console.warn('Received WebSocket message without progress field:', msg);
          return null;
        }

        const progress = msg.progress;
        return {
          importId: progress.importId,
          progressPercentage: progress.progressPercentage,
          totalSteps: progress.totalSteps || 8,
          currentStep: progress.currentStep,
          estimatedTimeRemainingSeconds: progress.estimatedTimeRemainingSeconds || null,
          startedAt: progress.startedAt,
          lastUpdatedAt: progress.lastUpdatedAt
        } as ImportProgress;
      }),
      filter((progress): progress is ImportProgress => progress !== null),
      catchError(error => {
        console.warn('WebSocket progress updates failed, using HTTP polling only', error);
        return of(null);
      })
    );

    // Merge both streams, preferring WebSocket updates when available
    const combined$ = merge(
      polling$.pipe(startWith(null)),
      webSocket$
    ).pipe(
      filter((progress): progress is ImportProgress => progress !== null),
      distinctUntilChanged((prev, curr) =>
        prev!.progressPercentage === curr!.progressPercentage &&
        prev!.currentStep === curr!.currentStep
      )
    ) as Observable<ImportProgress>;

    return stopSignal ? combined$.pipe(takeUntil(stopSignal)) : combined$;
  }

  /**
   * Monitors import status changes with WebSocket + HTTP hybrid approach
   */
  monitorImportStatus(importId: string, stopSignal?: Observable<void>): Observable<FeedImportDetail> {
    // HTTP polling as fallback
    const polling$ = timer(0, 3000).pipe(
      switchMap(() => this.getImport(importId)),
      distinctUntilChanged((prev, curr) => prev.status === curr.status),
      catchError(error => {
        console.warn('HTTP status polling failed, continuing...', error);
        return of(null);
      })
    );

    // WebSocket real-time status updates
    const webSocket$ = this.webSocketService.subscribeToImportStatus(importId).pipe(
      switchMap(() => this.getImport(importId)), // Fetch full details when status changes
      catchError(error => {
        console.warn('WebSocket status updates failed, using HTTP polling only', error);
        return of(null);
      })
    );

    // Merge both streams
    const combined$ = merge(
      polling$.pipe(startWith(null)),
      webSocket$
    ).pipe(
      filter((importDetail): importDetail is FeedImportDetail => importDetail !== null),
      distinctUntilChanged((prev, curr) => prev!.status === curr!.status)
    ) as Observable<FeedImportDetail>;

    return stopSignal ? combined$.pipe(takeUntil(stopSignal)) : combined$;
  }

  /**
   * Gets import history for all feeds (admin view)
   */
  getAllImportHistory(options?: {
    page?: number;
    size?: number;
    status?: ImportStatus;
    triggerType?: TriggerType;
  }): Observable<{ imports: FeedImport[]; totalElements: number; totalPages: number }> {
    let params = new HttpParams();
    if (options?.page !== undefined) {
      params = params.set('page', options.page.toString());
    }
    if (options?.size !== undefined) {
      params = params.set('size', options.size.toString());
    }
    if (options?.status) {
      params = params.set('status', options.status);
    }
    if (options?.triggerType) {
      params = params.set('triggerType', options.triggerType);
    }

    return this.http.get<ImportsResponse>(`${this.apiUrl}/imports`, { params }).pipe(
      map(response => ({
        imports: response.imports,
        totalElements: response.page.totalElements,
        totalPages: response.page.totalPages
      }))
    );
  }

  /**
   * Gets import statistics
   */
  getImportStatistics(): Observable<{
    totalImports: number;
    successfulImports: number;
    failedImports: number;
    activeImports: number;
    averageImportTime: number;
  }> {
    // This would be implemented with a dedicated statistics endpoint
    // For now, we'll derive it from active imports
    return this.getActiveImports().pipe(
      map(activeImports => ({
        totalImports: 0, // Would come from backend
        successfulImports: 0, // Would come from backend
        failedImports: 0, // Would come from backend
        activeImports: activeImports.length,
        averageImportTime: 0 // Would come from backend
      }))
    );
  }

  /**
   * Checks if an import is currently running for a feed
   */
  isImportRunningForFeed(feedOnestopId: string): Observable<boolean> {
    return this.getActiveImports().pipe(
      map(activeImports =>
        activeImports.some(imp => imp.feedOnestopId === feedOnestopId)
      )
    );
  }

  /**
   * Gets the current active import for a feed (if any)
   */
  getActiveImportForFeed(feedOnestopId: string): Observable<FeedImportSummary | null> {
    return this.getActiveImports().pipe(
      map(activeImports =>
        activeImports.find(imp => imp.feedOnestopId === feedOnestopId) || null
      )
    );
  }

  /**
   * Retries a failed import
   */
  retryImport(importId: string): Observable<FeedImport> {
    // Get the original import details and start a new import
    return this.getImport(importId).pipe(
      switchMap(importDetail =>
        this.startImport(importDetail.feedOnestopId, { force: true })
      )
    );
  }

  /**
   * Bulk cancel multiple imports
   */
  bulkCancelImports(importIds: string[]): Promise<{
    id: string;
    status: 'COMPLETED' | 'FAILED';
    result?: FeedImport;
    error?: string;
  }[]> {
    const cancelRequests = importIds.map(id =>
      this.cancelImport(id).toPromise().then(
        result => ({ id, status: 'COMPLETED', result }),
        error => ({ id, status: 'FAILED', error: error.message || 'Unknown error' })
      )
    );
    return Promise.all(cancelRequests);
  }

  /**
   * Gets recent imports (last 24 hours)
   */
  getRecentImports(limit = 50): Observable<FeedImport[]> {
    return this.getAllImportHistory({ size: limit }).pipe(
      map(response => response.imports.filter(imp => {
        const importDate = new Date(imp.createdAt);
        const twentyFourHoursAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);
        return importDate > twentyFourHoursAgo;
      }))
    );
  }

  /**
   * Gets failed imports that need attention
   */
  getFailedImports(): Observable<FeedImport[]> {
    return this.getAllImportHistory({ status: ImportStatus.FAILED }).pipe(
      map(response => response.imports)
    );
  }

  /**
   * Cleanup method to stop polling when service is destroyed
   */
  ngOnDestroy(): void {
    this.stopPollingActiveImports();
  }
}
