import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { FeedImportSummary, ImportProgress } from '../models';
import { BrandButtonComponent } from '../../shared/components/brand-button.component';
import { BrandCardComponent } from '../../shared/components/brand-card.component';
import { ImportService } from '../services/import.service';

@Component({
  selector: 'app-active-import-item',
  standalone: true,
  imports: [
    CommonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressBarModule,
    BrandButtonComponent,
    BrandCardComponent
  ],
  template: `
    <app-brand-card
      titleIcon="rss_feed"
      [title]="importItem.feedName ?? undefined"
      [subtitle]="importItem.regionName ?? undefined"
      [badge]="currentStatus"
      [hasFooter]="true">

      <!-- Progress information -->
      @if (currentProgress) {
        <div class="progress-section mb-4">
          <!-- Step indicator -->
          <div class="step-indicator mb-2 flex items-center gap-2">
            <span class="step-count text-sm font-semibold text-[var(--mat-sys-primary)]">
              Step {{ getCurrentStepNumber() }} of {{ currentProgress.totalSteps }}
            </span>
            <span class="step-divider text-[var(--mat-sys-on-surface-variant)]">•</span>
            <span class="progress-step text-sm italic text-[var(--mat-sys-on-surface-variant)]">
              {{ currentProgress.currentStep }}
            </span>
          </div>

          <!-- Progress bar with percentage -->
          <div class="progress-bar-section">
            <div class="progress-details mb-2 flex items-center justify-between gap-3">
              <span class="progress-percentage text-lg font-bold text-[var(--mat-sys-primary)]">
                {{ currentProgress.progressPercentage }}%
              </span>
            </div>
            <mat-progress-bar
              mode="determinate"
              [value]="currentProgress.progressPercentage"
              color="primary">
            </mat-progress-bar>
          </div>

          @if (currentProgress.estimatedTimeRemainingSeconds !== null && currentProgress.estimatedTimeRemainingSeconds > 0) {
            <div class="time-remaining mt-2 text-sm text-[var(--mat-sys-on-surface-variant)]">
              <mat-icon class="inline-block align-middle text-base">schedule</mat-icon>
              Est. time remaining: {{ formatTimeRemaining(currentProgress.estimatedTimeRemainingSeconds) }}
            </div>
          }
        </div>
      }

      <!-- Started time -->
      @if (importItem.startedAt) {
        <div class="started-time mb-3 flex items-center gap-1.5 text-sm text-[var(--mat-sys-on-surface-variant)]">
          <mat-icon class="text-base">access_time</mat-icon>
          Started: {{ importItem.startedAt | date:'short' }}
        </div>
      }

      <!-- Action button in footer -->
      <div card-footer class="flex w-full items-center justify-end">
        <app-brand-button
          variant="destructive"
          size="sm"
          (click)="onCancelImport()"
          matTooltip="Stop import">
          <mat-icon>stop_circle</mat-icon>
          Stop
        </app-brand-button>
      </div>
    </app-brand-card>
  `,
  styles: [`
    .step-indicator {
      padding: 8px 0;
    }

    .step-count {
      font-size: 0.875rem;
      letter-spacing: 0.025em;
    }

    .step-divider {
      font-size: 0.875rem;
      opacity: 0.6;
    }

    .progress-step {
      font-size: 0.875rem;
    }

    .progress-percentage {
      font-size: 1.1rem;
    }

    .time-remaining mat-icon {
      margin-right: 4px;
      font-size: 16px;
      width: 16px;
      height: 16px;
    }

    .started-time mat-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ActiveImportCardComponent implements OnInit, OnDestroy {
  @Input() importItem!: FeedImportSummary;
  @Output() cancelImport = new EventEmitter<string>();

  private destroy$ = new Subject<void>();

  currentStatus: string;
  currentProgress: ImportProgress | null = null;

  constructor(
    private importService: ImportService,
    private cdr: ChangeDetectorRef
  ) {
    this.currentStatus = '';
  }

  ngOnInit(): void {
    // Initialize current values from input
    this.currentStatus = this.importItem.status;
    this.currentProgress = this.importItem.progress;

    // Subscribe to real-time progress updates via WebSocket + HTTP polling
    this.importService.monitorImportProgress(this.importItem.id, this.destroy$)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (progress) => {
          this.currentProgress = progress;
          this.cdr.markForCheck();
        },
        error: (error) => {
          console.error('Error monitoring import progress:', error);
        }
      });

    // Subscribe to status updates
    this.importService.monitorImportStatus(this.importItem.id, this.destroy$)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (importDetail) => {
          this.currentStatus = importDetail.status;
          if (importDetail.progress) {
            this.currentProgress = importDetail.progress;
          }
          this.cdr.markForCheck();
        },
        error: (error) => {
          console.error('Error monitoring import status:', error);
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onCancelImport(): void {
    this.cancelImport.emit(this.importItem.id);
  }

  getCurrentStepNumber(): number {
    if (!this.currentProgress) return 0;
    // Calculate current step based on progress percentage
    return Math.ceil((this.currentProgress.progressPercentage / 100) * this.currentProgress.totalSteps);
  }

  formatTimeRemaining(seconds: number): string {
    if (seconds <= 0) return 'Unknown';

    if (seconds < 60) {
      return `${seconds}s`;
    }

    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;

    if (minutes < 60) {
      return remainingSeconds > 0 ? `${minutes}m ${remainingSeconds}s` : `${minutes}m`;
    }

    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    return remainingMinutes > 0 ? `${hours}h ${remainingMinutes}m` : `${hours}h`;
  }
}
