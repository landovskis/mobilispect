import { Component, Input, OnChanges, SimpleChanges, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subject, Observable, combineLatest, of } from 'rxjs';
import { map, takeUntil, catchError, tap } from 'rxjs/operators';
import { MetropolitanRegion, MetropolitanRegionDetail, Feed, RegionUtils } from '../../feeds/models/region.models';
import { AgencyFeedGroup, FeedGroupingUtils } from '../../feeds/models/agency-feed-group.model';
import { RegionService } from '../../feeds/services/region.service';
import { ImportService } from '../../feeds/services/import.service';
import { AgencyService } from '../../agencies/services/agency.service';
import { AgencyListResponse } from '../../transit-frequency/services/agency.service';
import { FeedsMetricsService } from '../../feeds/services/feeds-metrics.service';
import { FeedsEventsService } from '../../feeds/services/feeds-events.service';
import { AgencyCardComponent } from '../../transit-frequency/components/agency-card/agency-card.component';
import { BulkImportResponse } from '../../feeds/models/import.models';

interface RegionSummary {
  name: string;
  totalAgencies: number;
  totalActiveRoutes: number;
}

/**
 * Region Detail Panel Component
 *
 * Displays detailed information about a selected region in a tabbed interface.
 * Shows feeds grouped by agency and an overview with summary statistics.
 * This is the "detail" panel in the master-detail layout.
 *
 * Constitutional Compliance:
 * - UX Consistency: Material Design 3 with tabbed navigation
 * - Performance: OnPush change detection, lazy loading of tab content
 * - Accessibility: ARIA labels, keyboard navigation, tab announcements
 * - Responsive: Mobile-first design with responsive grids
 */
