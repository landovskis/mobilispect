import { Component, OnDestroy, OnInit } from '@angular/core';

import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MetropolitanRegion, Feed, RegionUtils } from '../models';
import { AgencyFeedGroup, FeedGroupingUtils } from '../models/agency-feed-group.model';
import { RegionService } from '../services/region.service';
import { ImportService } from '../services/import.service';
import { FeedsMetricsService } from '../services/feeds-metrics.service';
import { FeedsEventsService } from '../services/feeds-events.service';
import { RegionSelectorComponent } from '../../regions/components/region-selector.component';
import { AgencyFeedCardComponent } from '../components/agency-feed-card.component';
import { BrandSectionComponent } from '../../shared/components/brand-section.component';

@Component({
  selector: 'app-discover-feeds-page',
  standalone: true,
  imports: [
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatIconModule,
    BrandSectionComponent,
    RegionSelectorComponent,
    AgencyFeedCardComponent
],
  template: `
    <app-brand-section
      title="Discover Feeds"
      subtitle="Choose a metropolitan region to explore its agencies and feeds"
      icon="travel_explore">
      <app-region-selector
        [regions]="regions"
        [selectedRegionId]="selectedRegionId"
        (regionChange)="onRegionChange($event)"
      ></app-region-selector>

      @if (loadingFeeds) {
        <div class="loading-state" role="status" aria-live="polite">
          <mat-spinner diameter="40"></mat-spinner>
          <p>Loading feeds...</p>
        </div>
      } @else {
        @if (agencyGroups.length > 0) {
          <div class="feeds-grid">
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
          <div class="empty-state">
            <mat-icon class="empty-icon">inbox</mat-icon>
            <h3>No feeds found</h3>
            <p>
              @if (selectedRegionId) {
                No feeds are available for the selected region yet.
              } @else {
                Select a region to view available transit feeds.
              }
            </p>
          </div>
        }
      }
    </app-brand-section>
  `,
  styles: [`
    .feeds-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 24px;
      margin-top: 24px;
    }

    @media (max-width: 768px) {
      .feeds-grid {
        grid-template-columns: 1fr;
        gap: 16px;
      }
    }

    @media (min-width: 769px) and (max-width: 1024px) {
      .feeds-grid {
        grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      }
    }

    @media (min-width: 1025px) {
      .feeds-grid {
        grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      }
    }

    .loading-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 48px 24px;
      gap: 16px;
    }

    .loading-state p {
      color: rgba(0, 0, 0, 0.6);
      font-size: 0.875rem;
    }

    :host-context(.dark-theme) .loading-state p {
      color: rgba(255, 255, 255, 0.7);
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 64px 24px;
      text-align: center;
    }

    .empty-state .empty-icon {
      font-size: 64px;
      width: 64px;
      height: 64px;
      color: rgba(0, 0, 0, 0.3);
      margin-bottom: 16px;
    }

    :host-context(.dark-theme) .empty-state .empty-icon {
      color: rgba(255, 255, 255, 0.3);
    }

    .empty-state h3 {
      margin: 0 0 8px 0;
      color: rgba(0, 0, 0, 0.7);
      font-size: 1.25rem;
      font-weight: 600;
    }

    :host-context(.dark-theme) .empty-state h3 {
      color: rgba(255, 255, 255, 0.87);
    }

    .empty-state p {
      margin: 0;
      color: rgba(0, 0, 0, 0.6);
      font-size: 0.875rem;
      max-width: 400px;
    }

    :host-context(.dark-theme) .empty-state p {
      color: rgba(255, 255, 255, 0.7);
    }
  `]
})
export class DiscoverFeedsPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  regions: MetropolitanRegion[] = [];
  selectedRegionId: string | null = null;
  selectedRegion: MetropolitanRegion | null = null;
  regionFeeds: Feed[] = [];
  agencyGroups: AgencyFeedGroup[] = [];
  loadingFeeds = false;

  constructor(
    private readonly regionService: RegionService,
    private readonly importService: ImportService,
    private readonly snackBar: MatSnackBar,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly metrics: FeedsMetricsService,
    private readonly events: FeedsEventsService
  ) {}

  ngOnInit(): void {
    this.loadRegions();
    this.subscribeToRefresh();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onRegionChange(regionId: string): void {
    if (!regionId) {
      this.clearSelection();
      return;
    }

    this.selectedRegionId = regionId;
    this.selectedRegion = this.regions.find(r => r.regionOnestopId === regionId) ?? null;
    this.metrics.setSelectedRegion(this.selectedRegionId, this.getRegionDisplayName(this.selectedRegion));
    this.updateUrlWithRegion(regionId);
    this.loadFeedsForRegion(regionId);
    this.router.navigate(['/feeds/discover', regionId]);
  }

  importMultipleFeeds(feeds: Feed[]): void {
    feeds.forEach(feed => this.importFeed(feed));
  }

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

  viewFeedDetails(feed: Feed): void {
    this.snackBar.open(`Viewing details for ${feed.name}`, 'Close', { duration: 2000 });
  }

  private loadRegions(): void {
    this.regionService.listRegions().pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (regions) => {
        this.regions = [...regions].sort((a, b) => a.name.localeCompare(b.name));
        this.metrics.setSelectedRegion(this.selectedRegionId, this.getRegionDisplayName(this.selectedRegion));
        this.bootstrapRegionFromQuery();
      },
      error: (error) => {
        console.error('Failed to load regions:', error);
        this.snackBar.open('Failed to load regions', 'Close', { duration: 3000 });
      }
    });
  }

  private bootstrapRegionFromQuery(): void {
    const initialRegion = this.route.snapshot.queryParamMap.get('region');
    if (initialRegion) {
      this.selectedRegionId = initialRegion;
      this.selectedRegion = this.regions.find(r => r.regionOnestopId === initialRegion) ?? null;
      this.metrics.setSelectedRegion(this.selectedRegionId, this.getRegionDisplayName(this.selectedRegion));
      this.loadFeedsForRegion(initialRegion);
    } else {
      this.clearSelection();
    }

    this.route.queryParamMap
      .pipe(takeUntil(this.destroy$))
      .subscribe(params => {
        const regionId = params.get('region');
        if (regionId && regionId !== this.selectedRegionId) {
          this.onRegionChange(regionId);
        } else if (!regionId && this.selectedRegionId) {
          this.clearSelection();
        }
      });
  }

  private loadFeedsForRegion(onestopId: string): void {
    this.loadingFeeds = true;
    this.regionFeeds = [];
    this.agencyGroups = [];
    this.metrics.setDiscoverFeedCount(0);

    this.regionService.getCachedRegions().pipe(
      takeUntil(this.destroy$)
    ).subscribe((regions) => {
      const region = regions?.find(r => r.regionOnestopId === onestopId);
      if (region) {
        this.selectedRegion = region;
        this.metrics.setSelectedRegion(region.regionOnestopId, this.getRegionDisplayName(region));
      }
    });

    this.regionService.listFeedsForRegion(onestopId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (feeds) => {
        this.regionFeeds = feeds;
        this.agencyGroups = FeedGroupingUtils.sortAgencyGroups(
          FeedGroupingUtils.groupFeedsByAgency(feeds)
        );
        this.metrics.setDiscoverFeedCount(feeds.length);
        this.loadingFeeds = false;
        const regionName = this.getRegionDisplayName(this.selectedRegion) || onestopId;
        this.snackBar.open(
          `Viewing ${feeds.length} feeds from ${this.agencyGroups.length} agencies for ${regionName}`,
          'Close',
          { duration: 2000 }
        );
      },
      error: (error) => {
        console.error('Failed to load feeds:', error);
        this.loadingFeeds = false;
        const regionName = this.getRegionDisplayName(this.selectedRegion) || onestopId;
        this.snackBar.open(`Failed to load feeds for ${regionName}`, 'Retry', {
          duration: 5000
        }).onAction().subscribe(() => this.loadFeedsForRegion(onestopId));
      }
    });
  }

  private updateUrlWithRegion(regionId: string): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { region: regionId },
      queryParamsHandling: 'merge'
    });
  }

  private clearSelection(): void {
    this.selectedRegionId = null;
    this.selectedRegion = null;
    this.regionFeeds = [];
    this.agencyGroups = [];
    this.metrics.resetSelectedRegion();
    this.metrics.setDiscoverFeedCount(0);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { region: null },
      queryParamsHandling: 'merge'
    });
  }

  private subscribeToRefresh(): void {
    this.events.refresh$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.loadRegions();
        if (this.selectedRegionId) {
          this.loadFeedsForRegion(this.selectedRegionId);
        }
      });
  }

  private getRegionDisplayName(region: MetropolitanRegion | null | undefined): string | null {
    if (!region) {
      return null;
    }
    return RegionUtils.getDisplayName(region);
  }
}
