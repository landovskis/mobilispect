import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
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
    MatIconModule,
    MatButtonModule,
    MatSnackBarModule,
    AppBarComponent,
    ThemeToggleComponent
  ],
  template: `
    <div class="flex min-h-screen flex-col">
      <app-bar
        (refresh)="onRefresh()"
        (breadcrumbSelected)="onBreadcrumbSelected($event)"
        >
        <div toolbar-actions>
          <app-theme-toggle></app-theme-toggle>
        </div>
      </app-bar>

      <div class="shell-body flex-1 bg-transparent md:h-[calc(100vh-64px)] max-md:h-auto max-md:flex-col">
        <aside class="app-rail w-[5.5rem] min-w-[5.5rem] border-r border-[#E1F3FF] bg-white px-2 pb-6 pt-6 text-[#0B3558] max-md:w-full max-md:min-w-0 max-md:border-b max-md:border-r-0">
          <nav class="rail-nav flex flex-col items-center gap-4 sticky top-24 max-md:static max-md:flex-row max-md:justify-around" aria-label="Feed navigation">
            <div class="rail-heading mb-1 text-[10px] font-semibold uppercase tracking-[0.2em] text-[#6B7280] max-md:hidden">Inspect</div>

            <button
              type="button"
              class="rail-link relative flex flex-col items-center gap-2 rounded-2xl border border-[#D1D5DB] bg-white px-3 py-3 text-center font-semibold text-[#0B4F8A] shadow-[0_6px_14px_rgba(11,79,138,0.05)] transition-all duration-200"
              routerLink="/feeds/imports"
              routerLinkActive="active">
              <mat-icon class="text-[22px] text-[#0B4F8A]">history</mat-icon>
              <span class="rail-label text-[11px] leading-tight">Imports</span>
              @let totalImports = totalImportElements$ | async;
              @if ((totalImports ?? 0) > 0) {
                <span class="rail-badge">{{ totalImports }}</span>
              }
              @let activeImports = activeImportCount$ | async;
              @if ((activeImports ?? 0) > 0) {
                <span class="rail-badge active">{{ activeImports }}</span>
              }
            </button>

            <button
              type="button"
              class="rail-link flex flex-col items-center gap-2 rounded-2xl border border-[#D1D5DB] bg-white px-3 py-3 text-center font-semibold text-[#0B4F8A] shadow-[0_6px_14px_rgba(11,79,138,0.05)] transition-all duration-200"
              routerLink="/regions"
              routerLinkActive="active">
              <mat-icon class="text-[22px] text-[#0B4F8A]">map</mat-icon>
              <span class="rail-label text-[11px] leading-tight">Regions</span>
            </button>
          </nav>
        </aside>

        <main class="flex-1">
          <div class="content-area min-h-[calc(100vh-64px)] bg-[#fafafa] p-6 max-md:p-4">
            <div class="mx-auto max-w-[1500px]">
              <section class="view-content min-h-[calc(100vh-160px)] rounded-2xl bg-white p-6 shadow-[0_12px_30px_rgba(15,23,42,0.08)] max-md:p-4">
                <router-outlet></router-outlet>
              </section>
            </div>
          </div>
        </main>
      </div>
    </div>
    `,
  styles: [`
    .shell-body {
      display: flex;
    }

    .app-rail {
      background: #ffffff;
      color: #0B3558;
    }

    .rail-link {
      background: #fff;
      color: #0B4F8A;
    }

    .rail-link mat-icon {
      color: #0B4F8A;
    }

    .rail-badge {
      position: absolute;
      top: 6px;
      right: 6px;
      border-radius: 999px;
      padding: 2px 6px;
      font-size: 10px;
      font-weight: 700;
      background: #E1F3FF;
      color: #0B4F8A;
    }

    .rail-badge.active {
      top: 22px;
      background: rgba(0, 167, 196, 0.15);
      color: #0B3558;
    }

    .rail-link:hover {
      border-color: #00A7C4;
      color: #0B3558;
      box-shadow: 0 10px 24px rgba(0, 167, 196, 0.18);
      transform: translateY(-1px);
    }

    .rail-link:hover mat-icon {
      color: #0B3558;
    }

    .rail-link.active {
      background: linear-gradient(90deg, #0B4F8A 0%, #0B4F8A 60%, #00A7C4 100%);
      color: #E5F1FF;
      box-shadow: 0 12px 28px rgba(11, 79, 138, 0.28);
      border-color: transparent;
    }

    .rail-link.active mat-icon {
      color: #E5F1FF;
    }

    .rail-link.active .rail-badge {
      background: rgba(255, 255, 255, 0.2);
      color: #E5F1FF;
    }

    .view-content {
      background: #fff;
      box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
    }

    :host-context(.dark-theme) .shell-body {
      background: #0b1220;
    }

    :host-context(.dark-theme) .app-rail {
      background: #0f172a;
      border-color: rgba(148, 163, 184, 0.24);
      color: #e5f1ff;
    }

    :host-context(.dark-theme) .rail-heading {
      color: rgba(226, 232, 240, 0.8);
    }

    :host-context(.dark-theme) .rail-link {
      background: #111827;
      color: #e5f1ff;
      border-color: rgba(148, 163, 184, 0.24);
      box-shadow: 0 10px 20px rgba(0, 0, 0, 0.35);
    }

    :host-context(.dark-theme) .rail-link mat-icon {
      color: #cbd5e1;
    }

    :host-context(.dark-theme) .rail-badge {
      background: rgba(0, 167, 196, 0.18);
      color: #e5f1ff;
    }

    :host-context(.dark-theme) .rail-badge.active {
      background: rgba(0, 167, 196, 0.3);
      color: #e5f1ff;
    }

    :host-context(.dark-theme) .rail-link.active {
      background: linear-gradient(90deg, #0B4F8A 0%, #0B4F8A 60%, #00A7C4 100%);
      border-color: transparent;
      color: #E5F1FF;
      box-shadow: 0 12px 28px rgba(11, 79, 138, 0.5);
    }

    :host-context(.dark-theme) .rail-link.active mat-icon {
      color: #E5F1FF;
    }

    :host-context(.dark-theme) .rail-link.active .rail-badge {
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

  `]
})
export class AppShellComponent {
  readonly discoverFeedCount$: Observable<number>;
  readonly totalImportElements$: Observable<number>;
  readonly activeImportCount$: Observable<number>;

  constructor(
    private readonly router: Router,
    private readonly snackBar: MatSnackBar,
    private readonly importService: ImportService,
    private readonly metrics: FeedsMetricsService,
    private readonly events: FeedsEventsService,
    private readonly regionService: RegionService
  ) {
    this.discoverFeedCount$ = this.metrics.discoverFeedCount$;
    this.totalImportElements$ = this.metrics.totalImportElements$;
    this.activeImportCount$ = this.importService.getActiveImportsObservable().pipe(
      map((imports: any[] | null | undefined) => imports?.length ?? 0)
    );
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
}
