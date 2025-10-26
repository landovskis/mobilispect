import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatBadgeModule } from '@angular/material/badge';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatTabsModule } from '@angular/material/tabs';
import { Observable, Subject, BehaviorSubject, combineLatest } from 'rxjs';
import { map, takeUntil, filter } from 'rxjs/operators';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { MetropolitanRegion, Feed } from '../models/region.models';
import { ImportSummary } from '../models/import.models';
import { RegionListComponent } from '../components/region-list.component';
import { ImportDialogComponent, ImportDialogData } from '../components/import-dialog.component';
import { RegionService } from '../services/region.service';
import { ImportService } from '../services/import.service';

/**
 * Feed Management Page Component
 *
 * Main page for feed management operations. Provides a dashboard-style
 * interface for region selection, feed import management, and progress monitoring.
 *
 * Constitutional Compliance:
 * - UX Consistency: Material Design 3 with responsive layout patterns
 * - Cross-Platform: Mobile-first responsive design
 * - Accessibility: Full keyboard navigation and screen reader support
 * - Performance: Efficient data loading and virtual scrolling for large lists
 * - Observability: Real-time status updates and progress monitoring
 */
@Component({
  selector: 'app-feed-management',
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
    MatDialogModule,
    MatTabsModule,
    RegionListComponent
  ],
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

          <mat-nav-list class="navigation-list">
            <a
              mat-list-item
              [class.active]="currentView === 'regions'"
              (click)="navigateToView('regions')"
              [attr.aria-label]="'View regions'"
            >
              <mat-icon matListItemIcon>location_on</mat-icon>
              <span matListItemTitle>Regions</span>
              <span matListItemLine>Select transit regions</span>
            </a>

            <a
              mat-list-item
              [class.active]="currentView === 'imports'"
              (click)="navigateToView('imports')"
              [attr.aria-label]="'View active imports with ' + (activeImports$ | async)?.length + ' active'"
            >
              <mat-icon
                matListItemIcon
                [matBadge]="(activeImports$ | async)?.length || 0"
                [matBadgeHidden]="((activeImports$ | async)?.length || 0) === 0"
                matBadgeColor="accent"
                matBadgeSize="small"
              >
                download
              </mat-icon>
              <span matListItemTitle>Active Imports</span>
              <span matListItemLine>Monitor progress</span>
            </a>

            <a
              mat-list-item
              [class.active]="currentView === 'history'"
              (click)="navigateToView('history')"
              [attr.aria-label]="'View import history'"
            >
              <mat-icon matListItemIcon>history</mat-icon>
              <span matListItemTitle>Import History</span>
              <span matListItemLine>View past imports</span>
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

          <!-- Quick Stats -->
          <div class="sidenav-footer">
            <div class="quick-stats" *ngIf="quickStats$ | async as stats">
              <div class="stat-item">
                <mat-icon>location_on</mat-icon>
                <span>{{ stats.totalRegions }} regions</span>
              </div>
              <div class="stat-item">
                <mat-icon>feed</mat-icon>
                <span>{{ stats.totalFeeds }} feeds</span>
              </div>
              <div class="stat-item">
                <mat-icon [ngClass]="{ 'active-import': stats.activeImports > 0 }">
                  download
                </mat-icon>
                <span>{{ stats.activeImports }} active</span>
              </div>
            </div>
          </div>
        </mat-sidenav>

        <!-- Main Content -->
        <mat-sidenav-content class="main-content">
          <!-- Toolbar -->
          <mat-toolbar class="content-toolbar" color="primary">
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
                <h1>{{ getViewTitle() }}</h1>
                <span class="subtitle" *ngIf="selectedRegion">{{ selectedRegion.name }}</span>
              </div>

              <div class="toolbar-actions">
                <!-- Region-specific actions -->
                <button
                  mat-raised-button
                  color="accent"
                  *ngIf="currentView === 'regions' && selectedRegion"
                  (click)="openImportDialog()"
                  [disabled]="selectedRegion.feedCount === 0"
                  [attr.aria-label]="'Import feeds for ' + selectedRegion.name"
                >
                  <mat-icon>play_arrow</mat-icon>
                  Import Feeds
                </button>

                <!-- Active imports actions -->
                <button
                  mat-button
                  *ngIf="currentView === 'imports' && (activeImports$ | async)?.length"
                  (click)="pauseAllImports()"
                  [attr.aria-label]="'Pause all active imports'"
                >
                  <mat-icon>pause</mat-icon>
                  Pause All
                </button>
              </div>
            </div>
          </mat-toolbar>

          <!-- Content Area -->
          <div class="content-area">
            <!-- Regions View -->
            <div *ngIf="currentView === 'regions'" class="view-content">
              <app-region-list
                [selectedRegion]="selectedRegion"
                (regionSelected)="onRegionSelected($event)"
                (regionDetailsRequested)="onRegionDetailsRequested($event)"
              ></app-region-list>
            </div>

            <!-- Active Imports View -->
            <div *ngIf="currentView === 'imports'" class="view-content">
              <div class="active-imports-view">
                <div class="import-summary" *ngIf="(activeImports$ | async)?.length === 0">
                  <mat-icon>download_done</mat-icon>
                  <h3>No Active Imports</h3>
                  <p>All import operations are complete. Select a region to start new imports.</p>
                  <button mat-raised-button color="primary" (click)="navigateToView('regions')">
                    <mat-icon>location_on</mat-icon>
                    Browse Regions
                  </button>
                </div>

                <!-- Active Import Cards -->
                <div class="active-imports-grid" *ngIf="(activeImports$ | async)?.length">
                  <mat-card
                    *ngFor="let importItem of activeImports$ | async; trackBy: trackByImportId"
                    class="import-card"
                  >
                    <mat-card-header>
                      <mat-card-title>{{ importItem.feedName }}</mat-card-title>
                      <mat-card-subtitle>{{ importItem.regionName }}</mat-card-subtitle>
                    </mat-card-header>

                    <mat-card-content>
                      <div class="import-progress" *ngIf="importItem.progress">
                        <div class="progress-info">
                          <span class="progress-step">{{ importItem.progress.currentStep }}</span>
                          <span class="progress-percentage">{{ importItem.progress.progressPercentage }}%</span>
                        </div>
                        <mat-progress-bar
                          mode="determinate"
                          [value]="importItem.progress.progressPercentage"
                        ></mat-progress-bar>
                        <div class="progress-details" *ngIf="importItem.progress.estimatedTimeRemainingSeconds">
                          <small>{{ formatTimeRemaining(importItem.progress.estimatedTimeRemainingSeconds) }} remaining</small>
                        </div>
                      </div>

                      <div class="import-meta">
                        <div class="meta-item">
                          <mat-icon>schedule</mat-icon>
                          <span>Started {{ formatStartTime(importItem.startedAt) }}</span>
                        </div>
                        <div class="meta-item">
                          <mat-icon>person</mat-icon>
                          <span>{{ importItem.triggerType === 'manual' ? 'Manual' : 'Automatic' }}</span>
                        </div>
                      </div>
                    </mat-card-content>

                    <mat-card-actions align="end">
                      <button
                        mat-button
                        color="warn"
                        (click)="cancelImport(importItem.id)"
                        [attr.aria-label]="'Cancel import for ' + importItem.feedName"
                      >
                        <mat-icon>stop</mat-icon>
                        Cancel
                      </button>
                      <button
                        mat-button
                        (click)="viewImportDetails(importItem.id)"
                        [attr.aria-label]="'View details for ' + importItem.feedName + ' import'"
                      >
                        <mat-icon>info</mat-icon>
                        Details
                      </button>
                    </mat-card-actions>
                  </mat-card>
                </div>
              </div>
            </div>

          </div>
        </mat-sidenav-content>
      </mat-sidenav-container>
    </div>
  `,
  styles: [`
    .feed-management-container {
      height: 100vh;
      overflow: hidden;
    }

    .sidenav-container {
      height: 100%;
    }

    .sidenav {
      width: 280px;
      background-color: var(--mdc-theme-surface);
      border-right: 1px solid var(--mdc-theme-outline-variant);
    }

    .sidenav-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px;
      background-color: var(--mdc-theme-primary-container);
      color: var(--mdc-theme-on-primary-container);
    }

    .sidenav-header h2 {
      margin: 0;
      font-size: 18px;
      font-weight: 500;
    }

    .drawer-toggle {
      color: var(--mdc-theme-on-primary-container);
    }

    .navigation-list {
      padding-top: 8px;
    }

    .navigation-list a {
      color: var(--mdc-theme-on-surface);
      text-decoration: none;
    }

    .navigation-list a.active {
      background-color: var(--mdc-theme-primary-container);
      color: var(--mdc-theme-on-primary-container);
    }

    .navigation-list a:hover {
      background-color: var(--mdc-theme-surface-variant);
    }

    .sidenav-footer {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      padding: 16px;
      border-top: 1px solid var(--mdc-theme-outline-variant);
      background-color: var(--mdc-theme-surface);
    }

    .quick-stats {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .stat-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
      color: var(--mdc-theme-on-surface-variant);
    }

    .stat-item mat-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }

    .active-import {
      color: var(--mdc-theme-secondary) !important;
      animation: pulse 2s infinite;
    }

    @keyframes pulse {
      0% { opacity: 1; }
      50% { opacity: 0.5; }
      100% { opacity: 1; }
    }

    .main-content {
      display: flex;
      flex-direction: column;
      height: 100%;
    }

    .content-toolbar {
      flex-shrink: 0;
      position: relative;
      z-index: 2;
    }

    .menu-button {
      margin-right: 16px;
    }

    .toolbar-content {
      display: flex;
      justify-content: space-between;
      align-items: center;
      width: 100%;
    }

    .toolbar-title h1 {
      margin: 0;
      font-size: 20px;
      font-weight: 500;
    }

    .subtitle {
      font-size: 14px;
      opacity: 0.8;
      margin-left: 8px;
    }

    .toolbar-actions {
      display: flex;
      gap: 8px;
    }

    .content-area {
      flex: 1;
      overflow: auto;
      background-color: var(--mdc-theme-surface);
    }

    .view-content {
      height: 100%;
      padding: 0;
    }

    .active-imports-view {
      padding: 16px;
      max-width: 1200px;
      margin: 0 auto;
    }

    .import-summary {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 48px 16px;
      text-align: center;
      color: var(--mdc-theme-on-surface-variant);
    }

    .import-summary mat-icon {
      font-size: 64px;
      width: 64px;
      height: 64px;
      margin-bottom: 16px;
      opacity: 0.5;
    }

    .import-summary h3 {
      margin: 0 0 8px 0;
      font-size: 24px;
      color: var(--mdc-theme-on-surface);
    }

    .import-summary p {
      margin: 0 0 24px 0;
      max-width: 400px;
    }

    .active-imports-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
      gap: 16px;
    }

    .import-card {
      border: 1px solid var(--mdc-theme-outline-variant);
    }

    .import-progress {
      margin-bottom: 16px;
    }

    .progress-info {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
    }

    .progress-step {
      font-size: 14px;
      color: var(--mdc-theme-on-surface);
    }

    .progress-percentage {
      font-size: 14px;
      font-weight: 500;
      color: var(--mdc-theme-primary);
    }

    .progress-details {
      margin-top: 4px;
      text-align: center;
    }

    .progress-details small {
      color: var(--mdc-theme-on-surface-variant);
    }

    .import-meta {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .meta-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      color: var(--mdc-theme-on-surface-variant);
    }

    .meta-item mat-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }


    /* Responsive Design */
    @media (max-width: 768px) {
      .sidenav {
        width: 100vw;
      }

      .active-imports-grid {
        grid-template-columns: 1fr;
        gap: 12px;
      }

      .toolbar-content {
        flex-direction: column;
        align-items: flex-start;
        gap: 8px;
      }

      .toolbar-actions {
        width: 100%;
        justify-content: flex-end;
      }
    }

    @media (max-width: 480px) {
      .active-imports-view {
        padding: 12px;
      }

      .import-summary {
        padding: 24px 12px;
      }
    }
  `]
})
export class FeedManagementComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  // View state
  currentView: 'regions' | 'imports' | 'history' = 'regions';
  selectedRegion: MetropolitanRegion | null = null;

  // Responsive design
  isDesktop$: Observable<boolean>;

  // Data streams
  activeImports$ = new BehaviorSubject<ImportSummary[]>([]);
  quickStats$: Observable<{
    totalRegions: number;
    totalFeeds: number;
    activeImports: number;
  }>;

  constructor(
    private regionService: RegionService,
    private importService: ImportService,
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

    // Setup quick stats
    this.quickStats$ = combineLatest([
      this.regionService.getCachedRegions(),
      this.activeImports$
    ]).pipe(
      map(([regions, activeImports]) => ({
        totalRegions: regions?.length || 0,
        totalFeeds: regions?.reduce((sum, region) => sum + region.feedCount, 0) || 0,
        activeImports: activeImports.length
      }))
    );
  }

  ngOnInit(): void {
    // Handle route-based view switching
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.updateCurrentViewFromRoute();
    });

    // Initial view setup
    this.updateCurrentViewFromRoute();

    // Load initial data
    this.loadActiveImports();

    // Start polling for real-time updates
    this.importService.startPollingActiveImports();

    // Subscribe to active imports updates
    this.importService.getActiveImportsObservable().pipe(
      takeUntil(this.destroy$)
    ).subscribe(imports => {
      this.activeImports$.next(imports);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.importService.stopPollingActiveImports();
  }

  private loadActiveImports(): void {
    this.importService.getActiveImports().pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (imports) => {
        this.activeImports$.next(imports);
      },
      error: (error) => {
        console.error('Failed to load active imports:', error);
        this.snackBar.open('Failed to load active imports', 'Close', { duration: 5000 });
      }
    });
  }

  setCurrentView(view: 'regions' | 'imports' | 'history'): void {
    this.currentView = view;
  }

  navigateToView(view: 'regions' | 'imports' | 'history'): void {
    if (view === 'history') {
      // Navigate to the standalone history page
      this.router.navigate(['history'], { relativeTo: this.route });
    } else {
      this.router.navigate([view], { relativeTo: this.route });
    }
  }

  private updateCurrentViewFromRoute(): void {
    const routeData = this.route.firstChild?.snapshot?.data;
    if (routeData?.['view']) {
      this.currentView = routeData['view'];
    } else {
      // Default to regions if no specific view is set
      const url = this.router.url;
      if (url.includes('/imports')) {
        this.currentView = 'imports';
      } else {
        this.currentView = 'regions';
      }
    }
  }

  getViewTitle(): string {
    switch (this.currentView) {
      case 'regions':
        return 'Regional Transit Feeds';
      case 'imports':
        return 'Active Imports';
      case 'history':
        return 'Import History';
      default:
        return 'Feed Management';
    }
  }

  onRegionSelected(region: MetropolitanRegion): void {
    this.selectedRegion = region;
  }

  onRegionDetailsRequested(region: MetropolitanRegion): void {
    // TODO: Implement region details dialog
    this.snackBar.open(`Region details for ${region.name} - Coming soon!`, 'Close', { duration: 3000 });
  }

  openImportDialog(): void {
    if (!this.selectedRegion) return;

    const dialogData: ImportDialogData = {
      region: this.selectedRegion
    };

    const dialogRef = this.dialog.open(ImportDialogComponent, {
      width: '800px',
      maxWidth: '95vw',
      maxHeight: '90vh',
      data: dialogData,
      disableClose: false
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result?.success) {
        this.snackBar.open(
          `Started ${result.importsStarted} import${result.importsStarted === 1 ? '' : 's'}`,
          'View Progress',
          { duration: 5000 }
        ).onAction().subscribe(() => {
          this.navigateToView('imports');
        });

        // Refresh active imports
        this.loadActiveImports();
      }
    });
  }

  cancelImport(importId: string): void {
    this.importService.cancelImport(importId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.snackBar.open('Import cancelled successfully', 'Close', { duration: 3000 });
        this.loadActiveImports();
      },
      error: (error) => {
        console.error('Failed to cancel import:', error);
        this.snackBar.open('Failed to cancel import', 'Close', { duration: 5000 });
      }
    });
  }

  pauseAllImports(): void {
    // TODO: Implement bulk pause functionality
    this.snackBar.open('Bulk pause feature - Coming soon!', 'Close', { duration: 3000 });
  }

  viewImportDetails(importId: string): void {
    // TODO: Implement import details dialog
    this.snackBar.open('Import details - Coming soon!', 'Close', { duration: 3000 });
  }

  refreshData(): void {
    this.regionService.clearCache();
    this.loadActiveImports();
    this.snackBar.open('Data refreshed', 'Close', { duration: 2000 });
  }

  trackByImportId(index: number, importItem: ImportSummary): string {
    return importItem.id;
  }

  formatTimeRemaining(seconds: number): string {
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}m`;
    const hours = Math.floor(minutes / 60);
    return `${hours}h ${minutes % 60}m`;
  }

  formatStartTime(startedAt: string | null): string {
    if (!startedAt) return 'Unknown';

    const startTime = new Date(startedAt);
    const now = new Date();
    const diffMs = now.getTime() - startTime.getTime();
    const diffMinutes = Math.floor(diffMs / (1000 * 60));

    if (diffMinutes < 1) return 'Just now';
    if (diffMinutes < 60) return `${diffMinutes}m ago`;

    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) return `${diffHours}h ago`;

    return startTime.toLocaleDateString();
  }
}