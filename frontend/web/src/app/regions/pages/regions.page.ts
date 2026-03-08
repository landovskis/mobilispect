import { Component, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, BehaviorSubject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MetropolitanRegion } from '../../feeds/models/region.models';
import { RegionService } from '../../feeds/services/region.service';
import { RegionMasterPanelComponent } from '../components/region-master-panel.component';
import { RegionDetailPanelComponent } from '../components/region-detail-panel.component';

/**
 * Regions Page Component
 *
 * Master-detail layout for browsing regions and viewing their feeds.
 * Consolidates the previous discover and list functionality
 * into a unified interface following Material Design patterns.
 *
 * Constitutional Compliance:
 * - Test-Driven Quality: ≥80% coverage with comprehensive unit tests
 * - Accessibility: WCAG 2.1 AA with keyboard navigation and ARIA labels
 * - Performance: OnPush change detection, responsive to route changes
 * - UX Parity: Material Design 3 with light/dark theme support
 * - Responsive: Mobile-first with stacked layout on small screens
 */
@Component({
  selector: 'app-regions-page',
  standalone: true,
  imports: [AsyncPipe, RegionMasterPanelComponent, RegionDetailPanelComponent],
  template: `
    <div
      class="regions-container master-detail-layout"
      [class.has-selection]="selectedRegion$ | async"
    >
      <!-- Master Panel (Left/Top) - Region Grid -->
      <div class="master-panel">
        <app-region-master-panel
          [selectedRegion]="selectedRegion$ | async"
          (regionSelected)="onRegionSelected($event)"
          (regionDetailsRequested)="onRegionDetailsRequested($event)"
        ></app-region-master-panel>
      </div>

      <!-- Detail Panel (Right/Bottom) - Region Details and Feeds -->
      <div class="detail-panel">
        <app-region-detail-panel [region]="selectedRegion$ | async"></app-region-detail-panel>
      </div>
    </div>
  `,
  styles: [
    `
      .regions-container {
        display: grid;
        grid-template-columns: 1fr;
        gap: 1.5rem;
        padding: 1rem;
        max-width: 100%;
        min-height: calc(100vh - 64px); /* Account for header */
      }

      /* Tablet and up: side-by-side layout */
      @media (min-width: 768px) {
        .regions-container {
          grid-template-columns: 40% 60%;
          padding: 1.5rem;
        }
      }

      /* Desktop: optimal split */
      @media (min-width: 1024px) {
        .regions-container {
          grid-template-columns: 35% 65%;
          max-width: 1600px;
          margin: 0 auto;
        }
      }

      /* Mobile: stacked layout with master on top */
      @media (max-width: 767px) {
        .regions-container {
          grid-template-columns: 1fr;
          gap: 1rem;
        }

        /* When a region is selected on mobile, hide master panel for better UX */
        .regions-container.has-selection .master-panel {
          display: none;
        }
      }

      .master-panel,
      .detail-panel {
        overflow-y: auto;
        max-height: calc(100vh - 96px);
      }

      /* Scrollbar styling */
      .master-panel::-webkit-scrollbar,
      .detail-panel::-webkit-scrollbar {
        width: 8px;
      }

      .master-panel::-webkit-scrollbar-track,
      .detail-panel::-webkit-scrollbar-track {
        background: var(--mdc-theme-surface-variant, #f5f5f5);
        border-radius: 4px;
      }

      .master-panel::-webkit-scrollbar-thumb,
      .detail-panel::-webkit-scrollbar-thumb {
        background: var(--mdc-theme-outline, #9e9e9e);
        border-radius: 4px;
      }

      .master-panel::-webkit-scrollbar-thumb:hover,
      .detail-panel::-webkit-scrollbar-thumb:hover {
        background: var(--mdc-theme-primary, #1976d2);
      }

      /* Dark theme support */
      :host-context(.dark-theme) .master-panel::-webkit-scrollbar-track,
      :host-context(.dark-theme) .detail-panel::-webkit-scrollbar-track {
        background: rgba(255, 255, 255, 0.1);
      }

      :host-context(.dark-theme) .master-panel::-webkit-scrollbar-thumb,
      :host-context(.dark-theme) .detail-panel::-webkit-scrollbar-thumb {
        background: rgba(255, 255, 255, 0.3);
      }

      :host-context(.dark-theme) .master-panel::-webkit-scrollbar-thumb:hover,
      :host-context(.dark-theme) .detail-panel::-webkit-scrollbar-thumb:hover {
        background: rgba(255, 255, 255, 0.5);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegionsPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  selectedRegion$ = new BehaviorSubject<MetropolitanRegion | null>(null);

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly regionService: RegionService
  ) {}

  ngOnInit(): void {
    // Subscribe to route parameter changes to update selected region
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const regionId = params.get('regionId');
      if (regionId) {
        this.loadRegion(regionId);
      } else {
        this.selectedRegion$.next(null);
      }
    });

    // Also support legacy query parameter ?region=xyz for backwards compatibility
    this.route.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe((queryParams) => {
      const regionIdFromQuery = queryParams.get('region');
      if (regionIdFromQuery && !this.route.snapshot.paramMap.get('regionId')) {
        // Redirect to new URL format
        this.router.navigate(['/regions', regionIdFromQuery], {
          replaceUrl: true,
        });
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Load region details when route parameter changes
   */
  private loadRegion(regionId: string): void {
    this.regionService
      .getRegion(regionId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (region) => {
          this.selectedRegion$.next(region);
        },
        error: (error) => {
          console.error('Failed to load region:', error);
          this.selectedRegion$.next(null);
          // Could show error snackbar here
        },
      });
  }

  /**
   * Handle region selection from master panel
   */
  onRegionSelected(region: MetropolitanRegion): void {
    this.router.navigate(['/regions', region.regionOnestopId]);
  }

  /**
   * Handle details request (for future enhancement - could open dialog or navigate)
   */
  onRegionDetailsRequested(region: MetropolitanRegion): void {
    // For now, just select the region
    this.onRegionSelected(region);
  }
}
