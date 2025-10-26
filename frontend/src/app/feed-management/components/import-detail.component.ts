import { Component, OnInit, OnDestroy, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatTableModule } from '@angular/material/table';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { MatListModule } from '@angular/material/list';
import { Observable, Subject, BehaviorSubject } from 'rxjs';
import { takeUntil, switchMap } from 'rxjs/operators';
import {
  ImportDetails,
  ImportMetrics,
  ImportHistoryUtils
} from '../models/import-history.model';
import {
  FeedImport,
  ImportLog,
  ImportProgress,
  ImportStatus,
  TriggerType,
  LogLevel,
  ImportUtils,
  ProgressUtils
} from '../models/import.models';
import { HistoryService } from '../services/history.service';
import { ImportService } from '../services/import.service';

/**
 * Import Detail Component
 *
 * Displays comprehensive information about a specific import including
 * progress, logs, metrics, and detailed status information.
 *
 * Constitutional Compliance:
 * - UX Consistency: Material Design 3 with light/dark mode support
 * - Performance: Lazy loading of log data, efficient data display
 * - Accessibility: ARIA labels, keyboard navigation, screen reader support
 * - Real-time: Live progress updates for active imports
 */
@Component({
  selector: 'app-import-detail',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTabsModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatTooltipModule,
    MatExpansionModule,
    MatTableModule,
    MatMenuModule,
    MatDividerModule,
    MatListModule
  ],
  template: `
    <div class="import-detail-container" *ngIf="importDetails$ | async as details">
      <!-- Header Section -->
      <div class="detail-header">
        <div class="header-content">
          <div class="import-info">
            <h2>
              <mat-icon>description</mat-icon>
              Import Details
            </h2>
            <div class="import-id">
              <span class="label">Import ID:</span>
              <code>{{ details.import.id }}</code>
              <button
                mat-icon-button
                (click)="copyImportId(details.import.id)"
                matTooltip="Copy import ID"
              >
                <mat-icon>content_copy</mat-icon>
              </button>
            </div>
          </div>

          <div class="header-actions">
            <button
              mat-raised-button
              color="primary"
              (click)="refreshDetails()"
              [disabled]="isLoading$ | async"
              matTooltip="Refresh import details"
            >
              <mat-icon>refresh</mat-icon>
              Refresh
            </button>

            <button
              mat-stroked-button
              *ngIf="canRetryImport(details.import)"
              (click)="retryImport(details.import)"
              matTooltip="Retry this import"
            >
              <mat-icon>refresh</mat-icon>
              Retry
            </button>

            <button
              mat-stroked-button
              color="warn"
              *ngIf="canCancelImport(details.import)"
              (click)="cancelImport(details.import)"
              matTooltip="Cancel this import"
            >
              <mat-icon>cancel</mat-icon>
              Cancel
            </button>

            <button
              mat-icon-button
              [matMenuTriggerFor]="moreMenu"
              matTooltip="More actions"
            >
              <mat-icon>more_vert</mat-icon>
            </button>

            <mat-menu #moreMenu="matMenu">
              <button mat-menu-item (click)="downloadLogs(details.import)">
                <mat-icon>download</mat-icon>
                Download Logs
              </button>
              <button mat-menu-item (click)="shareImport(details.import)">
                <mat-icon>share</mat-icon>
                Share Link
              </button>
              <button mat-menu-item (click)="exportMetrics(details.import)">
                <mat-icon>analytics</mat-icon>
                Export Metrics
              </button>
            </mat-menu>

            <button
              mat-icon-button
              (click)="closeDetails()"
              matTooltip="Close details"
            >
              <mat-icon>close</mat-icon>
            </button>
          </div>
        </div>

        <!-- Status and Progress Section -->
        <div class="status-section">
          <div class="status-info">
            <div class="status-chip">
              <mat-chip [ngClass]="getStatusChipClass(details.import.status)">
                <mat-icon>{{ getStatusIcon(details.import.status) }}</mat-icon>
                {{ getStatusDisplayName(details.import.status) }}
              </mat-chip>
            </div>

            <div class="trigger-chip">
              <mat-chip [ngClass]="getTriggerTypeChipClass(details.import.triggerType)">
                <mat-icon>{{ getTriggerTypeIcon(details.import.triggerType) }}</mat-icon>
                {{ getTriggerTypeDisplayName(details.import.triggerType) }}
              </mat-chip>
            </div>

            <div class="timing-info">
              <div class="timing-item">
                <mat-icon>schedule</mat-icon>
                <span>Started: {{ formatTimestamp(details.import.createdAt) }}</span>
              </div>
              <div class="timing-item" *ngIf="details.import.completedAt">
                <mat-icon>done</mat-icon>
                <span>Completed: {{ formatTimestamp(details.import.completedAt) }}</span>
              </div>
              <div class="timing-item" *ngIf="getDuration(details.import)">
                <mat-icon>timer</mat-icon>
                <span>Duration: {{ getDuration(details.import) }}</span>
              </div>
            </div>
          </div>

          <!-- Live Progress Bar (for active imports) -->
          <div class="progress-section" *ngIf="isActiveImport(details.import)">
            <div class="progress-info">
              <span class="progress-label">Import Progress</span>
              <span class="progress-percentage">{{ getProgressPercentage() }}%</span>
            </div>
            <mat-progress-bar
              [value]="getProgressPercentage()"
              [color]="getProgressColor()"
            ></mat-progress-bar>
            <div class="progress-details" *ngIf="currentProgress$ | async as progress">
              <span class="current-step">{{ progress.currentStep }}</span>
              <span class="eta" *ngIf="progress.estimatedTimeRemainingSeconds">
                ETA: {{ formatEstimatedTime(progress.estimatedTimeRemainingSeconds) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Main Content Tabs -->
      <mat-tab-group class="detail-tabs" [selectedIndex]="selectedTabIndex" (selectedTabChange)="onTabChange($event)">
        <!-- Overview Tab -->
        <mat-tab label="Overview">
          <div class="tab-content overview-tab">
            <div class="overview-grid">
              <!-- Feed Information Card -->
              <mat-card class="info-card">
                <mat-card-header>
                  <mat-card-title>
                    <mat-icon>feed</mat-icon>
                    Feed Information
                  </mat-card-title>
                </mat-card-header>
                <mat-card-content>
                  <div class="info-list">
                    <div class="info-item">
                      <span class="label">Feed ID:</span>
                      <code>{{ details.import.feedOnestopId }}</code>
                    </div>
                    <div class="info-item" *ngIf="details.import.versionSha1">
                      <span class="label">Version SHA1:</span>
                      <code>{{ details.import.versionSha1 | slice:0:12 }}...</code>
                    </div>
                    <div class="info-item" *ngIf="details.import.fileSizeBytes">
                      <span class="label">File Size:</span>
                      <span>{{ formatFileSize(details.import.fileSizeBytes) }}</span>
                    </div>
                  </div>
                </mat-card-content>
              </mat-card>

              <!-- Administrator Information Card -->
              <mat-card class="info-card">
                <mat-card-header>
                  <mat-card-title>
                    <mat-icon>person</mat-icon>
                    Administrator
                  </mat-card-title>
                </mat-card-header>
                <mat-card-content>
                  <div class="info-list">
                    <div class="info-item" *ngIf="details.import.administratorUsername">
                      <span class="label">Username:</span>
                      <span>{{ details.import.administratorUsername }}</span>
                    </div>
                    <div class="info-item" *ngIf="details.import.administratorId">
                      <span class="label">Administrator ID:</span>
                      <code>{{ details.import.administratorId }}</code>
                    </div>
                    <div class="info-item" *ngIf="!details.import.administratorUsername">
                      <span class="automatic-import">
                        <mat-icon>smart_toy</mat-icon>
                        Automatic Import
                      </span>
                    </div>
                  </div>
                </mat-card-content>
              </mat-card>

              <!-- Import Metrics Card -->
              <mat-card class="info-card" *ngIf="details.metrics">
                <mat-card-header>
                  <mat-card-title>
                    <mat-icon>analytics</mat-icon>
                    Metrics
                  </mat-card-title>
                </mat-card-header>
                <mat-card-content>
                  <div class="metrics-grid">
                    <div class="metric-item">
                      <span class="metric-value">{{ details.metrics.durationSeconds || 0 }}s</span>
                      <span class="metric-label">Duration</span>
                    </div>
                    <div class="metric-item">
                      <span class="metric-value">{{ details.metrics.errorCount }}</span>
                      <span class="metric-label">Errors</span>
                    </div>
                    <div class="metric-item">
                      <span class="metric-value">{{ details.metrics.warningCount }}</span>
                      <span class="metric-label">Warnings</span>
                    </div>
                    <div class="metric-item">
                      <span class="metric-value">{{ details.metrics.infoCount }}</span>
                      <span class="metric-label">Info Messages</span>
                    </div>
                  </div>
                </mat-card-content>
              </mat-card>

              <!-- Error Information Card (if failed) -->
              <mat-card class="info-card error-card" *ngIf="details.import.errorMessage">
                <mat-card-header>
                  <mat-card-title>
                    <mat-icon color="warn">error</mat-icon>
                    Error Information
                  </mat-card-title>
                </mat-card-header>
                <mat-card-content>
                  <div class="error-message">
                    <pre>{{ details.import.errorMessage }}</pre>
                  </div>
                </mat-card-content>
              </mat-card>
            </div>
          </div>
        </mat-tab>

        <!-- Logs Tab -->
        <mat-tab label="Logs" [badge]="details.logs.length">
          <div class="tab-content logs-tab">
            <div class="logs-header">
              <div class="logs-summary">
                <span class="log-count">{{ details.logs.length }} log entries</span>
                <div class="log-level-filters">
                  <mat-chip-listbox [multiple]="true" [(ngModel)]="selectedLogLevels">
                    <mat-chip-option
                      *ngFor="let level of logLevels"
                      [value]="level"
                      [ngClass]="getLogLevelChipClass(level)"
                    >
                      <mat-icon>{{ getLogLevelIcon(level) }}</mat-icon>
                      {{ getLogLevelDisplayName(level) }}
                      <span class="log-count-badge">{{ getLogCountForLevel(details.logs, level) }}</span>
                    </mat-chip-option>
                  </mat-chip-listbox>
                </div>
              </div>

              <div class="logs-actions">
                <button
                  mat-raised-button
                  (click)="downloadLogs(details.import)"
                  [disabled]="details.logs.length === 0"
                >
                  <mat-icon>download</mat-icon>
                  Download Logs
                </button>
              </div>
            </div>

            <div class="logs-container">
              <div
                *ngFor="let log of getFilteredLogs(details.logs); trackBy: trackByLogId"
                class="log-entry"
                [ngClass]="getLogEntryClass(log.level)"
              >
                <div class="log-header">
                  <div class="log-level">
                    <mat-icon [ngClass]="getLogLevelIconClass(log.level)">
                      {{ getLogLevelIcon(log.level) }}
                    </mat-icon>
                    <span>{{ getLogLevelDisplayName(log.level) }}</span>
                  </div>
                  <div class="log-timestamp">
                    {{ formatTimestamp(log.createdAt) }}
                  </div>
                  <div class="log-component" *ngIf="log.component">
                    <mat-icon>extension</mat-icon>
                    {{ log.component }}
                  </div>
                </div>

                <div class="log-message">
                  <pre>{{ log.message }}</pre>
                </div>

                <div class="log-details" *ngIf="log.details">
                  <mat-expansion-panel>
                    <mat-expansion-panel-header>
                      <mat-panel-title>
                        <mat-icon>code</mat-icon>
                        Additional Details
                      </mat-panel-title>
                    </mat-expansion-panel-header>
                    <pre class="details-content">{{ formatLogDetails(log.details) }}</pre>
                  </mat-expansion-panel>
                </div>
              </div>

              <div *ngIf="getFilteredLogs(details.logs).length === 0" class="no-logs">
                <mat-icon>info</mat-icon>
                <p>No logs match the selected filters.</p>
              </div>
            </div>
          </div>
        </mat-tab>

        <!-- Real-time Progress Tab (for active imports) -->
        <mat-tab label="Live Progress" *ngIf="isActiveImport(details.import)">
          <div class="tab-content progress-tab">
            <div class="progress-monitor" *ngIf="currentProgress$ | async as progress">
              <div class="progress-header">
                <h3>
                  <mat-icon class="spinning">sync</mat-icon>
                  Import in Progress
                </h3>
                <div class="progress-stats">
                  <span class="current-percentage">{{ progress.progressPercentage }}%</span>
                  <span class="step-info">Step {{ getCurrentStepNumber(progress) }} of {{ progress.totalSteps }}</span>
                </div>
              </div>

              <div class="progress-visualization">
                <mat-progress-bar
                  [value]="progress.progressPercentage"
                  [color]="getProgressColor()"
                  mode="determinate"
                ></mat-progress-bar>
              </div>

              <div class="progress-details">
                <div class="current-step">
                  <h4>Current Step:</h4>
                  <p>{{ progress.currentStep }}</p>
                </div>

                <div class="time-estimates" *ngIf="progress.estimatedTimeRemainingSeconds">
                  <div class="estimate-item">
                    <mat-icon>schedule</mat-icon>
                    <span>ETA: {{ formatEstimatedTime(progress.estimatedTimeRemainingSeconds) }}</span>
                  </div>
                </div>
              </div>

              <div class="progress-timeline">
                <h4>Progress Timeline</h4>
                <div class="timeline-steps">
                  <!-- This would show completed and upcoming steps -->
                  <div class="timeline-step completed">
                    <mat-icon>check_circle</mat-icon>
                    <span>Download feed data</span>
                  </div>
                  <div class="timeline-step active">
                    <mat-icon>sync</mat-icon>
                    <span>{{ progress.currentStep }}</span>
                  </div>
                  <div class="timeline-step pending">
                    <mat-icon>radio_button_unchecked</mat-icon>
                    <span>Validation and storage</span>
                  </div>
                </div>
              </div>
            </div>

            <div *ngIf="!(currentProgress$ | async)" class="no-progress">
              <mat-icon>info</mat-icon>
              <p>No live progress data available for this import.</p>
            </div>
          </div>
        </mat-tab>

        <!-- Technical Details Tab -->
        <mat-tab label="Technical">
          <div class="tab-content technical-tab">
            <mat-accordion>
              <mat-expansion-panel>
                <mat-expansion-panel-header>
                  <mat-panel-title>
                    <mat-icon>storage</mat-icon>
                    Database Information
                  </mat-panel-title>
                </mat-expansion-panel-header>
                <div class="technical-content">
                  <div class="tech-info-grid">
                    <div class="tech-item">
                      <span class="label">Import ID:</span>
                      <code>{{ details.import.id }}</code>
                    </div>
                    <div class="tech-item">
                      <span class="label">Created At:</span>
                      <span>{{ details.import.createdAt }}</span>
                    </div>
                    <div class="tech-item">
                      <span class="label">Updated At:</span>
                      <span>{{ details.import.updatedAt }}</span>
                    </div>
                  </div>
                </div>
              </mat-expansion-panel>

              <mat-expansion-panel *ngIf="details.import.versionSha1">
                <mat-expansion-panel-header>
                  <mat-panel-title>
                    <mat-icon>fingerprint</mat-icon>
                    Version Information
                  </mat-panel-title>
                </mat-expansion-panel-header>
                <div class="technical-content">
                  <div class="tech-info-grid">
                    <div class="tech-item">
                      <span class="label">SHA1 Hash:</span>
                      <code class="full-hash">{{ details.import.versionSha1 }}</code>
                    </div>
                  </div>
                </div>
              </mat-expansion-panel>

              <mat-expansion-panel *ngIf="details.metrics">
                <mat-expansion-panel-header>
                  <mat-panel-title>
                    <mat-icon>analytics</mat-icon>
                    Performance Metrics
                  </mat-panel-title>
                </mat-expansion-panel-header>
                <div class="technical-content">
                  <div class="metrics-table">
                    <table mat-table [dataSource]="getMetricsTableData(details.metrics)">
                      <ng-container matColumnDef="metric">
                        <th mat-header-cell *matHeaderCellDef>Metric</th>
                        <td mat-cell *matCellDef="let element">{{ element.metric }}</td>
                      </ng-container>

                      <ng-container matColumnDef="value">
                        <th mat-header-cell *matHeaderCellDef>Value</th>
                        <td mat-cell *matCellDef="let element">{{ element.value }}</td>
                      </ng-container>

                      <tr mat-header-row *matHeaderRowDef="['metric', 'value']"></tr>
                      <tr mat-row *matRowDef="let row; columns: ['metric', 'value'];"></tr>
                    </table>
                  </div>
                </div>
              </mat-expansion-panel>
            </mat-accordion>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>

    <!-- Loading State -->
    <div *ngIf="isLoading$ | async" class="loading-container">
      <mat-spinner diameter="40"></mat-spinner>
      <p>Loading import details...</p>
    </div>

    <!-- Error State -->
    <div *ngIf="error$ | async as error" class="error-container">
      <mat-icon color="warn">error</mat-icon>
      <p>{{ error }}</p>
      <button mat-raised-button color="primary" (click)="refreshDetails()">
        <mat-icon>refresh</mat-icon>
        Retry
      </button>
    </div>
  `,
  styles: [`
    .import-detail-container {
      padding: 16px;
      max-width: 1200px;
      margin: 0 auto;
    }

    .detail-header {
      margin-bottom: 24px;
    }

    .header-content {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
      margin-bottom: 16px;
    }

    .import-info h2 {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 0 8px 0;
    }

    .import-id {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
    }

    .import-id .label {
      color: var(--mdc-theme-on-surface-variant);
    }

    .import-id code {
      background-color: var(--mdc-theme-surface-variant);
      padding: 4px 8px;
      border-radius: 4px;
      font-family: 'Roboto Mono', monospace;
    }

    .header-actions {
      display: flex;
      gap: 8px;
      align-items: center;
    }

    .status-section {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 16px;
      background-color: var(--mdc-theme-surface-variant);
      border-radius: 8px;
    }

    .status-info {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      align-items: center;
    }

    .status-chip, .trigger-chip {
      display: flex;
      align-items: center;
    }

    .timing-info {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      margin-left: auto;
    }

    .timing-item {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: var(--mdc-theme-on-surface-variant);
    }

    .progress-section {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .progress-info {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .progress-details {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 12px;
      color: var(--mdc-theme-on-surface-variant);
    }

    .detail-tabs {
      background-color: var(--mdc-theme-surface);
      border-radius: 8px;
      overflow: hidden;
    }

    .tab-content {
      padding: 24px;
    }

    .overview-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: 16px;
    }

    .info-card {
      height: fit-content;
    }

    .info-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .info-item {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .info-item .label {
      min-width: 80px;
      color: var(--mdc-theme-on-surface-variant);
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .automatic-import {
      display: flex;
      align-items: center;
      gap: 4px;
      color: var(--mdc-theme-on-surface-variant);
      font-style: italic;
    }

    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 16px;
    }

    .metric-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
    }

    .metric-value {
      font-size: 24px;
      font-weight: 600;
      color: var(--mdc-theme-primary);
    }

    .metric-label {
      font-size: 11px;
      color: var(--mdc-theme-on-surface-variant);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .error-card {
      border-left: 4px solid var(--mdc-theme-error);
    }

    .error-message pre {
      font-family: 'Roboto Mono', monospace;
      font-size: 12px;
      white-space: pre-wrap;
      margin: 0;
      color: var(--mdc-theme-error);
    }

    .logs-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
      margin-bottom: 24px;
    }

    .logs-summary {
      flex: 1;
    }

    .log-count {
      font-size: 14px;
      color: var(--mdc-theme-on-surface-variant);
      margin-bottom: 12px;
      display: block;
    }

    .log-level-filters {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .log-count-badge {
      background-color: var(--mdc-theme-surface-variant);
      padding: 2px 6px;
      border-radius: 12px;
      font-size: 10px;
      margin-left: 4px;
    }

    .logs-container {
      display: flex;
      flex-direction: column;
      gap: 12px;
      max-height: 600px;
      overflow-y: auto;
    }

    .log-entry {
      background-color: var(--mdc-theme-surface-variant);
      border-radius: 8px;
      padding: 12px;
      border-left: 4px solid transparent;
    }

    .log-entry.error {
      border-left-color: var(--mdc-theme-error);
    }

    .log-entry.warn {
      border-left-color: var(--mdc-theme-tertiary);
    }

    .log-entry.info {
      border-left-color: var(--mdc-theme-primary);
    }

    .log-header {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 8px;
    }

    .log-level {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      font-weight: 500;
    }

    .log-timestamp {
      font-size: 11px;
      color: var(--mdc-theme-on-surface-variant);
      font-family: 'Roboto Mono', monospace;
    }

    .log-component {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 11px;
      color: var(--mdc-theme-outline);
    }

    .log-message pre {
      font-family: 'Roboto Mono', monospace;
      font-size: 12px;
      white-space: pre-wrap;
      margin: 0;
      line-height: 1.4;
    }

    .details-content {
      font-family: 'Roboto Mono', monospace;
      font-size: 11px;
      white-space: pre-wrap;
      margin: 0;
      background-color: var(--mdc-theme-surface);
      padding: 12px;
      border-radius: 4px;
    }

    .no-logs {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 48px 16px;
      text-align: center;
      color: var(--mdc-theme-on-surface-variant);
    }

    .progress-monitor {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .progress-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .progress-header h3 {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
    }

    .spinning {
      animation: spin 2s linear infinite;
    }

    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    .progress-stats {
      display: flex;
      gap: 16px;
      align-items: center;
      font-size: 14px;
    }

    .current-percentage {
      font-size: 18px;
      font-weight: 600;
      color: var(--mdc-theme-primary);
    }

    .progress-visualization {
      margin: 16px 0;
    }

    .progress-details {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .current-step h4 {
      margin: 0 0 8px 0;
      color: var(--mdc-theme-on-surface-variant);
    }

    .time-estimates {
      display: flex;
      gap: 16px;
    }

    .estimate-item {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: var(--mdc-theme-on-surface-variant);
    }

    .progress-timeline h4 {
      margin: 0 0 16px 0;
      color: var(--mdc-theme-on-surface-variant);
    }

    .timeline-steps {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .timeline-step {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px;
      border-radius: 4px;
    }

    .timeline-step.completed {
      background-color: var(--mdc-theme-tertiary-container);
      color: var(--mdc-theme-on-tertiary-container);
    }

    .timeline-step.active {
      background-color: var(--mdc-theme-primary-container);
      color: var(--mdc-theme-on-primary-container);
    }

    .timeline-step.pending {
      background-color: var(--mdc-theme-surface-variant);
      color: var(--mdc-theme-on-surface-variant);
    }

    .technical-content {
      padding: 16px 0;
    }

    .tech-info-grid {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .tech-item {
      display: flex;
      gap: 16px;
      align-items: center;
    }

    .tech-item .label {
      min-width: 120px;
      color: var(--mdc-theme-on-surface-variant);
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .full-hash {
      font-family: 'Roboto Mono', monospace;
      font-size: 11px;
      word-break: break-all;
      background-color: var(--mdc-theme-surface-variant);
      padding: 4px 8px;
      border-radius: 4px;
    }

    .metrics-table {
      width: 100%;
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

    /* Status and log level specific styles */
    .status-completed { background-color: var(--mdc-theme-tertiary-container); }
    .status-failed { background-color: var(--mdc-theme-error-container); }
    .status-running { background-color: var(--mdc-theme-secondary-container); }
    .status-pending { background-color: var(--mdc-theme-primary-container); }
    .status-cancelled { background-color: var(--mdc-theme-outline-variant); }

    .trigger-automatic { background-color: var(--mdc-theme-tertiary-container); }
    .trigger-manual { background-color: var(--mdc-theme-secondary-container); }

    .log-level-error { color: var(--mdc-theme-error); }
    .log-level-warn { color: var(--mdc-theme-tertiary); }
    .log-level-info { color: var(--mdc-theme-primary); }

    /* Responsive Design */
    @media (max-width: 768px) {
      .import-detail-container {
        padding: 12px;
      }

      .header-content {
        flex-direction: column;
        align-items: stretch;
      }

      .overview-grid {
        grid-template-columns: 1fr;
      }

      .status-info {
        flex-direction: column;
        align-items: flex-start;
      }

      .timing-info {
        margin-left: 0;
        flex-direction: column;
        align-items: flex-start;
      }

      .logs-header {
        flex-direction: column;
        align-items: stretch;
      }
    }
  `]
})
export class ImportDetailComponent implements OnInit, OnDestroy {
  @Input() importId!: string;
  @Output() closed = new EventEmitter<void>();
  @Output() importRetried = new EventEmitter<FeedImport>();
  @Output() importCancelled = new EventEmitter<FeedImport>();

