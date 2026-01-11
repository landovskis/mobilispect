import { Component, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { BreakpointObserver, LayoutModule } from '@angular/cdk/layout';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Observable, Subject, firstValueFrom } from 'rxjs';
import { map, shareReplay } from 'rxjs/operators';
import { AppBarComponent, BreadcrumbSelection } from './app-bar.component';
import { ImportService } from '../../feeds/services/import.service';
import { FeedsMetricsService } from '../../feeds/services/feeds-metrics.service';
import { FeedsEventsService } from '../../feeds/services/feeds-events.service';
import { RegionService } from '../../feeds/services/region.service';
import { ThemeToggleComponent } from './theme-toggle.component';
import { FeedImportSummary } from '../../feeds/models';

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
    ThemeToggleComponent,
  ],
  template: `
    <div class="flex min-h-screen flex-col">
      <app-bar
        (refresh)="onRefresh()"
        (breadcrumbSelected)="onBreadcrumbSelected($event)"
      >
        <div toolbar-actions>
          @if (isHandset$ | async) {
            <button
              mat-icon-button
              type="button"
              aria-label="Toggle navigation"
              (click)="toggleSidenav()"
            >
              <mat-icon>menu</mat-icon>
            </button>
          }
          <app-theme-toggle></app-theme-toggle>
        </div>
      </app-bar>

      <mat-sidenav-container
        class="flex-1 bg-transparent md:h-[calc(100vh-64px)] max-md:h-auto"
      >
        <mat-sidenav
          class="app-sidenav w-60 border-r border-[#E1F3FF] bg-white px-[18px] pb-8 pt-7 text-[#0B3558] max-md:w-full max-md:border-b max-md:border-r-0"
          [mode]="(isHandset$ | async) ? 'over' : 'side'"
          [opened]="(isHandset$ | async) ? sidebarOpened : true"
          (openedChange)="onSidenavOpenedChange($event)"
        >
          <nav
            class="flex flex-col gap-4 sticky top-24 max-md:static"
            aria-label="Feed navigation"
          >
            <div
              class="mb-1 px-1.5 text-xs font-semibold uppercase tracking-[0.08em] text-[#6B7280]"
            >
              Inspect
            </div>

            <button
              type="button"
              class="sidebar-link flex w-full items-center gap-2.5 rounded-xl border border-[#D1D5DB] bg-white px-4 py-3 text-left font-semibold text-[#0B4F8A] shadow-[0_6px_14px_rgba(11,79,138,0.05)] transition-all duration-200"
              routerLink="/regions/discover"
              routerLinkActive="active"
              [routerLinkActiveOptions]="{ exact: true }"
            >
              <mat-icon class="text-[20px] text-[#0B4F8A]">rss_feed</mat-icon>
              <span>Discover Regions</span>
              @let discoverCount = discoverFeedCount$ | async;
              @if ((discoverCount ?? 0) > 0) {
                <span
                  class="nav-count ml-auto rounded-full px-2.5 py-0.5 text-xs font-semibold"
                  >{{ discoverCount }}</span
                >
              }
            </button>

            <button
              type="button"
              class="sidebar-link flex w-full items-center gap-2.5 rounded-xl border border-[#D1D5DB] bg-white px-4 py-3 text-left font-semibold text-[#0B4F8A] shadow-[0_6px_14px_rgba(11,79,138,0.05)] transition-all duration-200"
              routerLink="/feeds/imports"
              routerLinkActive="active"
            >
              <mat-icon class="text-[20px] text-[#0B4F8A]">history</mat-icon>
              <span>Feed Imports</span>
              @let totalImports = totalImportElements$ | async;
              @if ((totalImports ?? 0) > 0) {
                <span
                  class="nav-count ml-auto rounded-full px-2.5 py-0.5 text-xs font-semibold"
                  >{{ totalImports }}</span
                >
              }
              @let activeImports = activeImportCount$ | async;
              @if ((activeImports ?? 0) > 0) {
                <span
                  class="nav-count active ml-auto rounded-full px-2.5 py-0.5 text-xs font-semibold"
                  >{{ activeImports }} active</span
                >
              }
            </button>

            <button
              type="button"
              class="sidebar-link flex w-full items-center gap-2.5 rounded-xl border border-[#D1D5DB] bg-white px-4 py-3 text-left font-semibold text-[#0B4F8A] shadow-[0_6px_14px_rgba(11,79,138,0.05)] transition-all duration-200"
              routerLink="/regions"
              routerLinkActive="active"
            >
              <mat-icon class="text-[20px] text-[#0B4F8A]">map</mat-icon>
              <span>Regions</span>
            </button>
          </nav>
        </mat-sidenav>

        <mat-sidenav-content>
          <div
            class="content-area min-h-[calc(100vh-64px)] bg-[#fafafa] p-6 max-md:p-4"
          >
            <div class="mx-auto max-w-[1200px]">
              <section
                class="view-content min-h-[calc(100vh-160px)] rounded-2xl bg-white p-6 shadow-[0_12px_30px_rgba(15,23,42,0.08)] max-md:p-4"
              >
                <router-outlet></router-outlet>
              </section>
            </div>
          </div>
        </mat-sidenav-content>
      </mat-sidenav-container>
    </div>
  `,
  styles: [
    `
      .app-sidenav {
        background: #ffffff;
        color: #0b3558;
      }

      .sidebar-link {
        background: #fff;
        color: #0b4f8a;
      }

      .sidebar-link mat-icon {
        color: #0b4f8a;
      }

      .sidebar-link .nav-count {
        font-size: 12px;
        font-weight: 600;
        background: #e1f3ff;
        color: #0b4f8a;
      }

      .sidebar-link .nav-count.active {
        background: rgba(0, 167, 196, 0.15);
        color: #0b3558;
      }

      .sidebar-link:hover {
        border-color: #00a7c4;
        color: #0b3558;
        box-shadow: 0 10px 24px rgba(0, 167, 196, 0.18);
        transform: translateY(-1px);
      }

      .sidebar-link:hover mat-icon {
        color: #0b3558;
      }

      .sidebar-link.active {
        background: linear-gradient(
          90deg,
          #0b4f8a 0%,
          #0b4f8a 60%,
          #00a7c4 100%
        );
        color: #e5f1ff;
        box-shadow: 0 12px 28px rgba(11, 79, 138, 0.28);
        border-color: transparent;
      }

      .sidebar-link.active mat-icon {
        color: #e5f1ff;
      }

      .sidebar-link.active .nav-count {
        background: rgba(255, 255, 255, 0.2);
        color: #e5f1ff;
      }

      .view-content {
        background: #fff;
        box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
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
        background: linear-gradient(
          90deg,
          #0b4f8a 0%,
          #0b4f8a 60%,
          #00a7c4 100%
        );
        border-color: transparent;
        color: #e5f1ff;
        box-shadow: 0 12px 28px rgba(11, 79, 138, 0.5);
      }

      :host-context(.dark-theme) .sidebar-link.active mat-icon {
        color: #e5f1ff;
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
    `,
  ],
})
export class AppShellComponent implements OnDestroy {
  private readonly destroy$ = new Subject<void>();

  sidebarOpened = false;

  readonly isHandset$: Observable<boolean>;
  readonly discoverFeedCount$: Observable<number>;
  readonly totalImportElements$: Observable<number>;
  readonly activeImportCount$: Observable<number>;

  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly importService = inject(ImportService);
  private readonly metrics = inject(FeedsMetricsService);
  private readonly events = inject(FeedsEventsService);
  private readonly regionService = inject(RegionService);

  constructor() {
    this.isHandset$ = this.breakpointObserver
      .observe('(max-width: 768px)')
      .pipe(
        map((result) => result.matches),
        shareReplay({ bufferSize: 1, refCount: true }),
      );
    this.discoverFeedCount$ = this.metrics.discoverFeedCount$;
    this.totalImportElements$ = this.metrics.totalImportElements$;
    this.activeImportCount$ = this.importService
      .getActiveImportsObservable()
      .pipe(
        map(
          (imports: FeedImportSummary[] | null | undefined) =>
            imports?.length ?? 0,
        ),
      );
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
    if (selection.breadcrumb.id === 'regions') {
      selection.originalEvent.preventDefault();
      selection.originalEvent.stopPropagation();
      this.metrics.resetSelectedRegion();
      this.router.navigate(['/regions/discover']);
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
}
