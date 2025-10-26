import { Component, Inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
import { MatChipsModule } from '@angular/material/chips';
import { Observable, Subject, BehaviorSubject } from 'rxjs';
import { takeUntil, finalize, switchMap } from 'rxjs/operators';
import { MetropolitanRegion, Feed, FeedUtils, FeedStatus, FeedSpecType } from '../models/region.models';
import { ImportRequest, FeedImport } from '../models/import.models';
import { RegionService } from '../services/region.service';
import { ImportService } from '../services/import.service';

export interface ImportDialogData {
  region: MetropolitanRegion;
  selectedFeeds?: Feed[];
}

/**
 * Import Dialog Component
 *
 * Modal dialog for configuring and initiating feed imports.
 * Allows users to select feeds, configure import options, and monitor progress.
 *
 * Constitutional Compliance:
 * - UX Consistency: Material Design 3 with proper modal patterns
 * - Accessibility: ARIA labels, keyboard navigation, screen reader support
 * - Performance: Efficient data loading and form validation
 * - Error Handling: Comprehensive user feedback and recovery options
 */
@Component({
  selector: 'app-import-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatExpansionModule,
    MatDividerModule,
    MatChipsModule
  ],
  template: `
    <div class="import-dialog">
      <div mat-dialog-title class="dialog-header">
        <div class="header-content">
          <mat-icon>download</mat-icon>
          <div class="header-text">
            <h2>Import Transit Feeds</h2>
            <p>{{ data.region.name }}</p>
          </div>
        </div>
        <button mat-icon-button mat-dialog-close [attr.aria-label]="'Close import dialog'">
          <mat-icon>close</mat-icon>
        </button>
      </div>

      <mat-dialog-content class="dialog-content">
        <!-- Loading State -->
        <div *ngIf="isLoadingFeeds$ | async" class="loading-section">
          <mat-spinner diameter="32"></mat-spinner>
          <p>Loading available feeds...</p>
        </div>

        <!-- Error State -->
        <div *ngIf="feedLoadError$ | async as error" class="error-section">
          <mat-icon color="warn">error</mat-icon>
          <p>{{ error }}</p>
          <button mat-button color="primary" (click)="loadFeeds()">
            <mat-icon>refresh</mat-icon>
            Retry
          </button>
        </div>

        <!-- Feed Selection -->
        <div *ngIf="!(isLoadingFeeds$ | async) && !(feedLoadError$ | async)" class="feed-selection-section">
          <div class="section-header">
            <h3>Select Feeds to Import</h3>
            <div class="feed-stats">
              {{ availableFeeds.length }} feeds available
              <span *ngIf="getSelectedFeedCount() > 0">
                • {{ getSelectedFeedCount() }} selected
              </span>
            </div>
          </div>

          <!-- Feed Type Filter -->
          <div class="filter-section">
            <mat-form-field appearance="outline">
              <mat-label>Filter by type</mat-label>
              <mat-select [(value)]="feedTypeFilter" (selectionChange)="onFeedTypeFilterChange()">
                <mat-option [value]="null">All Types</mat-option>
                <mat-option [value]="FeedSpecType.GTFS">GTFS Static</mat-option>
                <mat-option [value]="FeedSpecType.GTFS_RT">GTFS Realtime</mat-option>
              </mat-select>
            </mat-form-field>

            <div class="bulk-actions">
              <button
                mat-button
                (click)="selectAllFeeds()"
                [disabled]="filteredFeeds.length === 0"
              >
                Select All
              </button>
              <button
                mat-button
                (click)="deselectAllFeeds()"
                [disabled]="getSelectedFeedCount() === 0"
              >
                Deselect All
              </button>
            </div>
          </div>

          <!-- Feed List -->
          <div class="feed-list" *ngIf="filteredFeeds.length > 0">
            <div
              *ngFor="let feed of filteredFeeds; trackBy: trackByFeedId"
              class="feed-item"
              [class.feed-item-selected]="isSelectedFeed(feed)"
            >
              <mat-checkbox
                [checked]="isSelectedFeed(feed)"
                [disabled]="!FeedUtils.isAvailableForImport(feed) || isImportRunning(feed)"
                (change)="toggleFeedSelection(feed, $event.checked)"
                [attr.aria-label]="'Select ' + feed.name + ' for import'"
              >
              </mat-checkbox>

              <div class="feed-info">
                <div class="feed-header">
                  <span class="feed-name">{{ feed.name }}</span>
                  <div class="feed-badges">
                    <mat-chip-set>
                      <mat-chip [color]="getSpecTypeColor(feed.specType)">
                        {{ FeedUtils.getSpecTypeDisplayName(feed.specType) }}
                      </mat-chip>
                      <mat-chip [ngClass]="FeedUtils.getStatusColorClass(feed.status)">
                        {{ FeedUtils.getStatusDisplayName(feed.status) }}
                      </mat-chip>
                      <mat-chip *ngIf="isImportRunning(feed)" color="accent">
                        <mat-icon>sync</mat-icon>
                        Importing
                      </mat-chip>
                    </mat-chip-set>
                  </div>
                </div>

                <div class="feed-details">
                  <span class="feed-id">{{ feed.feedOnestopId }}</span>
                  <span *ngIf="feed.lastUpdatedAt" class="feed-updated">
                    Updated: {{ FeedUtils.formatLastUpdated(feed) }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- No Feeds Available -->
          <div *ngIf="filteredFeeds.length === 0" class="no-feeds">
            <mat-icon>info</mat-icon>
            <p>No feeds available for import with the current filter.</p>
          </div>
        </div>

        <mat-divider></mat-divider>

        <!-- Import Options -->
        <div class="import-options-section">
          <h3>Import Options</h3>

          <form [formGroup]="importForm" class="import-form">
            <mat-checkbox formControlName="force">
              <div class="checkbox-content">
                <span class="checkbox-label">Force Import</span>
                <small class="checkbox-description">
                  Import even if the feed version hasn't changed
                </small>
              </div>
            </mat-checkbox>

            <!-- Advanced Options -->
            <mat-expansion-panel class="advanced-options">
              <mat-expansion-panel-header>
                <mat-panel-title>Advanced Options</mat-panel-title>
              </mat-expansion-panel-header>

              <div class="advanced-content">
                <p class="info-text">
                  <mat-icon>info</mat-icon>
                  Additional import options will be added in future versions.
                </p>
              </div>
            </mat-expansion-panel>
          </form>
        </div>
      </mat-dialog-content>

      <mat-dialog-actions class="dialog-actions">
        <div class="actions-left">
          <span *ngIf="getSelectedFeedCount() > 0" class="selection-count">
            {{ getSelectedFeedCount() }} feed{{ getSelectedFeedCount() === 1 ? '' : 's' }} selected
          </span>
        </div>

        <div class="actions-right">
          <button mat-button mat-dialog-close>Cancel</button>
          <button
            mat-raised-button
            color="primary"
            [disabled]="!canStartImport()"
            (click)="startImports()"
            [attr.aria-label]="'Start import for ' + getSelectedFeedCount() + ' feeds'"
          >
            <mat-icon *ngIf="!(isImporting$ | async)">play_arrow</mat-icon>
            <mat-spinner *ngIf="isImporting$ | async" diameter="16"></mat-spinner>
            {{ (isImporting$ | async) ? 'Starting...' : 'Start Import' }}
          </button>
        </div>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .import-dialog {
      width: 100%;
      max-width: 800px;
      min-height: 400px;
    }

    .dialog-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      padding: 0 !important;
      margin: 0 !important;
    }

    .header-content {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .header-content mat-icon {
      font-size: 32px;
      width: 32px;
      height: 32px;
      color: var(--mdc-theme-primary);
    }

    .header-text h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 500;
    }

    .header-text p {
      margin: 4px 0 0 0;
      color: var(--mdc-theme-on-surface-variant);
      font-size: 14px;
    }

    .dialog-content {
      padding: 16px 0 !important;
      min-height: 300px;
      max-height: 60vh;
      overflow-y: auto;
    }

    .loading-section, .error-section {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 32px 16px;
      text-align: center;
    }

    .error-section {
      color: var(--mdc-theme-error);
    }

    .error-section mat-icon {
      font-size: 32px;
      width: 32px;
      height: 32px;
      margin-bottom: 16px;
    }

    .feed-selection-section {
      margin-bottom: 24px;
    }

    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }

    .section-header h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 500;
    }

    .feed-stats {
      font-size: 14px;
      color: var(--mdc-theme-on-surface-variant);
    }

    .filter-section {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
      gap: 16px;
    }

    .filter-section mat-form-field {
      flex: 1;
      max-width: 200px;
    }

    .bulk-actions {
      display: flex;
      gap: 8px;
    }

    .feed-list {
      max-height: 300px;
      overflow-y: auto;
      border: 1px solid var(--mdc-theme-outline-variant);
      border-radius: 8px;
    }

    .feed-item {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 12px 16px;
      border-bottom: 1px solid var(--mdc-theme-outline-variant);
      transition: background-color 0.2s ease;
    }

    .feed-item:last-child {
      border-bottom: none;
    }

    .feed-item:hover {
      background-color: var(--mdc-theme-surface-variant);
    }

    .feed-item-selected {
      background-color: var(--mdc-theme-primary-container);
    }

    .feed-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .feed-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 12px;
    }

    .feed-name {
      font-weight: 500;
      font-size: 14px;
      line-height: 1.4;
    }

    .feed-badges {
      flex-shrink: 0;
    }

    .feed-badges mat-chip-set {
      --mdc-chip-container-height: 24px;
    }

    .feed-badges mat-chip {
      font-size: 11px;
      --mdc-chip-label-text-size: 11px;
    }

    .feed-details {
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: 12px;
      color: var(--mdc-theme-on-surface-variant);
    }

    .no-feeds {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 32px 16px;
      text-align: center;
      color: var(--mdc-theme-on-surface-variant);
    }

    .no-feeds mat-icon {
      font-size: 32px;
      width: 32px;
      height: 32px;
      margin-bottom: 16px;
      opacity: 0.5;
    }

    .import-options-section {
      margin-bottom: 24px;
    }

    .import-options-section h3 {
      margin: 0 0 16px 0;
      font-size: 16px;
      font-weight: 500;
    }

    .import-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .checkbox-content {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .checkbox-label {
      font-weight: 500;
    }

    .checkbox-description {
      color: var(--mdc-theme-on-surface-variant);
      font-size: 12px;
    }

    .advanced-options {
      margin-top: 8px;
    }

    .advanced-content {
      padding: 16px 0;
    }

    .info-text {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
      color: var(--mdc-theme-on-surface-variant);
      font-size: 14px;
    }

    .info-text mat-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }

    .dialog-actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px 0 0 0 !important;
      margin: 0 !important;
    }

    .actions-left {
      flex: 1;
    }

    .selection-count {
      font-size: 14px;
      color: var(--mdc-theme-on-surface-variant);
    }

    .actions-right {
      display: flex;
      gap: 8px;
    }

    /* Responsive Design */
    @media (max-width: 600px) {
      .import-dialog {
        max-width: 100vw;
        height: 100vh;
      }

      .filter-section {
        flex-direction: column;
        align-items: stretch;
        gap: 12px;
      }

      .bulk-actions {
        justify-content: center;
      }

      .feed-header {
        flex-direction: column;
        align-items: flex-start;
        gap: 8px;
      }

      .dialog-actions {
        flex-direction: column;
        gap: 12px;
      }

      .actions-left, .actions-right {
        width: 100%;
        text-align: center;
      }
    }
  `]
})
export class ImportDialogComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  // Expose enums to template
  FeedSpecType = FeedSpecType;
  FeedUtils = FeedUtils;

  // Form
  importForm: FormGroup;

  // Data
  availableFeeds: Feed[] = [];
  selectedFeeds = new Set<string>();
  feedTypeFilter: FeedSpecType | null = null;

  // State
  isLoadingFeeds$ = new BehaviorSubject<boolean>(true);
  feedLoadError$ = new BehaviorSubject<string | null>(null);
  isImporting$ = new BehaviorSubject<boolean>(false);
  activeImports$ = new BehaviorSubject<string[]>([]);

  // Computed
  filteredFeeds: Feed[] = [];

  constructor(
    private dialogRef: MatDialogRef<ImportDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ImportDialogData,
    private fb: FormBuilder,
    private regionService: RegionService,
    private importService: ImportService,
    private snackBar: MatSnackBar
  ) {
    this.importForm = this.fb.group({
      force: [false]
    });

    // Pre-select feeds if provided
    if (data.selectedFeeds) {
      data.selectedFeeds.forEach(feed => {
        this.selectedFeeds.add(feed.feedOnestopId);
      });
    }
  }

  ngOnInit(): void {
    this.loadFeeds();
    this.loadActiveImports();

    // Subscribe to active imports updates
    this.importService.getActiveImportsObservable().pipe(
      takeUntil(this.destroy$)
    ).subscribe(imports => {
      this.activeImports$.next(imports.map(imp => imp.feedOnestopId));
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadFeeds(): void {
    this.isLoadingFeeds$.next(true);
    this.feedLoadError$.next(null);

    this.regionService.listFeedsForRegion(this.data.region.regionOnestopId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (feeds) => {
        this.availableFeeds = feeds;
        this.applyFeedFilter();
        this.isLoadingFeeds$.next(false);
      },
      error: (error) => {
        console.error('Failed to load feeds:', error);
        this.feedLoadError$.next('Failed to load feeds. Please try again.');
        this.isLoadingFeeds$.next(false);
      }
    });
  }

  private loadActiveImports(): void {
    this.importService.getActiveImports().pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (imports) => {
        this.activeImports$.next(imports.map(imp => imp.feedOnestopId));
      },
      error: (error) => {
        console.error('Failed to load active imports:', error);
      }
    });
  }

  private applyFeedFilter(): void {
    this.filteredFeeds = this.availableFeeds.filter(feed => {
      if (this.feedTypeFilter && feed.specType !== this.feedTypeFilter) {
        return false;
      }
      return true;
    });
  }

  onFeedTypeFilterChange(): void {
    this.applyFeedFilter();
  }

  toggleFeedSelection(feed: Feed, selected: boolean): void {
    if (selected) {
      this.selectedFeeds.add(feed.feedOnestopId);
    } else {
      this.selectedFeeds.delete(feed.feedOnestopId);
    }
  }

  isSelectedFeed(feed: Feed): boolean {
    return this.selectedFeeds.has(feed.feedOnestopId);
  }

  selectAllFeeds(): void {
    this.filteredFeeds.forEach(feed => {
      if (FeedUtils.isAvailableForImport(feed) && !this.isImportRunning(feed)) {
        this.selectedFeeds.add(feed.feedOnestopId);
      }
    });
  }

  deselectAllFeeds(): void {
    this.filteredFeeds.forEach(feed => {
      this.selectedFeeds.delete(feed.feedOnestopId);
    });
  }

  getSelectedFeedCount(): number {
    return this.selectedFeeds.size;
  }

  canStartImport(): boolean {
    return this.getSelectedFeedCount() > 0 && !this.isImporting$.value;
  }

  isImportRunning(feed: Feed): boolean {
    return this.activeImports$.value.includes(feed.feedOnestopId);
  }

  getSpecTypeColor(specType: FeedSpecType): string {
    return specType === FeedSpecType.GTFS ? 'primary' : 'accent';
  }

  trackByFeedId(index: number, feed: Feed): string {
    return feed.feedOnestopId;
  }

  startImports(): void {
    if (!this.canStartImport()) return;

    this.isImporting$.next(true);

    const selectedFeedIds = Array.from(this.selectedFeeds);
    const selectedFeedObjects = this.availableFeeds.filter(feed =>
      selectedFeedIds.includes(feed.feedOnestopId)
    );

    const importOptions: ImportRequest = {
      force: this.importForm.get('force')?.value || false
    };

    // Start imports for all selected feeds
    const importPromises = selectedFeedObjects.map(feed =>
      this.importService.startImport(feed.feedOnestopId, importOptions).toPromise()
    );

    Promise.allSettled(importPromises).then(results => {
      const successful = results.filter(result => result.status === 'fulfilled').length;
      const failed = results.filter(result => result.status === 'rejected').length;

      if (successful > 0) {
        this.snackBar.open(
          `Started ${successful} import${successful === 1 ? '' : 's'} successfully`,
          'Close',
          { duration: 5000 }
        );
      }

      if (failed > 0) {
        this.snackBar.open(
          `Failed to start ${failed} import${failed === 1 ? '' : 's'}`,
          'Close',
          { duration: 5000 }
        );
      }

      this.isImporting$.next(false);

      if (successful > 0) {
        // Close dialog and return the started imports
        this.dialogRef.close({
          success: true,
          importsStarted: successful,
          importsFailed: failed
        });
      }
    }).catch(error => {
      console.error('Error starting imports:', error);
      this.snackBar.open('Failed to start imports', 'Close', { duration: 5000 });
      this.isImporting$.next(false);
    });
  }
}
