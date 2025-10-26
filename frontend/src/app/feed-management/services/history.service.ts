import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  ImportHistoryRequest,
  ImportHistoryResponse,
  ImportStatisticsRequest,
  ImportStatistics,
  ImportDetails,
  FailureAnalysis,
  FailureAnalysisRequest,
  ImportDashboardSummary,
  ImportPeriod,
  ImportHistoryChartData,
  ImportHistoryExportOptions,
  ImportHistoryUtils
} from '../models/import-history.model';
import { FeedImport, ImportStatus, TriggerType } from '../models/import.models';
import { environment } from '../../../environments/environment';

/**
 * Import History Service
 *
 * Handles API calls for import history operations including querying
 * historical data, generating statistics, and failure analysis.
 *
 * Constitutional Compliance:
 * - Performance: Efficient pagination and filtering
 * - Observability: Comprehensive error handling and metrics
 * - UX: Advanced filtering and analytics capabilities
 */
@Injectable({
  providedIn: 'root'
})
export class HistoryService {
  private readonly apiUrl = `${environment.apiUrl}/api/feed-management/history`;

  constructor(private http: HttpClient) {}

  /**
   * Gets import history with filtering and pagination
   */
  getImportHistory(request: ImportHistoryRequest = {}): Observable<ImportHistoryResponse> {
    let params = new HttpParams();

    // Add filter parameters
    if (request.feedOnestopId) {
      params = params.set('feedOnestopId', request.feedOnestopId);
    }
    if (request.status) {
      params = params.set('status', request.status);
    }
    if (request.triggerType) {
      params = params.set('triggerType', request.triggerType);
    }
    if (request.administratorId) {
      params = params.set('administratorId', request.administratorId);
    }
    if (request.startDate) {
      params = params.set('startDate', request.startDate);
    }
    if (request.endDate) {
      params = params.set('endDate', request.endDate);
    }

    // Add pagination parameters
    if (request.page !== undefined) {
      params = params.set('page', request.page.toString());
    }
    if (request.size !== undefined) {
      params = params.set('size', request.size.toString());
    }

    // Add sorting parameters
    if (request.sortBy) {
      params = params.set('sortBy', request.sortBy);
    }
    if (request.sortDir) {
      params = params.set('sortDir', request.sortDir);
    }

    return this.http.get<ImportHistoryResponse>(this.apiUrl, { params });
  }

  /**
   * Gets detailed import information including logs and metrics
   */
  getImportDetails(importId: string): Observable<ImportDetails> {
    return this.http.get<ImportDetails>(`${this.apiUrl}/${importId}`);
  }

  /**
   * Gets import statistics for a time period
   */
  getImportStatistics(request: ImportStatisticsRequest = {}): Observable<ImportStatistics> {
    let params = new HttpParams();

    if (request.feedOnestopId) {
      params = params.set('feedOnestopId', request.feedOnestopId);
    }
    if (request.startDate) {
      params = params.set('startDate', request.startDate);
    }
    if (request.endDate) {
      params = params.set('endDate', request.endDate);
    }

    return this.http.get<ImportStatistics>(`${this.apiUrl}/statistics`, { params });
  }

  /**
   * Gets failure analysis for a time period
   */
  getFailureAnalysis(request: FailureAnalysisRequest = {}): Observable<FailureAnalysis> {
    let params = new HttpParams();

    if (request.startDate) {
      params = params.set('startDate', request.startDate);
    }
    if (request.endDate) {
      params = params.set('endDate', request.endDate);
    }

    return this.http.get<FailureAnalysis>(`${this.apiUrl}/failures`, { params });
  }

  /**
   * Gets dashboard summary data combining multiple metrics
   */
  getDashboardSummary(
    feedOnestopId?: string,
    period?: ImportPeriod
  ): Observable<ImportDashboardSummary> {
    let params = new HttpParams();

    if (feedOnestopId) {
      params = params.set('feedOnestopId', feedOnestopId);
    }
    if (period?.startDate) {
      params = params.set('startDate', period.startDate);
    }
    if (period?.endDate) {
      params = params.set('endDate', period.endDate);
    }

    return this.http.get<ImportDashboardSummary>(`${this.apiUrl}/dashboard`, { params });
  }

  /**
   * Gets recent import activity (last 24 hours by default)
   */
  getRecentActivity(limit = 10): Observable<FeedImport[]> {
    const twentyFourHoursAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);

