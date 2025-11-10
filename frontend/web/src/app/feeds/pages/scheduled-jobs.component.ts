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
import { Observable, Subject, timer, combineLatest } from 'rxjs';
import { takeUntil, switchMap, startWith, share, map } from 'rxjs/operators';
import { SchedulerService } from '../services/scheduler.service';
import { ImportService } from '../services/import.service';
import {
  SchedulerStatus,
  ImportStats,
  FeedVersionInfo,
  ManualCheckResult
} from '../models/scheduler.model';
import { FeedImport, ImportStatus, TriggerType } from '../models/import.models';
import { MobilispectCardComponent } from '../../core/components/mobilispect-card.component';

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
    MobilispectCardComponent
  ],
  template: `
    <div class="scheduled-jobs-page">
      <div class="page-header">
        <h1>Scheduled Jobs Monitoring</h1>
        <p class="subtitle">Monitor automatic feed update jobs and their execution status</p>

        <div class="header-actions">
          <button
            mat-raised-button
            color="primary"
            (click)="triggerManualCheck()"
            [disabled]="triggering">
            <mat-icon>refresh</mat-icon>
            {{ triggering ? 'Checking...' : 'Trigger Manual Check' }}
          </button>

          <button
            mat-stroked-button
            (click)="refreshData()">
            <mat-icon>sync</mat-icon>
            Refresh
          </button>
        </div>
      </div>

      <!-- Status Overview -->
      <div class="status-overview">
        @if (schedulerStatus$ | async; as status) {
          <app-mobilispect-card>
            <div card-header>
              <div card-title>Scheduler Status</div>
            </div>
            <div card-content>
              <div class="status-grid">
                <div class="status-item">
                  <mat-icon [class]="status.enabled ? 'status-enabled' : 'status-disabled'">
                    {{ status.enabled ? 'check_circle' : 'cancel' }}
                  </mat-icon>
                  <div class="status-info">
                    <span class="label">Scheduler</span>
                    <span class="value" [class]="status.enabled ? 'enabled' : 'disabled'">
                      {{ status.enabled ? 'Enabled' : 'Disabled' }}
                    </span>
                  </div>
                </div>

                <div class="status-item">
                  <mat-icon class="info">schedule</mat-icon>
                  <div class="status-info">
                    <span class="label">Next Run</span>
                    <span class="value">{{ status.nextScheduledRun }}</span>
                  </div>
                </div>

                <div class="status-item">
                  <mat-icon class="info">feed</mat-icon>
                  <div class="status-info">
                    <span class="label">Active Feeds</span>
                    <span class="value">{{ status.totalActiveFeeds }}</span>
                  </div>
                </div>

                <div class="status-item">
                  <mat-icon class="info">update</mat-icon>
                  <div class="status-info">
                    <span class="label">Checked (24h)</span>
                    <span class="value">{{ status.feedsCheckedInLast24Hours }}</span>
                  </div>
                </div>
              </div>
            </div>
          </app-mobilispect-card>
        }
      </div>

      <!-- Import Statistics -->
      <div class="import-stats">
        @if (importStats$ | async; as stats) {
          <app-mobilispect-card>
            <div card-header>
              <div card-title>Import Activity (Last 24 Hours)</div>
            </div>
            <div card-content>
              <div class="stats-grid">
                <div class="stat-card success">
                  <div class="stat-number">{{ stats.successfulImportsLast24h }}</div>
                  <div class="stat-label">Successful</div>
                  <mat-icon>check_circle</mat-icon>
                </div>

                <div class="stat-card failed">
                  <div class="stat-number">{{ stats.failedImportsLast24h }}</div>
                  <div class="stat-label">Failed</div>
                  <mat-icon>error</mat-icon>
                </div>

                <div class="stat-card running">
                  <div class="stat-number">{{ stats.currentlyRunningAutoImports }}</div>
                  <div class="stat-label">Running</div>
                  <mat-icon>sync</mat-icon>
                </div>

                <div class="stat-card total">
                  <div class="stat-number">{{ stats.totalAutomaticImportsLast24h }}</div>
                  <div class="stat-label">Total</div>
                  <mat-icon>analytics</mat-icon>
                </div>
              </div>

              @if (stats.lastAutomaticImportTime) {
                <div class="last-import">
                  <mat-icon>schedule</mat-icon>
                  <span>Last automatic import: {{ stats.lastAutomaticImportTime | date:'medium' }}</span>
                </div>
              }
            </div>
          </app-mobilispect-card>
        }
      </div>

      <!-- Feed Version Status -->
      <div class="feed-versions">
        <app-mobilispect-card>
          <div card-header>
            <div card-title>Feed Version Status</div>
            <div card-subtitle>Current version status for all monitored feeds</div>
          </div>
          <div card-content>
            <div class="table-container">
              <table mat-table [dataSource]="(feedVersions$ | async) || []" class="feed-versions-table">
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
                    <code class="sha1">{{ version.currentVersionSha1 || 'N/A' }}</code>
                  </td>
                </ng-container>

                <!-- Latest Version Column -->
                <ng-container matColumnDef="latestVersion">
                  <th mat-header-cell *matHeaderCellDef>Latest SHA1</th>
                  <td mat-cell *matCellDef="let version">
                    <code class="sha1">{{ version.latestVersionSha1 || 'N/A' }}</code>
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
          </div>
        </app-mobilispect-card>
      </div>

      <!-- Recent Automatic Imports -->
      <div class="recent-imports">
        <app-mobilispect-card>
          <div card-header>
            <div card-title>Recent Automatic Imports</div>
            <div card-subtitle>Last 20 automatic import operations</div>
          </div>
          <div card-content>
            <div class="table-container">
              <table mat-table [dataSource]="(recentImports$ | async) || []" class="imports-table">
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
          </div>
        </app-mobilispect-card>
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
