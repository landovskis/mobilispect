import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Observable, Subject, BehaviorSubject, combineLatest } from 'rxjs';
import { map, takeUntil, filter } from 'rxjs/operators';
import { MetropolitanRegion, Feed, FeedStatus, FeedSpecType } from '../models/region.models';
import { FeedImportSummary } from '../models/import.models';
import { AuthenticationStatistics } from '../models/feed-authentication.model';
import { RegionService } from '../services/region.service';
import { ImportService } from '../services/import.service';
import { FeedAuthenticationService } from '../services/feed-authentication.service';
import { ImportProgressDialogComponent } from '../components/import-progress-dialog.component';
import { ImportConfirmationDialogComponent } from '../components/import-confirmation-dialog.component';
import { ProgressMonitorComponent } from '../components/progress-monitor.component';
import { ProgressWebSocketService } from '../services/progress-websocket.service';
import { ThemeToggleComponent } from '../../core/components/theme-toggle.component';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-feed-management',
  standalone: false,
  template: `
    <div class="feed-management-container">
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
            <h2>Feed Management</h2>
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

          <mat-nav-list class="navigation-list" role="tablist">
            <a
              mat-list-item
              role="tab"
              [class.active]="currentView === 'regions' || currentView === 'imports'"
              [attr.aria-selected]="currentView === 'regions' || currentView === 'imports'"
              (click)="navigateToView('regions')"
              [attr.aria-label]="'View feed management with ' + (activeImports$ | async)?.length + ' active imports'"
            >
              <mat-icon
                matListItemIcon
                [matBadge]="(activeImports$ | async)?.length || 0"
                [matBadgeHidden]="((activeImports$ | async)?.length || 0) === 0"
                matBadgeColor="accent"
                matBadgeSize="small"
                aria-hidden="false"
                [attr.aria-label]="'Active imports: ' + ((activeImports$ | async)?.length || 0)"
              >
                dashboard
              </mat-icon>
              <span matListItemTitle>Feed Management</span>
              <span matListItemLine>Regions & Imports</span>
            </a>

            <a
              mat-list-item
              role="tab"
              [class.active]="currentView === 'feeds'"
              [attr.aria-selected]="currentView === 'feeds'"
              (click)="navigateToView('feeds')"
              [attr.aria-label]="'View feeds' + (selectedRegion ? ' for ' + selectedRegion.name : '')"
              *ngIf="selectedRegion"
            >
              <mat-icon
                matListItemIcon
                [matBadge]="regionFeeds.length || 0"
                [matBadgeHidden]="regionFeeds.length === 0"
                matBadgeColor="primary"
                matBadgeSize="small"
              >
                rss_feed
              </mat-icon>
              <span matListItemTitle>{{ selectedRegion?.name }} Feeds</span>
              <span matListItemLine>{{ regionFeeds.length }} feeds available</span>
            </a>

            <mat-divider></mat-divider>

            <a
              mat-list-item
              (click)="refreshData()"
              [attr.aria-label]="'Refresh all data'"
            >
              <mat-icon matListItemIcon>refresh</mat-icon>
              <span matListItemTitle>Refresh</span>
              <span matListItemLine>Update all data</span>
            </a>
          </mat-nav-list>
        </mat-sidenav>

        <!-- Main Content -->
        <mat-sidenav-content class="main-content">
          <!-- Toolbar -->
          <mat-toolbar color="primary" class="main-toolbar">
            <button
              mat-icon-button
              (click)="drawer.toggle()"
              [attr.aria-label]="'Open navigation'"
              *ngIf="!(isDesktop$ | async)"
            >
              <mat-icon>menu</mat-icon>
            </button>

            <span class="toolbar-title">{{ getViewTitle() }}</span>

            <span class="toolbar-spacer"></span>

            <!-- Centered Navigation -->
            <div class="toolbar-nav-center">
              <button
                mat-button
                (click)="navigateToView('feeds')"
                [class.active-toolbar-button]="currentView === 'feeds'"
                class="toolbar-nav-button"
              >
                <mat-icon>rss_feed</mat-icon>
                Feeds
              </button>
            </div>

            <span class="toolbar-spacer"></span>

            <!-- Quick Stats -->
            <div class="quick-stats" *ngIf="quickStats$ | async as stats">
              <mat-chip-set>
                <mat-chip *ngIf="stats.activeImports > 0" class="active-imports">
                  <mat-icon>download</mat-icon>
                  {{ stats.activeImports }} importing
                </mat-chip>
              </mat-chip-set>
            </div>

            <!-- Theme Toggle (Constitutional Requirement) -->
            <app-theme-toggle></app-theme-toggle>
          </mat-toolbar>

          <!-- Content Area -->
          <div class="content-area">
            <!-- Feed Management View (Regions, Active Imports & History Tabs) -->
            <div *ngIf="currentView === 'regions' || currentView === 'imports'" class="view-container">
              <mat-card>
                <mat-card-header>
                  <mat-card-title>Feed Management</mat-card-title>
                  <mat-card-subtitle>Select regions and manage transit feed imports</mat-card-subtitle>
                </mat-card-header>

                <mat-card-content>
                  <mat-tab-group animationDuration="200ms" [(selectedIndex)]="selectedTabIndex">
                    <!-- Regions Tab -->
                    <mat-tab>
                      <ng-template mat-tab-label>
                        <mat-icon class="tab-icon">location_on</mat-icon>
                        Regions
                        <span class="tab-badge" *ngIf="mockRegions.length > 0">
                          {{ mockRegions.length }}
                        </span>
                      </ng-template>

                      <div class="tab-content">
                        <mat-card class="welcome-card">
                          <mat-card-content>
                            <p>Choose from available metropolitan regions to view and import their transit feeds.</p>
                            <button mat-raised-button color="primary" (click)="testImport()">
                              <mat-icon>download</mat-icon>
                              Test Feed Import
                            </button>
                          </mat-card-content>
                        </mat-card>

                        <!-- Regions Grid -->
                        <div class="regions-grid" *ngIf="mockRegions.length > 0">
                          <mat-card *ngFor="let region of mockRegions" class="region-card region-item">
                            <mat-card-header>
                              <mat-card-title>{{ region.name }}</mat-card-title>
                              <mat-card-subtitle>{{ region.feedCount }} feeds</mat-card-subtitle>
                            </mat-card-header>
                            <mat-card-actions>
                              <button mat-button (click)="selectRegion(region)">
                                <mat-icon>visibility</mat-icon>
                                View Feeds
                              </button>
                              <button mat-raised-button color="primary" (click)="importRegionFeeds(region)">
                                <mat-icon>download</mat-icon>
                                Import
                              </button>
                            </mat-card-actions>
                          </mat-card>
                        </div>
                      </div>
                    </mat-tab>

                    <!-- Active Imports Tab -->
                    <mat-tab>
                      <ng-template mat-tab-label>
                        <mat-icon class="tab-icon">download</mat-icon>
                        Active Imports
                        <span class="tab-badge" *ngIf="(activeImports$ | async)?.length">
                          {{ (activeImports$ | async)?.length }}
                        </span>
                      </ng-template>

                      <div class="tab-content">
                        <!-- Bulk Actions -->
                        <div class="bulk-actions" *ngIf="((activeImports$ | async)?.length ?? 0) > 0">
                          <mat-checkbox
                            [(ngModel)]="allImportsSelected"
                            [indeterminate]="someImportsSelected && !allImportsSelected"
                            (change)="toggleAllImports($event.checked)">
                            Select All
                          </mat-checkbox>
                          <button
                            mat-button
                            color="warn"
                            [disabled]="selectedImportIds.size === 0"
                            (click)="bulkCancelImports()">
                            <mat-icon>cancel</mat-icon>
                            Cancel Selected ({{ selectedImportIds.size }})
                          </button>
                        </div>

                        <div *ngIf="(activeImports$ | async)?.length === 0" class="no-imports">
                          <mat-icon class="no-imports-icon">hourglass_empty</mat-icon>
                          <p>No active imports at this time.</p>
                          <p class="hint">Start an import from the regions view to see real-time progress here.</p>
                        </div>

                        <!-- Active Imports List -->
                        <div *ngFor="let activeImport of activeImports$ | async" class="active-import-card active-import-item">
                          <div class="import-header">
                            <mat-checkbox
                              [checked]="selectedImportIds.has(activeImport.id)"
                              (change)="toggleImportSelection(activeImport.id, $event.checked)"
                              class="import-checkbox">
                            </mat-checkbox>
                            <h3>
                              <mat-icon class="import-icon">download</mat-icon>
                              {{ activeImport.feedName }}
                            </h3>
                            <p class="import-subtitle">
                              {{ activeImport.regionName }} • Started: {{ activeImport.startedAt | date:'short' }}
                            </p>
                          </div>

                          <!-- Enhanced Progress Monitor -->
                          <app-progress-monitor
                            [importId]="activeImport.id"
                            [showActions]="true"
                            [showConnectionStatus]="false"
                            (cancelRequested)="cancelImport($event)">
                          </app-progress-monitor>
                        </div>
                      </div>
                    </mat-tab>

                    <!-- Import History Tab -->
                    <mat-tab>
                      <ng-template mat-tab-label>
                        <mat-icon class="tab-icon">history</mat-icon>
                        History
                        <span class="tab-badge" *ngIf="totalImportElements > 0">
                          {{ totalImportElements }}
                        </span>
                      </ng-template>

                      <div class="tab-content">
                        <div *ngIf="loadingHistory" class="loading-container" style="text-align: center; padding: 40px;">
                          <mat-spinner diameter="40" style="margin: 0 auto;"></mat-spinner>
                          <p style="margin-top: 20px;">Loading import history...</p>
                        </div>

                        <div *ngIf="!loadingHistory && importHistory.length === 0" class="empty-state" style="text-align: center; padding: 60px 20px;">
                          <mat-icon style="font-size: 64px; width: 64px; height: 64px; color: #999;">history</mat-icon>
                          <p style="font-size: 18px; margin-top: 20px; color: #666;">No import history available yet.</p>
                          <p style="color: #999;">Start an import to see it appear here when completed.</p>
                        </div>

                        <div *ngIf="!loadingHistory && importHistory.length > 0">
                          <table mat-table [dataSource]="importHistory" class="history-table" style="width: 100%;">
                            <ng-container matColumnDef="feedName">
                              <th mat-header-cell *matHeaderCellDef>Feed</th>
                              <td mat-cell *matCellDef="let import">{{ import.feedName || import.feedOnestopId }}</td>
                            </ng-container>

                            <ng-container matColumnDef="region">
                              <th mat-header-cell *matHeaderCellDef>Region</th>
                              <td mat-cell *matCellDef="let import">{{ import.regionName || import.regionOnestopId }}</td>
                            </ng-container>

                            <ng-container matColumnDef="status">
                              <th mat-header-cell *matHeaderCellDef>Status</th>
                              <td mat-cell *matCellDef="let import">
                                <span [ngClass]="{'status-badge': true, 'status-completed': import.status === 'COMPLETED', 'status-failed': import.status === 'FAILED', 'status-cancelled': import.status === 'CANCELLED'}">{{ import.status }}</span>
                              </td>
                            </ng-container>

                            <ng-container matColumnDef="startedAt">
                              <th mat-header-cell *matHeaderCellDef>Started</th>
                              <td mat-cell *matCellDef="let import">{{ import.startedAt | date:'short' }}</td>
                            </ng-container>

                            <ng-container matColumnDef="completedAt">
                              <th mat-header-cell *matHeaderCellDef>Completed</th>
                              <td mat-cell *matCellDef="let import">{{ import.completedAt | date:'short' }}</td>
                            </ng-container>

                            <ng-container matColumnDef="records">
                              <th mat-header-cell *matHeaderCellDef>Records</th>
                              <td mat-cell *matCellDef="let import">{{ import.recordsImported | number }}</td>
                            </ng-container>

                            <tr mat-header-row *matHeaderRowDef="['feedName', 'region', 'status', 'startedAt', 'completedAt', 'records']"></tr>
                            <tr mat-row *matRowDef="let row; columns: ['feedName', 'region', 'status', 'startedAt', 'completedAt', 'records']"></tr>
                          </table>

                          <mat-paginator
                            [length]="totalImportElements"
                            [pageSize]="importHistorySize"
                            [pageIndex]="importHistoryPage"
                            [pageSizeOptions]="[10, 20, 50, 100]"
                            (page)="loadImportHistory($event.pageIndex)"
                            showFirstLastButtons>
                          </mat-paginator>
                        </div>
                      </div>
                    </mat-tab>
                  </mat-tab-group>
                </mat-card-content>
              </mat-card>
            </div>

            <!-- Feeds View -->
            <div *ngIf="currentView === 'feeds'" class="view-container">
              <mat-card class="feeds-header-card">
                <mat-card-header>
                  <mat-card-title>
                    <button mat-icon-button (click)="backToRegions()" class="back-button">
                      <mat-icon>arrow_back</mat-icon>
                    </button>
                    {{ selectedRegion?.name }} Feeds
                  </mat-card-title>
                  <mat-card-subtitle>{{ regionFeeds.length }} transit feeds available</mat-card-subtitle>
                </mat-card-header>
              </mat-card>

              <!-- Loading State -->
              <mat-card *ngIf="loadingFeeds" class="loading-card">
                <mat-card-content class="loading-content">
                  <mat-spinner diameter="40"></mat-spinner>
                  <p>Loading feeds...</p>
                </mat-card-content>
              </mat-card>

              <!-- Feeds List -->
              <div *ngIf="!loadingFeeds && regionFeeds.length > 0" class="feeds-grid">
                <mat-card *ngFor="let feed of regionFeeds" class="feed-card">
                  <mat-card-header>
                    <mat-card-title class="feed-title">
                      <mat-icon [class]="'feed-icon ' + feed.specType">{{ getFeedIcon(feed.specType) }}</mat-icon>
                      {{ feed.name }}
                    </mat-card-title>
                    <mat-card-subtitle>{{ feed.feedOnestopId }}</mat-card-subtitle>
                  </mat-card-header>
                  <mat-card-content>
                    <div class="feed-details">
                      <div class="feed-status">
                        <mat-chip [class]="'status-chip ' + feed.status">
                          <mat-icon>{{ feed.status === 'active' ? 'check_circle' : 'cancel' }}</mat-icon>
                          {{ feed.status | titlecase }}
                        </mat-chip>
                        <mat-chip class="spec-chip">{{ feed.specType.toUpperCase() }}</mat-chip>
                      </div>
                      <div class="feed-meta">
                        <p *ngIf="feed.lastCheckedAt">
                          <mat-icon>schedule</mat-icon>
                          Last checked: {{ feed.lastCheckedAt | date:'short' }}
                        </p>
                        <p *ngIf="feed.lastUpdatedAt">
                          <mat-icon>download</mat-icon>
                          Last updated: {{ feed.lastUpdatedAt | date:'short' }}
                        </p>
                        <p *ngIf="feed.downloadUrl">
                          <mat-icon>link</mat-icon>
                          <a [href]="feed.downloadUrl" target="_blank">Download URL</a>
                        </p>
                      </div>
                    </div>
                  </mat-card-content>
                  <mat-card-actions>
                    <button mat-button (click)="viewFeedDetails(feed)">
                      <mat-icon>info</mat-icon>
                      Details
                    </button>
                    <button mat-raised-button color="primary" (click)="importFeed(feed)"
                            [disabled]="feed.status !== 'active'">
                      <mat-icon>download</mat-icon>
                      Import
                    </button>
                  </mat-card-actions>
                </mat-card>
              </div>

              <!-- Empty State -->
              <mat-card *ngIf="!loadingFeeds && regionFeeds.length === 0" class="empty-state">
                <mat-card-content class="empty-content">
                  <mat-icon class="empty-icon">inbox</mat-icon>
                  <h3>No feeds found</h3>
                  <p>No transit feeds are available for this region.</p>
                </mat-card-content>
              </mat-card>
            </div>
          </div>
        </mat-sidenav-content>
      </mat-sidenav-container>
    </div>
  `,
  styles: [`
    .feed-management-container {
      height: 100vh;
      display: flex;
      flex-direction: column;
    }

    .sidenav-container {
      flex: 1;
    }

    .sidenav {
      width: 280px;
      background: white;
      border-right: 1px solid #e0e0e0;
    }

    .sidenav-header {
      padding: 20px 16px;
      border-bottom: 1px solid #e0e0e0;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .sidenav-header h2 {
      margin: 0;
      font-size: 1.25rem;
      font-weight: 500;
    }

    .navigation-list {
      padding-top: 8px;
    }

    .navigation-list a.active {
      background-color: #f3e5f5;
      color: #7b1fa2;
    }

    .main-toolbar {
      position: sticky;
      top: 0;
      z-index: 10;
    }

    .toolbar-title {
      font-size: 1.25rem;
      font-weight: 500;
    }

    .toolbar-spacer {
      flex: 1;
    }

    .toolbar-nav-button {
      margin-right: 16px;
      color: rgba(255, 255, 255, 0.87);
    }

    .toolbar-nav-button mat-icon {
      margin-right: 4px;
    }

    .toolbar-nav-button.active-toolbar-button {
      background-color: rgba(255, 255, 255, 0.15);
    }

    .quick-stats mat-chip {
      margin-left: 8px;
    }

    .quick-stats .active-imports {
      background-color: #ff9800;
      color: white;
    }

    .content-area {
      padding: 24px;
      background-color: #fafafa;
      min-height: calc(100vh - 64px);
    }

    .view-container {
      max-width: 1200px;
      margin: 0 auto;
    }

    .welcome-card {
      margin-bottom: 24px;
    }

    .regions-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: 16px;
      margin-top: 16px;
    }

    .region-card {
      transition: transform 0.2s, box-shadow 0.2s;
    }

    .region-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 8px rgba(0,0,0,0.12);
    }

    /* Feeds View Styles */
    .feeds-header-card {
      margin-bottom: 24px;
    }

    .back-button {
      margin-right: 8px;
    }

    .loading-card {
      margin-bottom: 24px;
    }

    .loading-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 40px 20px;
    }

    .loading-content mat-spinner {
      margin-bottom: 16px;
    }

    .feeds-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
      gap: 16px;
    }

    .feed-card {
      transition: transform 0.2s, box-shadow 0.2s;
    }

    .feed-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 8px rgba(0,0,0,0.12);
    }

    .feed-title {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .feed-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }

    .feed-icon.gtfs {
      color: #2196f3;
    }

    .feed-icon.gtfs-rt {
      color: #ff9800;
    }

    .feed-icon.gbfs {
      color: #4caf50;
    }

    .feed-details {
      margin-top: 16px;
    }

    .feed-status {
      display: flex;
      gap: 8px;
      margin-bottom: 16px;
      flex-wrap: wrap;
    }

    .status-chip {
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .status-chip.active {
      background-color: #e8f5e8;
      color: #4caf50;
    }

    .status-chip.inactive {
      background-color: #fce4ec;
      color: #f44336;
    }

    .spec-chip {
      background-color: #e3f2fd;
      color: #1976d2;
      font-weight: 500;
    }

    .feed-meta p {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 8px 0;
      font-size: 0.875rem;
      color: #666;
    }

    .feed-meta mat-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }

    .empty-state {
      margin-top: 24px;
    }

    .empty-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 40px 20px;
      text-align: center;
    }

    .empty-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: #ccc;
      margin-bottom: 16px;
    }

    .empty-content h3 {
      margin: 0 0 8px 0;
      color: #666;
    }

    .empty-content p {
      margin: 0;
      color: #999;
    }

    @media (max-width: 768px) {
      .content-area {
        padding: 16px;
      }

      .regions-grid {
        grid-template-columns: 1fr;
      }

      .feeds-grid {
        grid-template-columns: 1fr;
      }

      .feed-status {
        flex-direction: column;
        gap: 4px;
      }
    }

    /* Error Snackbar Styling */
    :host ::ng-deep .error-snackbar {
      background-color: #f44336 !important;
      color: white !important;
    }

    :host ::ng-deep .error-snackbar .mat-mdc-button {
      color: white !important;
      border-color: white !important;
    }

    /* Active Imports Styles */
    .no-imports {
      text-align: center;
      padding: 40px 20px;
      color: #666;
    }

    .no-imports-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: #ccc;
      margin-bottom: 16px;
    }

    .hint {
      font-size: 14px;
      color: #999;
      margin-top: 8px;
    }

    .active-import-card {
      margin-bottom: 16px;
    }

    .import-progress-card {
      border-left: 4px solid #2196f3;
    }

    .import-icon {
      margin-right: 8px;
      vertical-align: middle;
    }

    .progress-info {
      margin-top: 16px;
    }

    .import-progress-bar {
      margin-bottom: 12px;
    }

    .progress-details {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 14px;
    }

    .progress-percentage {
      font-weight: 600;
      color: #2196f3;
    }

    .progress-step {
      color: #666;
      font-style: italic;
    }

    /* Tab Styles */
    .tab-icon {
      margin-right: 8px;
    }

    .tab-badge {
      margin-left: 8px;
      padding: 2px 8px;
      background-color: rgba(33, 150, 243, 0.1);
      border-radius: 12px;
      font-size: 12px;
      font-weight: 500;
    }

    .tab-content {
      padding: 24px 0;
    }

    .stat-value {
      font-size: 2rem;
      font-weight: 600;
      margin-bottom: 4px;
      color: #333;
    }

    .stat-label {
      font-size: 0.875rem;
      color: #666;
      font-weight: 500;
    }

    /* Bulk Actions Styles */
    .bulk-actions {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px 24px;
      background-color: #f5f5f5;
      border-bottom: 1px solid #e0e0e0;
    }

    .import-checkbox {
      margin-right: 12px;
    }

    .import-header {
      display: flex;
      align-items: flex-start;
      gap: 8px;
    }

    .import-header h3 {
      flex: 1;
      margin: 0;
    }

    .import-header .import-subtitle {
      flex: 1;
      margin: 4px 0 0 0;
    }
  `]
})
export class FeedManagementComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  // Expose enums to template
  FeedStatus = FeedStatus;
  FeedSpecType = FeedSpecType;

  // Observable streams
  isDesktop$: Observable<boolean>;
  activeImports$: Observable<FeedImportSummary[]>;
  quickStats$: Observable<any>;
  authStats$: Observable<AuthenticationStatistics>;

  // Component state
  currentView = 'regions';
  selectedTabIndex = 0; // Track which tab is selected (0=Regions, 1=Active Imports, 2=History)
  mockRegions: MetropolitanRegion[] = [];
  selectedRegion: MetropolitanRegion | null = null;
  regionFeeds: Feed[] = [];
  allFeeds: Feed[] = [];
  selectedFeedForAuth: string | null = null;
  loadingFeeds = false;

  // Import history state
  importHistory: FeedImportSummary[] = [];
  importHistoryPage = 0;
  importHistorySize = 20;
  totalImportPages = 0;
  totalImportElements = 0;
  loadingHistory = false;

  // Bulk selection state
  selectedImportIds = new Set<string>();
  allImportsSelected = false;
  someImportsSelected = false;

  constructor(
    private regionService: RegionService,
    private importService: ImportService,
    private authService: FeedAuthenticationService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private breakpointObserver: BreakpointObserver,
    private router: Router,
    private route: ActivatedRoute
  ) {
    // Setup responsive design
    this.isDesktop$ = this.breakpointObserver.observe([
      Breakpoints.Medium,
      Breakpoints.Large,
      Breakpoints.XLarge
    ]).pipe(
      map(result => result.matches)
    );

    // Setup active imports observable
    this.activeImports$ = this.importService.getActiveImportsObservable();

    // Setup quick stats
    this.quickStats$ = combineLatest([
      this.regionService.getCachedRegions(),
      this.activeImports$
    ]).pipe(
      map(([regions, activeImports]) => ({
        totalRegions: regions?.length || 0,
        activeImports: activeImports?.length || 0
      }))
    );

    // Setup authentication stats
    this.authStats$ = this.authService.getAuthenticationStatistics().pipe(
      map(stats => stats || {
        total: 0,
        active: 0,
        expired: 0,
        locked: 0,
        noAuth: 0,
        byType: {}
      })
    );
  }

  ngOnInit(): void {
    console.log('Feed Management Component initialized');

    // Handle route-based view switching
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.updateCurrentViewFromRoute();
    });

    // Initial view setup
    this.updateCurrentViewFromRoute();

    // Load mock data
    this.loadMockData();

    // Start polling for active imports
    this.importService.startPollingActiveImports();

    // Load all feeds for authentication management
    this.loadAllFeeds();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.importService.stopPollingActiveImports();
  }

  getViewTitle(): string {
    switch (this.currentView) {
      case 'regions':
      case 'imports':
        return 'Feed Management';
      case 'feeds':
        return `${this.selectedRegion?.name || 'Region'} Feeds`;
      default:
        return 'Feed Management';
    }
  }

  getFeedIcon(specType: FeedSpecType): string {
    switch (specType) {
      case FeedSpecType.GTFS: return 'directions_transit';
      case FeedSpecType.GTFS_RT: return 'real_time_tracking';
      default: return 'feed';
    }
  }

  viewFeedDetails(feed: Feed): void {
    this.snackBar.open(`Viewing details for ${feed.name}`, 'Close', { duration: 2000 });
    // In a real app, this would open a dialog or navigate to a details page
  }

  importFeed(feed: Feed): void {
    this.snackBar.open(`Starting import for ${feed.name}...`, 'Close', { duration: 2000 });

    this.importService.startImport(feed.feedOnestopId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (result) => {
        // Automatically redirect to active imports view
        this.navigateToView('imports');

        // Show success notification
        this.snackBar.open(
          `Import started for ${feed.name}`,
          'Close',
          { duration: 3000 }
        );

        // Refresh active imports
        this.importService.refreshActiveImports();

        // Open progress dialog with real-time monitoring (optional)
        const dialogRef = this.dialog.open(ImportProgressDialogComponent, {
          width: '600px',
          maxWidth: '90vw',
          disableClose: false, // Allow closing
          data: {
            feedOnestopId: feed.feedOnestopId,
            feedName: feed.name,
            importResult: result
          }
        });

        // Handle dialog result
        dialogRef.afterClosed().subscribe((finalResult) => {
          if (finalResult) {
            const status = finalResult.status;
            if (status === 'COMPLETED') {
              this.snackBar.open(
                `✅ ${feed.name} import completed successfully!`,
                'Close',
                { duration: 4000 }
              );
            } else if (status === 'FAILED') {
              this.snackBar.open(
                `❌ ${feed.name} import failed`,
                'Close',
                { duration: 4000 }
              );
            } else if (status === 'CANCELLED') {
              this.snackBar.open(
                `⚠️ ${feed.name} import was cancelled`,
                'Close',
                { duration: 4000 }
              );
            }
          }
        });
      },
      error: (error) => {
        console.error('Failed to start import:', error);

        // Show detailed error message to help with troubleshooting
        const errorMessage = error.message || error.error?.message || 'Unknown error occurred';

        this.snackBar.open(
          `❌ Import failed: ${errorMessage}`,
          'Retry',
          {
            duration: 8000,
            panelClass: ['error-snackbar']
          }
        ).onAction().subscribe(() => {
          // Retry the import when user clicks "Retry"
          this.importFeed(feed);
        });
      }
    });
  }

  navigateToView(view: string): void {
    this.currentView = view;
    this.router.navigate(['/feed-management', view]);

    // Load data specific to the view
    if (view === 'imports') {
      this.loadImportHistory();
    }
  }

  updateCurrentViewFromRoute(): void {
    const urlSegments = this.router.url.split('/');
    const lastSegment = urlSegments[urlSegments.length - 1];

    if (['regions', 'imports'].includes(lastSegment)) {
      this.currentView = lastSegment;

      // Load data for the current view
      if (lastSegment === 'imports') {
        this.loadImportHistory();
      }
    } else {
      this.currentView = 'regions';
    }
  }

  refreshData(): void {
    this.snackBar.open('Refreshing data...', 'Close', { duration: 2000 });
    this.regionService.clearCache();
    this.importService.refreshActiveImports();
    this.loadMockData();
  }

  testImport(): void {
    console.log('Testing feed import...');
    this.importService.startImport('f-sf-bay-area').pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (result) => {
        console.log('Import result:', result);
        this.snackBar.open(
          `✅ Feed import ${result.status.toLowerCase()}! ID: ${result.id}`,
          'Close',
          { duration: 5000 }
        );
      },
      error: (error) => {
        console.error('Import failed:', error);
        this.snackBar.open('❌ Import failed', 'Close', { duration: 5000 });
      }
    });
  }

  selectRegion(region: MetropolitanRegion): void {
    this.selectedRegion = region;
    this.currentView = 'feeds';
    this.loadingFeeds = true;
    this.regionFeeds = [];

    // Mock feeds data since API might not be available
    this.loadingFeeds = false;
    this.regionFeeds = this.generateMockFeeds(region);

    this.snackBar.open(`Viewing feeds for ${region.name}`, 'Close', { duration: 2000 });
  }

  backToRegions(): void {
    this.currentView = 'regions';
    this.selectedRegion = null;
    this.regionFeeds = [];
  }

  private generateMockFeeds(region: MetropolitanRegion): Feed[] {
    const mockFeeds: Feed[] = [];
    const feedCount = region.feedCount || 3;

    for (let i = 0; i < Math.min(feedCount, 10); i++) {
      mockFeeds.push({
        feedOnestopId: `f-${region.regionOnestopId.substring(2)}-feed${i + 1}`,
        regionOnestopId: region.regionOnestopId,
        name: `${region.name} Transit Feed ${i + 1}`,
        specType: i === 0 ? FeedSpecType.GTFS : FeedSpecType.GTFS_RT,
        downloadUrl: `https://transit.land/api/v2/feeds/${region.regionOnestopId}/feed${i + 1}/download`,
        currentVersionSha1: i < 2 ? `sha1-${Date.now()}-${i}` : null,
        lastCheckedAt: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString(),
        lastUpdatedAt: i < 2 ? new Date(Date.now() - Math.random() * 3 * 24 * 60 * 60 * 1000).toISOString() : null,
        status: i === 0 ? FeedStatus.ACTIVE : (i === 1 ? FeedStatus.INACTIVE : FeedStatus.ACTIVE),
        hasAuthentication: i === 2,
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: new Date().toISOString()
      });
    }

    return mockFeeds;
  }

  importRegionFeeds(region: MetropolitanRegion): void {
    // Show confirmation dialog first
    const dialogRef = this.dialog.open(ImportConfirmationDialogComponent, {
      width: '500px',
      data: {
        regionName: region.name,
        feedCount: region.feedCount
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result === true) {
        // User confirmed - start the import
        // Navigate to active imports view
        this.navigateToView('imports');

        // Show starting notification
        this.snackBar.open(`Starting import for ${region.name}...`, 'Close', { duration: 3000 });

        // Start the import
        this.testImport();
      }
    });
  }

  private loadMockData(): void {
    this.regionService.listRegions().pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (regions) => {
        console.log('Loaded regions:', regions);
        this.mockRegions = regions;
      },
      error: (error) => {
        console.error('Failed to load regions:', error);
      }
    });
  }

  viewImportDetails(activeImport: any): void {
    // Open the import progress dialog for detailed view
    const dialogRef = this.dialog.open(ImportProgressDialogComponent, {
      width: '600px',
      data: {
        feedOnestopId: activeImport.feedOnestopId,
        feedName: activeImport.feedName,
        importResult: activeImport
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        // Handle any result from the dialog
        console.log('Import dialog closed with result:', result);
      }
    });
  }

  cancelImport(importId: string): void {
    this.snackBar.open('Cancelling import...', 'Close', { duration: 2000 });

    this.importService.cancelImport(importId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (result) => {
        this.snackBar.open(
          `✅ Import cancelled successfully`,
          'Close',
          { duration: 4000 }
        );
        // Refresh the active imports list
        this.importService.refreshActiveImports();
        // Refresh history if on history view
        if (this.currentView === 'history') {
          this.loadImportHistory();
        }
      },
      error: (error) => {
        console.error('Failed to cancel import:', error);
        const errorMessage = error.message || error.error?.message || 'Unknown error occurred';
        this.snackBar.open(
          `❌ Failed to cancel import: ${errorMessage}`,
          'Retry',
          {
            duration: 8000,
            panelClass: ['error-snackbar']
          }
        ).onAction().subscribe(() => {
          // Retry cancellation when user clicks "Retry"
          this.cancelImport(importId);
        });
      }
    });
  }

  loadImportHistory(page: number = 0): void {
    this.loadingHistory = true;
    this.importHistoryPage = page;

    this.importService.getAllImportHistory({
      page: page,
      size: this.importHistorySize
    }).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (response) => {
        this.importHistory = response.imports as any[];
        this.totalImportPages = response.totalPages;
        this.totalImportElements = response.totalElements;
        this.loadingHistory = false;
      },
      error: (error) => {
        console.error('Failed to load import history:', error);
        this.loadingHistory = false;
        this.snackBar.open(
          'Failed to load import history',
          'Close',
          { duration: 4000 }
        );
      }
    });
  }

  onFeedSelectedForAuth(feedOnestopId: string): void {
    this.selectedFeedForAuth = feedOnestopId;
  }

  private loadAllFeeds(): void {
    // In a real implementation, this would load all feeds from all regions
    // For now, we'll simulate with mock data
    const mockAllFeeds: Feed[] = [];

    // Generate mock feeds across multiple regions
    const regions = ['sf-bay-area', 'la-metro', 'seattle', 'portland'];
    regions.forEach((region, regionIndex) => {
      for (let i = 0; i < 3; i++) {
        mockAllFeeds.push({
          feedOnestopId: `f-${region}-feed${i + 1}`,
          regionOnestopId: `r-${region}`,
          name: `${region.replace('-', ' ').replace(/\b\w/g, l => l.toUpperCase())} Feed ${i + 1}`,
          specType: i === 0 ? FeedSpecType.GTFS : FeedSpecType.GTFS_RT,
          downloadUrl: `https://transit.land/api/v2/feeds/${region}/feed${i + 1}/download`,
          currentVersionSha1: i < 2 ? `sha1-${Date.now()}-${regionIndex}-${i}` : null,
          lastCheckedAt: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000).toISOString(),
          lastUpdatedAt: i < 2 ? new Date(Date.now() - Math.random() * 3 * 24 * 60 * 60 * 1000).toISOString() : null,
          status: Math.random() > 0.2 ? FeedStatus.ACTIVE : FeedStatus.INACTIVE,
          hasAuthentication: Math.random() > 0.7,
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: new Date().toISOString()
        });
      }
    });

    this.allFeeds = mockAllFeeds;
  }

  toggleImportSelection(importId: string, selected: boolean): void {
    if (selected) {
      this.selectedImportIds.add(importId);
    } else {
      this.selectedImportIds.delete(importId);
    }
    this.updateSelectionState();
  }

  toggleAllImports(selectAll: boolean): void {
    this.selectedImportIds.clear();
    if (selectAll) {
      this.activeImports$.pipe(takeUntil(this.destroy$)).subscribe(imports => {
        imports.forEach(imp => this.selectedImportIds.add(imp.id));
        this.updateSelectionState();
      });
    } else {
      this.updateSelectionState();
    }
  }

  private updateSelectionState(): void {
    this.activeImports$.pipe(takeUntil(this.destroy$)).subscribe(imports => {
      const totalImports = imports.length;
      const selectedCount = this.selectedImportIds.size;

      this.allImportsSelected = selectedCount > 0 && selectedCount === totalImports;
      this.someImportsSelected = selectedCount > 0 && selectedCount < totalImports;
    });
  }

  bulkCancelImports(): void {
    const importIds = Array.from(this.selectedImportIds);
    if (importIds.length === 0) return;

    const confirmMessage = `Are you sure you want to cancel ${importIds.length} import(s)? This action cannot be undone.`;
    if (!confirm(confirmMessage)) return;

    this.snackBar.open(`Cancelling ${importIds.length} imports...`, 'Close', { duration: 3000 });

    // Use the existing bulk cancel method from ImportService
    this.importService.bulkCancelImports(importIds).then(results => {
      const successCount = results.filter(r => r.status === 'COMPLETED').length;
      const failCount = results.length - successCount;

      if (failCount === 0) {
        this.snackBar.open(
          `✅ Successfully cancelled ${successCount} imports`,
          'Close',
          { duration: 4000 }
        );
      } else {
        this.snackBar.open(
          `⚠️ Cancelled ${successCount} imports, ${failCount} failed`,
          'Close',
          { duration: 6000 }
        );
      }

      // Clear selection and refresh
      this.selectedImportIds.clear();
      this.updateSelectionState();
      this.importService.refreshActiveImports();
    }).catch(error => {
      console.error('Bulk cancel failed:', error);
      this.snackBar.open(
        `❌ Bulk cancellation failed: ${error.message || 'Unknown error'}`,
        'Close',
        { duration: 8000 }
      );
    });
  }
}
