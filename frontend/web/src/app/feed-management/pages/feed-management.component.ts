import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, NavigationEnd, ActivatedRoute } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { Observable, Subject, combineLatest } from 'rxjs';
import { map, takeUntil, filter, take } from 'rxjs/operators';
import { MetropolitanRegion, Feed, FeedStatus, FeedSpecType } from '../models/region.models';
import { FeedImportSummary } from '../models/import.models';
import { AuthenticationStatistics } from '../models/feed-authentication.model';
import { AgencyFeedGroup, FeedGroupingUtils } from '../models/agency-feed-group.model';
import { RegionService } from '../services/region.service';
import { ImportService } from '../services/import.service';
import { FeedAuthenticationService } from '../services/feed-authentication.service';
import { ImportProgressDialogComponent } from '../components/import-progress-dialog.component';
import { ImportConfirmationDialogComponent } from '../components/import-confirmation-dialog.component';
import { BreadcrumbsComponent } from '../../shared/components/breadcrumbs.component';

@Component({
  selector: 'app-feed-management',
  standalone: false,
  template: `
    <div class="feed-management-container">
      <!-- App Bar -->
      <app-bar
        [activeImportsCount]="(quickStats$ | async)?.activeImports || 0"
        (refresh)="refreshData()"
      ></app-bar>

      <!-- Breadcrumbs (moved below toolbar) -->
      <app-breadcrumbs [tabName]="getCurrentTabName()" [region]="selectedRegion?.name"></app-breadcrumbs>

      <!-- Content Area -->
      <div class="content-area">
            <!-- Consolidated Tab View -->
            <div class="view-container">
              <mat-tab-group
                animationDuration="200ms"
                [(selectedIndex)]="selectedTabIndex"
                (selectedIndexChange)="onTabChange($event)">

                <!-- Feeds Tab -->
                <mat-tab>
                  <ng-template mat-tab-label>
                    <mat-icon class="tab-icon">rss_feed</mat-icon>
                    Feeds
                    <span class="tab-badge" *ngIf="regionFeeds.length > 0">
                      {{ regionFeeds.length }}
                    </span>
                  </ng-template>

                  <div class="tab-content">
                    <!-- Region Selector -->
                    <app-region-selector
                      [regions]="regions"
                      [selectedRegionId]="selectedRegionId"
                      (regionChange)="onRegionChange($event)"
                    ></app-region-selector>

                    <!-- Loading State -->
                    <mat-card *ngIf="loadingFeeds" class="loading-card">
                      <mat-card-content class="loading-content">
                        <mat-spinner diameter="40"></mat-spinner>
                        <p>Loading feeds...</p>
                      </mat-card-content>
                    </mat-card>

                    <!-- Agency Feed Cards (Grouped) -->
                    <div *ngIf="!loadingFeeds && agencyGroups.length > 0" class="feeds-grid">
                      <app-agency-feed-card
                        *ngFor="let agencyGroup of agencyGroups"
                        [agencyGroup]="agencyGroup"
                        (importFeed)="importFeed($event)"
                        (importAllFeeds)="importMultipleFeeds($event)"
                        (viewDetails)="viewFeedDetails($event)">
                      </app-agency-feed-card>
                    </div>

                    <!-- Empty State -->
                    <mat-card *ngIf="!loadingFeeds && regionFeeds.length === 0" class="empty-state">
                      <mat-card-content class="empty-content">
                        <mat-icon class="empty-icon">inbox</mat-icon>
                        <h3>No feeds found</h3>
                        <p>Select a region to view available transit feeds.</p>
                      </mat-card-content>
                    </mat-card>
                  </div>
                </mat-tab>

                <!-- Import History Tab (includes active imports) -->
                <mat-tab>
                  <ng-template mat-tab-label>
                    <mat-icon class="tab-icon">history</mat-icon>
                    Import History
                    <span class="tab-badge" *ngIf="totalImportElements > 0">
                      {{ totalImportElements }}
                    </span>
                    <span class="tab-badge active" *ngIf="(activeImports$ | async)?.length">
                      {{ (activeImports$ | async)?.length }} active
                    </span>
                  </ng-template>

                  <div class="tab-content">
                    <!-- Region Selector -->
                    <app-region-selector
                      [regions]="regions"
                      [selectedRegionId]="selectedRegionId"
                      (regionChange)="onRegionChange($event)"
                    ></app-region-selector>

                    <app-feed-history-tab
                    [loading]="loadingHistory"
                    [history]="importHistory"
                    [totalItems]="totalImportElements"
                    [pageIndex]="importHistoryPage"
                    [pageSize]="importHistorySize"
                    [activeImports$]="activeImports$"
                    [selectedImportIds]="selectedImportIds"
                    [allImportsSelected]="allImportsSelected"
                    [someImportsSelected]="someImportsSelected"
                    (selectAllChange)="toggleAllImports($event)"
                    (selectionChange)="toggleImportSelection($event.id, $event.selected)"
                    (bulkCancel)="bulkCancelImports()"
                    (cancelImport)="cancelImport($event)"
                    (pageChange)="loadImportHistory($event)"
                  ></app-feed-history-tab>
                  </div>
                </mat-tab>
              </mat-tab-group>
            </div>
          </div>
    </div>
  `,
  styles: [`
    .feed-management-container {
      height: 100vh;
      display: flex;
      flex-direction: column;
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
  activeImports$: Observable<FeedImportSummary[]>;
  quickStats$: Observable<any>;
  authStats$: Observable<AuthenticationStatistics>;

  // Component state
  selectedTabIndex = 0; // Track which tab is selected (0=Feeds, 1=Active Imports, 2=History)
  private isProgrammaticTabChange = false;
  regions: MetropolitanRegion[] = [];
  selectedRegion: MetropolitanRegion | null = null;
  selectedRegionId: string | null = null;
  regionFeeds: Feed[] = [];
  agencyGroups: AgencyFeedGroup[] = [];
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
    private router: Router,
    private route: ActivatedRoute
  ) {
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

    // Check current route path to determine initial tab
    const currentPath = this.router.url;
    const isImportsRoute = currentPath.includes('/imports');
    const isRegionsRoute = currentPath.includes('/regions');

    // Determine initial tab based on route
    let initialTabIndex = 0; // Default to Feeds tab
    if (isImportsRoute) {
      initialTabIndex = 1; // Import History tab
    } else if (isRegionsRoute) {
      initialTabIndex = 0; // Feeds tab
    }

    this.selectedTabIndex = initialTabIndex;

    // Load regions first
    this.regionService.listRegions().pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (regions) => {
        console.log('Loaded regions:', regions);
        this.regions = regions;

        // Check for region in URL query params
        const regionParam = this.route.snapshot.queryParamMap.get('region');
        let initialRegion: MetropolitanRegion | undefined;

        if (regionParam) {
          // Try to find the region from URL param
          initialRegion = regions.find(r => r.regionOnestopId === regionParam);
        }

        // Fall back to first region if not found or not specified
        if (!initialRegion && regions.length > 0) {
          initialRegion = regions[0];
        }

        if (initialRegion) {
          this.selectedRegionId = initialRegion.regionOnestopId;
          this.selectedRegion = initialRegion;
          this.loadFeedsForRegion(this.selectedRegionId);

          // Update URL based on current tab
          if (initialTabIndex === 1) {
            // Import History tab - use /feeds/imports route
            if (!isImportsRoute || !regionParam || regionParam !== initialRegion.regionOnestopId) {
              this.router.navigate(['/feeds/imports'], {
                queryParams: { region: initialRegion.regionOnestopId },
                replaceUrl: true
              });
            }
          } else {
            // Feeds tab - use /feeds/regions route
            if (!isRegionsRoute || !regionParam || regionParam !== initialRegion.regionOnestopId) {
              this.router.navigate(['/feeds/regions'], {
                queryParams: { region: initialRegion.regionOnestopId },
                replaceUrl: true
              });
            }
          }
        }

        // Load data for the initial tab
        if (initialTabIndex === 1) {
          this.loadImportHistory();
        }
      },
      error: (error) => {
        console.error('Failed to load regions:', error);
      }
    });

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

  getFeedIcon(specType: FeedSpecType): string {
    switch (specType) {
      case FeedSpecType.GTFS: return 'directions_transit';
      case FeedSpecType.GTFS_RT: return 'real_time_tracking';
      default: return 'feed';
    }
  }

  onTabChange(index: number): void {
    if (this.isProgrammaticTabChange) {
      this.isProgrammaticTabChange = false;
      return;
    }

    // Update URL with tab name
    this.updateUrlWithTab(index);

    switch (index) {
      case 0:
        // Feeds tab - no action needed, feeds already loaded
        break;
      case 1:
        // Import History tab
        this.importService.refreshActiveImports();
        this.loadImportHistory();
        break;
      default:
        break;
    }
  }

  onRegionChange(regionId: string): void {
    this.selectedRegionId = regionId;
    const region = this.regions.find(r => r.regionOnestopId === regionId);
    if (region) {
      this.selectedRegion = region;
      this.loadFeedsForRegion(regionId);
      this.updateUrlWithRegion(regionId);

      // Reload import history if on the import history tab
      if (this.selectedTabIndex === 1) {
        this.loadImportHistory();
      }
    }
  }

  getCurrentTabName(): string {
    switch (this.selectedTabIndex) {
      case 0: return 'Feeds';
      case 1: return 'Import History';
      default: return '';
    }
  }

  private getTabSlug(index: number): string {
    switch (index) {
      case 0: return 'regions';
      case 1: return 'imports';
      default: return 'regions';
    }
  }

  private getTabIndexFromSlug(slug: string | null): number {
    switch (slug) {
      case 'imports': return 1;
      case 'regions':
      default: return 0;
    }
  }

  private updateUrlWithRegion(regionId: string): void {
    // Navigate to appropriate route based on current tab
    if (this.selectedTabIndex === 1) {
      // Import History tab - navigate to /feeds/imports with region param
      this.router.navigate(['/feeds/imports'], {
        queryParams: { region: regionId },
        replaceUrl: true
      });
    } else {
      // Feeds tab - navigate to /feeds/regions with region param
      this.router.navigate(['/feeds/regions'], {
        queryParams: { region: regionId },
        replaceUrl: true
      });
    }
  }

  private updateUrlWithTab(tabIndex: number): void {
    const slug = this.getTabSlug(tabIndex);
    if (slug === 'imports') {
      // Import History tab - navigate to /feeds/imports with region query param
      this.router.navigate(['/feeds/imports'], {
        queryParams: { region: this.selectedRegionId },
        replaceUrl: true
      });
    } else {
      // Feeds tab - navigate to /feeds/regions with region query param
      this.router.navigate(['/feeds/regions'], {
        queryParams: { region: this.selectedRegionId },
        replaceUrl: true
      });
    }
  }

  private setSelectedTab(index: number): void {
    if (this.selectedTabIndex !== index) {
      this.isProgrammaticTabChange = true;
      this.selectedTabIndex = index;
    } else {
      this.isProgrammaticTabChange = false;
    }
  }

  viewFeedDetails(feed: Feed): void {
    this.snackBar.open(`Viewing details for ${feed.name}`, 'Close', { duration: 2000 });
    // In a real app, this would open a dialog or navigate to a details page
  }

  importMultipleFeeds(feeds: Feed[]): void {
    const feedNames = feeds.map(f => f.name).join(', ');
    this.snackBar.open(`Starting imports for ${feeds.length} feeds...`, 'Close', { duration: 2000 });

    // Import all feeds sequentially
    feeds.forEach(feed => this.importFeed(feed));
  }

  importFeed(feed: Feed): void {
    this.snackBar.open(`Starting import for ${feed.name}...`, 'Close', { duration: 2000 });

    this.importService.startImport(feed.feedOnestopId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (result) => {
        // Automatically switch to active imports tab
        this.selectedTabIndex = 1;

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

  refreshData(): void {
    this.snackBar.open('Refreshing data...', 'Close', { duration: 2000 });
    this.regionService.clearCache();
    this.importService.refreshActiveImports();

    // Reload regions
    this.regionService.listRegions().pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (regions) => {
        this.regions = regions;
      },
      error: (error) => {
        console.error('Failed to load regions:', error);
      }
    });

    // Reload feeds for current region
    if (this.selectedRegionId) {
      this.loadFeedsForRegion(this.selectedRegionId);
    }
  }

  private loadFeedsForRegion(onestopId: string): void {
    this.loadingFeeds = true;
    this.regionFeeds = [];
    this.agencyGroups = [];

    // First, try to find the region in our cached regions
    this.regionService.getCachedRegions().pipe(
      takeUntil(this.destroy$)
    ).subscribe(regions => {
      const region = regions?.find(r => r.regionOnestopId === onestopId);
      if (region) {
        this.selectedRegion = region;
      }
    });

    // Fetch feeds from transit.land API via backend
    this.regionService.listFeedsForRegion(onestopId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (feeds) => {
        this.regionFeeds = feeds;
        // Group feeds by agency
        this.agencyGroups = FeedGroupingUtils.sortAgencyGroups(
          FeedGroupingUtils.groupFeedsByAgency(feeds)
        );
        this.loadingFeeds = false;
        const regionName = this.selectedRegion?.name || onestopId;
        this.snackBar.open(
          `Viewing ${feeds.length} feeds from ${this.agencyGroups.length} agencies for ${regionName}`,
          'Close',
          { duration: 2000 }
        );
      },
      error: (error) => {
        console.error('Failed to load feeds:', error);
        this.loadingFeeds = false;
        const regionName = this.selectedRegion?.name || onestopId;
        this.snackBar.open(`Failed to load feeds for ${regionName}`, 'Retry', {
          duration: 5000
        }).onAction().subscribe(() => {
          // Retry loading feeds
          this.loadFeedsForRegion(onestopId);
        });
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
        // Refresh history if on history tab (tab index 2)
        if (this.selectedTabIndex === 2) {
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
    // Load all feeds from all regions for authentication management
    // This could be implemented later if needed for bulk operations
    this.allFeeds = [];
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
      this.activeImports$.pipe(take(1)).subscribe(imports => {
        imports.forEach(imp => this.selectedImportIds.add(imp.id));
        this.updateSelectionState();
      });
    } else {
      this.updateSelectionState();
    }
  }

  private updateSelectionState(): void {
    this.activeImports$.pipe(take(1)).subscribe(imports => {
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
