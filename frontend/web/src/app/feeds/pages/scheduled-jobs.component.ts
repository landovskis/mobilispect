import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule } from '@angular/material/paginator';
import { Observable, Subject, timer } from 'rxjs';
import { takeUntil, share, map } from 'rxjs/operators';
import { SchedulerService } from '../services/scheduler.service';
import { ImportService } from '../services/import.service';
import {
  SchedulerStatus,
  ImportStats,
  FeedVersionInfo,
  ManualCheckResult
} from '../models/scheduler.model';
import { FeedImport, ImportStatus, TriggerType } from '../models/import.models';
import { BrandCardComponent } from '../../shared/components/brand-card.component';
import { BrandButtonComponent } from '../../shared/components/brand-button.component';

@Component({
  selector: 'app-scheduled-jobs',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatChipsModule,
    MatTableModule,
    MatPaginatorModule,
    BrandCardComponent,
    BrandButtonComponent
  ],
  template: `
    <div class="scheduled-jobs-page mx-auto max-w-[1400px] p-6 max-lg:p-4 max-sm:p-2">
      <div class="page-header mb-8 flex flex-wrap items-start justify-between gap-4 max-md:flex-col">
        <h1 class="m-0">Scheduled Jobs Monitoring</h1>
        <p class="subtitle m-0 mt-2">Monitor automatic feed update jobs and their execution status</p>

        <div class="header-actions flex flex-wrap gap-4 max-md:w-full">
          <app-brand-button
            variant="primary"
            class="min-w-[140px] max-md:flex-1"
            (click)="triggerManualCheck()"
            [disabled]="triggering">
            <mat-icon class="mr-2">refresh</mat-icon>
            {{ triggering ? 'Checking...' : 'Trigger Manual Check' }}
          </app-brand-button>

          <app-brand-button
            variant="ghost"
            class="min-w-[140px] max-md:flex-1"
            (click)="refreshData()">
            <mat-icon class="mr-2">sync</mat-icon>
            Refresh
          </app-brand-button>
        </div>
      </div>

      <!-- Status Overview -->
      <div class="status-overview mb-8">
        @if (schedulerStatus$ | async; as status) {
          <app-brand-card title="Scheduler Status">
            <div class="status-grid mt-4 grid gap-6 sm:grid-cols-2 xl:grid-cols-4">
              <div class="status-item flex items-center gap-4 rounded-lg border border-[var(--mdc-theme-outline)] bg-[var(--mdc-theme-surface-variant)] p-4">
                <mat-icon
                  class="status-icon h-8 w-8 text-[2rem]"
                  [class]="status.enabled ? 'status-enabled' : 'status-disabled'">
                  {{ status.enabled ? 'check_circle' : 'cancel' }}
                </mat-icon>
                <div class="status-info">
                  <span class="label">Scheduler</span>
                  <span class="value" [class]="status.enabled ? 'enabled' : 'disabled'">
                    {{ status.enabled ? 'Enabled' : 'Disabled' }}
                  </span>
                </div>
              </div>

              <div class="status-item flex items-center gap-4 rounded-lg border border-[var(--mdc-theme-outline)] bg-[var(--mdc-theme-surface-variant)] p-4">
                <mat-icon class="info status-icon h-8 w-8 text-[2rem]">schedule</mat-icon>
                <div class="status-info">
                  <span class="label">Next Run</span>
                  <span class="value">{{ status.nextScheduledRun }}</span>
                </div>
              </div>

              <div class="status-item flex items-center gap-4 rounded-lg border border-[var(--mdc-theme-outline)] bg-[var(--mdc-theme-surface-variant)] p-4">
                <mat-icon class="info status-icon h-8 w-8 text-[2rem]">feed</mat-icon>
                <div class="status-info">
                  <span class="label">Active Feeds</span>
                  <span class="value">{{ status.totalActiveFeeds }}</span>
                </div>
              </div>

              <div class="status-item flex items-center gap-4 rounded-lg border border-[var(--mdc-theme-outline)] bg-[var(--mdc-theme-surface-variant)] p-4">
                <mat-icon class="info status-icon h-8 w-8 text-[2rem]">update</mat-icon>
                <div class="status-info">
                  <span class="label">Checked (24h)</span>
                  <span class="value">{{ status.feedsCheckedInLast24Hours }}</span>
                </div>
              </div>
            </div>
          </app-brand-card>
        }
      </div>

      <!-- Import Statistics -->
      <div class="import-stats mb-8">
        @if (importStats$ | async; as stats) {
          <app-brand-card title="Import Activity (Last 24 Hours)">
            <div class="stats-grid mt-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <div class="stat-card success relative flex flex-col items-center overflow-hidden rounded-xl p-6 max-sm:p-4">
                <div class="stat-number mb-2">{{ stats.successfulImportsLast24h }}</div>
                <div class="stat-label">Successful</div>
                <mat-icon class="stat-icon absolute right-2 top-2 text-[4rem] opacity-10 max-sm:text-[3rem]">check_circle</mat-icon>
              </div>

              <div class="stat-card failed relative flex flex-col items-center overflow-hidden rounded-xl p-6 max-sm:p-4">
                <div class="stat-number mb-2">{{ stats.failedImportsLast24h }}</div>
                <div class="stat-label">Failed</div>
                <mat-icon class="stat-icon absolute right-2 top-2 text-[4rem] opacity-10 max-sm:text-[3rem]">error</mat-icon>
              </div>

              <div class="stat-card running relative flex flex-col items-center overflow-hidden rounded-xl p-6 max-sm:p-4">
                <div class="stat-number mb-2">{{ stats.currentlyRunningAutoImports }}</div>
                <div class="stat-label">Running</div>
                <mat-icon class="stat-icon absolute right-2 top-2 text-[4rem] opacity-10 max-sm:text-[3rem]">sync</mat-icon>
              </div>

              <div class="stat-card total relative flex flex-col items-center overflow-hidden rounded-xl p-6 max-sm:p-4">
                <div class="stat-number mb-2">{{ stats.totalAutomaticImportsLast24h }}</div>
                <div class="stat-label">Total</div>
                <mat-icon class="stat-icon absolute right-2 top-2 text-[4rem] opacity-10 max-sm:text-[3rem]">analytics</mat-icon>
              </div>
            </div>

            @if (stats.lastAutomaticImportTime) {
              <div class="last-import mt-4 flex items-center gap-2 rounded-lg bg-[var(--mdc-theme-surface-variant)] p-3 text-sm text-[var(--mdc-theme-on-surface-variant)]">
                <mat-icon class="h-4 w-4 text-base">schedule</mat-icon>
                <span>Last automatic import: {{ stats.lastAutomaticImportTime | date:'medium' }}</span>
              </div>
            }
          </app-brand-card>
        }
      </div>

      <!-- Feed Version Status -->
      <div class="feed-versions mb-8">
        <app-brand-card title="Feed Version Status" subtitle="Current version status for all monitored feeds">
            <div class="table-container mt-4 max-h-[600px] overflow-auto rounded-lg border border-[var(--mdc-theme-outline)] max-md:text-sm">
              <table mat-table [dataSource]="(feedVersions$ | async) || []" class="feed-versions-table w-full">
                <!-- Feed ID Column -->
                <ng-container matColumnDef="feedId">
                  <th mat-header-cell *matHeaderCellDef>Feed ID</th>
                  <td mat-cell *matCellDef="let version">{{ version.feedOnestopId }}</td>
                </ng-container>

                <!-- Status Column -->
                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef>Status</th>
                  <td mat-cell *matCellDef="let version">
                    <mat-chip [ngClass]="getStatusChipClass(version.status)">
                      {{ getStatusLabel(version.status) }}
                    </mat-chip>
                  </td>
                </ng-container>

                <!-- Update Available Column -->
                <ng-container matColumnDef="hasUpdate">
                  <th mat-header-cell *matHeaderCellDef>Update Available</th>
                  <td mat-cell *matCellDef="let version">
                    <mat-icon
                      [class]="version.hasUpdate ? 'update-available' : 'update-none'"
                      [matTooltip]="version.hasUpdate ? 'New version available' : 'Up to date'">
                      {{ version.hasUpdate ? 'new_releases' : 'check' }}
                    </mat-icon>
                  </td>
                </ng-container>

                <!-- Current Version Column -->
                <ng-container matColumnDef="currentVersion">
                  <th mat-header-cell *matHeaderCellDef>Current SHA1</th>
                  <td mat-cell *matCellDef="let version">
                    <code
                      class="sha1 inline-block max-w-[120px] truncate rounded bg-[var(--mdc-theme-surface-variant)] px-2 py-1 text-[0.75rem] font-mono max-md:max-w-[80px] max-md:text-[0.7rem]"
                    >
                      {{ version.currentVersionSha1 || 'N/A' }}
                    </code>
                  </td>
                </ng-container>

                <!-- Latest Version Column -->
                <ng-container matColumnDef="latestVersion">
                  <th mat-header-cell *matHeaderCellDef>Latest SHA1</th>
                  <td mat-cell *matCellDef="let version">
                    <code
                      class="sha1 inline-block max-w-[120px] truncate rounded bg-[var(--mdc-theme-surface-variant)] px-2 py-1 text-[0.75rem] font-mono max-md:max-w-[80px] max-md:text-[0.7rem]"
                    >
                      {{ version.latestVersionSha1 || 'N/A' }}
                    </code>
                  </td>
                </ng-container>

                <!-- Last Checked Column -->
                <ng-container matColumnDef="lastChecked">
                  <th mat-header-cell *matHeaderCellDef>Last Checked</th>
                  <td mat-cell *matCellDef="let version">
                    {{ version.lastCheckedAt ? (version.lastCheckedAt | date:'short') : 'Never' }}
                  </td>
                </ng-container>

                <!-- Actions Column -->
                <ng-container matColumnDef="actions">
                  <th mat-header-cell *matHeaderCellDef>Actions</th>
                  <td mat-cell *matCellDef="let version">
                    <button
                      mat-icon-button
                      [matTooltip]="'Refresh version info'"
                      (click)="refreshFeedVersion(version.feedOnestopId)">
                      <mat-icon>refresh</mat-icon>
                    </button>
                    <button
                      mat-icon-button
                      [matTooltip]="'Check for updates'"
                      (click)="checkFeedUpdate(version.feedOnestopId)">
                      <mat-icon>update</mat-icon>
                    </button>
                  </td>
                </ng-container>

                <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
                <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
              </table>
            </div>

            <mat-paginator
              [pageSize]="10"
              [pageSizeOptions]="[5, 10, 20, 50]"
              showFirstLastButtons>
            </mat-paginator>
        </app-brand-card>
      </div>

      <!-- Recent Automatic Imports -->
      <div class="recent-imports mb-8">
        <app-brand-card title="Recent Automatic Imports" subtitle="Last 20 automatic import operations">
            <div class="table-container mt-4 max-h-[600px] overflow-auto rounded-lg border border-[var(--mdc-theme-outline)] max-md:text-sm">
              <table mat-table [dataSource]="(recentImports$ | async) || []" class="imports-table w-full">
                <!-- Feed ID Column -->
                <ng-container matColumnDef="feedId">
                  <th mat-header-cell *matHeaderCellDef>Feed ID</th>
                  <td mat-cell *matCellDef="let import">{{ import.feedOnestopId }}</td>
                </ng-container>

                <!-- Status Column -->
                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef>Status</th>
                  <td mat-cell *matCellDef="let import">
                    <mat-chip [ngClass]="getImportStatusChipClass(import.status)">
                      {{ import.status }}
                    </mat-chip>
                  </td>
                </ng-container>

                <!-- Started At Column -->
                <ng-container matColumnDef="startedAt">
                  <th mat-header-cell *matHeaderCellDef>Started</th>
                  <td mat-cell *matCellDef="let import">
                    {{ import.startedAt | date:'short' }}
                  </td>
                </ng-container>

                <!-- Duration Column -->
                <ng-container matColumnDef="duration">
                  <th mat-header-cell *matHeaderCellDef>Duration</th>
                  <td mat-cell *matCellDef="let import">
                    {{ calculateDuration(import) }}
                  </td>
                </ng-container>

                <!-- Error Message Column -->
                <ng-container matColumnDef="error">
                  <th mat-header-cell *matHeaderCellDef>Error</th>
                  <td mat-cell *matCellDef="let import">
                    @if (import.errorMessage) {
                      <span
                        class="error-message"
                        [matTooltip]="import.errorMessage">
                        {{ import.errorMessage | slice:0:50 }}{{ import.errorMessage.length > 50 ? '...' : '' }}
                      </span>
                    } @else {
                      <span>-</span>
                    }
                  </td>
                </ng-container>

                <tr mat-header-row *matHeaderRowDef="importColumns"></tr>
                <tr mat-row *matRowDef="let row; columns: importColumns;"></tr>
              </table>
            </div>
        </app-brand-card>
      </div>
    </div>
  `,
  styleUrls: ['./scheduled-jobs.component.scss']
})
export class ScheduledJobsComponent implements OnInit, OnDestroy {
  schedulerStatus$!: Observable<SchedulerStatus>;
  importStats$!: Observable<ImportStats>;
  feedVersions$!: Observable<FeedVersionInfo[]>;
  recentImports$!: Observable<FeedImport[]>;

