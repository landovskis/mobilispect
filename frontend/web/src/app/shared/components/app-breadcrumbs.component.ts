import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { RouterModule, Params } from '@angular/router';

@Component({
  selector: 'app-toolbar-breadcrumbs',
  standalone: true,
  imports: [CommonModule, MatIconModule, RouterModule],
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
            [ngClass]="{
              'breadcrumb-item': !last,
              'breadcrumb-region': last
            }"
          >
            {{ breadcrumb.label }}
          </span>
        </a>
      } @else {
        <span
          class="breadcrumb-item"
          [ngClass]="{ 'breadcrumb-region': last }"
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
      color: rgba(255, 255, 255, 0.85);
      margin-top: 2px;
      gap: 6px;
    }

    .breadcrumb-link {
      text-decoration: none;
      color: inherit;
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
      color: #ffffff;
    }

    .breadcrumb-region {
      font-weight: 500;
      color: #fff;
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
export class AppBreadcrumbsComponent {
  @Input() breadcrumbs: Breadcrumb[] = [];
  @Output() breadcrumbSelected = new EventEmitter<BreadcrumbSelection>();

  onBreadcrumbClick(event: MouseEvent, breadcrumb: Breadcrumb): void {
    this.breadcrumbSelected.emit({ breadcrumb, originalEvent: event });
  }
}

export interface Breadcrumb {
  id?: string;
  label: string;
  link?: string | any[];
  queryParams?: Params | null;
}

export interface BreadcrumbSelection {
  breadcrumb: Breadcrumb;
  originalEvent: MouseEvent;
}