    return this.getImportHistory({
      startDate: twentyFourHoursAgo.toISOString(),
      size: limit,
      sortBy: 'createdAt',
      sortDir: 'desc'
    }).pipe(
      map(response => response.content)
    );
  }

  /**
   * Gets import history for a specific feed
   */
  getFeedHistory(
    feedOnestopId: string,
    options: {
      page?: number;
      size?: number;
      status?: ImportStatus;
      startDate?: string;
      endDate?: string;
    } = {}
  ): Observable<ImportHistoryResponse> {
    return this.getImportHistory({
      feedOnestopId,
      ...options
    });
  }

  /**
   * Gets failed imports that need attention
   */
  getFailedImports(
    options: {
      page?: number;
      size?: number;
      startDate?: string;
      endDate?: string;
    } = {}
  ): Observable<ImportHistoryResponse> {
    return this.getImportHistory({
      status: ImportStatus.FAILED,
      ...options
    });
  }

  /**
   * Gets automatic imports
   */
  getAutomaticImports(
    options: {
      page?: number;
      size?: number;
      startDate?: string;
      endDate?: string;
    } = {}
  ): Observable<ImportHistoryResponse> {
    return this.getImportHistory({
      triggerType: TriggerType.AUTOMATIC,
      ...options
    });
  }

  /**
   * Gets manual imports
   */
  getManualImports(
    options: {
      page?: number;
      size?: number;
      startDate?: string;
      endDate?: string;
    } = {}
  ): Observable<ImportHistoryResponse> {
    return this.getImportHistory({
      triggerType: TriggerType.MANUAL,
      ...options
    });
  }

  /**
   * Gets chart data for import history visualizations
   */
  getChartData(period?: ImportPeriod): Observable<ImportHistoryChartData> {
    return this.getImportStatistics({
      startDate: period?.startDate,
      endDate: period?.endDate
    }).pipe(
      map(statistics => this.convertStatisticsToChartData(statistics))
    );
  }

  /**
   * Exports import history data in specified format
   */
  exportHistory(options: ImportHistoryExportOptions): Observable<Blob> {
    let params = new HttpParams()
      .set('format', options.format)
      .set('includeMetrics', options.includeMetrics.toString())
      .set('includeLogs', options.includeLogs.toString())
      .set('startDate', options.dateRange.startDate)
      .set('endDate', options.dateRange.endDate);

    // Add filter parameters
    if (options.filters.feedOnestopId) {
      params = params.set('feedOnestopId', options.filters.feedOnestopId);
    }
    if (options.filters.status) {
      params = params.set('status', options.filters.status);
    }
    if (options.filters.triggerType) {
      params = params.set('triggerType', options.filters.triggerType);
    }
    if (options.filters.administratorId) {
      params = params.set('administratorId', options.filters.administratorId);
    }
    if (options.filters.dateRange.startDate) {
      params = params.set('filterStartDate', options.filters.dateRange.startDate);
    }
    if (options.filters.dateRange.endDate) {
      params = params.set('filterEndDate', options.filters.dateRange.endDate);
    }

    return this.http.get(`${this.apiUrl}/export`, {
      params,
      responseType: 'blob'
    });
  }

  /**
   * Gets import statistics for the last 30 days
   */
  getLast30DaysStatistics(): Observable<ImportStatistics> {
    const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
    const now = new Date();

    return this.getImportStatistics({
      startDate: thirtyDaysAgo.toISOString(),
      endDate: now.toISOString()
    });
  }

  /**
   * Gets import statistics for the last 7 days
   */
  getLast7DaysStatistics(): Observable<ImportStatistics> {
    const sevenDaysAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
    const now = new Date();

    return this.getImportStatistics({
      startDate: sevenDaysAgo.toISOString(),
      endDate: now.toISOString()
    });
  }

  /**
   * Gets import history grouped by feed
   */
  getHistoryByFeed(period?: ImportPeriod): Observable<Record<string, FeedImport[]>> {
    return this.getImportHistory({
      startDate: period?.startDate,
      endDate: period?.endDate,
      size: 1000 // Get a large batch to group by feed
    }).pipe(
      map(response => {
        const groupedImports: Record<string, FeedImport[]> = {};
        response.content.forEach(import_ => {
          if (!groupedImports[import_.feedOnestopId]) {
            groupedImports[import_.feedOnestopId] = [];
          }
          groupedImports[import_.feedOnestopId].push(import_);
        });
        return groupedImports;
      })
    );
  }

  /**
   * Gets import history for multiple feeds
   */
  getMultipleFeedsHistory(
    feedOnestopIds: string[],
    options: {
      page?: number;
      size?: number;
      status?: ImportStatus;
      startDate?: string;
      endDate?: string;
    } = {}
  ): Observable<Record<string, ImportHistoryResponse>> {
    // Make parallel requests for each feed
    const requests = feedOnestopIds.map(feedId =>
      this.getFeedHistory(feedId, options).pipe(
        map(response => ({ feedId, response }))
      )
    );

    return new Observable(observer => {
      Promise.all(requests.map(req => req.toPromise())).then(results => {
        const grouped = results.reduce((acc, result) => {
          if (result) {
            acc[result.feedId] = result.response;
          }
          return acc;
        }, {} as Record<string, ImportHistoryResponse>);

        observer.next(grouped);
        observer.complete();
      }).catch(error => {
        observer.error(error);
      });
    });
  }

  /**
   * Searches import history by error message or log content
   */
  searchImportHistory(
    searchTerm: string,
    options: {
      page?: number;
      size?: number;
      startDate?: string;
      endDate?: string;
    } = {}
  ): Observable<ImportHistoryResponse> {
    let params = new HttpParams()
      .set('search', searchTerm);

    if (options.page !== undefined) {
      params = params.set('page', options.page.toString());
    }
    if (options.size !== undefined) {
      params = params.set('size', options.size.toString());
    }
    if (options.startDate) {
      params = params.set('startDate', options.startDate);
    }
    if (options.endDate) {
      params = params.set('endDate', options.endDate);
    }

    return this.http.get<ImportHistoryResponse>(`${this.apiUrl}/search`, { params });
  }

  /**
   * Gets import performance metrics
   */
  getPerformanceMetrics(period?: ImportPeriod): Observable<{
    averageImportTime: number;
    fastestImport: number;
    slowestImport: number;
    importThroughput: number;
    failureRate: number;
  }> {
    return this.getImportStatistics({
      startDate: period?.startDate,
      endDate: period?.endDate
    }).pipe(
      map(statistics => ({
        averageImportTime: statistics.averageDurationSeconds,
        fastestImport: 0, // Would be calculated on backend
        slowestImport: 0, // Would be calculated on backend
        importThroughput: statistics.totalImports / 30, // Imports per day over period
        failureRate: (statistics.failedImports / statistics.totalImports) * 100
      }))
    );
  }

  /**
   * Private helper to convert statistics to chart data
   */
  private convertStatisticsToChartData(statistics: ImportStatistics): ImportHistoryChartData {
    return {
      dailyImports: ImportHistoryUtils.convertDailyCountsToChartData(statistics.dailyCounts),
      statusDistribution: ImportHistoryUtils.createStatusDistributionChartData(statistics),
      triggerTypeDistribution: ImportHistoryUtils.createTriggerTypeDistributionChartData(statistics),
      hourlyDistribution: ImportHistoryUtils.convertHourlyDistributionToChartData(statistics.hourlyDistribution)
    };
  }

  /**
   * Validates filter parameters
   */
  validateFilters(request: ImportHistoryRequest): string[] {
    const errors: string[] = [];

    if (request.startDate && request.endDate) {
      const start = new Date(request.startDate);
      const end = new Date(request.endDate);
      if (start > end) {
        errors.push('Start date must be before end date');
      }
    }

    if (request.page !== undefined && request.page < 0) {
      errors.push('Page number must be non-negative');
    }

    if (request.size !== undefined && (request.size < 1 || request.size > 100)) {
      errors.push('Page size must be between 1 and 100');
    }

    return errors;
  }

  /**
   * Creates a default history request with sensible defaults
   */
  createDefaultRequest(): ImportHistoryRequest {
    return {
      page: 0,
      size: 20,
      sortBy: 'createdAt',
      sortDir: 'desc'
    };
  }

  /**
   * Creates a date range for common periods
   */
  createDateRange(period: 'last7days' | 'last30days' | 'last90days' | 'thisMonth' | 'lastMonth'): ImportPeriod {
    const now = new Date();
    let startDate: Date;

    switch (period) {
      case 'last7days':
        startDate = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
        break;
      case 'last30days':
        startDate = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
        break;
      case 'last90days':
        startDate = new Date(now.getTime() - 90 * 24 * 60 * 60 * 1000);
        break;
      case 'thisMonth':
        startDate = new Date(now.getFullYear(), now.getMonth(), 1);
        break;
      case 'lastMonth':
        startDate = new Date(now.getFullYear(), now.getMonth() - 1, 1);
        now.setDate(0); // Set to last day of previous month
        break;
      default:
        startDate = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
    }

    return {
      startDate: startDate.toISOString(),
      endDate: now.toISOString()
    };
  }
}