@Component({
  selector: 'app-region-detail-panel',
  standalone: true,
  imports: [
    CommonModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    AgencyCardComponent
  ],
  template: `
    <div class="region-detail-container">
      @if (!region) {
        <!-- Empty State - No Region Selected -->
        <div class="empty-state flex flex-col items-center justify-center px-6 py-16 text-center">
          <mat-icon class="empty-icon mb-4 text-[64px] text-[rgba(0,0,0,0.3)]">travel_explore</mat-icon>
          <h3 class="mb-2 text-xl font-semibold text-[rgba(0,0,0,0.7)]">No Region Selected</h3>
          <p class="max-w-[400px] text-sm text-[rgba(0,0,0,0.6)]">
            Select a region from the list to view its transit feeds and agencies.
          </p>
        </div>
      } @else {
        <!-- Region Header -->
        <div class="region-header mb-6">
          <div class="flex items-center justify-between">
            <h2 class="text-2xl font-semibold text-[var(--mdc-theme-on-surface)]">
              {{ getDisplayName(region) }}
            </h2>
            <!-- Import All Feeds Button -->
            @if (region.feedCount && region.feedCount > 0) {
              <button
                mat-raised-button
                color="primary"
                [disabled]="isImportingAll"
                (click)="confirmAndImportAllFeeds()"
                class="import-all-button"
              >
                @if (isImportingAll) {
                  <mat-spinner diameter="20" class="mr-2 inline-block"></mat-spinner>
                  <span>Importing...</span>
                } @else {
                  <mat-icon class="mr-1">cloud_download</mat-icon>
                  <span>Import All Feeds</span>
                }
              </button>
            }
          </div>
          <div class="region-meta mt-2 flex flex-wrap gap-4 text-sm text-[var(--mdc-theme-on-surface-variant)]">
            <div class="meta-item flex items-center gap-1">
              <mat-icon class="text-[18px]">location_on</mat-icon>
              <span>{{ region.regionOnestopId }}</span>
            </div>
            @if (region.feedCount !== undefined) {
              <div class="meta-item flex items-center gap-1">
                <mat-icon class="text-[18px]">feed</mat-icon>
                <span>{{ region.feedCount }} feeds</span>
              </div>
            }
            @if (region.autoUpdateEnabled !== undefined) {
              <div class="meta-item flex items-center gap-1">
                <mat-icon class="text-[18px]">
                  {{ region.autoUpdateEnabled ? 'sync' : 'sync_disabled' }}
                </mat-icon>
                <span>{{ region.autoUpdateEnabled ? 'Auto-update enabled' : 'Manual updates' }}</span>
              </div>
            }
          </div>
        </div>

        <div class="overview-header flex items-center gap-2 text-sm font-semibold uppercase tracking-[0.12em] text-[var(--mdc-theme-on-surface-variant)]">
          <mat-icon class="text-[18px]">analytics</mat-icon>
          Overview
        </div>

        <div class="tab-content pt-4">
          @if (loadingOverview) {
            <div class="loading-state flex flex-col items-center justify-center gap-4 px-6 py-12">
              <mat-spinner diameter="40"></mat-spinner>
              <p class="text-sm text-[var(--mdc-theme-on-surface-variant)]">Loading overview...</p>
            </div>
          } @else {
            <!-- Summary Statistics -->
            @if (summary$ | async; as summary) {
              <div class="summary-section mb-6">
                <h3 class="mb-4 text-lg font-semibold text-[var(--mdc-theme-on-surface)]">Summary</h3>
                <div class="summary-grid grid gap-4 md:grid-cols-2">
                  <div class="summary-card rounded-xl border border-[var(--mat-sys-outline-variant,#e0e0e0)] bg-[var(--mat-sys-surface-container,#f5f5f5)] p-5 text-center">
                    <div class="summary-value">{{ summary.totalAgencies }}</div>
                    <div class="summary-label mt-2">Transit Agencies</div>
                  </div>
                  <div class="summary-card rounded-xl border border-[var(--mat-sys-outline-variant,#e0e0e0)] bg-[var(--mat-sys-surface-container,#f5f5f5)] p-5 text-center">
                    <div class="summary-value">{{ summary.totalActiveRoutes }}</div>
                    <div class="summary-label mt-2">Active Routes</div>
                  </div>
                </div>
              </div>
            }

            <!-- Agencies Grid -->
            @if (agencies$ | async; as agenciesResponse) {
              <div class="agencies-section">
                <h3 class="mb-4 text-lg font-semibold text-[var(--mdc-theme-on-surface)]">Agencies</h3>
                <div class="agencies-grid grid gap-4 md:grid-cols-1 xl:grid-cols-2">
                  @for (agency of agenciesResponse.content; track agency) {
                    <app-agency-card [agency]="agency"></app-agency-card>
                  }
                </div>
                @if (agenciesResponse.content.length === 0) {
                  <p class="no-agencies py-4 text-center italic text-[var(--mat-sys-on-surface-variant)]">
                    No agencies found for this region.
                  </p>
                }
              </div>
            }
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .region-detail-container {
      padding: 1rem;
    }

    .empty-state {
      min-height: 400px;
    }

    .empty-icon {
      color: rgba(0, 0, 0, 0.3);
    }

    :host-context(.dark-theme) .empty-icon {
      color: rgba(255, 255, 255, 0.3);
    }

    :host-context(.dark-theme) .empty-state h3 {
      color: rgba(255, 255, 255, 0.87);
    }

    :host-context(.dark-theme) .empty-state p {
      color: rgba(255, 255, 255, 0.7);
    }

    .summary-value {
      font-size: 32px;
      font-weight: 600;
      color: var(--mat-sys-primary, #1976d2);
      line-height: 1.2;
    }

    .summary-label {
      font-size: 14px;
      font-weight: 500;
      color: var(--mat-sys-on-surface, #333);
    }

    .no-agencies {
      color: var(--mat-sys-on-surface-variant, #6b7280);
    }

    .loading-state p {
      color: rgba(0, 0, 0, 0.6);
    }

    :host-context(.dark-theme) .loading-state p {
      color: rgba(255, 255, 255, 0.7);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegionDetailPanelComponent implements OnChanges, OnDestroy {
  @Input() region: MetropolitanRegion | null = null;

  private readonly destroy$ = new Subject<void>();

  // Feeds tab state
  regionFeeds: Feed[] = [];
  agencyGroups: AgencyFeedGroup[] = [];
  loadingFeeds = false;

  // Overview tab state
  regionDetail$!: Observable<MetropolitanRegionDetail>;
  agencies$!: Observable<AgencyListResponse>;
  summary$!: Observable<RegionSummary | null>;
  loadingOverview = false;

  // Bulk import state
  isImportingAll = false;

  constructor(
    private readonly regionService: RegionService,
    private readonly importService: ImportService,
    private readonly agencyService: AgencyService,
    private readonly snackBar: MatSnackBar,
    private readonly router: Router,
    private readonly metrics: FeedsMetricsService,
    private readonly events: FeedsEventsService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['region'] && this.region) {
      this.loadFeedsForRegion(this.region.regionOnestopId);
      this.loadOverviewForRegion(this.region.regionOnestopId);
      this.metrics.setSelectedRegion(
        this.region.regionOnestopId,
        this.getDisplayName(this.region)
      );
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Load feeds for the selected region (Feeds tab)
   */
  private loadFeedsForRegion(regionId: string): void {
    this.loadingFeeds = true;
    this.regionFeeds = [];
    this.agencyGroups = [];
    this.metrics.setDiscoverFeedCount(0);

    this.regionService.listFeedsForRegion(regionId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (feeds) => {
        this.regionFeeds = feeds;
        this.agencyGroups = FeedGroupingUtils.sortAgencyGroups(
          FeedGroupingUtils.groupFeedsByAgency(feeds)
        );
        this.metrics.setDiscoverFeedCount(feeds.length);
        this.loadingFeeds = false;
      },
      error: (error) => {
        console.error('Failed to load feeds:', error);
        this.loadingFeeds = false;
        const regionName = this.getDisplayName(this.region) || regionId;
        this.snackBar.open(`Failed to load feeds for ${regionName}`, 'Retry', {
          duration: 5000
        }).onAction().subscribe(() => this.loadFeedsForRegion(regionId));
      }
    });
  }

  /**
   * Load overview data for the selected region (Overview tab)
   */
  private loadOverviewForRegion(regionId: string): void {
    this.loadingOverview = true;

    this.regionDetail$ = this.regionService.getRegion(regionId).pipe(
      catchError((error) => {
        console.error('Failed to load region details:', error);
        this.loadingOverview = false;
        this.snackBar.open('Failed to load region details.', 'Retry', {
          duration: 5000
        }).onAction().subscribe(() => this.loadOverviewForRegion(regionId));
        return of(null as unknown as MetropolitanRegionDetail);
      })
    );
    this.agencies$ = this.agencyService.listAgencies(0, 100, regionId).pipe(
      map(response => ({
        ...response,
        content: [...response.content].sort((a, b) => a.name.localeCompare(b.name))
      })),
      catchError((error) => {
        console.error('Failed to load agencies:', error);
        this.loadingOverview = false;
        this.snackBar.open('Failed to load agencies.', 'Retry', {
          duration: 5000
        }).onAction().subscribe(() => this.loadOverviewForRegion(regionId));
        return of({
          content: [],
          totalElements: 0,
          totalPages: 0
        } as AgencyListResponse);
      })
    );

    // Compute summary from region and agencies data
    this.summary$ = combineLatest([this.regionDetail$, this.agencies$]).pipe(
      tap(() => {
        this.loadingOverview = false;
      }),
      map(([region, agenciesResponse]) => {
        if (!region) {
          return null;
        }
        const agencies = agenciesResponse.content;

        // Sum active route counts across all agencies
        const totalActiveRoutes = agencies.reduce((sum, agency) => sum + agency.activeRouteCount, 0);

        return {
          name: region.name,
          totalAgencies: agencies.length,
          totalActiveRoutes
        };
      })
    );
  }

  /**
   * Import a single feed
   */
  importFeed(feed: Feed): void {
    this.snackBar.open(`Starting import for ${feed.name}...`, 'Close', { duration: 2000 });

    this.importService.startImport(feed.feedOnestopId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (result) => {
        this.snackBar.open(`Import started for ${feed.name}`, 'Close', { duration: 3000 });
        this.importService.refreshActiveImports();
        this.router.navigate(['/feeds/imports']);
      },
      error: (error) => {
        console.error('Failed to start import:', error);
        const errorMessage = error.message || error.error?.message || 'Unknown error occurred';
        this.snackBar.open(`❌ Import failed: ${errorMessage}`, 'Retry', {
          duration: 8000,
          panelClass: ['error-snackbar']
        }).onAction().subscribe(() => this.importFeed(feed));
      }
    });
  }

  /**
   * Import multiple feeds
   */
  importMultipleFeeds(feeds: Feed[]): void {
    feeds.forEach(feed => this.importFeed(feed));
  }

  /**
   * Confirm and start bulk import for all feeds in the region
   */
  confirmAndImportAllFeeds(): void {
    if (!this.region) {
      return;
    }

    const feedCount = this.region.feedCount || 0;

    // Show confirmation dialog for large regions (>20 feeds)
    if (feedCount > 20) {
      const confirmed = window.confirm(
        `You are about to import ${feedCount} feeds for ${this.getDisplayName(this.region)}. ` +
        `This may take some time. Continue?`
      );
      if (!confirmed) {
        return;
      }
    }

    this.isImportingAll = true;
    const regionName = this.getDisplayName(this.region) || this.region.regionOnestopId;

    this.snackBar.open(`Starting bulk import for ${regionName}...`, 'Close', { duration: 2000 });

    this.importService.importAllFeedsForRegion(this.region.regionOnestopId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (result) => {
        this.isImportingAll = false;
        this.showBulkImportResults(result, regionName);
        this.importService.refreshActiveImports();
        // Navigate to imports page to monitor progress
        this.router.navigate(['/feeds/imports']);
      },
      error: (error) => {
        console.error('Failed to start bulk import:', error);
        this.isImportingAll = false;
        const errorMessage = error.message || error.error?.message || 'Unknown error occurred';
        this.snackBar.open(`❌ Bulk import failed: ${errorMessage}`, 'Retry', {
          duration: 8000,
          panelClass: ['error-snackbar']
        }).onAction().subscribe(() => this.confirmAndImportAllFeeds());
      }
    });
  }

  /**
   * Display bulk import results in a snackbar
   */
  private showBulkImportResults(result: BulkImportResponse, regionName: string): void {
    const { totalFeeds, startedCount, failedCount, skippedCount } = result;

    // Build summary message
    const messageParts = [];
    if (startedCount > 0) {
      messageParts.push(`✅ ${startedCount} started`);
    }
    if (failedCount > 0) {
      messageParts.push(`❌ ${failedCount} failed`);
    }
    if (skippedCount > 0) {
      messageParts.push(`⏭️ ${skippedCount} skipped`);
    }

    const message = `Bulk import for ${regionName}: ${messageParts.join(', ')}`;

    // Show longer duration for results with failures
    const duration = failedCount > 0 ? 10000 : 5000;

    this.snackBar.open(message, 'View Imports', {
      duration,
      panelClass: failedCount > 0 ? ['warning-snackbar'] : []
    }).onAction().subscribe(() => {
      this.router.navigate(['/feeds/imports']);
    });
  }

  /**
   * View feed details
   */
  viewFeedDetails(feed: Feed): void {
    this.snackBar.open(`Viewing details for ${feed.name}`, 'Close', { duration: 2000 });
  }

  /**
   * Get display name for region
   */
  getDisplayName(region: MetropolitanRegion | null | undefined): string | null {
    if (!region) {
      return null;
    }
    return RegionUtils.getDisplayName(region);
  }
}
