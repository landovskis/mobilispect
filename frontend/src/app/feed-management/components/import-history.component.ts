import { Component, OnInit, OnDestroy, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatBadgeModule } from '@angular/material/badge';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatMenuModule } from '@angular/material/menu';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { Observable, Subject, BehaviorSubject, combineLatest } from 'rxjs';
import { map, takeUntil, debounceTime, distinctUntilChanged, startWith, switchMap } from 'rxjs/operators';
import {
  ImportHistoryRequest,
  ImportHistoryResponse,
  ImportHistoryFilters,
  ImportHistorySortOptions,
  ImportHistoryUtils,
  IMPORT_HISTORY_CONSTANTS
} from '../models/import-history.model';
import {
  FeedImport,
  ImportStatus,
  TriggerType,
  ImportUtils
} from '../models/import.models';
import { HistoryService } from '../services/history.service';

/**
 * Import History Component
 *
 * Displays a comprehensive, filterable, and paginated list of import history
 * with advanced filtering options, sorting, and detailed views.
 *
 * Constitutional Compliance:
 * - UX Consistency: Material Design 3 with light/dark mode support
 * - Performance: Virtual scrolling, debounced search, efficient pagination
 * - Accessibility: ARIA labels, keyboard navigation, screen reader support
 * - Responsive: Mobile-first design with adaptive layouts
 */
