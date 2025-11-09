import { Component, Inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { Observable, Subject, timer, of } from 'rxjs';
import { takeUntil, switchMap, finalize, catchError } from 'rxjs/operators';
import { ImportService } from '../services/import.service';
import { WebSocketService } from '../services/websocket.service';
import { FeedImport, ImportProgress, ImportStatus } from '../models/import.models';
import { MobilispectCardComponent } from '../../core/components/mobilispect-card.component';

export interface ImportProgressDialogData {
  feedOnestopId: string;
  feedName: string;
  importResult: FeedImport;
}

@Component({
  selector: 'app-import-progress-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatDividerModule,
    MobilispectCardComponent
  ],
  template: `
    <div class="import-progress-dialog import-dialog">
      <h2 mat-dialog-title>
        <mat-icon>{{ getStatusIcon() }}</mat-icon>
        Importing {{ data.feedName }}
      </h2>

      <mat-dialog-content class="dialog-content">
        <!-- Import Status Card -->
        <app-mobilispect-card class="status-card">
          <div card-header>
            <div class="status-header">
              <div class="status-info">
                <h3>{{ getStatusText() }}</h3>
                <p class="import-id">Import ID: {{ data.importResult.id }}</p>
                @if (connectionStatus$ | async; as status) {
                  <div class="connection-status">
                    <mat-icon [class]="'connection-icon ' + status.toLowerCase()">
                      {{ getConnectionIcon(status) }}
                    </mat-icon>
                    <span class="connection-text">{{ getConnectionText(status) }}</span>
                  </div>
                }
              </div>
              <div class="status-indicator">
                @if (isInProgress) {
                  <mat-spinner diameter="40"></mat-spinner>
                } @else {
                  <mat-icon [class]="getStatusClass()">
                    {{ getStatusIcon() }}
                  </mat-icon>
                }
              </div>
            </div>
          </div>

          <div card-content>
            <!-- Progress Bar (shown during active import) -->
            @if (currentProgress && isInProgress) {
              <div class="progress-section">
                <mat-progress-bar
                  mode="determinate"
                  [value]="currentProgress.progressPercentage">
                </mat-progress-bar>
                <div class="progress-details">
                  <span class="progress-text">
                    {{ currentProgress.progressPercentage }}% - {{ currentProgress.currentStep }}
                  </span>
                  @if (currentProgress.estimatedTimeRemainingSeconds) {
                    <span class="progress-eta">
                      ETA: {{ formatDuration(currentProgress.estimatedTimeRemainingSeconds) }}
                    </span>
                  }
                </div>
              </div>
            }

            <!-- Import Details -->
            <div class="import-details">
              <div class="detail-row">
                <mat-icon>schedule</mat-icon>
                <span>Started: {{ data.importResult.startedAt | date:'medium' }}</span>
              </div>
              @if (data.importResult.completedAt) {
                <div class="detail-row">
                  <mat-icon>check_circle</mat-icon>
                  <span>Completed: {{ data.importResult.completedAt | date:'medium' }}</span>
                </div>
              }
              @if (data.importResult.fileSizeBytes) {
                <div class="detail-row">
                  <mat-icon>storage</mat-icon>
                  <span>File Size: {{ formatFileSize(data.importResult.fileSizeBytes) }}</span>
                </div>
              }
              @if (data.importResult.errorMessage) {
                <div class="detail-row">
                  <mat-icon class="error-icon">error</mat-icon>
                  <span class="error-text">{{ data.importResult.errorMessage }}</span>
                </div>
              }
            </div>

            <!-- Current Step Details -->
            @if (currentProgress && isInProgress) {
              <div class="step-details">
                <h4>Current Activity</h4>
                <div class="activity-log" #activityLog>
                  @for (log of activityLogs; track log.timestamp) {
                    <div class="log-entry">
                      <span class="log-timestamp">{{ log.timestamp | date:'HH:mm:ss' }}</span>
                      <span class="log-message">{{ log.message }}</span>
                    </div>
                  }
                </div>
              </div>
            }
          </div>
        </app-mobilispect-card>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        @if (canCancel()) {
          <button
            mat-button
            (click)="cancelImport()"
            [disabled]="cancelling">
            <mat-icon>cancel</mat-icon>
            {{ cancelling ? 'Cancelling...' : 'Cancel Import' }}
          </button>
        }
        @if (canRetry()) {
          <button
            mat-button
            (click)="retryImport()"
            [disabled]="retrying">
            <mat-icon>refresh</mat-icon>
            {{ retrying ? 'Starting...' : 'Retry' }}
          </button>
        }
        <button
          mat-raised-button
          color="primary"
          (click)="close()"
          [disabled]="isInProgress && !canCancel()">
          {{ isInProgress ? 'Close' : 'Done' }}
        </button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .import-progress-dialog {
      width: 500px;
      max-width: 90vw;
    }

    .dialog-content {
      padding: 0;
      margin: 0;
      min-height: 300px;
    }

    .status-card {
      margin: 0;
      box-shadow: none;
    }

    .status-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }

    .status-info h3 {
      margin: 0 0 4px 0;
      font-size: 18px;
    }

    .import-id {
      margin: 0;
      font-size: 12px;
      color: #666;
      font-family: monospace;
    }

    .connection-status {
      display: flex;
      align-items: center;
      gap: 4px;
      margin-top: 4px;
      font-size: 11px;
    }

    .connection-icon {
      font-size: 14px;
      width: 14px;
      height: 14px;
    }

    .connection-icon.connected {
      color: #4caf50;
    }

    .connection-icon.connecting {
      color: #ff9800;
    }

    .connection-icon.disconnected {
      color: #666;
    }

    .connection-icon.error {
      color: #f44336;
    }

    .connection-text {
      color: #666;
    }

    .status-indicator {
      display: flex;
      align-items: center;
    }

    .status-indicator mat-icon {
      font-size: 40px;
      width: 40px;
      height: 40px;
    }

    .status-indicator mat-icon.success {
      color: #4caf50;
    }

    .status-indicator mat-icon.error {
      color: #f44336;
    }

    .status-indicator mat-icon.warning {
      color: #ff9800;
    }

    .progress-section {
      margin: 16px 0;
    }

    .progress-details {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 8px;
      font-size: 14px;
    }

    .progress-eta {
      color: #666;
    }

    .import-details {
      margin: 16px 0;
    }

    .detail-row {
      display: flex;
      align-items: center;
      margin: 8px 0;
      gap: 8px;
    }

    .detail-row mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      color: #666;
    }

    .error-icon {
      color: #f44336 !important;
    }

    .error-text {
      color: #f44336;
      font-weight: 500;
    }

    .step-details {
      margin-top: 16px;
    }

    .step-details h4 {
      margin: 0 0 8px 0;
      font-size: 14px;
      font-weight: 500;
    }

    .activity-log {
      max-height: 150px;
      overflow-y: auto;
      background: #f5f5f5;
      border-radius: 4px;
      padding: 8px;
      font-family: monospace;
      font-size: 12px;
    }

    .log-entry {
      display: flex;
      gap: 8px;
      margin: 2px 0;
    }

    .log-timestamp {
      color: #666;
      min-width: 60px;
    }

    .log-message {
      flex: 1;
    }

    mat-dialog-actions {
      padding: 16px 24px;
      gap: 8px;
    }

    h2[mat-dialog-title] {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 0 16px 0;
      padding: 24px 24px 0 24px;
    }

    h2[mat-dialog-title] mat-icon {
      font-size: 24px;
      width: 24px;
      height: 24px;
    }
  `]
})
export class ImportProgressDialogComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  currentProgress: ImportProgress | null = null;
  isInProgress = false;
  cancelling = false;
  retrying = false;
  activityLogs: Array<{ timestamp: Date; message: string }> = [];
  connectionStatus$!: Observable<string>;

  constructor(
    private dialogRef: MatDialogRef<ImportProgressDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ImportProgressDialogData,
    private importService: ImportService,
    private webSocketService: WebSocketService
  ) {
    this.connectionStatus$ = this.webSocketService.getConnectionStatus();
  }

  ngOnInit() {
    this.initializeStatus();
    this.startProgressMonitoring();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeStatus() {
    const status = this.data.importResult.status;
    this.isInProgress = status === ImportStatus.PENDING || status === ImportStatus.RUNNING;

    this.addActivityLog(`Import ${this.data.importResult.id} ${status.toLowerCase()}`);

    if (this.data.importResult.startedAt) {
      this.addActivityLog('Feed download started');
    }
  }

  private startProgressMonitoring() {
    if (!this.isInProgress) return;

    // Initialize WebSocket connection for real-time updates
    this.importService.initializeWebSocket();

    // Monitor progress with hybrid WebSocket + HTTP approach
    this.importService.monitorImportProgress(this.data.importResult.id, this.destroy$).pipe(
      takeUntil(this.destroy$),
      finalize(() => {
        this.isInProgress = false;
        // Unsubscribe from WebSocket updates for this import
        this.importService.disconnectWebSocket();
      })
    ).subscribe({
      next: (progress) => {
        if (progress) {
          this.updateProgress(progress);
        }
      },
      error: (error) => {
        console.error('Progress monitoring failed:', error);
        this.addActivityLog('Error monitoring progress - check connection');
      }
    });

    // Monitor import status changes with hybrid approach
    this.importService.monitorImportStatus(this.data.importResult.id, this.destroy$).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (importDetail) => {
        if (importDetail) {
          this.updateImportStatus(importDetail);
        }
      },
      error: (error) => {
        console.error('Status monitoring failed:', error);
        this.addActivityLog('Error monitoring status - check connection');
      }
    });
  }

  private updateProgress(progress: ImportProgress) {
    const previousStep = this.currentProgress?.currentStep;
    this.currentProgress = progress;

    // Add activity logs for step changes
    if (progress.currentStep && progress.currentStep !== previousStep) {
      this.addActivityLog(progress.currentStep);
    }
  }

  private updateImportStatus(importDetail: any) {
    if (importDetail.status !== this.data.importResult.status) {
      this.data.importResult = { ...this.data.importResult, ...importDetail };
      this.isInProgress = importDetail.status === ImportStatus.PENDING || importDetail.status === ImportStatus.RUNNING;

      if (importDetail.completedAt) {
        this.addActivityLog(`Import completed successfully`);
      } else if (importDetail.errorMessage) {
        this.addActivityLog(`Import failed: ${importDetail.errorMessage}`);
      }
    }
  }

  private addActivityLog(message: string) {
    this.activityLogs.push({
      timestamp: new Date(),
      message: message
    });

    // Keep only last 20 entries
    if (this.activityLogs.length > 20) {
      this.activityLogs = this.activityLogs.slice(-20);
    }

    // Auto-scroll to bottom
    setTimeout(() => {
      const logElement = document.querySelector('.activity-log');
      if (logElement) {
        logElement.scrollTop = logElement.scrollHeight;
      }
    }, 100);
  }

  getStatusText(): string {
    switch (this.data.importResult.status) {
      case ImportStatus.PENDING: return 'Preparing import...';
      case ImportStatus.RUNNING: return 'Import in progress...';
      case ImportStatus.COMPLETED: return 'Import completed successfully';
      case ImportStatus.FAILED: return 'Import failed';
      case ImportStatus.CANCELLED: return 'Import cancelled';
      default: return 'Unknown status';
    }
  }

  getStatusIcon(): string {
    switch (this.data.importResult.status) {
      case ImportStatus.PENDING: return 'hourglass_empty';
      case ImportStatus.RUNNING: return 'sync';
      case ImportStatus.COMPLETED: return 'check_circle';
      case ImportStatus.FAILED: return 'error';
      case ImportStatus.CANCELLED: return 'cancel';
      default: return 'help';
    }
  }

  getStatusClass(): string {
    switch (this.data.importResult.status) {
      case ImportStatus.COMPLETED: return 'success';
      case ImportStatus.FAILED: return 'error';
      case ImportStatus.CANCELLED: return 'warning';
      default: return '';
    }
  }

  canCancel(): boolean {
    return this.isInProgress && !this.cancelling;
  }

  canRetry(): boolean {
    return this.data.importResult.status === ImportStatus.FAILED && !this.retrying;
  }

  cancelImport() {
    this.cancelling = true;
    this.addActivityLog('Cancelling import...');

    this.importService.cancelImport(this.data.importResult.id).pipe(
      takeUntil(this.destroy$),
      finalize(() => {
        this.cancelling = false;
      })
    ).subscribe({
      next: (result) => {
        this.data.importResult = result;
        this.isInProgress = false;
        this.addActivityLog('Import cancelled successfully');
      },
      error: (error) => {
        console.error('Failed to cancel import:', error);
        this.addActivityLog('Failed to cancel import');
      }
    });
  }

  retryImport() {
    this.retrying = true;
    this.addActivityLog('Starting retry...');

    this.importService.retryImport(this.data.importResult.id).pipe(
      takeUntil(this.destroy$),
      finalize(() => {
        this.retrying = false;
      })
    ).subscribe({
      next: (result) => {
        this.data.importResult = result;
        this.isInProgress = true;
        this.addActivityLog('Retry started successfully');
        this.startProgressMonitoring();
      },
      error: (error) => {
        console.error('Failed to retry import:', error);
        this.addActivityLog('Failed to start retry');
      }
    });
  }

  formatDuration(seconds: number): string {
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}m ${remainingSeconds}s`;
  }

  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
  }

  getConnectionIcon(status: string): string {
    switch (status) {
      case 'CONNECTED': return 'wifi';
      case 'CONNECTING': return 'wifi_tethering';
      case 'DISCONNECTED': return 'wifi_off';
      case 'ERROR': return 'wifi_tethering_error';
      default: return 'help';
    }
  }

  getConnectionText(status: string): string {
    switch (status) {
      case 'CONNECTED': return 'Real-time updates';
      case 'CONNECTING': return 'Connecting...';
      case 'DISCONNECTED': return 'Offline mode';
      case 'ERROR': return 'Connection error';
      default: return 'Unknown';
    }
  }

  close() {
    this.dialogRef.close(this.data.importResult);
  }
}
