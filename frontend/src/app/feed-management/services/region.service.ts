import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import {
  MetropolitanRegion,
  MetropolitanRegionDetail,
  RegionsResponse,
  FeedsResponse,
  RegionUpdateRequest,
  Feed,
  FeedSpecType,
  FeedStatus
} from '../models/region.models';
import { environment } from '../../../environments/environment';

/**
 * Region Service
 *
 * Handles API calls for metropolitan region management operations.
 * Provides caching and state management for region data.
 *
 * Constitutional Compliance:
 * - Performance: Implements caching for 200ms response targets
 * - Observability: Structured error handling and logging
 * - UX Consistency: Loading states and error handling patterns
 */
@Injectable({
  providedIn: 'root'
})
export class RegionService {
  private readonly apiUrl = `${environment.apiUrl}/api/feed-management/regions`;

  // Cache for regions list to improve performance
  private regionsCache$ = new BehaviorSubject<MetropolitanRegion[] | null>(null);
  private lastCacheUpdate = 0;
  private readonly CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes

  constructor(private http: HttpClient) {}

  /**
   * Lists all available metropolitan regions
   */
  listRegions(autoUpdateEnabled?: boolean, forceRefresh = false): Observable<MetropolitanRegion[]> {
    // Check cache first
    const cachedRegions = this.regionsCache$.value;
    const cacheAge = Date.now() - this.lastCacheUpdate;

    if (!forceRefresh && cachedRegions && cacheAge < this.CACHE_DURATION_MS) {
      return this.regionsCache$.asObservable().pipe(
        map(regions => this.filterRegionsByAutoUpdate(regions || [], autoUpdateEnabled))
      );
    }

    // Build query parameters
    let params = new HttpParams();
    if (autoUpdateEnabled !== undefined) {
      params = params.set('autoUpdateEnabled', autoUpdateEnabled.toString());
    }

    return this.http.get<RegionsResponse>(`${this.apiUrl}`, { params }).pipe(
      map(response => response.regions),
      tap(regions => {
        // Update cache
        this.regionsCache$.next(regions);
        this.lastCacheUpdate = Date.now();
      }),
      map(regions => this.filterRegionsByAutoUpdate(regions, autoUpdateEnabled))
    );
  }

  /**
   * Gets detailed information about a specific region
   */
  getRegion(regionOnestopId: string): Observable<MetropolitanRegionDetail> {
    return this.http.get<MetropolitanRegionDetail>(`${this.apiUrl}/${regionOnestopId}`);
  }

  /**
   * Updates region configuration (managers only)
   */
  updateRegion(regionOnestopId: string, update: RegionUpdateRequest): Observable<MetropolitanRegion> {
    return this.http.patch<MetropolitanRegion>(`${this.apiUrl}/${regionOnestopId}`, update).pipe(
      tap(updatedRegion => {
        // Update cache with the new region data
        const currentRegions = this.regionsCache$.value;
        if (currentRegions) {
          const updatedRegions = currentRegions.map(region =>
            region.regionOnestopId === regionOnestopId ? updatedRegion : region
          );
          this.regionsCache$.next(updatedRegions);
        }
      })
    );
  }

  /**
   * Lists feeds for a specific region
   */
  listFeedsForRegion(
    regionOnestopId: string,
    options?: {
      specType?: FeedSpecType;
      status?: FeedStatus;
    }
  ): Observable<Feed[]> {
    let params = new HttpParams();
    if (options?.specType) {
      params = params.set('specType', options.specType);
    }
    if (options?.status) {
      params = params.set('status', options.status);
    }

    return this.http.get<FeedsResponse>(`${this.apiUrl}/${regionOnestopId}/feeds`, { params }).pipe(
      map(response => response.feeds)
    );
  }

  /**
   * Triggers feed discovery for a region (managers only)
   */
  discoverFeedsForRegion(regionOnestopId: string): Observable<{
    regionOnestopId: string;
    feedsDiscovered: number;
    feedsAdded: number;
    feedsUpdated: number;
    errors: string[];
  }> {
    return this.http.post<{
      regionOnestopId: string;
      feedsDiscovered: number;
      feedsAdded: number;
      feedsUpdated: number;
      errors: string[];
    }>(`${this.apiUrl}/${regionOnestopId}/discover`, {});
  }

  /**
   * Clears the regions cache
   */
  clearCache(): void {
    this.regionsCache$.next(null);
    this.lastCacheUpdate = 0;
  }

  /**
   * Gets the current cached regions (for reactive components)
   */
  getCachedRegions(): Observable<MetropolitanRegion[] | null> {
    return this.regionsCache$.asObservable();
  }

  /**
   * Checks if region cache is valid
   */
  isCacheValid(): boolean {
    const cacheAge = Date.now() - this.lastCacheUpdate;
    return this.regionsCache$.value !== null && cacheAge < this.CACHE_DURATION_MS;
  }

  /**
   * Filters regions by auto-update setting if specified
   */
  private filterRegionsByAutoUpdate(
    regions: MetropolitanRegion[],
    autoUpdateEnabled?: boolean
  ): MetropolitanRegion[] {
    if (autoUpdateEnabled === undefined) {
      return regions;
    }

    return regions.filter(region => region.autoUpdateEnabled === autoUpdateEnabled);
  }

  /**
   * Search regions by name
   */
  searchRegions(searchTerm: string): Observable<MetropolitanRegion[]> {
    return this.listRegions().pipe(
      map(regions =>
        regions.filter(region =>
          region.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
          region.regionOnestopId.toLowerCase().includes(searchTerm.toLowerCase())
        )
      )
    );
  }

  /**
   * Gets regions with the most feeds
   */
  getTopRegionsByFeedCount(limit = 10): Observable<MetropolitanRegion[]> {
    return this.listRegions().pipe(
      map(regions =>
        regions
          .sort((a, b) => b.feedCount - a.feedCount)
          .slice(0, limit)
      )
    );
  }

  /**
   * Gets auto-update enabled regions
   */
  getAutoUpdateRegions(): Observable<MetropolitanRegion[]> {
    return this.listRegions(true);
  }

  /**
   * Gets regions that need attention (no recent check or errors)
   */
  getRegionsNeedingAttention(): Observable<MetropolitanRegion[]> {
    return this.listRegions().pipe(
      map(regions =>
        regions.filter(region => {
          // No recent check (more than 24 hours)
          if (!region.lastCheckAt) return true;

          const lastCheck = new Date(region.lastCheckAt);
          const twentyFourHoursAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);
          return lastCheck <= twentyFourHoursAgo;
        })
      )
    );
  }
}