  displayedColumns = ['feedId', 'status', 'hasUpdate', 'currentVersion', 'latestVersion', 'lastChecked', 'actions'];
  importColumns = ['feedId', 'status', 'startedAt', 'duration', 'error'];

  triggering = false;
  private destroy$ = new Subject<void>();
  private refreshInterval = 30000; // 30 seconds

  constructor(
    private schedulerService: SchedulerService,
    private importService: ImportService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog
  ) {
    this.setupDataStreams();
  }

  ngOnInit(): void {
    // Auto-refresh data every 30 seconds
    timer(0, this.refreshInterval)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.refreshData());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private setupDataStreams(): void {
    this.schedulerStatus$ = this.schedulerService.getSchedulerStatus().pipe(share());
    this.importStats$ = this.schedulerService.getImportStats().pipe(share());
    this.feedVersions$ = this.schedulerService.getAllFeedVersions().pipe(share());

    // Get recent automatic imports
    this.recentImports$ = this.importService.getRecentImports(50)
      .pipe(
        takeUntil(this.destroy$),
        map((imports: FeedImport[]) => imports.filter((imp: FeedImport) => imp.triggerType === TriggerType.AUTOMATIC)
          .sort((a: FeedImport, b: FeedImport) => new Date(b.startedAt || b.createdAt).getTime() - new Date(a.startedAt || a.createdAt).getTime())
          .slice(0, 20)
        ),
        share()
      );
  }

