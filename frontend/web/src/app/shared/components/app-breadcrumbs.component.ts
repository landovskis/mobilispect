import {
  Component,
  Output,
  EventEmitter,
  ChangeDetectionStrategy,
  OnInit,
  OnDestroy,
  ChangeDetectorRef,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { RouterModule, Router, NavigationEnd } from '@angular/router';
import {
  AppBreadcrumbService,
  Breadcrumb,
} from '../services/app-breadcrumb.service';
export type { Breadcrumb };
import { Subject } from 'rxjs';
import { filter, startWith, takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-toolbar-breadcrumbs',
  standalone: true,
  imports: [CommonModule, MatIconModule, RouterModule],
  template: `
    <nav
      class="flex flex-wrap items-center gap-1.5 text-[0.85rem] text-[#0b3558] mt-0.5 max-md:text-xs"
    >
      @for (
        breadcrumb of breadcrumbs;
        track breadcrumb.id ?? breadcrumb.label;
        let first = $first;
        let last = $last
      ) {
        @if (!first) {
          <mat-icon
            class="breadcrumb-icon text-[18px] h-[18px] w-[18px] text-[#0b3558]"
            aria-hidden="true"
          >
            chevron_right
          </mat-icon>
        }

        @if (breadcrumb.link) {
          <a
            class="breadcrumb-link flex items-center text-[#0b3558] no-underline"
            [routerLink]="breadcrumb.link"
            [queryParams]="breadcrumb.queryParams || null"
            [attr.aria-label]="'Go to ' + breadcrumb.label"
            (click)="onBreadcrumbClick($event, breadcrumb)"
          >
            <span
              [ngClass]="{
                'breadcrumb-item': !last,
                'breadcrumb-region': last,
                'breadcrumb-active': last,
              }"
            >
              {{ breadcrumb.label }}
            </span>
          </a>
        } @else {
          <span
            class="breadcrumb-item"
            [ngClass]="{
              'breadcrumb-region': last,
              'breadcrumb-active': last,
            }"
            (click)="onBreadcrumbClick($event, breadcrumb)"
          >
            {{ breadcrumb.label }}
          </span>
        }
      }
    </nav>
  `,
  styles: [
    `
      .breadcrumb-item {
        font-weight: 400;
      }

      .breadcrumb-icon {
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
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppBreadcrumbsComponent implements OnInit, OnDestroy {
  breadcrumbs: Breadcrumb[] = [];
  @Output() breadcrumbSelected = new EventEmitter<BreadcrumbSelection>();

  private readonly destroy$ = new Subject<void>();

  private readonly router = inject(Router);
  private readonly breadcrumbService = inject(AppBreadcrumbService);
  private readonly cdr = inject(ChangeDetectorRef);

  constructor() {}

  ngOnInit(): void {
    this.router.events
      .pipe(
        filter(
          (event): event is NavigationEnd => event instanceof NavigationEnd,
        ),
        startWith(null), // Trigger initial load
        takeUntil(this.destroy$),
      )
      .subscribe(() => {
        this.updateBreadcrumbs();
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateBreadcrumbs(): void {
    const crumbs = this.breadcrumbService.getBreadcrumbs(
      this.router.routerState.root.snapshot,
    );
    this.breadcrumbs = crumbs.length
      ? crumbs
      : [{ id: 'regions', label: 'Regions', link: ['/regions/discover'] }];
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
