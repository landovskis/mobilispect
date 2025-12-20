import { Component, Input, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, NavigationEnd, RouterModule, ActivatedRoute, ActivatedRouteSnapshot } from '@angular/router';
import { BreakpointObserver, LayoutModule } from '@angular/cdk/layout';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Observable, Subject, firstValueFrom } from 'rxjs';
import { filter, map, shareReplay, startWith, takeUntil } from 'rxjs/operators';
import { AppBarComponent, Breadcrumb, BreadcrumbSelection } from './app-bar.component';
import { ImportService } from '../../feeds/services/import.service';
import { FeedsMetricsService } from '../../feeds/services/feeds-metrics.service';
import { FeedsEventsService } from '../../feeds/services/feeds-events.service';
import { RegionService } from '../../feeds/services/region.service';
import { ThemeToggleComponent } from './theme-toggle.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    LayoutModule,
    MatSidenavModule,
    MatIconModule,
    MatButtonModule,
    MatSnackBarModule,
    AppBarComponent,
    ThemeToggleComponent
  ],
  template: `
    <div class="feeds-container">
      <app-bar
        [breadcrumbs]="breadcrumbs"
        (refresh)="onRefresh()"
        (breadcrumbSelected)="onBreadcrumbSelected($event)"
        >
        <div toolbar-actions>
          @if (isHandset$ | async) {
            <button
              mat-icon-button
              type="button"
              aria-label="Toggle navigation"
              (click)="toggleSidenav()">
              <mat-icon>menu</mat-icon>
            </button>
          }
          <app-theme-toggle></app-theme-toggle>
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

            <div class="sidebar-heading">Regions</div>
            <button
              type="button"
              class="sidebar-link"
              routerLink="/regions"
              routerLinkActive="active">
              <mat-icon>map</mat-icon>
              <span>List</span>
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
      border-right: 1px solid #E1F3FF;
      padding: 28px 18px 32px;
      background: #ffffff;
      color: #0B3558;
    }

    .sidebar-nav {
      display: flex;
      flex-direction: column;
      gap: 16px;
      position: sticky;
      top: 96px;
    }

    .sidebar-heading {
      font-size: 0.82rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: #6B7280;
      margin-bottom: 4px;
      padding: 0 6px;
    }

    .sidebar-link {
      display: flex;
      align-items: center;
      gap: 10px;
      width: 100%;
      padding: 12px 16px;
      border-radius: 12px;
      border: 1px solid #D1D5DB;
      background: #fff;
      color: #0B4F8A;
      font-weight: 600;
      text-align: left;
      transition: all 0.2s ease;
      box-shadow: 0 6px 14px rgba(11, 79, 138, 0.05);
    }

    .sidebar-link mat-icon {
      font-size: 20px;
      color: #0B4F8A;
    }

    .sidebar-link .nav-count {
      margin-left: auto;
      padding: 2px 10px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 600;
      background: #E1F3FF;
      color: #0B4F8A;
    }

    .sidebar-link .nav-count.active {
      background: rgba(0, 167, 196, 0.15);
      color: #0B3558;
    }

    .sidebar-link:hover {
      border-color: #00A7C4;
      color: #0B3558;
      box-shadow: 0 10px 24px rgba(0, 167, 196, 0.18);
      transform: translateY(-1px);
    }

    .sidebar-link:hover mat-icon {
      color: #0B3558;
    }

    .sidebar-link.active {
      background: linear-gradient(90deg, #0B4F8A 0%, #0B4F8A 60%, #00A7C4 100%);
      color: #E5F1FF;
      box-shadow: 0 12px 28px rgba(11, 79, 138, 0.28);
      border-color: transparent;
    }

    .sidebar-link.active mat-icon {
      color: #E5F1FF;
    }

    .sidebar-link.active .nav-count {
      background: rgba(255, 255, 255, 0.2);
      color: #E5F1FF;
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

    :host-context(.dark-theme) .drawer-container {
      background: #0b1220;
    }

    :host-context(.dark-theme) .app-sidenav {
      background: #0f172a;
      border-color: rgba(148, 163, 184, 0.24);
      color: #e5f1ff;
    }

    :host-context(.dark-theme) .sidebar-heading {
      color: rgba(226, 232, 240, 0.8);
    }

    :host-context(.dark-theme) .sidebar-link {
      background: #111827;
      color: #e5f1ff;
      border-color: rgba(148, 163, 184, 0.24);
      box-shadow: 0 10px 20px rgba(0, 0, 0, 0.35);
    }

    :host-context(.dark-theme) .sidebar-link mat-icon {
      color: #cbd5e1;
    }

    :host-context(.dark-theme) .sidebar-link .nav-count {
      background: rgba(0, 167, 196, 0.18);
      color: #e5f1ff;
    }

    :host-context(.dark-theme) .sidebar-link .nav-count.active {
      background: rgba(0, 167, 196, 0.3);
      color: #e5f1ff;
    }

    :host-context(.dark-theme) .sidebar-link.active {
      background: linear-gradient(90deg, #0B4F8A 0%, #0B4F8A 60%, #00A7C4 100%);
      border-color: transparent;
      color: #E5F1FF;
      box-shadow: 0 12px 28px rgba(11, 79, 138, 0.5);
    }

    :host-context(.dark-theme) .sidebar-link.active mat-icon {
      color: #E5F1FF;
    }

    :host-context(.dark-theme) .sidebar-link.active .nav-count {
      background: rgba(11, 18, 32, 0.1);
      color: #0b1220;
    }

    :host-context(.dark-theme) .content-area {
      background-color: #0b1220;
    }

    :host-context(.dark-theme) .view-content {
      background: #0f172a;
      color: #e2e8f0;
      box-shadow: 0 12px 30px rgba(0, 0, 0, 0.35);
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
export class AppShellComponent implements OnDestroy {
  private readonly destroy$ = new Subject<void>();

  @Input() breadcrumbs: Breadcrumb[] = [];
  sidebarOpened = false;

  readonly isHandset$: Observable<boolean>;
  readonly discoverFeedCount$: Observable<number>;
  readonly totalImportElements$: Observable<number>;
  readonly activeImportCount$: Observable<number>;

  constructor(
    private readonly router: Router,
    private readonly activatedRoute: ActivatedRoute,
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
      map((imports: any[] | null | undefined) => imports?.length ?? 0)
    );

    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      startWith(null),
      takeUntil(this.destroy$),
      map(() => this.buildBreadcrumbsFromRoute(this.activatedRoute.snapshot))
    ).subscribe(crumbs => {
      this.breadcrumbs = crumbs.length
        ? crumbs
        : [{ id: 'feeds', label: 'Feeds', link: ['/feeds/discover'] }];
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

  private buildBreadcrumbsFromRoute(
    route: ActivatedRouteSnapshot,
    url: string = '',
    crumbs: Breadcrumb[] = []
  ): Breadcrumb[] {
    const children = route.children.filter(child => child.outlet === 'primary');

    if (!children.length) {
      return crumbs;
    }

    const [child] = children;
    if (!child) return crumbs;

    const routeURL = child.url.map(segment => segment.path).join('/');
    const nextUrl = routeURL ? `${url}/${routeURL}` : url;
    const label =
      child.data['breadcrumb'];

    if (label) {
      crumbs.push({
        id: child.routeConfig?.path ?? label,
        label,
        link: [nextUrl || '/']
      });
    }

    return this.buildBreadcrumbsFromRoute(child, nextUrl, crumbs);
  }
}
