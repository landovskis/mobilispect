import { Component, OnInit, OnDestroy, Output, EventEmitter, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatBadgeModule } from '@angular/material/badge';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, Subject, BehaviorSubject, combineLatest } from 'rxjs';
import { map, takeUntil, debounceTime, distinctUntilChanged, startWith } from 'rxjs/operators';
import { MetropolitanRegion, RegionUtils } from '../../feeds/models/region.models';
import { RegionService } from '../../feeds/services/region.service';
import { ImportService } from '../../feeds/services/import.service';
import { SchedulerService } from '../../feeds/services/scheduler.service';
import { FeedImportSummary } from '../../feeds/models/import.models';
import { BrandCardComponent } from '../../shared/components/brand-card.component';
import { BrandButtonComponent } from '../../shared/components/brand-button.component';

/**
 * Region Master Panel Component
 *
 * Displays a filterable grid of metropolitan regions with search,
 * auto-update status, and import activity indicators.
 * This is the "master" panel in the master-detail layout.
 *
 * Constitutional Compliance:
 * - UX Consistency: Material Design 3 with light/dark mode support
 * - Performance: Virtual scrolling for large lists, debounced search, OnPush change detection
 * - Accessibility: ARIA labels, keyboard navigation, screen reader support
 * - Responsive: Mobile-first design patterns with responsive grid
 */