  private destroy$ = new Subject<void>();

  // Data streams
  importDetails$ = new BehaviorSubject<ImportDetails | null>(null);
  currentProgress$ = new BehaviorSubject<ImportProgress | null>(null);
  isLoading$ = new BehaviorSubject<boolean>(true);
  error$ = new BehaviorSubject<string | null>(null);

  // UI state
  selectedTabIndex = 0;
  selectedLogLevels: LogLevel[] = Object.values(LogLevel);
  logLevels = Object.values(LogLevel);

  constructor(
    private historyService: HistoryService,
    private importService: ImportService
  ) {}

  ngOnInit(): void {
    if (this.importId) {
      this.loadImportDetails();
      this.startProgressMonitoring();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadImportDetails(): void {
    this.isLoading$.next(true);
    this.error$.next(null);

    this.historyService.getImportDetails(this.importId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (details) => {
        this.importDetails$.next(details);
        this.isLoading$.next(false);

        // Start progress monitoring if import is active
        if (this.isActiveImport(details.import)) {
          this.startProgressMonitoring();
        }
      },
      error: (error) => {
        console.error('Failed to load import details:', error);
        this.error$.next('Failed to load import details. Please try again.');
        this.isLoading$.next(false);
      }
    });
  }

  private startProgressMonitoring(): void {
    const details = this.importDetails$.value;
    if (!details || !this.isActiveImport(details.import)) {
      return;
    }

    this.importService.monitorImportProgress(this.importId, this.destroy$).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (progress) => {
        this.currentProgress$.next(progress);
      },
      error: (error) => {
        console.error('Progress monitoring failed:', error);
      }
    });
  }

  // Public methods for template
  refreshDetails(): void {
    this.loadImportDetails();
  }

  closeDetails(): void {
    this.closed.emit();
  }

  retryImport(import_: FeedImport): void {
    this.importRetried.emit(import_);
  }

  cancelImport(import_: FeedImport): void {
    this.importCancelled.emit(import_);
  }

  copyImportId(importId: string): void {
    navigator.clipboard.writeText(importId);
  }

  downloadLogs(import_: FeedImport): void {
    const details = this.importDetails$.value;
    if (!details) return;

    const logsText = details.logs.map(log =>
      `[${log.createdAt}] ${log.level.toUpperCase()}: ${log.message}`
    ).join('\n');

    const blob = new Blob([logsText], { type: 'text/plain' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `import-${import_.id}-logs.txt`;
    a.click();
    window.URL.revokeObjectURL(url);
  }

  shareImport(import_: FeedImport): void {
    const url = `${window.location.origin}/import-history/${import_.id}`;
    navigator.clipboard.writeText(url);
  }

  exportMetrics(import_: FeedImport): void {
    const details = this.importDetails$.value;
    if (!details?.metrics) return;

    const metricsData = {
      importId: import_.id,
      feedOnestopId: import_.feedOnestopId,
      status: import_.status,
      metrics: details.metrics,
      timestamp: new Date().toISOString()
    };

    const blob = new Blob([JSON.stringify(metricsData, null, 2)], { type: 'application/json' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `import-${import_.id}-metrics.json`;
    a.click();
    window.URL.revokeObjectURL(url);
  }

  onTabChange(event: any): void {
    this.selectedTabIndex = event.index;
  }

  getFilteredLogs(logs: ImportLog[]): ImportLog[] {
    return logs.filter(log => this.selectedLogLevels.includes(log.level));
  }

  trackByLogId(index: number, log: ImportLog): string {
    return log.id;
  }

  getMetricsTableData(metrics: ImportMetrics): { metric: string; value: string }[] {
    return [
      { metric: 'Duration', value: `${metrics.durationSeconds || 0} seconds` },
      { metric: 'Error Count', value: metrics.errorCount.toString() },
      { metric: 'Warning Count', value: metrics.warningCount.toString() },
      { metric: 'Info Count', value: metrics.infoCount.toString() }
    ];
  }

  // Utility methods
  isActiveImport(import_: FeedImport): boolean {
    return ImportUtils.isActive(import_);
  }

  canRetryImport(import_: FeedImport): boolean {
    return import_.status === ImportStatus.FAILED;
  }

  canCancelImport(import_: FeedImport): boolean {
    return ImportUtils.isCancellable(import_);
  }

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

  getLogLevelDisplayName(level: LogLevel): string {
    return ImportUtils.getLogLevelDisplayName(level);
  }

  getLogLevelChipClass(level: LogLevel): string {
    return `log-level-${level.toLowerCase()}`;
  }

  getLogLevelIconClass(level: LogLevel): string {
    return `log-level-${level.toLowerCase()}`;
  }

  getLogEntryClass(level: LogLevel): string {
    return level.toLowerCase();
  }

  getLogLevelIcon(level: LogLevel): string {
    switch (level) {
      case LogLevel.ERROR: return 'error';
      case LogLevel.WARN: return 'warning';
      case LogLevel.INFO: return 'info';
      default: return 'help';
    }
  }

  getLogCountForLevel(logs: ImportLog[], level: LogLevel): number {
    return logs.filter(log => log.level === level).length;
  }

  formatLogDetails(details: Record<string, any>): string {
    return JSON.stringify(details, null, 2);
  }

  formatTimestamp(timestamp: string): string {
    return ImportUtils.formatTimestamp(timestamp);
  }

  formatFileSize(bytes: number): string {
    return ImportUtils.formatFileSize(bytes);
  }

  getDuration(import_: FeedImport): string | null {
    return ImportUtils.getDuration(import_);
  }

  getProgressPercentage(): number {
    const progress = this.currentProgress$.value;
    return ProgressUtils.getProgressPercentage(progress);
  }

  getProgressColor(): 'primary' | 'accent' | 'warn' {
    const progress = this.currentProgress$.value;
    if (ProgressUtils.isProgressFailed(progress)) return 'warn';
    return 'primary';
  }

  getCurrentStepNumber(progress: ImportProgress): number {
    // This would be calculated based on the current step
    return 1; // Placeholder
  }

  formatEstimatedTime(seconds: number): string {
    return ImportUtils.formatEstimatedTimeRemaining(seconds);
  }
}