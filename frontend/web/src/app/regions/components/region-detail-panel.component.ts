import { Component, Input, OnChanges, SimpleChanges, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subject, Observable, combineLatest } from 'rxjs';
import { map, takeUntil } from 'rxjs/operators';
import { MetropolitanRegion, MetropolitanRegionDetail, Feed, RegionUtils } from '../../feeds/models/region.models';
import { AgencyFeedGroup, FeedGroupingUtils } from '../../feeds/models/agency-feed-group.model';
import { RegionService } from '../../feeds/services/region.service';
import { ImportService } from '../../feeds/services/import.service';
import { AgencyService } from '../../agencies/services/agency.service';
import { AgencyListResponse } from '../../transit-frequency/services/agency.service';
import { FeedsMetricsService } from '../../feeds/services/feeds-metrics.service';
import { FeedsEventsService } from '../../feeds/services/feeds-events.service';
import { AgencyFeedCardComponent } from '../../feeds/components/agency-feed-card.component';
import { AgencyCardComponent } from '../../transit-frequency/components/agency-card/agency-card.component';
import { BrandSectionComponent } from '../../shared/components/brand-section.component';

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
    MatTabsModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    BrandSectionComponent,
    AgencyFeedCardComponent,
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
          <h2 class="text-2xl font-semibold text-[var(--mdc-theme-on-surface)]">
            {{ getDisplayName(region) }}
          </h2>
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

        <!-- Tabbed Interface -->
        <mat-tab-group>
          <!-- Feeds Tab -->
          <mat-tab>
            <ng-template mat-tab-label>
              <mat-icon class="mr-2">feed</mat-icon>
              Feeds
            </ng-template>

            <div class="tab-content pt-4">
              @if (loadingFeeds) {
                <div class="loading-state flex flex-col items-center justify-center gap-4 px-6 py-12" role="status" aria-live="polite">
                  <mat-spinner diameter="40"></mat-spinner>
                  <p class="text-sm text-[var(--mdc-theme-on-surface-variant)]">Loading feeds...</p>
                </div>
              } @else {
                @if (agencyGroups.length > 0) {
                  <div class="feeds-grid grid gap-6 md:grid-cols-1 xl:grid-cols-2">
                    @for (group of agencyGroups; track group.agencyName) {
                      <app-agency-feed-card
                        [agencyGroup]="group"
                        (importFeed)="importFeed($event)"
                        (importAllFeeds)="importMultipleFeeds($event)"
                        (viewDetails)="viewFeedDetails($event)">
                      </app-agency-feed-card>
                    }
                  </div>
                } @else {
                  <div class="empty-state flex flex-col items-center justify-center px-6 py-16 text-center">
                    <mat-icon class="empty-icon mb-4 text-[64px] text-[rgba(0,0,0,0.3)]">inbox</mat-icon>
                    <h3 class="mb-2 text-xl font-semibold text-[rgba(0,0,0,0.7)]">No Feeds Available</h3>
                    <p class="max-w-[400px] text-sm text-[rgba(0,0,0,0.6)]">
                      No feeds are available for this region yet.
                    </p>
                  </div>
                }
              }
            </div>
          </mat-tab>

          <!-- Overview Tab -->
          <mat-tab>
            <ng-template mat-tab-label>
              <mat-icon class="mr-2">analytics</mat-icon>
              Overview
            </ng-template>

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
          </mat-tab>
        </mat-tab-group>
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
  summary$!: Observable<RegionSummary>;
  loadingOverview = false;

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

    this.regionDetail$ = this.regionService.getRegion(regionId);
    this.agencies$ = this.agencyService.listAgencies(0, 100, regionId).pipe(
      map(response => ({
        ...response,
        content: [...response.content].sort((a, b) => a.name.localeCompare(b.name))
      }))
    );

    // Compute summary from region and agencies data
    this.summary$ = combineLatest([this.regionDetail$, this.agencies$]).pipe(
      map(([region, agenciesResponse]) => {
        const agencies = agenciesResponse.content;

        // Sum active route counts across all agencies
        const totalActiveRoutes = agencies.reduce((sum, agency) => sum + agency.activeRouteCount, 0);

        this.loadingOverview = false;

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
