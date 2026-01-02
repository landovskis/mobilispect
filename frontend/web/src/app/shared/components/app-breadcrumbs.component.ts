import { Component, Output, EventEmitter, ChangeDetectionStrategy, OnInit, OnDestroy, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, NavigationEnd } from '@angular/router';
import { AppBreadcrumbService, Breadcrumb } from '../services/app-breadcrumb.service';
export type { Breadcrumb };
import { Subject } from 'rxjs';
import { filter, startWith, takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-toolbar-breadcrumbs',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-title mt-0.5 text-[#0b3558]">
      {{ pageTitle }}
    </div>
  `,
  styles: [`
    .page-title {
      font-size: 1.15rem;
      font-weight: 700;
      letter-spacing: 0.01em;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppBreadcrumbsComponent implements OnInit, OnDestroy {
  breadcrumbs: Breadcrumb[] = [];
  pageTitle = '';
  @Output() breadcrumbSelected = new EventEmitter<BreadcrumbSelection>();

  private readonly router = inject(Router);
  private readonly breadcrumbService = inject(AppBreadcrumbService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      startWith(null), // Trigger initial load
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.updateBreadcrumbs();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateBreadcrumbs(): void {
    const crumbs = this.breadcrumbService.getBreadcrumbs(this.router.routerState.root.snapshot);
    this.breadcrumbs = crumbs.length
      ? crumbs
      : [{ id: 'regions', label: 'Regions', link: ['/regions/discover'] }];
    this.pageTitle = this.breadcrumbs[this.breadcrumbs.length - 1]?.label ?? 'Regions';
    this.cdr.markForCheck();
  }

  onBreadcrumbClick(event: MouseEvent, breadcrumb: Breadcrumb): void {
    this.breadcrumbSelected.emit({ breadcrumb, originalEvent: event });
  }
}



export interface BreadcrumbSelection {
  breadcrumb: Breadcrumb;
  originalEvent: MouseEvent;
}
