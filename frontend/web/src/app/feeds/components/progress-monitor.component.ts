import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy,
  inject,
} from '@angular/core';
import { Subject, BehaviorSubject, timer, combineLatest } from 'rxjs';
import {
  takeUntil,
  map,
  startWith,
  switchMap,
  catchError,
} from 'rxjs/operators';
import { CommonModule } from '@angular/common';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import {
  ImportProgress,
  ProgressDisplayData,
  ProgressStatus,
} from '../models/import-progress.model';
import { ProgressWebSocketService } from '../services/progress-websocket.service';
import { BrandCardComponent } from '../../shared/components/brand-card.component';
import { BrandButtonComponent } from '../../shared/components/brand-button.component';

@Component({
  selector: 'app-progress-monitor',
  standalone: true,
  imports: [
    CommonModule,
    MatProgressBarModule,
    MatIconModule,
    MatChipsModule,
    BrandCardComponent,
    BrandButtonComponent,
  ],
  template: `
    @if (importId) {
      <div class="progress-monitor w-full max-w-[600px]">
        <app-brand-card
          class="progress-card mb-4"
          [ngClass]="'status-' + progressStatus"
          [loading]="!!(isLoading$ | async)"
        >
          <div card-header>
            <div class="flex items-center gap-2 text-white font-semibold">
              <mat-icon [ngClass]="getIconClass()">{{
                getStatusIcon()
              }}</mat-icon>
              Import Progress
            </div>
            <div card-subtitle class="text-white/90">
              Import ID: {{ importId }}
            </div>
          </div>

          <div card-content>
            @if (displayData$ | async; as data) {
              <div class="progress-info">
                <!-- Progress Bar -->
                <div class="progress-section mb-4 flex items-center gap-4">
                  <div
                    class="progress-percentage min-w-[60px] text-2xl font-bold"
                  >
                    {{ data.progress.progressPercentage }}%
                  </div>
                  <mat-progress-bar
                    mode="determinate"
                    [value]="data.progress.progressPercentage"
                    [color]="data.progressBarColor"
                  >
                  </mat-progress-bar>
                </div>

                <!-- Current Step -->
                <div class="current-step mb-4 rounded p-2">
                  <strong>Current Step:</strong> {{ data.progress.currentStep }}
                </div>

                <!-- Step Progress -->
                <div class="step-progress mb-4">
                  <mat-chip-listbox>
                    @for (
                      step of getStepArray(data.progress.totalSteps);
                      track step;
                      let i = $index
                    ) {
                      <mat-chip [color]="getStepColor(i, data.progress)">
                        {{ i + 1 }}
                      </mat-chip>
                    }
                  </mat-chip-listbox>
                </div>

                <!-- Timing Information -->
                <div class="timing-info mb-4 flex flex-wrap gap-6">
                  <div class="duration flex items-center gap-2">
                    <mat-icon>schedule</mat-icon>
                    <span>Duration: {{ formatDuration(data.duration) }}</span>
                  </div>

                  @if (data.estimatedCompletion) {
                    <div class="estimated-completion flex items-center gap-2">
                      <mat-icon>event</mat-icon>
                      <span
                        >Est. Completion:
                        {{ data.estimatedCompletion | date: 'short' }}</span
                      >
                    </div>
                  }
                </div>
              </div>
            }

            <!-- Error State -->
            @if (error$ | async; as error) {
              <div class="error-state flex items-center gap-2 rounded p-4">
                <mat-icon color="warn">error</mat-icon>
                <span>{{ error }}</span>
              </div>
            }

            <!-- Loading State -->
            @if (isLoading$ | async) {
              <div class="loading-state flex items-center gap-2 rounded p-4">
                <mat-progress-bar mode="indeterminate"></mat-progress-bar>
                <span>Connecting to progress updates...</span>
              </div>
            }
          </div>

          @if (showActions) {
            <div card-actions>
              <app-brand-button
                variant="accent"
                size="sm"
                (click)="refreshProgress()"
                [disabled]="!!(isLoading$ | async)"
              >
                <mat-icon>refresh</mat-icon>
                Refresh
              </app-brand-button>
              @if (progressStatus === 'active') {
                <app-brand-button
                  variant="ghost"
                  size="sm"
                  (click)="onCancel()"
                  [disabled]="!!(isLoading$ | async)"
                >
                  <mat-icon>cancel</mat-icon>
                  Cancel Import
                </app-brand-button>
              }
            </div>
          }
        </app-brand-card>

        <!-- WebSocket Connection Status -->
        @if (showConnectionStatus) {
          <div class="connection-status mt-2">
            <mat-chip [color]="getConnectionColor()" highlighted>
              <mat-icon>{{ getConnectionIcon() }}</mat-icon>
              {{ connectionStatus$ | async }}
            </mat-chip>
          </div>
        }
      </div>
    }
  `,
  styles: [
    `
      .progress-card.status-active {
        border-left: 4px solid #2196f3;
      }

      .progress-card.status-completed {
        border-left: 4px solid #4caf50;
      }

      .progress-card.status-error {
        border-left: 4px solid #f44336;
      }

      .progress-percentage {
        color: #0b4f8a;
      }

      .current-step {
        background-color: #f5f5f5;
      }

      .error-state {
        background-color: #ffebee;
        color: #c62828;
      }

      .loading-state {
        background-color: #e3f2fd;
        color: #1565c0;
      }

      .icon-spinning {
        animation: spin 2s linear infinite;
      }

      .icon-success {
        color: #4caf50;
      }

      .icon-error {
        color: #f44336;
      }

      .icon-active {
        color: #2196f3;
      }

      @keyframes spin {
        from {
          transform: rotate(0deg);
        }
        to {
          transform: rotate(360deg);
        }
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgressMonitorComponent implements OnInit, OnDestroy {
  @Input() importId!: string;
  @Input() showActions = true;
  @Input() showConnectionStatus = false;

  @Output() cancelRequested = new EventEmitter<string>();

  private readonly progressWebSocketService = inject(ProgressWebSocketService);

  progress$ = new BehaviorSubject<ImportProgress | null>(null);
  error$ = new BehaviorSubject<string | null>(null);
  isLoading$ = new BehaviorSubject<boolean>(true);
  connectionStatus$ = this.progressWebSocketService.getConnectionStatus();
  displayData$ = combineLatest([
    this.progress$,
    timer(0, 1000), // Update every second for timing
  ]).pipe(
    map(([progress]) =>
      progress ? this.calculateDisplayData(progress) : null,
    ),
  );
  progressStatus: ProgressStatus = 'pending';

  private destroy$ = new Subject<void>();
  private refreshTrigger$ = new Subject<void>();

  ngOnInit(): void {
    if (!this.importId) {
      this.error$.next('Import ID is required');
      return;
    }

    this.subscribeToProgress();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private subscribeToProgress(): void {
    this.refreshTrigger$
      .pipe(
        startWith(null),
        switchMap(() => {
          this.isLoading$.next(true);
          this.error$.next(null);

          return this.progressWebSocketService
            .subscribeToImportProgress(this.importId)
            .pipe(
              catchError((error) => {
                this.error$.next(
                  error.message || 'Failed to connect to progress updates',
                );
                this.isLoading$.next(false);
                return [];
              }),
            );
        }),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: (progress) => {
          this.progress$.next(progress);
          this.isLoading$.next(false);
          this.updateProgressStatus(progress);
        },
        complete: () => {
          this.progressStatus = 'completed';
          this.isLoading$.next(false);
        },
        error: (error) => {
          this.error$.next(error.message || 'Progress update failed');
          this.progressStatus = 'error';
          this.isLoading$.next(false);
        },
      });
  }

  private updateProgressStatus(progress: ImportProgress): void {
    if (progress.progressPercentage >= 100) {
      this.progressStatus = 'completed';
    } else if (progress.progressPercentage > 0) {
      this.progressStatus = 'active';
    } else {
      this.progressStatus = 'pending';
    }
  }

  private calculateDisplayData(progress: ImportProgress): ProgressDisplayData {
    const startTime = new Date(progress.startedAt);
    const now = new Date();
    const duration = Math.floor((now.getTime() - startTime.getTime()) / 1000);

    let estimatedCompletion: Date | undefined;
    if (
      progress.estimatedTimeRemainingSeconds &&
      progress.estimatedTimeRemainingSeconds > 0
    ) {
      estimatedCompletion = new Date(
        now.getTime() + progress.estimatedTimeRemainingSeconds * 1000,
      );
    }

    const progressBarColor = this.getProgressBarColor(
      progress.progressPercentage,
    );

    return {
      progress,
      duration,
      estimatedCompletion,
      progressBarColor,
    };
  }

  private getProgressBarColor(
    percentage: number,
  ): 'primary' | 'accent' | 'warn' {
    if (percentage >= 100) return 'accent';
    if (percentage >= 75) return 'primary';
    return 'primary';
  }

  getStatusIcon(): string {
    switch (this.progressStatus) {
      case 'pending':
        return 'hourglass_empty';
      case 'active':
        return 'sync';
      case 'completed':
        return 'check_circle';
      case 'error':
        return 'error';
      case 'cancelled':
        return 'cancel';
      default:
        return 'help';
    }
  }

  getIconClass(): string {
    switch (this.progressStatus) {
      case 'active':
        return 'icon-active icon-spinning';
      case 'completed':
        return 'icon-success';
      case 'error':
        return 'icon-error';
      default:
        return '';
    }
  }

  getStepArray(totalSteps: number): number[] {
    return Array(totalSteps)
      .fill(0)
      .map((_, i) => i);
  }

  getCurrentStepIndex(progress: ImportProgress): number {
    return Math.floor(
      (progress.progressPercentage / 100) * progress.totalSteps,
    );
  }

  getStepColor(
    stepIndex: number,
    progress: ImportProgress,
  ): 'primary' | 'accent' | undefined {
    const currentStep = this.getCurrentStepIndex(progress);
    if (stepIndex < currentStep) return 'accent';
    if (stepIndex === currentStep) return 'primary';
    return undefined;
  }

  formatDuration(seconds: number): string {
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}m ${remainingSeconds}s`;
  }

  getConnectionColor(): 'primary' | 'accent' | 'warn' {
    return 'primary'; // Could be dynamic based on connection status
  }

  getConnectionIcon(): string {
    return 'wifi';
  }

  refreshProgress(): void {
    this.refreshTrigger$.next();
  }

  onCancel(): void {
    if (
      confirm(
        'Are you sure you want to cancel this import? This action cannot be undone.',
      )
    ) {
      this.cancelRequested.emit(this.importId);
    }
  }
}