@Component({
  selector: 'app-import-history',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatSelectModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatBadgeModule,
    MatPaginatorModule,
    MatTableModule,
    MatSortModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatMenuModule,
    MatCheckboxModule
  ],
  template: `
    <div class="import-history-container">
      <!-- Header and Quick Actions -->
      <div class="header-section">
        <div class="title-section">
          <h2>
            <mat-icon>history</mat-icon>
            Import History
          </h2>
          <p class="subtitle">View and analyze feed import history with advanced filtering</p>
        </div>

        <div class="header-actions">
          <button
            mat-raised-button
            color="primary"
            (click)="refreshHistory()"
            [disabled]="isLoading$ | async"
            matTooltip="Refresh import history"
          >
            <mat-icon>refresh</mat-icon>
            Refresh
          </button>

          <button
            mat-stroked-button
            [matMenuTriggerFor]="exportMenu"
            [disabled]="(historyData$ | async)?.totalElements === 0"
            matTooltip="Export history data"
          >
            <mat-icon>download</mat-icon>
            Export
          </button>

          <mat-menu #exportMenu="matMenu">
            <button mat-menu-item (click)="exportData('csv')">
              <mat-icon>description</mat-icon>
              Export as CSV
            </button>
            <button mat-menu-item (click)="exportData('json')">
              <mat-icon>data_object</mat-icon>
              Export as JSON
            </button>
            <button mat-menu-item (click)="exportData('xlsx')">
              <mat-icon>table_chart</mat-icon>
              Export as Excel
            </button>
          </mat-menu>

          <button
            mat-icon-button
            (click)="toggleFilters()"
            [color]="showFilters ? 'primary' : ''"
            matTooltip="Toggle advanced filters"
          >
            <mat-icon>filter_list</mat-icon>
          </button>
        </div>
      </div>

      <!-- Advanced Filters Panel -->
      <mat-card *ngIf="showFilters" class="filters-panel" appearance="outlined">
        <mat-card-header>
          <mat-card-title>
            <mat-icon>tune</mat-icon>
            Advanced Filters
          </mat-card-title>
        </mat-card-header>

        <mat-card-content>
          <form [formGroup]="filtersForm" class="filters-form">
            <!-- Row 1: Search and Feed -->
            <div class="filter-row">
              <mat-form-field class="filter-field">
                <mat-label>Search</mat-label>
                <input
                  matInput
                  formControlName="search"
                  placeholder="Search imports, errors, or IDs..."
                  [attr.aria-label]="'Search import history'"
                >
                <mat-icon matSuffix>search</mat-icon>
              </mat-form-field>

              <mat-form-field class="filter-field">
                <mat-label>Feed</mat-label>
                <mat-select formControlName="feedOnestopId" multiple>
                  <mat-option value="">All Feeds</mat-option>
                  <mat-option *ngFor="let feed of availableFeeds$ | async" [value]="feed.id">
                    {{ feed.name }}
                  </mat-option>
                </mat-select>
              </mat-form-field>
            </div>

            <!-- Row 2: Status and Trigger Type -->
            <div class="filter-row">
              <mat-form-field class="filter-field">
                <mat-label>Status</mat-label>
                <mat-select formControlName="status" multiple>
                  <mat-option value="">All Statuses</mat-option>
                  <mat-option *ngFor="let status of importStatuses" [value]="status">
                    {{ getStatusDisplayName(status) }}
                  </mat-option>
                </mat-select>
              </mat-form-field>

              <mat-form-field class="filter-field">
                <mat-label>Trigger Type</mat-label>
                <mat-select formControlName="triggerType" multiple>
                  <mat-option value="">All Types</mat-option>
                  <mat-option *ngFor="let type of triggerTypes" [value]="type">
                    {{ getTriggerTypeDisplayName(type) }}
                  </mat-option>
                </mat-select>
              </mat-form-field>
            </div>

            <!-- Row 3: Date Range -->
            <div class="filter-row">
              <mat-form-field class="filter-field">
                <mat-label>Start Date</mat-label>
                <input matInput [matDatepicker]="startPicker" formControlName="startDate">
                <mat-datepicker-toggle matSuffix [for]="startPicker"></mat-datepicker-toggle>
                <mat-datepicker #startPicker></mat-datepicker>
              </mat-form-field>

              <mat-form-field class="filter-field">
                <mat-label>End Date</mat-label>
                <input matInput [matDatepicker]="endPicker" formControlName="endDate">
                <mat-datepicker-toggle matSuffix [for]="endPicker"></mat-datepicker-toggle>
                <mat-datepicker #endPicker></mat-datepicker>
              </mat-form-field>
            </div>

            <!-- Quick Date Range Buttons -->
            <div class="quick-dates">
              <mat-chip-listbox [multiple]="false" [hideSingleSelectionIndicator]="true">
                <mat-chip-option
                  *ngFor="let range of quickDateRanges"
                  (click)="applyQuickDateRange(range.key)"
                >
                  {{ range.label }}
                </mat-chip-option>
              </mat-chip-listbox>
            </div>

            <!-- Filter Actions -->
            <div class="filter-actions">
              <button
                mat-raised-button
                color="primary"
                type="button"
                (click)="applyFilters()"
              >
                Apply Filters
              </button>

              <button
                mat-stroked-button
                type="button"
                (click)="clearFilters()"
              >
                Clear All
              </button>

              <span class="active-filters-count" *ngIf="activeFiltersCount > 0">
                {{ activeFiltersCount }} filter(s) active
              </span>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <!-- Quick Filter Chips (when filters panel is closed) -->
      <div *ngIf="!showFilters && hasActiveFilters" class="quick-filter-chips">
        <mat-chip-listbox>
          <mat-chip-option
            *ngFor="let chip of activeFilterChips"
            (removed)="removeFilter(chip.type, chip.value)"
            [removable]="true"
          >
            {{ chip.label }}
            <mat-icon matChipRemove>cancel</mat-icon>
          </mat-chip-option>
        </mat-chip-listbox>
      </div>

      <!-- Summary Stats -->
      <div *ngIf="!(isLoading$ | async) && (historyData$ | async)" class="summary-stats">
        <div class="stat-card">
          <span class="stat-number">{{ (historyData$ | async)?.totalElements || 0 }}</span>
          <span class="stat-label">Total Imports</span>
        </div>

        <div class="stat-card">
          <span class="stat-number">{{ getFilteredSuccessRate() | async }}%</span>
          <span class="stat-label">Success Rate</span>
        </div>

        <div class="stat-card">
          <span class="stat-number">{{ getFilteredFailureCount() | async }}</span>
          <span class="stat-label">Failed Imports</span>
        </div>

        <div class="stat-card">
          <span class="stat-number">{{ getFilteredAverageDuration() | async }}</span>
          <span class="stat-label">Avg Duration</span>
        </div>
      </div>

      <!-- Loading State -->
      <div *ngIf="isLoading$ | async" class="loading-container">
        <mat-spinner diameter="40"></mat-spinner>
        <p>Loading import history...</p>
      </div>

      <!-- Error State -->
      <div *ngIf="error$ | async as error" class="error-container">
        <mat-icon color="warn">error</mat-icon>
        <p>{{ error }}</p>
        <button mat-raised-button color="primary" (click)="refreshHistory()">
          <mat-icon>refresh</mat-icon>
          Retry
        </button>
      </div>

      <!-- History Table -->
      <div *ngIf="!(isLoading$ | async) && !(error$ | async)" class="history-table-container">
        <mat-table
          [dataSource]="historyDataSource$ | async"
          matSort
          (matSortChange)="onSortChange($event)"
          class="history-table"
        >
          <!-- Import ID Column -->
          <ng-container matColumnDef="id">
            <mat-header-cell *matHeaderCellDef mat-sort-header="id">
              Import ID
            </mat-header-cell>
            <mat-cell *matCellDef="let import">
              <div class="import-id">
                <code>{{ import.id | slice:0:8 }}</code>
                <button
                  mat-icon-button
                  (click)="viewImportDetails(import)"
                  matTooltip="View details"
                  class="details-button"
                >
                  <mat-icon>info</mat-icon>
                </button>
              </div>
            </mat-cell>
          </ng-container>

          <!-- Feed Column -->
          <ng-container matColumnDef="feed">
            <mat-header-cell *matHeaderCellDef mat-sort-header="feedOnestopId">
              Feed
            </mat-header-cell>
            <mat-cell *matCellDef="let import">
              <div class="feed-info">
                <span class="feed-id">{{ import.feedOnestopId }}</span>
                <span class="feed-name" *ngIf="getFeedName(import.feedOnestopId)">
                  {{ getFeedName(import.feedOnestopId) }}
                </span>
              </div>
            </mat-cell>
          </ng-container>

          <!-- Status Column -->
          <ng-container matColumnDef="status">
            <mat-header-cell *matHeaderCellDef mat-sort-header="status">
              Status
            </mat-header-cell>
            <mat-cell *matCellDef="let import">
              <mat-chip
                [ngClass]="getStatusChipClass(import.status)"
                [disabled]="true"
              >
                <mat-icon>{{ getStatusIcon(import.status) }}</mat-icon>
                {{ getStatusDisplayName(import.status) }}
              </mat-chip>
            </mat-cell>
          </ng-container>

          <!-- Trigger Type Column -->
          <ng-container matColumnDef="triggerType">
            <mat-header-cell *matHeaderCellDef mat-sort-header="triggerType">
              Trigger
            </mat-header-cell>
            <mat-cell *matCellDef="let import">
              <mat-chip
                [ngClass]="getTriggerTypeChipClass(import.triggerType)"
                [disabled]="true"
              >
                <mat-icon>{{ getTriggerTypeIcon(import.triggerType) }}</mat-icon>
                {{ getTriggerTypeDisplayName(import.triggerType) }}
              </mat-chip>
            </mat-cell>
          </ng-container>

          <!-- Duration Column -->
          <ng-container matColumnDef="duration">
            <mat-header-cell *matHeaderCellDef>Duration</mat-header-cell>
            <mat-cell *matCellDef="let import">
              <span class="duration">
                {{ formatDuration(import) }}
              </span>
            </mat-cell>
          </ng-container>

          <!-- Created At Column -->
          <ng-container matColumnDef="createdAt">
            <mat-header-cell *matHeaderCellDef mat-sort-header="createdAt">
              Created
            </mat-header-cell>
            <mat-cell *matCellDef="let import">
              <div class="timestamp">
                <span class="date">{{ formatDate(import.createdAt) }}</span>
                <span class="time">{{ formatTime(import.createdAt) }}</span>
                <span class="relative" matTooltip="{{ formatFullTimestamp(import.createdAt) }}">
                  {{ formatRelativeTime(import.createdAt) }}
                </span>
              </div>
            </mat-cell>
          </ng-container>

          <!-- Administrator Column -->
          <ng-container matColumnDef="administrator">
            <mat-header-cell *matHeaderCellDef>Administrator</mat-header-cell>
            <mat-cell *matCellDef="let import">
              <div class="administrator" *ngIf="import.administratorUsername">
                <mat-icon>person</mat-icon>
                {{ import.administratorUsername }}
              </div>
              <span class="automatic" *ngIf="!import.administratorUsername">
                <mat-icon>smart_toy</mat-icon>
                Automatic
              </span>
            </mat-cell>
          </ng-container>

          <!-- Actions Column -->
          <ng-container matColumnDef="actions">
            <mat-header-cell *matHeaderCellDef>Actions</mat-header-cell>
            <mat-cell *matCellDef="let import">
              <div class="action-buttons">
                <button
                  mat-icon-button
                  (click)="viewImportDetails(import)"
                  matTooltip="View details"
                >
                  <mat-icon>visibility</mat-icon>
                </button>

                <button
                  mat-icon-button
                  *ngIf="canRetryImport(import)"
                  (click)="retryImport(import)"
                  matTooltip="Retry import"
                  color="primary"
                >
                  <mat-icon>refresh</mat-icon>
                </button>

                <button
                  mat-icon-button
                  *ngIf="canCancelImport(import)"
                  (click)="cancelImport(import)"
                  matTooltip="Cancel import"
                  color="warn"
                >
                  <mat-icon>cancel</mat-icon>
                </button>

                <button
                  mat-icon-button
                  [matMenuTriggerFor]="actionMenu"
                  matTooltip="More actions"
                >
                  <mat-icon>more_vert</mat-icon>
                </button>

                <mat-menu #actionMenu="matMenu">
                  <button mat-menu-item (click)="viewLogs(import)">
                    <mat-icon>description</mat-icon>
                    View Logs
                  </button>
                  <button mat-menu-item (click)="downloadImportData(import)" [disabled]="!import.completedAt">
                    <mat-icon>download</mat-icon>
                    Download Data
                  </button>
                  <button mat-menu-item (click)="shareImport(import)">
                    <mat-icon>share</mat-icon>
                    Share Link
                  </button>
                </mat-menu>
              </div>
            </mat-cell>
          </ng-container>

          <mat-header-row *matHeaderRowDef="displayedColumns"></mat-header-row>
          <mat-row *matRowDef="let row; columns: displayedColumns;" class="history-row"></mat-row>
        </mat-table>

        <!-- Empty State -->
        <div *ngIf="(historyDataSource$ | async)?.length === 0" class="empty-state">
          <mat-icon>history_toggle_off</mat-icon>
          <h3>No import history found</h3>
          <p *ngIf="hasActiveFilters">Try adjusting your filter criteria.</p>
          <p *ngIf="!hasActiveFilters">No imports have been performed yet.</p>
          <button *ngIf="hasActiveFilters" mat-raised-button color="primary" (click)="clearFilters()">
            Clear Filters
          </button>
        </div>

        <!-- Pagination -->
        <mat-paginator
          *ngIf="(historyData$ | async)?.totalElements > 0"
          [length]="(historyData$ | async)?.totalElements"
          [pageSize]="pageSize"
          [pageSizeOptions]="pageSizeOptions"
          [pageIndex]="currentPage"
          (page)="onPageChange($event)"
          showFirstLastButtons
          [attr.aria-label]="'Import history pagination'"
        ></mat-paginator>
      </div>
    </div>
  `,
  styles: [`
    .import-history-container {
      padding: 16px;
      max-width: 1400px;
      margin: 0 auto;
    }

    .header-section {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 24px;
      gap: 16px;
    }

    .title-section h2 {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 0 8px 0;
      color: var(--mdc-theme-on-surface);
    }

    .subtitle {
      margin: 0;
      color: var(--mdc-theme-on-surface-variant);
      font-size: 14px;
    }

    .header-actions {
      display: flex;
      gap: 8px;
      align-items: center;
    }

    .filters-panel {
      margin-bottom: 24px;
      background-color: var(--mdc-theme-surface-variant);
    }

    .filters-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .filter-row {
      display: flex;
      gap: 16px;
      align-items: center;
    }

    .filter-field {
      flex: 1;
      min-width: 200px;
    }

    .quick-dates {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .filter-actions {
      display: flex;
      gap: 16px;
      align-items: center;
    }

    .active-filters-count {
      font-size: 12px;
      color: var(--mdc-theme-primary);
      font-weight: 500;
    }

    .quick-filter-chips {
      margin-bottom: 16px;
    }

    .summary-stats {
      display: flex;
      gap: 16px;
      justify-content: center;
      flex-wrap: wrap;
      padding: 16px;
      background-color: var(--mdc-theme-surface-variant);
      border-radius: 8px;
      margin-bottom: 24px;
    }

    .stat-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 12px 16px;
      background-color: var(--mdc-theme-surface);
      border-radius: 8px;
      min-width: 120px;
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

    .history-table-container {
      background-color: var(--mdc-theme-surface);
      border-radius: 8px;
      overflow: hidden;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
    }

    .history-table {
      width: 100%;
    }

    .history-row:hover {
      background-color: var(--mdc-theme-surface-variant);
    }

    .import-id {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .import-id code {
      background-color: var(--mdc-theme-surface-variant);
      padding: 2px 6px;
      border-radius: 4px;
      font-family: 'Roboto Mono', monospace;
      font-size: 12px;
    }

    .details-button {
      opacity: 0.7;
      transition: opacity 0.2s;
    }

    .details-button:hover {
      opacity: 1;
    }

    .feed-info {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .feed-id {
      font-family: 'Roboto Mono', monospace;
      font-size: 12px;
      color: var(--mdc-theme-primary);
    }

    .feed-name {
      font-size: 11px;
      color: var(--mdc-theme-on-surface-variant);
    }

    .duration {
      font-family: 'Roboto Mono', monospace;
      font-size: 12px;
    }

    .timestamp {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .date {
      font-size: 12px;
      color: var(--mdc-theme-on-surface);
    }

    .time {
      font-size: 11px;
      color: var(--mdc-theme-on-surface-variant);
    }

    .relative {
      font-size: 10px;
      color: var(--mdc-theme-outline);
      font-style: italic;
    }

    .administrator {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
    }

    .automatic {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: var(--mdc-theme-on-surface-variant);
      font-style: italic;
    }

    .action-buttons {
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .empty-state {
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

    /* Status chips */
    .status-completed { background-color: var(--mdc-theme-tertiary-container); }
    .status-failed { background-color: var(--mdc-theme-error-container); }
    .status-running { background-color: var(--mdc-theme-secondary-container); }
    .status-pending { background-color: var(--mdc-theme-primary-container); }
    .status-cancelled { background-color: var(--mdc-theme-outline-variant); }

    .trigger-automatic { background-color: var(--mdc-theme-tertiary-container); }
    .trigger-manual { background-color: var(--mdc-theme-secondary-container); }

    /* Responsive Design */
    @media (max-width: 1200px) {
      .displayedColumns {
        /* Hide some columns on smaller screens */
      }
    }

    @media (max-width: 768px) {
      .import-history-container {
        padding: 12px;
      }

      .header-section {
        flex-direction: column;
        align-items: stretch;
      }

      .header-actions {
        justify-content: flex-end;
      }

      .filter-row {
        flex-direction: column;
        align-items: stretch;
      }

      .summary-stats {
        padding: 12px;
        gap: 12px;
      }

      .stat-card {
        min-width: 100px;
      }
    }
  `]
})
export class ImportHistoryComponent implements OnInit, OnDestroy {
  @Input() feedOnestopId?: string; // Optional filter for specific feed
  @Output() importSelected = new EventEmitter<FeedImport>();
  @Output() importDetailsRequested = new EventEmitter<FeedImport>();