  refreshData(): void {
    this.schedulerStatus$ = this.schedulerService.getSchedulerStatus().pipe(share());
    this.importStats$ = this.schedulerService.getImportStats().pipe(share());
    this.feedVersions$ = this.schedulerService.getAllFeedVersions().pipe(share());
  }

  triggerManualCheck(): void {
    this.triggering = true;

    this.schedulerService.triggerManualCheck()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (result: ManualCheckResult) => {
          this.triggering = false;
          this.snackBar.open(
            `Manual check completed: ${result.checkedCount} feeds checked, ${result.updatesTriggered} updates triggered`,
            'Close',
            { duration: 5000 }
          );
          this.refreshData();
        },
        error: (error) => {
          this.triggering = false;
          console.error('Error triggering manual check:', error);
          this.snackBar.open('Failed to trigger manual check', 'Close', { duration: 3000 });
        }
      });
  }

  refreshFeedVersion(feedOnestopId: string): void {
    this.schedulerService.refreshFeedVersion(feedOnestopId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.snackBar.open(`Version refreshed for feed ${feedOnestopId}`, 'Close', { duration: 3000 });
          this.refreshData();
        },
        error: (error) => {
          console.error('Error refreshing feed version:', error);
          this.snackBar.open('Failed to refresh feed version', 'Close', { duration: 3000 });
        }
      });
  }

  checkFeedUpdate(feedOnestopId: string): void {
    this.schedulerService.checkFeedUpdate(feedOnestopId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (hasUpdate) => {
          const message = hasUpdate
            ? `Update available for feed ${feedOnestopId}`
            : `Feed ${feedOnestopId} is up to date`;
          this.snackBar.open(message, 'Close', { duration: 3000 });
          this.refreshData();
        },
        error: (error) => {
          console.error('Error checking feed update:', error);
          this.snackBar.open('Failed to check for updates', 'Close', { duration: 3000 });
        }
      });
  }

  getStatusChipClass(status: string): string {
    switch (status) {
      case 'available': return 'chip-success';
      case 'api_unavailable': return 'chip-error';
      case 'error': return 'chip-error';
      case 'not_found': return 'chip-warning';
      default: return 'chip-neutral';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'available': return 'Available';
      case 'api_unavailable': return 'API Unavailable';
      case 'error': return 'Error';
      case 'not_found': return 'Not Found';
      default: return status;
    }
  }

  getImportStatusChipClass(status: ImportStatus): string {
    switch (status) {
      case ImportStatus.COMPLETED: return 'chip-success';
      case ImportStatus.FAILED: return 'chip-error';
      case ImportStatus.RUNNING: return 'chip-warning';
      case ImportStatus.CANCELLED: return 'chip-neutral';
      case ImportStatus.PENDING:
      default: return 'chip-neutral';
    }
  }

  calculateDuration(importRecord: FeedImport): string {
    if (!importRecord.completedAt) {
      return importRecord.status === ImportStatus.RUNNING ? 'Running...' : '-';
    }

    if (!importRecord.startedAt || !importRecord.completedAt) {
      return '-';
    }
    const start = new Date(importRecord.startedAt);
    const end = new Date(importRecord.completedAt);
    const duration = Math.round((end.getTime() - start.getTime()) / 1000);

    if (duration < 60) {
      return `${duration}s`;
    } else if (duration < 3600) {
      return `${Math.round(duration / 60)}m`;
    } else {
      return `${Math.round(duration / 3600)}h`;
    }
  }
}
