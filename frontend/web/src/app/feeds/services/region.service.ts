import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, BehaviorSubject, throwError } from 'rxjs';
import { map, tap, catchError } from 'rxjs/operators';
import {
  MetropolitanRegion,
  MetropolitanRegionDetail,
  RegionsResponse,
  FeedsResponse,
  RegionUpdateRequest,
  Feed,
  FeedSpecType,
  FeedStatus,
  FeedDiscoveryResult,
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
  providedIn: 'root',
})
export class RegionService {
  private readonly apiUrl = `${environment.apiUrl}/feeds/regions`;
  private readonly http = inject(HttpClient);

  // Cache for regions list to improve performance
  private regionsCache$ = new BehaviorSubject<MetropolitanRegion[] | null>(null);
  private lastCacheUpdate = 0;
  private readonly CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes

  /**
   * Lists all available metropolitan regions
   */
  listRegions(autoUpdateEnabled?: boolean, forceRefresh = false): Observable<MetropolitanRegion[]> {
    // Check cache first
    const cachedRegions = this.regionsCache$.value;
    const cacheAge = Date.now() - this.lastCacheUpdate;

    if (!forceRefresh && cachedRegions && cacheAge < this.CACHE_DURATION_MS) {
      return this.regionsCache$
        .asObservable()
        .pipe(map((regions) => this.filterRegionsByAutoUpdate(regions || [], autoUpdateEnabled)));
    }

    // Build query parameters
    let params = new HttpParams();
    if (autoUpdateEnabled !== undefined) {
      params = params.set('autoUpdateEnabled', autoUpdateEnabled.toString());
    }

    return this.http.get<RegionsResponse>(`${this.apiUrl}`, { params }).pipe(
      map((response) => response.regions),
      tap((regions) => {
        // Update cache
        this.regionsCache$.next(regions);
        this.lastCacheUpdate = Date.now();
      }),
      map((regions) => this.filterRegionsByAutoUpdate(regions, autoUpdateEnabled)),
      catchError((error) => {
        console.error('Failed to load regions', error);
        return throwError(() => error);
      })
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
  updateRegion(
    regionOnestopId: string,
    update: RegionUpdateRequest
  ): Observable<MetropolitanRegion> {
    return this.http.patch<MetropolitanRegion>(`${this.apiUrl}/${regionOnestopId}`, update).pipe(
      tap((updatedRegion) => {
        // Update cache with the new region data
        const currentRegions = this.regionsCache$.value;
        if (currentRegions) {
          const updatedRegions = currentRegions.map((region) =>
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

    return this.http
      .get<FeedsResponse>(`${this.apiUrl}/${regionOnestopId}/feeds`, { params })
      .pipe(map((response) => response.feeds));
  }

  /**
   * Triggers feed discovery for a region (managers only)
   */
  discoverFeedsForRegion(regionOnestopId: string): Observable<FeedDiscoveryResult> {
    return this.http.post<FeedDiscoveryResult>(`${this.apiUrl}/${regionOnestopId}/discover`, {});
  }

  /**
   * Clears the regions cache
   */
  clearCache(): void {
    this.regionsCache$.next(null);
    this.lastCacheUpdate = 0;
  }

  /**
   * Gets a region from the in-memory cache (if available)
   */
  getCachedRegion(regionOnestopId: string): MetropolitanRegion | undefined {
    return this.regionsCache$.value?.find((region) => region.regionOnestopId === regionOnestopId);
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

    return regions.filter((region) => region.autoUpdateEnabled === autoUpdateEnabled);
  }

  /**
   * Search regions by name
   */
  searchRegions(searchTerm: string): Observable<MetropolitanRegion[]> {
    return this.listRegions().pipe(
      map((regions) =>
        regions.filter(
          (region) =>
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
      map((regions) => regions.sort((a, b) => b.feedCount - a.feedCount).slice(0, limit))
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
      map((regions) =>
        regions.filter((region) => {
          // No recent check (more than 24 hours)
          if (!region.lastCheckAt) return true;

          const lastCheck = new Date(region.lastCheckAt);
          const twentyFourHoursAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);
          return lastCheck <= twentyFourHoursAgo;
        })
      )
    );
  }

  /**
   * Checks if a region is a Canadian region based on name patterns
   */
  isCanadianRegion(region: MetropolitanRegion): boolean {
    const canadianProvinces = [
      'Ontario',
      'Quebec',
      'British Columbia',
      'Alberta',
      'Manitoba',
      'Saskatchewan',
      'Nova Scotia',
      'New Brunswick',
      'Newfoundland',
      'Prince Edward Island',
      'Yukon',
      'Northwest Territories',
      'Nunavut',
      'ON',
      'QC',
      'BC',
      'AB',
      'MB',
      'SK',
      'NS',
      'NB',
      'NL',
      'PE',
      'YT',
      'NT',
      'NU',
    ];

    const canadianCities = [
      'Toronto',
      'Vancouver',
      'Montreal',
      'Calgary',
      'Edmonton',
      'Ottawa',
      'Winnipeg',
      'Quebec City',
      'Hamilton',
      'Kitchener',
      'London',
      'Victoria',
      'Halifax',
      'Oshawa',
      'Windsor',
      'Saskatoon',
      'Regina',
      "St. John's",
      'Barrie',
      'Kelowna',
      'Sherbrooke',
      'Guelph',
      'Kanata',
      'Abbotsford',
      'Trois-Rivières',
      'Kingston',
      'Milton',
      'Moncton',
      'Thunder Bay',
    ];

    const nameAndId = `${region.name} ${region.regionOnestopId}`.toLowerCase();

    // Check for Canadian province or city names
    return (
      canadianProvinces.some((province) => nameAndId.includes(province.toLowerCase())) ||
      canadianCities.some((city) => nameAndId.includes(city.toLowerCase())) ||
      nameAndId.includes('canada') ||
      nameAndId.includes('canadian')
    );
  }

  /**
   * Sorts regions to prioritize Canadian regions first
   */
  sortWithCanadianPriority(regions: MetropolitanRegion[]): MetropolitanRegion[] {
    return regions.sort((a, b) => {
      const aIsCanadian = this.isCanadianRegion(a);
      const bIsCanadian = this.isCanadianRegion(b);

      // Canadian regions first
      if (aIsCanadian && !bIsCanadian) return -1;
      if (!aIsCanadian && bIsCanadian) return 1;

      // Within same category, sort by feed count (descending)
      if (a.feedCount !== b.feedCount) {
        return b.feedCount - a.feedCount;
      }

      // Finally, sort alphabetically by name
      return a.name.localeCompare(b.name);
    });
  }
}
