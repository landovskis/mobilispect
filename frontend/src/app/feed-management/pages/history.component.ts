import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatBadgeModule } from '@angular/material/badge';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatChipsModule } from '@angular/material/chips';
import { Observable, Subject, BehaviorSubject, combineLatest } from 'rxjs';
import { map, takeUntil, switchMap } from 'rxjs/operators';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import {
  ImportStatistics,
  ImportDashboardSummary,
  FailureAnalysis,
  ImportHistoryChartData,
  ImportHistoryUtils,
  ImportPeriod
} from '../models/import-history.model';
import { FeedImport, ImportStatus } from '../models/import.models';
import { ImportHistoryComponent } from '../components/import-history.component';
import { ImportDetailComponent } from '../components/import-detail.component';
import { HistoryService } from '../services/history.service';
import { ImportService } from '../services/import.service';

/**
 * History Page Component
 *
 * Comprehensive import history page with dashboard analytics,
 * detailed history table, and import detail views.
 *
 * Constitutional Compliance:
 * - UX Consistency: Material Design 3 with responsive layout patterns
 * - Cross-Platform: Mobile-first responsive design with adaptive layouts
 * - Accessibility: Full keyboard navigation and screen reader support
 * - Performance: Efficient data loading with virtualization and pagination
 * - Analytics: Comprehensive historical data analysis and visualization
 */
