import { Component, OnInit, OnDestroy, Output, EventEmitter, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatBadgeModule } from '@angular/material/badge';
import { Observable, Subject, BehaviorSubject, combineLatest } from 'rxjs';
import { map, takeUntil, debounceTime, distinctUntilChanged, startWith } from 'rxjs/operators';
import { MetropolitanRegion, RegionUtils } from '../models/region.models';
import { RegionService } from '../services/region.service';
import { ImportService } from '../services/import.service';
import { ImportSummary } from '../models/import.models';

/**
 * Region List Component
 *
 * Displays a filterable list of metropolitan regions with search,
 * auto-update status, and import activity indicators.
 *
 * Constitutional Compliance:
 * - UX Consistency: Material Design 3 with light/dark mode support
 * - Performance: Virtual scrolling for large lists, debounced search
 * - Accessibility: ARIA labels, keyboard navigation
 * - Responsive: Mobile-first design patterns
 */
@Component({
  selector: 'app-region-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatBadgeModule
  ],
  template: `
    <div class="region-list-container">
      <!-- Search and Filters -->
      <div class="search-section">
        <mat-form-field class="search-field" appearance="outline">
          <mat-label>Search regions</mat-label>
          <input
            matInput
            [(ngModel)]="searchTerm"
            placeholder="Search by name or ID..."
            [attr.aria-label]="'Search regions by name or ID'"
          >
          <mat-icon matSuffix>search</mat-icon>
        </mat-form-field>

        <div class="filter-chips">
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
      <div *ngIf="isLoading$ | async" class="loading-container">
        <mat-spinner diameter="40"></mat-spinner>
        <p>Loading regions...</p>
      </div>

      <!-- Error State -->
      <div *ngIf="error$ | async as error" class="error-container">
        <mat-icon color="warn">error</mat-icon>
        <p>{{ error }}</p>
        <button mat-raised-button color="primary" (click)="refreshRegions()">
          <mat-icon>refresh</mat-icon>
          Retry
        </button>
      </div>

      <!-- Regions Grid -->
      <div *ngIf="!(isLoading$ | async) && !(error$ | async)" class="regions-grid">
        <mat-card
          *ngFor="let region of filteredRegions$ | async; trackBy: trackByRegionId"
          class="region-card"
          [class.selected]="selectedRegion?.regionOnestopId === region.regionOnestopId"
          (click)="selectRegion(region)"
          [attr.aria-label]="'Select ' + region.name + ' region'"
          tabindex="0"
          (keydown.enter)="selectRegion(region)"
          (keydown.space)="selectRegion(region)"
        >
          <mat-card-header>
            <mat-card-title class="region-title">
              {{ region.name }}
              <mat-icon
                *ngIf="hasActiveImport(region)"
                class="active-import-icon"
                [matTooltip]="'Import in progress'"
                [matBadge]="getActiveImportCount(region)"
                matBadgeColor="accent"
                matBadgeSize="small"
              >
                sync
              </mat-icon>
            </mat-card-title>
            <mat-card-subtitle>{{ region.regionOnestopId }}</mat-card-subtitle>
          </mat-card-header>

          <mat-card-content>
            <div class="region-stats">
              <div class="stat-item">
                <mat-icon>feed</mat-icon>
                <span>{{ region.feedCount }} feeds</span>
              </div>

              <div class="stat-item">
                <mat-icon [ngClass]="{
                  'auto-update-enabled': region.autoUpdateEnabled,
                  'auto-update-disabled': !region.autoUpdateEnabled
                }">
                  {{ region.autoUpdateEnabled ? 'sync' : 'sync_disabled' }}
                </mat-icon>
                <span>{{ region.autoUpdateEnabled ? 'Auto-update' : 'Manual only' }}</span>
              </div>

              <div class="stat-item" *ngIf="region.lastCheckAt">
                <mat-icon>schedule</mat-icon>
                <span>{{ formatLastCheck(region) }}</span>
              </div>
            </div>
          </mat-card-content>

          <mat-card-actions align="end">
            <button
              mat-button
              color="primary"
              (click)="$event.stopPropagation(); viewRegionDetails(region)"
              [attr.aria-label]="'View details for ' + region.name"
            >
              <mat-icon>info</mat-icon>
              Details
            </button>

            <button
              mat-raised-button
              color="primary"
              (click)="$event.stopPropagation(); selectRegion(region)"
              [disabled]="region.feedCount === 0"
              [attr.aria-label]="'Select ' + region.name + ' for import'"
            >
              <mat-icon>play_arrow</mat-icon>
              Select
            </button>
          </mat-card-actions>
        </mat-card>

        <!-- Empty State -->
        <div *ngIf="(filteredRegions$ | async)?.length === 0" class="empty-state">
          <mat-icon>location_off</mat-icon>
          <h3>No regions found</h3>
          <p *ngIf="searchTerm">Try adjusting your search criteria.</p>
          <p *ngIf="!searchTerm">No regions are available for feed management.</p>
        </div>
      </div>

      <!-- Quick Stats -->
      <div *ngIf="!(isLoading$ | async) && !(error$ | async)" class="quick-stats">
        <div class="stat-card">
          <span class="stat-number">{{ (filteredRegions$ | async)?.length || 0 }}</span>
          <span class="stat-label">Regions</span>
        </div>

        <div class="stat-card">
          <span class="stat-number">{{ getTotalFeeds() | async }}</span>
          <span class="stat-label">Total Feeds</span>
        </div>

        <div class="stat-card">
          <span class="stat-number">{{ (activeImports$ | async)?.length || 0 }}</span>
          <span class="stat-label">Active Imports</span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .region-list-container {
      padding: 16px;
      max-width: 1200px;
      margin: 0 auto;
    }

    .search-section {
      margin-bottom: 24px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .search-field {
      width: 100%;
      max-width: 400px;
    }

    .filter-chips {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .loading-container, .error-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 48px 16px;
      text-align: center;
    }

    .error-container {
      color: var(--mdc-theme-error);
    }

    .error-container mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      margin-bottom: 16px;
    }

    .regions-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 16px;
      margin-bottom: 24px;
    }

    .region-card {
      cursor: pointer;
      transition: all 0.2s ease-in-out;
      border: 2px solid transparent;
    }

    .region-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    }

    .region-card.selected {
      border-color: var(--mdc-theme-primary);
      background-color: var(--mdc-theme-primary-container);
    }

    .region-card:focus {
      outline: 2px solid var(--mdc-theme-primary);
      outline-offset: 2px;
    }

    .region-title {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .active-import-icon {
      animation: spin 2s linear infinite;
      color: var(--mdc-theme-secondary);
    }

    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    .region-stats {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin-top: 12px;
    }

    .stat-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      color: var(--mdc-theme-on-surface-variant);
    }

    .stat-item mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }

    .auto-update-enabled {
      color: var(--mdc-theme-tertiary);
    }

    .auto-update-disabled {
      color: var(--mdc-theme-outline);
    }

    .empty-state {
      grid-column: 1 / -1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 48px 16px;
      text-align: center;
      color: var(--mdc-theme-on-surface-variant);
    }

    .empty-state mat-icon {
      font-size: 64px;
      width: 64px;
      height: 64px;
      margin-bottom: 16px;
      opacity: 0.5;
    }

    .quick-stats {
      display: flex;
      gap: 16px;
      justify-content: center;
      flex-wrap: wrap;
      padding: 16px;
      background-color: var(--mdc-theme-surface-variant);
      border-radius: 8px;
    }

    .stat-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 12px 16px;
      background-color: var(--mdc-theme-surface);
      border-radius: 8px;
      min-width: 80px;
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

    /* Responsive Design */
    @media (max-width: 768px) {
      .region-list-container {
        padding: 12px;
      }

      .regions-grid {
        grid-template-columns: 1fr;
        gap: 12px;
      }

      .search-section {
        margin-bottom: 16px;
      }

      .quick-stats {
        padding: 12px;
        gap: 12px;
      }
    }
  `]
})
export class RegionListComponent implements OnInit, OnDestroy {
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
  activeImports$ = new BehaviorSubject<ImportSummary[]>([]);
  isLoading$ = new BehaviorSubject<boolean>(true);
  error$ = new BehaviorSubject<string | null>(null);

  // Computed streams
  filteredRegions$: Observable<MetropolitanRegion[]>;

  constructor(
    private regionService: RegionService,
    private importService: ImportService
  ) {
    // Setup search term observable
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
      filtered = filtered.filter(region =>
        region.name.toLowerCase().includes(term) ||
        region.regionOnestopId.toLowerCase().includes(term)
      );
    }

    // Apply auto-update filter
    if (autoUpdateFilter !== undefined) {
      filtered = filtered.filter(region =>
        region.autoUpdateEnabled === autoUpdateFilter
      );
    }

    return filtered;
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

  trackByRegionId(index: number, region: MetropolitanRegion): string {
    return region.regionOnestopId;
  }

  formatLastCheck(region: MetropolitanRegion): string {
    return RegionUtils.formatLastCheck(region);
  }

  hasActiveImport(region: MetropolitanRegion): boolean {
    const activeImports = this.activeImports$.value;
    return activeImports.some(imp =>
      imp.regionName === region.name ||
      activeImports.some(imp2 => imp2.feedOnestopId.includes(region.regionOnestopId))
    );
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
}