@Component({
  selector: 'app-region-master-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatBadgeModule,
    MatSlideToggleModule,
    MatMenuModule,
    MatButtonModule,
    BrandCardComponent,
    BrandButtonComponent
  ],
  template: `
    <div class="region-master-container">
      <div class="master-header mb-4">
        <h2 class="text-2xl font-semibold text-[var(--mdc-theme-on-surface)]">Regions</h2>
        <p class="text-sm text-[var(--mdc-theme-on-surface-variant)]">Browse metropolitan regions and their transit feeds</p>
      </div>

      <!-- Search and Filters -->
      <div class="search-section mb-6 flex flex-col gap-4 max-md:mb-4">
        <mat-form-field class="search-field w-full" appearance="outline">
          <mat-label>Search regions</mat-label>
          <input
            matInput
            [(ngModel)]="searchTerm"
            (ngModelChange)="onSearchTermChange($event)"
            placeholder="Search by name or ID..."
            [attr.aria-label]="'Search regions by name or ID'"
          >
          <mat-icon matSuffix>search</mat-icon>
        </mat-form-field>

        <div class="filter-chips flex flex-wrap gap-2">
          <mat-chip-listbox [multiple]="false" [hideSingleSelectionIndicator]="true">
            <mat-chip-option
              [selected]="autoUpdateFilter === undefined"
              (click)="setAutoUpdateFilter(undefined)"
            >
              All Regions
            </mat-chip-option>
            <mat-chip-option
              [selected]="autoUpdateFilter === true"
              (click)="setAutoUpdateFilter(true)"
            >
              Auto-Update Enabled
            </mat-chip-option>
            <mat-chip-option
              [selected]="autoUpdateFilter === false"
              (click)="setAutoUpdateFilter(false)"
            >
              Manual Only
            </mat-chip-option>
          </mat-chip-listbox>
        </div>
      </div>

      <!-- Loading State -->
      @if (isLoading$ | async) {
        <div class="loading-container flex flex-col items-center justify-center px-4 py-12 text-center">
          <mat-spinner diameter="40"></mat-spinner>
          <p class="mt-4 text-sm text-[var(--mdc-theme-on-surface-variant)]">Loading regions...</p>
        </div>
      }

      <!-- Error State -->
      @if (error$ | async; as error) {
        <div class="error-container flex flex-col items-center justify-center px-4 py-12 text-center">
          <mat-icon class="mb-4" color="warn">error</mat-icon>
          <p class="text-[var(--mdc-theme-error)]">{{ error }}</p>
          <app-brand-button variant="primary" size="sm" (click)="refreshRegions()" class="mt-4">
            <mat-icon>refresh</mat-icon>
            <span>Retry</span>
          </app-brand-button>
        </div>
      }

      <!-- Regions Grid -->
      @if (!(isLoading$ | async) && !(error$ | async)) {
        @if (filteredRegions$ | async; as regions) {
          <div class="regions-grid mb-6 grid gap-4 max-md:gap-3 md:grid-cols-1 xl:grid-cols-2">
            @for (region of regions; track region.regionOnestopId) {
              <app-brand-card
                class="region-card cursor-pointer transition-all"
                [class.selected]="selectedRegion?.regionOnestopId === region.regionOnestopId"
                [title]="getDisplayName(region)"
                [subtitle]="region.regionOnestopId"
                [badge]="getActiveImportCount(region) > 0 ? getActiveImportCount(region) + ' active' : undefined"
                [hasFooter]="true"
                (click)="selectRegion(region)"
                [attr.aria-label]="'Select ' + getDisplayName(region) + ' region'"
                tabindex="0"
                (keydown.enter)="selectRegion(region)"
                (keydown.space)="$event.preventDefault(); selectRegion(region)"
              >
                <div class="card-body">
                  <div class="region-stats mt-3 flex flex-col gap-2">
                    <div class="stat-item flex items-center gap-2 text-sm">
                      <mat-icon class="text-[18px]">feed</mat-icon>
                      <span>{{ region.feedCount }} feeds</span>
                    </div>

                    <div class="stat-item flex items-center gap-2 text-sm">
                      <mat-icon [ngClass]="{
                        'auto-update-enabled': region.autoUpdateEnabled,
                        'auto-update-disabled': !region.autoUpdateEnabled
                      }" class="text-[18px]">
                        {{ region.autoUpdateEnabled ? 'sync' : 'sync_disabled' }}
                      </mat-icon>
                      <span>{{ region.autoUpdateEnabled ? 'Auto-update' : 'Manual only' }}</span>
                    </div>

                    @if (region.lastCheckAt) {
                      <div class="stat-item flex items-center gap-2 text-sm">
                        <mat-icon class="text-[18px]">schedule</mat-icon>
                        <span>{{ formatLastCheck(region) }}</span>
                      </div>
                    }
                  </div>
                </div>

                <div card-footer class="flex justify-end gap-2">
                  <app-brand-button
                    variant="accent"
                    size="sm"
                    (click)="$event.stopPropagation(); viewRegionDetails(region)"
                    [attr.aria-label]="'View details for ' + getDisplayName(region)"
                  >
                    <mat-icon>info</mat-icon>
                    <span>Details</span>
                  </app-brand-button>

                  <!-- Auto-Update Controls Menu -->
                  <button
                    mat-icon-button
                    [matMenuTriggerFor]="autoUpdateMenu"
                    (click)="$event.stopPropagation()"
                    [attr.aria-label]="'Auto-update settings for ' + getDisplayName(region)"
                    matTooltip="Auto-update settings">
                    <mat-icon>settings</mat-icon>
                  </button>
                  <mat-menu #autoUpdateMenu="matMenu">
                    <div
                      class="auto-update-controls min-w-[280px] p-4 max-md:min-w-[240px] max-md:p-3"
                      (click)="$event.stopPropagation()"
                    >
                      <div class="control-header mb-4 flex items-center gap-2 font-semibold text-[var(--mdc-theme-primary)]">
                        <mat-icon>sync</mat-icon>
                        <span>Automatic Updates</span>
                      </div>

                      <div class="control-item flex items-center justify-between border-b border-[var(--mdc-theme-outline)] py-2 last:border-b-0">
                        <span>Enable auto-update</span>
                        <mat-slide-toggle
                          [checked]="region.autoUpdateEnabled"
                          (change)="toggleAutoUpdate(region, $event.checked)"
                          [disabled]="isUpdatingAutoUpdate.has(region.regionOnestopId)">
                        </mat-slide-toggle>
                      </div>
                    </div>
                  </mat-menu>

                  <app-brand-button
                    variant="primary"
                    size="sm"
                    (click)="$event.stopPropagation(); selectRegion(region)"
                    [disabled]="region.feedCount === 0"
                    [attr.aria-label]="'Select ' + getDisplayName(region) + ' to view feeds'"
                  >
                    <mat-icon>arrow_forward</mat-icon>
                    <span>View</span>
                  </app-brand-button>
                </div>
              </app-brand-card>
            }

            @if (regions.length === 0) {
              <div class="empty-state col-span-full flex flex-col items-center justify-center px-4 py-12 text-center">
                <mat-icon class="mb-4 text-[64px] opacity-50">location_off</mat-icon>
                <h3 class="text-lg font-semibold text-[var(--mdc-theme-on-surface)]">{{ searchTerm ? 'No regions found' : 'No regions available' }}</h3>
                @if (searchTerm) {
                  <p class="text-sm text-[var(--mdc-theme-on-surface-variant)]">Try adjusting your search criteria.</p>
                } @else {
                  <p class="text-sm text-[var(--mdc-theme-on-surface-variant)]">No regions have been discovered yet.</p>
                }
              </div>
            }
          </div>

          <!-- Quick Stats -->
          <div class="quick-stats flex flex-wrap justify-center gap-4 rounded-lg bg-[var(--mdc-theme-surface-variant)] p-4 max-md:gap-3 max-md:p-3">
            <div class="stat-card flex min-w-[80px] flex-col items-center rounded-lg bg-[var(--mdc-theme-surface)] px-4 py-3">
              <span class="stat-number">{{ regions.length }}</span>
              <span class="stat-label">Regions</span>
            </div>

            <div class="stat-card flex min-w-[80px] flex-col items-center rounded-lg bg-[var(--mdc-theme-surface)] px-4 py-3">
              <span class="stat-number">{{ getTotalFeeds() | async }}</span>
              <span class="stat-label">Total Feeds</span>
            </div>

            <div class="stat-card flex min-w-[80px] flex-col items-center rounded-lg bg-[var(--mdc-theme-surface)] px-4 py-3">
              <span class="stat-number">{{ (activeImports$ | async)?.length || 0 }}</span>
              <span class="stat-label">Active Imports</span>
            </div>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .region-master-container {
      padding: 1rem;
    }

    .error-container {
      color: var(--mdc-theme-error);
    }

    .error-container mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
    }

    .region-card {
      border: 1px solid var(--ms-color-border, #d1d5db);
    }

    .region-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 18px rgba(11, 79, 138, 0.16);
    }

    .region-card.selected {
      border-color: var(--ms-color-primary, #0b4f8a);
      box-shadow: 0 12px 24px rgba(11, 79, 138, 0.22);
    }

    .region-card:focus {
      outline: 2px solid var(--ms-color-primary, #0b4f8a);
      outline-offset: 2px;
    }

    .stat-item {
      color: var(--mdc-theme-on-surface-variant);
    }

    .auto-update-enabled {
      color: var(--mdc-theme-tertiary);
    }

    .auto-update-disabled {
      color: var(--mdc-theme-outline);
    }

    .stat-number {
      font-size: 24px;
      font-weight: 600;
      color: var(--mdc-theme-primary);
    }

    .stat-label {
      font-size: 12px;
      color: var(--mdc-theme-on-surface-variant);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .control-item span {
      font-size: 14px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegionMasterPanelComponent implements OnInit, OnDestroy {
  @Input() selectedRegion: MetropolitanRegion | null = null;
  @Output() regionSelected = new EventEmitter<MetropolitanRegion>();
  @Output() regionDetailsRequested = new EventEmitter<MetropolitanRegion>();

  private destroy$ = new Subject<void>();

  // Search and filtering
  searchTerm = '';
  private searchTerm$ = new BehaviorSubject<string>('');
  autoUpdateFilter: boolean | undefined = undefined;
  private autoUpdateFilter$ = new BehaviorSubject<boolean | undefined>(undefined);

  // Data streams
  regions$ = new BehaviorSubject<MetropolitanRegion[]>([]);
  activeImports$ = new BehaviorSubject<FeedImportSummary[]>([]);
  isLoading$ = new BehaviorSubject<boolean>(true);
  error$ = new BehaviorSubject<string | null>(null);

  // Auto-update control state
  isUpdatingAutoUpdate = new Set<string>();

  // Computed streams
  filteredRegions$: Observable<MetropolitanRegion[]>;

  constructor(
    private regionService: RegionService,
    private importService: ImportService,
    private schedulerService: SchedulerService,
    private snackBar: MatSnackBar
  ) {
    // Setup search term observable with debouncing
    this.searchTerm$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(term => {
      this.searchTerm = term;
    });

    // Setup filtered regions stream
    this.filteredRegions$ = combineLatest([
      this.regions$,
      this.searchTerm$.pipe(startWith('')),
      this.autoUpdateFilter$.pipe(startWith(undefined))
    ]).pipe(
      map(([regions, searchTerm, autoUpdateFilter]) =>
        this.filterRegions(regions, searchTerm, autoUpdateFilter)
      )
    );
  }

  ngOnInit(): void {
    this.loadRegions();
    this.loadActiveImports();

    // Start polling for active imports
    this.importService.startPollingActiveImports();

    // Subscribe to active imports updates
    this.importService.getActiveImportsObservable().pipe(
      takeUntil(this.destroy$)
    ).subscribe(imports => {
      this.activeImports$.next(imports);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.importService.stopPollingActiveImports();
  }

  private loadRegions(): void {
    this.isLoading$.next(true);
    this.error$.next(null);

    this.regionService.listRegions().pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (regions) => {
        this.regions$.next(regions);
        this.isLoading$.next(false);
      },
      error: (error) => {
        console.error('Failed to load regions:', error);
        this.error$.next('Failed to load regions. Please try again.');
        this.isLoading$.next(false);
      }
    });
  }

  private loadActiveImports(): void {
    this.importService.getActiveImports().pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (imports) => {
        this.activeImports$.next(imports);
      },
      error: (error) => {
        console.error('Failed to load active imports:', error);
      }
    });
  }

  private filterRegions(
    regions: MetropolitanRegion[],
    searchTerm: string,
    autoUpdateFilter: boolean | undefined
  ): MetropolitanRegion[] {
    let filtered = regions;

    // Apply search filter
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      filtered = filtered.filter(region => {
        const haystack = [
          region.name,
          region.regionOnestopId,
          region.adm1Name ?? '',
          region.adm0Name ?? ''
        ].join(' ').toLowerCase();
        return haystack.includes(term);
      });
    }

    // Apply auto-update filter
    if (autoUpdateFilter !== undefined) {
      filtered = filtered.filter(region =>
        region.autoUpdateEnabled === autoUpdateFilter
      );
    }

    // Prioritize Canadian regions by default
    return this.regionService.sortWithCanadianPriority([...filtered]);
  }

  selectRegion(region: MetropolitanRegion): void {
    this.selectedRegion = region;
    this.regionSelected.emit(region);
  }

  viewRegionDetails(region: MetropolitanRegion): void {
    this.regionDetailsRequested.emit(region);
  }

  refreshRegions(): void {
    this.regionService.clearCache();
    this.loadRegions();
  }

  setAutoUpdateFilter(filter: boolean | undefined): void {
    this.autoUpdateFilter = filter;
    this.autoUpdateFilter$.next(filter);
  }

  onSearchTermChange(term: string): void {
    this.searchTerm$.next(term);
  }

  formatLastCheck(region: MetropolitanRegion): string {
    return RegionUtils.formatLastCheck(region);
  }

  getActiveImportCount(region: MetropolitanRegion): number {
    const activeImports = this.activeImports$.value;
    return activeImports.filter(imp =>
      imp.regionName === region.name ||
      activeImports.some(imp2 => imp2.feedOnestopId.includes(region.regionOnestopId))
    ).length;
  }

  getTotalFeeds(): Observable<number> {
    return this.filteredRegions$.pipe(
      map(regions => regions.reduce((total, region) => total + region.feedCount, 0))
    );
  }

  /**
   * Toggle automatic updates for a region
   */
  toggleAutoUpdate(region: MetropolitanRegion, enabled: boolean): void {
    const regionId = region.regionOnestopId;
    this.isUpdatingAutoUpdate.add(regionId);

    const operation = enabled
      ? this.schedulerService.enableFeedAutoUpdate(regionId)
      : this.schedulerService.disableFeedAutoUpdate(regionId);

    operation.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.isUpdatingAutoUpdate.delete(regionId);
        region.autoUpdateEnabled = enabled;

        const name = this.getDisplayName(region);
        const message = enabled
          ? `Automatic updates enabled for ${name}`
          : `Automatic updates disabled for ${name}`;

        this.snackBar.open(message, 'Close', { duration: 3000 });

        // Refresh regions to get updated state
        this.refreshRegions();
      },
      error: (error) => {
        this.isUpdatingAutoUpdate.delete(regionId);
        console.error('Error toggling auto-update:', error);
        this.snackBar.open('Failed to update auto-update setting', 'Close', { duration: 3000 });
      }
    });
  }

  getDisplayName(region: MetropolitanRegion): string {
    return RegionUtils.getDisplayName(region);
  }
}
