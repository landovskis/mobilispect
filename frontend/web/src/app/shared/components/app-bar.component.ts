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
          <div class="toolbar-breadcrumbs" *ngIf="breadcrumbRoot">
            <a
              class="breadcrumb-link"
              [routerLink]="breadcrumbRootLink"
              [queryParams]="breadcrumbRootQueryParams"
              aria-label="Go to {{ breadcrumbRoot }}"
            (click)="onBreadcrumbRootClick($event)"
          >
            <span class="breadcrumb-item">{{ breadcrumbRoot }}</span>
          </a>
          <ng-container *ngIf="breadcrumbRegion">
            <mat-icon class="breadcrumb-icon" aria-hidden="true">chevron_right</mat-icon>
            <a
              class="breadcrumb-link"
              [routerLink]="breadcrumbRegionLink || breadcrumbRootLink"
              [queryParams]="breadcrumbRegionQueryParams"
              aria-label="Go to {{ breadcrumbRegion }}"
            >
              <span class="breadcrumb-region">{{ breadcrumbRegion }}</span>
            </a>
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

      <!-- Quick Stats -->
      <div class="quick-stats" *ngIf="activeImportsCount > 0">
        <mat-chip-set>
          <mat-chip class="active-imports">
            <mat-icon>download</mat-icon>
            {{ activeImportsCount }} importing
          </mat-chip>
        </mat-chip-set>
      </div>

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

    .toolbar-left .mat-icon {
      color: #ffffff !important;
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

    .quick-stats {
      margin-right: 8px;
    }

    .quick-stats mat-chip {
      margin-left: 8px;
    }

    .quick-stats .active-imports {
      background-color: #ff9800;
      color: white;
    }

    @media (max-width: 768px) {
      .toolbar-title {
        font-size: 1rem;
      }

      .app-logo {
        height: 32px;
        width: 32px;
      }

      .quick-stats {
        display: none;
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
  @Input() activeImportsCount = 0;
  @Input() breadcrumbRoot = 'Feeds';
  @Input() breadcrumbRegion: string | null = null;
  @Input() breadcrumbRootLink: string | any[] = ['/feeds/regions'];
  @Input() breadcrumbRootQueryParams: Params | null = null;
  @Input() breadcrumbRegionLink: string | any[] | null = null;
  @Input() breadcrumbRegionQueryParams: Params | null = null;
  @Output() breadcrumbRootSelected = new EventEmitter<MouseEvent>();

  @Output() refresh = new EventEmitter<void>();

  onRefresh(): void {
    this.refresh.emit();
  }

  onBreadcrumbRootClick(event: MouseEvent): void {
    this.breadcrumbRootSelected.emit(event);
  }
}