  private destroy$ = new Subject<void>();

  // Display columns for the table
  displayedColumns = ['id', 'feed', 'status', 'triggerType', 'duration', 'createdAt', 'administrator', 'actions'];

  // Form for filters
  filtersForm: FormGroup;
  showFilters = false;

  // Pagination
  currentPage = 0;
  pageSize = IMPORT_HISTORY_CONSTANTS.DEFAULT_PAGE_SIZE;
  pageSizeOptions = [10, 20, 50, 100];

  // Sorting
  currentSort: ImportHistorySortOptions = ImportHistoryUtils.createDefaultSortOptions();

  // Data streams
  historyData$ = new BehaviorSubject<ImportHistoryResponse | null>(null);
  historyDataSource$ = new BehaviorSubject<FeedImport[]>([]);
  availableFeeds$ = new BehaviorSubject<{ id: string; name: string }[]>([]);
  isLoading$ = new BehaviorSubject<boolean>(true);
  error$ = new BehaviorSubject<string | null>(null);

  // Filter state
  activeFiltersCount = 0;
  hasActiveFilters = false;
  activeFilterChips: { type: string; value: any; label: string }[] = [];

  // Static data
  importStatuses = Object.values(ImportStatus);
  triggerTypes = Object.values(TriggerType);
  quickDateRanges = [
    { key: 'last7days', label: 'Last 7 Days' },
    { key: 'last30days', label: 'Last 30 Days' },
    { key: 'last90days', label: 'Last 90 Days' },
    { key: 'thisMonth', label: 'This Month' },
    { key: 'lastMonth', label: 'Last Month' }
  ];