@Component({
  selector: 'app-history',
  standalone: true,
  imports: [
    CommonModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatSidenavModule,
    MatListModule,
    MatBadgeModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatTabsModule,
    MatCardModule,
    MatDividerModule,
    MatChipsModule,
    ImportHistoryComponent,
    ImportDetailComponent
  ],
  template: `
    <div class="history-page-container">
      <!-- Mobile/Desktop Layout -->
      <mat-sidenav-container class="sidenav-container" [hasBackdrop]="!(isDesktop$ | async)">
        <!-- Side Navigation -->
        <mat-sidenav
          #drawer
          class="sidenav"
          [mode]="(isDesktop$ | async) ? 'side' : 'over'"
          [opened]="(isDesktop$ | async)"
          [fixedInViewport]="!(isDesktop$ | async)"
          [attr.role]="'navigation'"
        >
          <div class="sidenav-header">
            <h2>Import History</h2>
            <button
              mat-icon-button
              (click)="drawer.toggle()"
              class="drawer-toggle"
              [attr.aria-label]="'Toggle navigation'"
              *ngIf="!(isDesktop$ | async)"
            >
              <mat-icon>close</mat-icon>
            </button>
          </div>

          <mat-nav-list class="navigation-list">
            <a
              mat-list-item
              [class.active]="currentView === 'dashboard'"
              (click)="navigateToView('dashboard')"
              [attr.aria-label]="'View dashboard analytics'"
            >
              <mat-icon matListItemIcon>dashboard</mat-icon>
              <span matListItemTitle>Dashboard</span>
              <span matListItemLine>Analytics overview</span>
            </a>

            <a
              mat-list-item
              [class.active]="currentView === 'history'"
              (click)="navigateToView('history')"
              [attr.aria-label]="'View detailed history'"
            >
              <mat-icon matListItemIcon>history</mat-icon>
              <span matListItemTitle>History</span>
              <span matListItemLine>Detailed import list</span>
            </a>

            <a
              mat-list-item
              [class.active]="currentView === 'failures'"
              (click)="navigateToView('failures')"
              [attr.aria-label]="'View failed imports'"
              [matBadge]="(failureCount$ | async) || 0"
              [matBadgeHidden]="(failureCount$ | async) === 0"
              matBadgeColor="warn"
            >
              <mat-icon matListItemIcon>error</mat-icon>
              <span matListItemTitle>Failed Imports</span>
              <span matListItemLine>Troubleshoot issues</span>
            </a>

            <a
              mat-list-item
              [class.active]="currentView === 'analytics'"
              (click)="navigateToView('analytics')"
              [attr.aria-label]="'View analytics and trends'"
            >
              <mat-icon matListItemIcon>analytics</mat-icon>
              <span matListItemTitle>Analytics</span>
              <span matListItemLine>Trends and patterns</span>
            </a>

            <mat-divider></mat-divider>

            <!-- Quick Time Period Filters -->
            <div class="nav-section-header">Time Period</div>
            <a
              mat-list-item
              *ngFor="let period of quickPeriods"
              [class.active]="selectedPeriod === period.key"
              (click)="selectTimePeriod(period.key)"
              [attr.aria-label]="'Filter by ' + period.label"
            >
              <mat-icon matListItemIcon>{{ period.icon }}</mat-icon>
              <span matListItemTitle>{{ period.label }}</span>
            </a>

            <mat-divider></mat-divider>

            <!-- Quick Actions -->
            <div class="nav-section-header">Quick Actions</div>
            <a
              mat-list-item
              (click)="refreshData()"
              [attr.aria-label]="'Refresh all data'"
            >
              <mat-icon matListItemIcon>refresh</mat-icon>
              <span matListItemTitle>Refresh Data</span>
            </a>

            <a
              mat-list-item
              (click)="exportData()"
              [attr.aria-label]="'Export history data'"
            >
              <mat-icon matListItemIcon>download</mat-icon>
              <span matListItemTitle>Export Data</span>
            </a>

            <a
              mat-list-item
              (click)="navigateToFeedManagement()"
              [attr.aria-label]="'Go back to feed management'"
            >
              <mat-icon matListItemIcon>arrow_back</mat-icon>
              <span matListItemTitle>Back to Feeds</span>
            </a>
          </mat-nav-list>
        </mat-sidenav>

        <!-- Main Content -->
        <mat-sidenav-content class="main-content">
          <!-- Top Toolbar -->
          <mat-toolbar class="page-toolbar" color="primary">
            <button
              mat-icon-button
              (click)="drawer.toggle()"
              class="menu-button"
              [attr.aria-label]="'Toggle navigation menu'"
              *ngIf="!(isDesktop$ | async)"
            >
              <mat-icon>menu</mat-icon>
            </button>

            <div class="toolbar-content">
              <div class="toolbar-title">
                <h1>{{ getPageTitle() }}</h1>
                <span class="page-subtitle">{{ getPageSubtitle() }}</span>
              </div>

              <div class="toolbar-actions">
                <mat-chip-listbox [multiple]="false" [hideSingleSelectionIndicator]="true">
                  <mat-chip-option
                    *ngFor="let period of quickPeriods"
                    [selected]="selectedPeriod === period.key"
                    (click)="selectTimePeriod(period.key)"
                  >
                    {{ period.label }}
                  </mat-chip-option>
                </mat-chip-listbox>

                <button
                  mat-raised-button
                  color="accent"
                  (click)="refreshData()"
                  [disabled]="isLoading$ | async"
                  matTooltip="Refresh all data"
                >
                  <mat-icon>refresh</mat-icon>
                  Refresh
                </button>
              </div>
            </div>
          </mat-toolbar>

          <!-- Content Area -->
          <div class="content-area" [ngSwitch]="currentView">
            <!-- Dashboard View -->
            <div *ngSwitchCase="'dashboard'" class="dashboard-view">
              <div class="dashboard-grid">
                <!-- Key Metrics Cards -->
                <div class="metrics-section">
                  <h2>Key Metrics</h2>
                  <div class="metrics-cards" *ngIf="statistics$ | async as stats">
                    <mat-card class="metric-card success">
                      <mat-card-content>
                        <div class="metric-icon">
                          <mat-icon>check_circle</mat-icon>
                        </div>
                        <div class="metric-data">
                          <span class="metric-value">{{ stats.successfulImports }}</span>
                          <span class="metric-label">Successful</span>
                          <span class="metric-percentage">{{ getSuccessRate(stats) }}%</span>
                        </div>
                      </mat-card-content>
                    </mat-card>

                    <mat-card class="metric-card failed">
                      <mat-card-content>
                        <div class="metric-icon">
                          <mat-icon>error</mat-icon>
                        </div>
                        <div class="metric-data">
                          <span class="metric-value">{{ stats.failedImports }}</span>
                          <span class="metric-label">Failed</span>
                          <span class="metric-percentage">{{ getFailureRate(stats) }}%</span>
                        </div>
                      </mat-card-content>
                    </mat-card>

                    <mat-card class="metric-card total">
                      <mat-card-content>
                        <div class="metric-icon">
                          <mat-icon>import_export</mat-icon>
                        </div>
                        <div class="metric-data">
                          <span class="metric-value">{{ stats.totalImports }}</span>
                          <span class="metric-label">Total Imports</span>
                          <span class="metric-percentage">{{ formatPeriod(stats.period) }}</span>
                        </div>
                      </mat-card-content>
                    </mat-card>

                    <mat-card class="metric-card performance">
                      <mat-card-content>
                        <div class="metric-icon">
                          <mat-icon>timer</mat-icon>
                        </div>
                        <div class="metric-data">
                          <span class="metric-value">{{ formatDuration(stats.averageDurationSeconds) }}</span>
                          <span class="metric-label">Avg Duration</span>
                          <span class="metric-percentage">Per import</span>
                        </div>
                      </mat-card-content>
                    </mat-card>
                  </div>
                </div>

                <!-- Quick Insights -->
                <div class="insights-section">
                  <h2>Quick Insights</h2>
                  <mat-card class="insights-card">
                    <mat-card-content>
                      <div class="insight-list" *ngIf="statistics$ | async as stats">
                        <div class="insight-item">
                          <mat-icon class="insight-icon success">trending_up</mat-icon>
                          <div class="insight-content">
                            <span class="insight-title">Success Rate</span>
                            <span class="insight-description">
                              {{ getSuccessRate(stats) }}% of imports completed successfully
                            </span>
                          </div>
                        </div>

                        <div class="insight-item">
                          <mat-icon class="insight-icon info">schedule</mat-icon>
                          <div class="insight-content">
                            <span class="insight-title">Peak Hours</span>
                            <span class="insight-description">
                              Most imports occur at {{ getPeakHour(stats.hourlyDistribution) }}:00
                            </span>
                          </div>
                        </div>

                        <div class="insight-item">
                          <mat-icon class="insight-icon automatic">smart_toy</mat-icon>
                          <div class="insight-content">
                            <span class="insight-title">Automation</span>
                            <span class="insight-description">
                              {{ getAutomationRate(stats) }}% of imports are automatic
                            </span>
                          </div>
                        </div>

                        <div class="insight-item" *ngIf="stats.failedImports > 0">
                          <mat-icon class="insight-icon warning">warning</mat-icon>
                          <div class="insight-content">
                            <span class="insight-title">Attention Needed</span>
                            <span class="insight-description">
                              {{ stats.failedImports }} failed imports require attention
                            </span>
                          </div>
                        </div>
                      </div>
                    </mat-card-content>
                  </mat-card>
                </div>

                <!-- Recent Activity -->
                <div class="recent-activity-section">
                  <h2>Recent Activity</h2>
                  <mat-card class="activity-card">
                    <mat-card-content>
                      <div class="activity-list" *ngIf="recentActivity$ | async as activity">
                        <div
                          *ngFor="let import of activity; trackBy: trackByImportId"
                          class="activity-item"
                          (click)="viewImportDetails(import)"
                        >
                          <div class="activity-icon">
                            <mat-icon [ngClass]="getStatusIconClass(import.status)">
                              {{ getStatusIcon(import.status) }}
                            </mat-icon>
                          </div>
                          <div class="activity-content">
                            <span class="activity-title">{{ import.feedOnestopId }}</span>
                            <span class="activity-time">{{ formatRelativeTime(import.createdAt) }}</span>
                          </div>
                          <div class="activity-status">
                            <mat-chip [ngClass]="getStatusChipClass(import.status)">
                              {{ getStatusDisplayName(import.status) }}
                            </mat-chip>
                          </div>
                        </div>

                        <div *ngIf="activity.length === 0" class="no-activity">
                          <mat-icon>history_toggle_off</mat-icon>
                          <p>No recent import activity</p>
                        </div>
                      </div>

                      <div class="activity-actions">
                        <button
                          mat-stroked-button
                          (click)="navigateToView('history')"
                        >
                          <mat-icon>history</mat-icon>
                          View All History
                        </button>
                      </div>
                    </mat-card-content>
                  </mat-card>
                </div>
              </div>
            </div>

            <!-- History View -->
            <div *ngSwitchCase="'history'" class="history-view">
              <app-import-history
                (importDetailsRequested)="viewImportDetails($event)"
                (importSelected)="selectImport($event)"
              ></app-import-history>
            </div>

            <!-- Failed Imports View -->
            <div *ngSwitchCase="'failures'" class="failures-view">
              <div class="failures-header">
                <h2>Failed Imports Analysis</h2>
                <p>Analyze and troubleshoot failed imports to improve reliability</p>
              </div>

              <div class="failures-content" *ngIf="failureAnalysis$ | async as analysis">
                <!-- Failure Summary -->
                <mat-card class="failure-summary-card">
                  <mat-card-header>
                    <mat-card-title>
                      <mat-icon>error</mat-icon>
                      Failure Summary
                    </mat-card-title>
                  </mat-card-header>
                  <mat-card-content>
                    <div class="failure-stats">
                      <div class="failure-stat">
                        <span class="stat-value">{{ analysis.totalFailures }}</span>
                        <span class="stat-label">Total Failures</span>
                      </div>
                      <div class="failure-stat">
                        <span class="stat-value">{{ getMostFailedFeed(analysis) }}</span>
                        <span class="stat-label">Most Failed Feed</span>
                      </div>
                      <div class="failure-stat">
                        <span class="stat-value">{{ getMostCommonError(analysis) }}</span>
                        <span class="stat-label">Common Error</span>
                      </div>
                    </div>
                  </mat-card-content>
                </mat-card>

                <!-- Error Patterns -->
                <mat-card class="error-patterns-card">
                  <mat-card-header>
                    <mat-card-title>
                      <mat-icon>pattern</mat-icon>
                      Error Patterns
                    </mat-card-title>
                  </mat-card-header>
                  <mat-card-content>
                    <div class="error-patterns-list">
                      <div
                        *ngFor="let pattern of getErrorPatterns(analysis.errorPatterns)"
                        class="error-pattern-item"
                      >
                        <div class="pattern-error">{{ pattern.error }}</div>
                        <div class="pattern-count">{{ pattern.count }} occurrences</div>
                      </div>
                    </div>
                  </mat-card-content>
                </mat-card>
              </div>

              <!-- Failed Imports List -->
              <app-import-history
                [feedOnestopId]="undefined"
                (importDetailsRequested)="viewImportDetails($event)"
                (importSelected)="selectImport($event)"
              ></app-import-history>
            </div>

            <!-- Analytics View -->
            <div *ngSwitchCase="'analytics'" class="analytics-view">
              <div class="analytics-header">
                <h2>Analytics & Trends</h2>
                <p>Comprehensive analysis of import patterns and performance trends</p>
              </div>

              <div class="analytics-content">
                <div class="analytics-placeholder">
                  <mat-card>
                    <mat-card-content>
                      <div class="placeholder-content">
                        <mat-icon>bar_chart</mat-icon>
                        <h3>Analytics Dashboard</h3>
                        <p>Advanced analytics and visualizations will be implemented here</p>
                        <ul class="planned-features">
                          <li>Import volume trends over time</li>
                          <li>Performance metrics and bottlenecks</li>
                          <li>Feed reliability scores</li>
                          <li>Predictive failure analysis</li>
                          <li>Comparative feed performance</li>
                        </ul>
                      </div>
                    </mat-card-content>
                  </mat-card>
                </div>
              </div>
            </div>
          </div>
        </mat-sidenav-content>
      </mat-sidenav-container>

      <!-- Import Detail Modal/Overlay -->
      <div
        *ngIf="selectedImportId"
        class="detail-overlay"
        (click)="closeImportDetails()"
      >
        <div class="detail-container" (click)="$event.stopPropagation()">
          <app-import-detail
            [importId]="selectedImportId"
            (closed)="closeImportDetails()"
            (importRetried)="onImportRetried($event)"
            (importCancelled)="onImportCancelled($event)"
          ></app-import-detail>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .history-page-container {
      height: 100vh;
      overflow: hidden;
    }

    .sidenav-container {
      height: 100%;
    }

    .sidenav {
      width: 280px;
      background-color: var(--mdc-theme-surface-variant);
    }

    .sidenav-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px;
      background-color: var(--mdc-theme-primary);
      color: var(--mdc-theme-on-primary);
    }

    .sidenav-header h2 {
      margin: 0;
      font-size: 18px;
      font-weight: 500;
    }

    .navigation-list {
      padding: 8px 0;
    }

    .navigation-list a.active {
      background-color: var(--mdc-theme-primary-container);
      color: var(--mdc-theme-on-primary-container);
    }

    .nav-section-header {
      padding: 16px 16px 8px 16px;
      font-size: 12px;
      font-weight: 500;
      color: var(--mdc-theme-outline);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .main-content {
      background-color: var(--mdc-theme-background);
    }

    .page-toolbar {
      position: sticky;
      top: 0;
      z-index: 10;
    }

    .toolbar-content {
      display: flex;
      justify-content: space-between;
      align-items: center;
      width: 100%;
      gap: 16px;
    }

    .toolbar-title h1 {
      margin: 0;
      font-size: 20px;
      font-weight: 500;
    }

    .page-subtitle {
      font-size: 12px;
      opacity: 0.8;
    }

    .toolbar-actions {
      display: flex;
      gap: 16px;
      align-items: center;
    }

    .content-area {
      padding: 24px;
      height: calc(100vh - 64px);
      overflow-y: auto;
    }

    /* Dashboard Styles */
    .dashboard-grid {
      display: grid;
      grid-template-columns: 2fr 1fr;
      grid-template-rows: auto auto;
      gap: 24px;
      grid-template-areas:
        "metrics insights"
        "activity activity";
    }

    .metrics-section {
      grid-area: metrics;
    }

    .insights-section {
      grid-area: insights;
    }

    .recent-activity-section {
      grid-area: activity;
    }

    .metrics-cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
    }

    .metric-card {
      cursor: pointer;
      transition: transform 0.2s, box-shadow 0.2s;
    }

    .metric-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    }

    .metric-card mat-card-content {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 20px !important;
    }

    .metric-icon {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .metric-card.success .metric-icon {
      background-color: var(--mdc-theme-tertiary-container);
      color: var(--mdc-theme-on-tertiary-container);
    }

    .metric-card.failed .metric-icon {
      background-color: var(--mdc-theme-error-container);
      color: var(--mdc-theme-on-error-container);
    }

    .metric-card.total .metric-icon {
      background-color: var(--mdc-theme-primary-container);
      color: var(--mdc-theme-on-primary-container);
    }

    .metric-card.performance .metric-icon {
      background-color: var(--mdc-theme-secondary-container);
      color: var(--mdc-theme-on-secondary-container);
    }

    .metric-data {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .metric-value {
      font-size: 24px;
      font-weight: 600;
      color: var(--mdc-theme-on-surface);
    }

    .metric-label {
      font-size: 12px;
      color: var(--mdc-theme-on-surface-variant);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .metric-percentage {
      font-size: 11px;
      color: var(--mdc-theme-outline);
    }

    .insight-list, .activity-list {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .insight-item, .activity-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px;
      border-radius: 8px;
      transition: background-color 0.2s;
    }

    .activity-item {
      cursor: pointer;
    }

    .activity-item:hover {
      background-color: var(--mdc-theme-surface-variant);
    }

    .insight-icon {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .insight-icon.success { color: var(--mdc-theme-tertiary); }
    .insight-icon.info { color: var(--mdc-theme-primary); }
    .insight-icon.warning { color: var(--mdc-theme-error); }
    .insight-icon.automatic { color: var(--mdc-theme-secondary); }

    .insight-content, .activity-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .insight-title, .activity-title {
      font-weight: 500;
      font-size: 14px;
    }

    .insight-description {
      font-size: 12px;
      color: var(--mdc-theme-on-surface-variant);
    }

    .activity-time {
      font-size: 11px;
      color: var(--mdc-theme-outline);
    }

    .activity-icon {
      width: 24px;
      height: 24px;
    }

    .activity-status {
      margin-left: auto;
    }

    .activity-actions {
      margin-top: 16px;
      text-align: center;
    }

    .no-activity {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 24px;
      color: var(--mdc-theme-on-surface-variant);
    }

    .no-activity mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      margin-bottom: 8px;
      opacity: 0.5;
    }

    /* Failures View Styles */
    .failures-header, .analytics-header {
      margin-bottom: 24px;
    }

    .failures-header h2, .analytics-header h2 {
      margin: 0 0 8px 0;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .failures-header p, .analytics-header p {
      margin: 0;
      color: var(--mdc-theme-on-surface-variant);
    }

    .failures-content {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;
      margin-bottom: 24px;
    }

    .failure-stats {
      display: flex;
      justify-content: space-around;
      gap: 16px;
    }

    .failure-stat {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
    }

    .failure-stat .stat-value {
      font-size: 18px;
      font-weight: 600;
      color: var(--mdc-theme-error);
    }

    .failure-stat .stat-label {
      font-size: 11px;
      color: var(--mdc-theme-on-surface-variant);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .error-patterns-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .error-pattern-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 12px;
      background-color: var(--mdc-theme-surface-variant);
      border-radius: 4px;
    }

    .pattern-error {
      font-family: 'Roboto Mono', monospace;
      font-size: 12px;
      color: var(--mdc-theme-error);
    }

    .pattern-count {
      font-size: 11px;
      color: var(--mdc-theme-on-surface-variant);
    }

    /* Analytics Placeholder Styles */
    .analytics-placeholder {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 400px;
    }

    .placeholder-content {
      text-align: center;
      padding: 48px;
      color: var(--mdc-theme-on-surface-variant);
    }

    .placeholder-content mat-icon {
      font-size: 64px;
      width: 64px;
      height: 64px;
      margin-bottom: 16px;
      opacity: 0.5;
    }

    .planned-features {
      text-align: left;
      margin: 24px 0;
      color: var(--mdc-theme-outline);
    }

    /* Detail Overlay Styles */
    .detail-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: rgba(0, 0, 0, 0.5);
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 16px;
    }

    .detail-container {
      background-color: var(--mdc-theme-surface);
      border-radius: 8px;
      max-width: 90vw;
      max-height: 90vh;
      width: 1000px;
      overflow: auto;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
    }

    /* Status Chip Styles */
    .status-completed { background-color: var(--mdc-theme-tertiary-container); }
    .status-failed { background-color: var(--mdc-theme-error-container); }
    .status-running { background-color: var(--mdc-theme-secondary-container); }
    .status-pending { background-color: var(--mdc-theme-primary-container); }
    .status-cancelled { background-color: var(--mdc-theme-outline-variant); }

    .status-icon-completed { color: var(--mdc-theme-tertiary); }
    .status-icon-failed { color: var(--mdc-theme-error); }
    .status-icon-running { color: var(--mdc-theme-secondary); }
    .status-icon-pending { color: var(--mdc-theme-primary); }
    .status-icon-cancelled { color: var(--mdc-theme-outline); }

    /* Responsive Design */
    @media (max-width: 1200px) {
      .dashboard-grid {
        grid-template-columns: 1fr;
        grid-template-areas:
          "metrics"
          "insights"
          "activity";
      }

      .failures-content {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 768px) {
      .content-area {
        padding: 16px;
      }

      .toolbar-content {
        flex-direction: column;
        align-items: stretch;
        gap: 8px;
      }

      .toolbar-actions {
        justify-content: center;
      }

      .metrics-cards {
        grid-template-columns: 1fr;
      }

      .failure-stats {
        flex-direction: column;
      }

      .detail-overlay {
        padding: 8px;
      }

      .detail-container {
        max-width: 100vw;
        max-height: 100vh;
        width: 100%;
      }
    }
  `]
})
export class HistoryComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  // Responsive layout
  isDesktop$ = this.breakpointObserver.observe([Breakpoints.Large, Breakpoints.XLarge])
    .pipe(map(result => result.matches));

  // Navigation state
  currentView = 'dashboard';
  selectedPeriod = 'last30days';
  selectedImportId: string | null = null;

  // Data streams
  statistics$ = new BehaviorSubject<ImportStatistics | null>(null);
  failureAnalysis$ = new BehaviorSubject<FailureAnalysis | null>(null);
  recentActivity$ = new BehaviorSubject<FeedImport[]>([]);
  failureCount$ = new BehaviorSubject<number>(0);
  isLoading$ = new BehaviorSubject<boolean>(true);

  // Quick period options
  quickPeriods = [
    { key: 'last7days', label: 'Last 7 Days', icon: 'today' },
    { key: 'last30days', label: 'Last 30 Days', icon: 'date_range' },
    { key: 'last90days', label: 'Last 90 Days', icon: 'calendar_month' },
    { key: 'thisMonth', label: 'This Month', icon: 'calendar_today' },
    { key: 'lastMonth', label: 'Last Month', icon: 'event' }
  ];

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar,
    private breakpointObserver: BreakpointObserver,
    private historyService: HistoryService,
    private importService: ImportService
  ) {}

  ngOnInit(): void {
    this.loadInitialData();
    this.setupRouteSubscription();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private setupRouteSubscription(): void {
    this.route.params.pipe(
      takeUntil(this.destroy$)
    ).subscribe(params => {
      if (params['view']) {
        this.currentView = params['view'];
      }
      if (params['importId']) {
        this.selectedImportId = params['importId'];
      }
    });
  }

  private loadInitialData(): void {
    this.refreshData();
  }

  refreshData(): void {
    this.isLoading$.next(true);

    const period = this.historyService.createDateRange(this.selectedPeriod as any);

    // Load statistics
    this.historyService.getImportStatistics({
      startDate: period.startDate,
      endDate: period.endDate
    }).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (stats) => {
        this.statistics$.next(stats);
        this.failureCount$.next(stats.failedImports);
      },
      error: (error) => {
        console.error('Failed to load statistics:', error);
        this.showErrorSnackbar('Failed to load statistics');
      }
    });

    // Load failure analysis
    this.historyService.getFailureAnalysis({
      startDate: period.startDate,
      endDate: period.endDate
    }).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (analysis) => {
        this.failureAnalysis$.next(analysis);
      },
      error: (error) => {
        console.error('Failed to load failure analysis:', error);
      }
    });

    // Load recent activity
    this.historyService.getRecentActivity(10).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (activity) => {
        this.recentActivity$.next(activity);
        this.isLoading$.next(false);
      },
      error: (error) => {
        console.error('Failed to load recent activity:', error);
        this.isLoading$.next(false);
      }
    });
  }

  // Navigation methods
  navigateToView(view: string): void {
    this.currentView = view;
    this.router.navigate(['/history', view]);
  }

  navigateToFeedManagement(): void {
    this.router.navigate(['/feed-management']);
  }

  selectTimePeriod(period: string): void {
    this.selectedPeriod = period;
    this.refreshData();
  }

  // Import interaction methods
  viewImportDetails(import_: FeedImport): void {
    this.selectedImportId = import_.id;
  }

  closeImportDetails(): void {
    this.selectedImportId = null;
  }

  selectImport(import_: FeedImport): void {
    this.viewImportDetails(import_);
  }

  onImportRetried(import_: FeedImport): void {
    this.snackBar.open(`Retrying import for ${import_.feedOnestopId}`, 'Close', {
      duration: 3000
    });
    this.closeImportDetails();
    this.refreshData();
  }

  onImportCancelled(import_: FeedImport): void {
    this.snackBar.open(`Cancelled import for ${import_.feedOnestopId}`, 'Close', {
      duration: 3000
    });
    this.closeImportDetails();
    this.refreshData();
  }

  // Data export
  exportData(): void {
    const period = this.historyService.createDateRange(this.selectedPeriod as any);

    // This would open an export dialog or directly export
    this.snackBar.open('Export functionality would be implemented here', 'Close', {
      duration: 3000
    });
  }

  // Utility methods for template
  getPageTitle(): string {
    switch (this.currentView) {
      case 'dashboard': return 'Import History Dashboard';
      case 'history': return 'Import History';
      case 'failures': return 'Failed Imports';
      case 'analytics': return 'Analytics & Trends';
      default: return 'Import History';
    }
  }

  getPageSubtitle(): string {
    const period = this.quickPeriods.find(p => p.key === this.selectedPeriod);
    return period ? period.label : 'Historical data analysis';
  }

  getSuccessRate(stats: ImportStatistics): number {
    return ImportHistoryUtils.calculateSuccessRate(stats.successfulImports, stats.totalImports);
  }

  getFailureRate(stats: ImportStatistics): number {
    return Math.round((stats.failedImports / stats.totalImports) * 100) || 0;
  }

  getAutomationRate(stats: ImportStatistics): number {
    return Math.round((stats.automaticImports / stats.totalImports) * 100) || 0;
  }

  getPeakHour(hourlyDistribution: Record<number, number>): number {
    const entries = Object.entries(hourlyDistribution);
    if (entries.length === 0) return 0;

    return parseInt(entries.reduce((a, b) => a[1] > b[1] ? a : b)[0]);
  }

  formatDuration(seconds: number): string {
    return ImportHistoryUtils.formatDuration(seconds);
  }

  formatPeriod(period: ImportPeriod): string {
    return ImportHistoryUtils.formatPeriod(period);
  }

  formatRelativeTime(timestamp: string): string {
    return ImportUtils.formatRelativeTime(timestamp);
  }

  getMostFailedFeed(analysis: FailureAnalysis): string {
    return ImportHistoryUtils.getMostFailedFeed(analysis) || 'N/A';
  }

  getMostCommonError(analysis: FailureAnalysis): string {
    const error = ImportHistoryUtils.getMostCommonError(analysis);
    return error ? error.substring(0, 30) + '...' : 'N/A';
  }

  getErrorPatterns(patterns: Record<string, number>): { error: string; count: number }[] {
    return Object.entries(patterns)
      .map(([error, count]) => ({ error, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 5); // Show top 5
  }

  getStatusDisplayName(status: ImportStatus): string {
    return ImportUtils.getStatusDisplayName(status);
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

  getStatusChipClass(status: ImportStatus): string {
    return `status-${status.toLowerCase()}`;
  }

  getStatusIconClass(status: ImportStatus): string {
    return `status-icon-${status.toLowerCase()}`;
  }

  trackByImportId(index: number, import_: FeedImport): string {
    return import_.id;
  }

  private showErrorSnackbar(message: string): void {
    this.snackBar.open(message, 'Close', {
      duration: 5000,
      panelClass: ['error-snackbar']
    });
  }
}