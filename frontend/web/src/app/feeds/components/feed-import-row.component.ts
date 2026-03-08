import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { FeedImportSummary, ImportProgress, ImportStatus } from '../models/import.models';
import { ImportService } from '../services/import.service';

/**
 * Compact row component for displaying individual feed import progress.
 *
 * Displays feed name, status badge, real-time progress bar, current step,
 * estimated time remaining, and a stop button.
 *
 * Uses OnPush change detection for performance and real-time monitoring
 * via ImportService subscriptions.
 */
@Component({
  selector: 'app-feed-import-row',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <div class="feed-row" role="listitem">
      <div class="feed-info">
        <mat-icon class="feed-icon">rss_feed</mat-icon>
        <span class="feed-name">{{ feedImport.feedName || feedImport.feedOnestopId }}</span>
        <span class="status-badge" [ngClass]="getStatusClass()">
          {{ feedImport.status }}
        </span>
      </div>

      <div class="progress-section">
        <mat-progress-bar
          [value]="currentProgress?.progressPercentage || 0"
          [mode]="currentProgress ? 'determinate' : 'indeterminate'"
          color="primary"
        ></mat-progress-bar>
        <div class="progress-details">
          <span class="step-info">{{ currentProgress?.currentStep || 'Starting...' }}</span>
          @if (currentProgress?.estimatedTimeRemainingSeconds) {
            <span class="time-remaining">{{ formatTimeRemaining() }}</span>
          }
        </div>
      </div>

      <button
        mat-icon-button
        color="warn"
        (click)="onStop()"
        [attr.aria-label]="'Stop import for ' + (feedImport.feedName || feedImport.feedOnestopId)"
        class="stop-button"
      >
        <mat-icon>stop_circle</mat-icon>
      </button>
    </div>
  `,
  styles: [
    `
      .feed-row {
        display: grid;
        grid-template-columns: 1fr 2fr auto;
        gap: 1rem;
        align-items: center;
        padding: 0.75rem 0;
        border-bottom: 1px solid var(--ms-color-border, #e0e0e0);
      }

      .feed-row:last-child {
        border-bottom: none;
      }

      .feed-info {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        min-width: 0; /* Enable text truncation */
      }

      .feed-icon {
        color: var(--mat-sys-on-surface-variant, #666);
        flex-shrink: 0;
      }

      .feed-name {
        font-weight: 500;
        color: var(--mat-sys-on-surface, #000);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .status-badge {
        padding: 0.125rem 0.5rem;
        border-radius: 0.25rem;
        font-size: 0.75rem;
        font-weight: 600;
        text-transform: uppercase;
        flex-shrink: 0;
      }

      .status-running {
        background-color: var(--mat-sys-primary-container, #e3f2fd);
        color: var(--mat-sys-on-primary-container, #0d47a1);
      }

      .status-pending {
        background-color: var(--mat-sys-secondary-container, #fff3e0);
        color: var(--mat-sys-on-secondary-container, #e65100);
      }

      .status-completed {
        background-color: var(--mat-sys-tertiary-container, #e8f5e9);
        color: var(--mat-sys-on-tertiary-container, #2e7d32);
      }

      .status-failed {
        background-color: var(--mat-sys-error-container, #ffebee);
        color: var(--mat-sys-on-error-container, #c62828);
      }

      .status-cancelled {
        background-color: var(--mat-sys-surface-variant, #f5f5f5);
        color: var(--mat-sys-on-surface-variant, #666);
      }

      .progress-section {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
      }

      .progress-details {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-size: 0.75rem;
        color: var(--mat-sys-on-surface-variant, #666);
        margin-top: 0.25rem;
      }

      .step-info {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .time-remaining {
        margin-left: 0.5rem;
        color: var(--mat-sys-primary, #1976d2);
        font-weight: 500;
        flex-shrink: 0;
      }

      .stop-button {
        flex-shrink: 0;
      }

      /* Dark theme support */
      :host-context(.dark-theme) .feed-name {
        color: var(--mat-sys-on-surface, #fff);
      }

      :host-context(.dark-theme) .feed-row {
        border-bottom-color: var(--ms-color-border, #424242);
      }

      /* Focus indicators for accessibility */
      .stop-button:focus-visible {
        outline: 2px solid var(--mat-sys-primary, #1976d2);
        outline-offset: 2px;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FeedImportRowComponent implements OnInit, OnDestroy {
  /** Feed import summary data */
  @Input() feedImport!: FeedImportSummary;

  /** Event emitted when user clicks stop button */
  @Output() stopImport = new EventEmitter<string>();

  /** Current progress data (updated in real-time) */
  currentProgress: ImportProgress | null = null;

  /** Subject for managing subscriptions */
  private destroy$ = new Subject<void>();

  constructor(
    private importService: ImportService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    // Initialize with existing progress from feedImport
    this.currentProgress = this.feedImport.progress;

    // Subscribe to real-time progress updates
    this.importService
      .monitorImportProgress(this.feedImport.id, this.destroy$)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (progress) => {
          this.currentProgress = progress;
          this.cdr.markForCheck(); // Trigger change detection with OnPush strategy
        },
        error: (error) => {
          console.error('Error monitoring import progress:', error);
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Handles stop button click by emitting the import ID.
   */
  onStop(): void {
    this.stopImport.emit(this.feedImport.id);
  }

  /**
   * Formats estimated time remaining into human-readable format.
   *
   * Examples:
   * - 125 seconds → "2m 5s"
   * - 60 seconds → "1m 0s"
   * - 30 seconds → "30s"
   *
   * @returns Formatted time string
   */
  formatTimeRemaining(): string {
    if (!this.currentProgress?.estimatedTimeRemainingSeconds) {
      return '';
    }

    const seconds = this.currentProgress.estimatedTimeRemainingSeconds;
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;

    if (minutes > 0) {
      return `${minutes}m ${remainingSeconds}s`;
    }

    return `${seconds}s`;
  }

  /**
   * Returns CSS class for status badge based on import status.
   *
   * @returns CSS class name (e.g., 'status-running', 'status-failed')
   */
  getStatusClass(): string {
    const statusMap: Record<ImportStatus, string> = {
      [ImportStatus.PENDING]: 'status-pending',
      [ImportStatus.RUNNING]: 'status-running',
      [ImportStatus.COMPLETED]: 'status-completed',
      [ImportStatus.FAILED]: 'status-failed',
      [ImportStatus.CANCELLED]: 'status-cancelled',
    };

    return statusMap[this.feedImport.status] || 'status-pending';
  }
}