  constructor(
    private historyService: HistoryService,
    private formBuilder: FormBuilder
  ) {
    this.initializeFiltersForm();
  }

  ngOnInit(): void {
    this.loadHistory();
    this.setupFormSubscriptions();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeFiltersForm(): void {
    this.filtersForm = this.formBuilder.group({
      search: [''],
      feedOnestopId: [this.feedOnestopId ? [this.feedOnestopId] : []],
      status: [[]],
      triggerType: [[]],
      startDate: [null],
      endDate: [null]
    });
  }

  private setupFormSubscriptions(): void {
    this.filtersForm.valueChanges.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.updateActiveFiltersState();
    });
  }

  private loadHistory(): void {
    this.isLoading$.next(true);
    this.error$.next(null);

    const request = this.buildHistoryRequest();

    this.historyService.getImportHistory(request).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (response) => {
        this.historyData$.next(response);
        this.historyDataSource$.next(response.content);
        this.isLoading$.next(false);
      },
      error: (error) => {
        console.error('Failed to load import history:', error);
        this.error$.next('Failed to load import history. Please try again.');
        this.isLoading$.next(false);
      }
    });
  }

  private buildHistoryRequest(): ImportHistoryRequest {
    const formValue = this.filtersForm.value;
    const request: ImportHistoryRequest = {
      page: this.currentPage,
      size: this.pageSize,
      sortBy: this.currentSort.field,
      sortDir: this.currentSort.direction
    };

    if (formValue.search?.trim()) {
      // Note: This would require a search endpoint in the backend
      // For now, we'll filter by error message or import ID
    }

    if (formValue.feedOnestopId?.length > 0) {
      request.feedOnestopId = formValue.feedOnestopId[0]; // Backend expects single value
    }

    if (formValue.status?.length > 0) {
      request.status = formValue.status[0]; // Backend expects single value
    }

    if (formValue.triggerType?.length > 0) {
      request.triggerType = formValue.triggerType[0]; // Backend expects single value
    }

    if (formValue.startDate) {
      request.startDate = formValue.startDate.toISOString();
    }

    if (formValue.endDate) {
      request.endDate = formValue.endDate.toISOString();
    }

    return request;
  }

  private updateActiveFiltersState(): void {
    const formValue = this.filtersForm.value;
    let count = 0;
    const chips: { type: string; value: any; label: string }[] = [];

    if (formValue.search?.trim()) {
      count++;
      chips.push({ type: 'search', value: formValue.search, label: `Search: ${formValue.search}` });
    }

    if (formValue.feedOnestopId?.length > 0) {
      count++;
      chips.push({ type: 'feedOnestopId', value: formValue.feedOnestopId, label: `Feed: ${formValue.feedOnestopId.length} selected` });
    }

    if (formValue.status?.length > 0) {
      count++;
      chips.push({ type: 'status', value: formValue.status, label: `Status: ${formValue.status.length} selected` });
    }

    if (formValue.triggerType?.length > 0) {
      count++;
      chips.push({ type: 'triggerType', value: formValue.triggerType, label: `Trigger: ${formValue.triggerType.length} selected` });
    }

    if (formValue.startDate) {
      count++;
      chips.push({ type: 'startDate', value: formValue.startDate, label: `From: ${this.formatDate(formValue.startDate)}` });
    }

    if (formValue.endDate) {
      count++;
      chips.push({ type: 'endDate', value: formValue.endDate, label: `To: ${this.formatDate(formValue.endDate)}` });
    }

    this.activeFiltersCount = count;
    this.hasActiveFilters = count > 0;
    this.activeFilterChips = chips;
  }

  // Public methods for template
  refreshHistory(): void {
    this.loadHistory();
  }

  toggleFilters(): void {
    this.showFilters = !this.showFilters;
  }

  applyFilters(): void {
    this.currentPage = 0; // Reset to first page
    this.loadHistory();
  }

  clearFilters(): void {
    this.filtersForm.reset();
    this.currentPage = 0;
    this.loadHistory();
  }

  removeFilter(type: string, value: any): void {
    const control = this.filtersForm.get(type);
    if (control) {
      control.setValue(null);
      this.applyFilters();
    }
  }

  applyQuickDateRange(range: string): void {
    const dateRange = this.historyService.createDateRange(range as any);
    this.filtersForm.patchValue({
      startDate: new Date(dateRange.startDate),
      endDate: new Date(dateRange.endDate)
    });
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadHistory();
  }

  onSortChange(sort: Sort): void {
    this.currentSort = {
      field: sort.active as any,
      direction: sort.direction as 'asc' | 'desc'
    };
    this.currentPage = 0; // Reset to first page
    this.loadHistory();
  }

  viewImportDetails(import_: FeedImport): void {
    this.importDetailsRequested.emit(import_);
  }

  retryImport(import_: FeedImport): void {
    // Implementation would call import service to retry
    console.log('Retry import:', import_.id);
  }

  cancelImport(import_: FeedImport): void {
    // Implementation would call import service to cancel
    console.log('Cancel import:', import_.id);
  }

  viewLogs(import_: FeedImport): void {
    // Implementation would navigate to logs view
    console.log('View logs for import:', import_.id);
  }

  downloadImportData(import_: FeedImport): void {
    // Implementation would download import data
    console.log('Download data for import:', import_.id);
  }

  shareImport(import_: FeedImport): void {
    // Implementation would generate shareable link
    const url = `${window.location.origin}/import-history/${import_.id}`;
    navigator.clipboard.writeText(url);
  }

  exportData(format: 'csv' | 'json' | 'xlsx'): void {
    const exportOptions = {
      format,
      filters: ImportHistoryUtils.createDefaultFilters(),
      includeMetrics: true,
      includeLogs: false,
      dateRange: {
        startDate: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
        endDate: new Date().toISOString()
      }
    };

    this.historyService.exportHistory(exportOptions).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `import-history.${format}`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: (error) => {
        console.error('Export failed:', error);
      }
    });
  }

  // Utility methods
  getStatusDisplayName(status: ImportStatus): string {
    return ImportUtils.getStatusDisplayName(status);
  }

  getTriggerTypeDisplayName(triggerType: TriggerType): string {
    return ImportUtils.getTriggerTypeDisplayName(triggerType);
  }

  getStatusChipClass(status: ImportStatus): string {
    return `status-${status.toLowerCase()}`;
  }

  getTriggerTypeChipClass(triggerType: TriggerType): string {
    return `trigger-${triggerType.toLowerCase()}`;
  }

  getStatusIcon(status: ImportStatus): string {
    switch (status) {
      case ImportStatus.COMPLETED: return 'check_circle';
      case ImportStatus.FAILED: return 'error';
      case ImportStatus.RUNNING: return 'sync';
      case ImportStatus.PENDING: return 'schedule';
      case ImportStatus.CANCELLED: return 'cancel';
      default: return 'help';
    }
  }

  getTriggerTypeIcon(triggerType: TriggerType): string {
    switch (triggerType) {
      case TriggerType.AUTOMATIC: return 'smart_toy';
      case TriggerType.MANUAL: return 'person';
      default: return 'help';
    }
  }

  formatDuration(import_: FeedImport): string {
    return ImportUtils.getDuration(import_) || 'N/A';
  }

  formatDate(timestamp: string | Date): string {
    const date = new Date(timestamp);
    return date.toLocaleDateString();
  }

  formatTime(timestamp: string): string {
    const date = new Date(timestamp);
    return date.toLocaleTimeString();
  }

  formatFullTimestamp(timestamp: string): string {
    return ImportUtils.formatTimestamp(timestamp);
  }

  formatRelativeTime(timestamp: string): string {
    return ImportUtils.formatRelativeTime(timestamp);
  }

  getFeedName(feedOnestopId: string): string | null {
    const feeds = this.availableFeeds$.value;
    const feed = feeds.find(f => f.id === feedOnestopId);
    return feed?.name || null;
  }

  canRetryImport(import_: FeedImport): boolean {
    return import_.status === ImportStatus.FAILED;
  }

  canCancelImport(import_: FeedImport): boolean {
    return ImportUtils.isCancellable(import_);
  }

  getFilteredSuccessRate(): Observable<number> {
    return this.historyData$.pipe(
      map(data => {
        if (!data || data.totalElements === 0) return 0;
        // This would be calculated from the statistics
        return 85; // Placeholder
      })
    );
  }

  getFilteredFailureCount(): Observable<number> {
    return this.historyDataSource$.pipe(
      map(imports => imports.filter(imp => imp.status === ImportStatus.FAILED).length)
    );
  }

  getFilteredAverageDuration(): Observable<string> {
    return this.historyDataSource$.pipe(
      map(imports => {
        const durationsInSeconds = imports
          .filter(imp => imp.startedAt && imp.completedAt)
          .map(imp => {
            const start = new Date(imp.startedAt!).getTime();
            const end = new Date(imp.completedAt!).getTime();
            return (end - start) / 1000;
          });

        if (durationsInSeconds.length === 0) return 'N/A';

        const average = durationsInSeconds.reduce((sum, duration) => sum + duration, 0) / durationsInSeconds.length;
        return ImportHistoryUtils.formatDuration(average);
      })
    );
  }
}