import { Component, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, NavigationEnd, RouterModule } from '@angular/router';
import { BreakpointObserver, LayoutModule } from '@angular/cdk/layout';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Observable, Subject, combineLatest, firstValueFrom } from 'rxjs';
import { filter, map, shareReplay, startWith, takeUntil } from 'rxjs/operators';
import { AppBarComponent, Breadcrumb, BreadcrumbSelection } from '../../shared/components/app-bar.component';
import { ImportService } from '../services/import.service';
import { FeedsMetricsService } from '../services/feeds-metrics.service';
import { FeedsEventsService } from '../services/feeds-events.service';
import { RegionService } from '../services/region.service';

@Component({
  selector: 'app-feeds-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    LayoutModule,
    MatSidenavModule,
    MatIconModule,
    MatButtonModule,
    MatSnackBarModule,
    AppBarComponent
  ],
  template: `
    <div class="feeds-container">
      <app-bar
        [breadcrumbs]="breadcrumbs"
        (refresh)="onRefresh()"
        (breadcrumbSelected)="onBreadcrumbSelected($event)"
      >
        <div toolbar-actions>
          <button
            *ngIf="isHandset$ | async"
            mat-icon-button
            type="button"
            aria-label="Toggle navigation"
            (click)="toggleSidenav()">
            <mat-icon>menu</mat-icon>
          </button>
        </div>
      </app-bar>

      <mat-sidenav-container class="drawer-container">
        <mat-sidenav
          class="app-sidenav"
          [mode]="(isHandset$ | async) ? 'over' : 'side'"
          [opened]="(isHandset$ | async) ? sidebarOpened : true"
          (openedChange)="onSidenavOpenedChange($event)">
          <nav class="sidebar-nav" aria-label="Feed navigation">
            <div class="sidebar-heading">Feeds</div>

            <button
              type="button"
              class="sidebar-link"
              routerLink="/feeds/discover"
              routerLinkActive="active"
              [routerLinkActiveOptions]="{ exact: true }">
              <mat-icon>rss_feed</mat-icon>
              <span>Discover</span>
              @let discoverCount = discoverFeedCount$ | async;
              @if ((discoverCount ?? 0) > 0) {
                <span class="nav-count">{{ discoverCount }}</span>
              }
            </button>

            <button
              type="button"
              class="sidebar-link"
              routerLink="/feeds/imports"
              routerLinkActive="active">
              <mat-icon>history</mat-icon>
              <span>Imports</span>
              @let totalImports = totalImportElements$ | async;
              @if ((totalImports ?? 0) > 0) {
                <span class="nav-count">{{ totalImports }}</span>
              }
              @let activeImports = activeImportCount$ | async;
              @if ((activeImports ?? 0) > 0) {
                <span class="nav-count active">{{ activeImports }} active</span>
              }
            </button>
          </nav>
        </mat-sidenav>

        <mat-sidenav-content>
          <div class="content-area">
            <div class="view-container">
              <section class="view-content">
                <router-outlet></router-outlet>
              </section>
            </div>
          </div>
        </mat-sidenav-content>
      </mat-sidenav-container>
    </div>
  `,
  styles: [`
    .feeds-container {
      height: 100vh;
      display: flex;
      flex-direction: column;
    }

    .drawer-container {
      flex: 1;
      height: calc(100vh - 64px);
      background: transparent;
    }

    .app-sidenav {
      width: 240px;
      border-right: 1px solid rgba(15, 23, 42, 0.08);
      padding: 24px 16px;
      background: #fff;
    }

    .sidebar-nav {
      display: flex;
      flex-direction: column;
      gap: 12px;
      position: sticky;
      top: 96px;
    }

    .sidebar-heading {
      font-size: 0.85rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: rgba(15, 23, 42, 0.6);
      margin-bottom: 4px;
    }

    .sidebar-link {
      display: flex;
      align-items: center;
      gap: 10px;
      width: 100%;
      padding: 12px 16px;
      border-radius: 12px;
      border: 1px solid rgba(41, 128, 185, 0.25);
      background: #fff;
      color: #2980B9;
      font-weight: 600;
      text-align: left;
      transition: all 0.2s ease;
    }

    .sidebar-link mat-icon {
      font-size: 20px;
    }

    .sidebar-link .nav-count {
      margin-left: auto;
      padding: 2px 10px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 600;
      background: rgba(41, 128, 185, 0.08);
      color: #2980B9;
    }

    .sidebar-link .nav-count.active {
      background: rgba(33, 150, 243, 0.15);
      color: #0c4a6e;
    }

    .sidebar-link.active {
      background: #2980B9;
      color: #fff;
      box-shadow: 0 10px 25px rgba(41, 128, 185, 0.25);
      border-color: transparent;
    }

    .sidebar-link.active .nav-count {
      background: rgba(255, 255, 255, 0.2);
      color: #fff;
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

    .view-content {
      background: #fff;
      border-radius: 16px;
      box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
      padding: 24px;
      min-height: calc(100vh - 160px);
    }

    @media (max-width: 768px) {
      .content-area {
        padding: 16px;
      }

      .drawer-container {
        height: auto;
      }

      .app-sidenav {
        width: 100%;
        border-right: none;
        border-bottom: 1px solid rgba(15, 23, 42, 0.08);
      }

      .sidebar-nav {
        position: static;
        flex-direction: column;
      }

      .sidebar-link {
        flex: none;
        width: 100%;
      }

      .view-content {
        padding: 16px;
      }
    }
  `]
})
export class FeedsShellComponent implements OnDestroy {
  private readonly destroy$ = new Subject<void>();

