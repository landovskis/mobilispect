import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { RouterModule, Params } from '@angular/router';
import { ThemeToggleComponent } from '../../core/components/theme-toggle.component';

@Component({
  selector: 'app-bar',
  standalone: true,
  imports: [
    CommonModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    RouterModule,
    ThemeToggleComponent
  ],
  template: `
    <mat-toolbar color="primary" class="app-toolbar">
      <div class="toolbar-left">
        <img [src]="logoUrl" [alt]="appName + ' Logo'" class="app-logo" />
        <div class="toolbar-heading">
          <span class="toolbar-title">{{ appName }}</span>
          <div class="toolbar-breadcrumbs" *ngIf="breadcrumbs?.length">
            <ng-container *ngFor="let breadcrumb of breadcrumbs; let first = first; let last = last">
              <mat-icon
                *ngIf="!first"
                class="breadcrumb-icon"
                aria-hidden="true"
              >
                chevron_right
              </mat-icon>

              <ng-container *ngIf="breadcrumb.link; else breadcrumbLabel">
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
              </ng-container>

              <ng-template #breadcrumbLabel>
                <span
                  class="breadcrumb-item"
                  [ngClass]="{ 'breadcrumb-region': last }"
                  (click)="onBreadcrumbClick($event, breadcrumb)"
                >
                  {{ breadcrumb.label }}
                </span>
              </ng-template>
            </ng-container>
          </div>
        </div>
      </div>

      <span class="toolbar-spacer"></span>

      <!-- Action Buttons -->
      <ng-content select="[toolbar-actions]"></ng-content>

      <!-- Refresh Button -->
      <button
        *ngIf="showRefresh"
        mat-icon-button
        class="toolbar-icon-button"
        (click)="onRefresh()"
        [attr.aria-label]="'Refresh all data'"
      >
        <mat-icon>refresh</mat-icon>
      </button>

      <!-- Theme Toggle -->
      <app-theme-toggle></app-theme-toggle>
    </mat-toolbar>
  `,
  styles: [`
    .app-toolbar {
      position: sticky;
      top: 0;
      z-index: 10;
      background-color: #1e3a8a !important;
      color: white !important;
    }

    .toolbar-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .toolbar-heading {
      display: flex;
      flex-direction: column;
      justify-content: center;
      line-height: 1.2;
    }

    .toolbar-left .toolbar-title {
      color: white !important;
    }

    .app-logo {
      height: 40px;
      width: 40px;
      object-fit: contain;
    }

    .toolbar-title {
      font-size: 1.25rem;
      font-weight: 500;
      color: white !important;
    }

    .toolbar-breadcrumbs {
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

    .toolbar-spacer {
      flex: 1;
    }

    .toolbar-icon-button {
      color: white;
      margin-right: 8px;
    }

    .quick-stats mat-chip {
      margin-left: 8px;
    }

    @media (max-width: 768px) {
      .toolbar-title {
        font-size: 1rem;
      }

      .app-logo {
        height: 32px;
        width: 32px;
      }

      .toolbar-breadcrumbs {
        font-size: 0.75rem;
        flex-wrap: wrap;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppBarComponent {
  @Input() appName = 'Mobilispect';
  @Input() logoUrl = '/logo.png';
  @Input() showRefresh = true;
  @Input() breadcrumbs: Breadcrumb[] = [];

  @Output() refresh = new EventEmitter<void>();
  @Output() breadcrumbSelected = new EventEmitter<BreadcrumbSelection>();

  onRefresh(): void {
    this.refresh.emit();
  }

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
