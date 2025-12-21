import { Component, Output, EventEmitter, ChangeDetectionStrategy, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';

import { MatIconModule } from '@angular/material/icon';
import { RouterModule, Params, Router, NavigationEnd } from '@angular/router';
import { AppBreadcrumbService, Breadcrumb } from '../services/app-breadcrumb.service';
export type { Breadcrumb };
import { Subject } from 'rxjs';
import { filter, startWith, takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-toolbar-breadcrumbs',
  standalone: true,
  imports: [MatIconModule, RouterModule],
  template: `
    @for (breadcrumb of breadcrumbs; track breadcrumb.id ?? breadcrumb.label; let first = $first; let last = $last) {
      @if (!first) {
        <mat-icon
          class="breadcrumb-icon"
          aria-hidden="true"
        >
          chevron_right
        </mat-icon>
      }

      @if (breadcrumb.link) {
        <a
          class="breadcrumb-link"
          [routerLink]="breadcrumb.link"
          [queryParams]="breadcrumb.queryParams || null"
          [attr.aria-label]="'Go to ' + breadcrumb.label"
          (click)="onBreadcrumbClick($event, breadcrumb)"
        >
          <span
            [class]="{
              'breadcrumb-item': !last,
              'breadcrumb-region': last,
              'breadcrumb-active': last
            }"
          >
            {{ breadcrumb.label }}
          </span>
        </a>
      } @else {
        <span
          class="breadcrumb-item"
          [class]="{
            'breadcrumb-region': last,
            'breadcrumb-active': last
          }"
          (click)="onBreadcrumbClick($event, breadcrumb)"
        >
          {{ breadcrumb.label }}
        </span>
      }
    }
  `,
  styles: [`
    :host {
      display: flex;
      align-items: center;
      font-size: 0.85rem;
      color: #0b3558;
      margin-top: 2px;
      gap: 6px;
    }

    .breadcrumb-link {
      text-decoration: none;
      color: #0b3558;
      display: flex;
      align-items: center;
    }

    .breadcrumb-item {
      font-weight: 400;
    }

    .breadcrumb-icon {
      font-size: 18px;
      height: 18px;
      width: 18px;
      color: #0b3558;
    }

    .breadcrumb-region {
      font-weight: 500;
      color: #0b3558;
    }

    .breadcrumb-active {
      text-decoration: none;
      font-weight: 700;
    }

    @media (max-width: 768px) {
      :host {
        font-size: 0.75rem;
        flex-wrap: wrap;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppBreadcrumbsComponent implements OnInit, OnDestroy {
  breadcrumbs: Breadcrumb[] = [];
  @Output() breadcrumbSelected = new EventEmitter<BreadcrumbSelection>();

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly router: Router,
    private readonly breadcrumbService: AppBreadcrumbService,
    private readonly cdr: ChangeDetectorRef
  ) { }

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
      : [{ id: 'feeds', label: 'Feeds', link: ['/feeds/discover'] }];
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