  breadcrumbs: Breadcrumb[] = [{ id: 'feeds', label: 'Feeds', link: ['/feeds/discover'] }];
  sidebarOpened = false;

  readonly isHandset$: Observable<boolean>;
  readonly discoverFeedCount$: Observable<number>;
  readonly totalImportElements$: Observable<number>;
  readonly activeImportCount$: Observable<number>;

  constructor(
    private readonly router: Router,
    private readonly snackBar: MatSnackBar,
    breakpointObserver: BreakpointObserver,
    private readonly importService: ImportService,
    private readonly metrics: FeedsMetricsService,
    private readonly events: FeedsEventsService,
    private readonly regionService: RegionService
  ) {
    this.isHandset$ = breakpointObserver.observe('(max-width: 768px)').pipe(
      map(result => result.matches),
      shareReplay({ bufferSize: 1, refCount: true })
    );
    this.discoverFeedCount$ = this.metrics.discoverFeedCount$;
    this.totalImportElements$ = this.metrics.totalImportElements$;
    this.activeImportCount$ = this.importService.getActiveImportsObservable().pipe(
      map(imports => imports?.length || 0)
    );

    combineLatest([
      this.metrics.selectedRegion$,
      this.router.events.pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        startWith(null)
      )
    ]).pipe(
      takeUntil(this.destroy$),
      map(([region]) => this.buildBreadcrumbs(region))
    ).subscribe(crumbs => {
      this.breadcrumbs = crumbs;
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onRefresh(): void {
    this.snackBar.open('Refreshing data...', 'Close', { duration: 2000 });
    this.regionService.clearCache();
    this.importService.refreshActiveImports();
    this.events.triggerRefresh();
  }

  onBreadcrumbSelected(selection: BreadcrumbSelection): void {
    if (selection.breadcrumb.id === 'feeds') {
      selection.originalEvent.preventDefault();
      selection.originalEvent.stopPropagation();
      this.metrics.resetSelectedRegion();
      this.router.navigate(['/feeds/discover']);
    }
  }

  async toggleSidenav(): Promise<void> {
    const isHandset = await firstValueFrom(this.isHandset$);
    if (!isHandset) return;
    this.sidebarOpened = !this.sidebarOpened;
  }

  onSidenavOpenedChange(opened: boolean): void {
    this.sidebarOpened = opened;
  }

  private buildBreadcrumbs(region: { id: string | null; name: string | null } | null): Breadcrumb[] {
    const crumbs: Breadcrumb[] = [
      {
        id: 'feeds',
        label: 'Feeds',
        link: ['/feeds/discover']
      }
    ];

    if (region?.id) {
      crumbs.push({
        id: 'region',
        label: region.name ?? region.id,
        link: ['/feeds/discover'],
        queryParams: { region: region.id }
      });
    }

    return crumbs;
  }
}
