import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { AppBreadcrumbsComponent, Breadcrumb, BreadcrumbSelection } from './app-breadcrumbs.component';

@Component({
  selector: 'app-bar',
  standalone: true,
  imports: [
    CommonModule,
    MatToolbarModule,
    MatIconModule,
    AppBreadcrumbsComponent
  ],
  template: `
    <mat-toolbar
      color="primary"
      class="app-toolbar"
    >
      <div class="toolbar-left">
        <img [src]="logoUrl" [alt]="appName + ' Logo'" class="app-logo" />
        @if (breadcrumbs.length) {
          <app-toolbar-breadcrumbs
            class="toolbar-breadcrumbs"
            [breadcrumbs]="breadcrumbs"
            (breadcrumbSelected)="breadcrumbSelected.emit($event)"
          ></app-toolbar-breadcrumbs>
        }
      </div>

      <div class="toolbar-right">
        <ng-content select="[toolbar-actions]"></ng-content>
      </div>
    </mat-toolbar>
  `,
  styles: [`
    .app-toolbar {
      position: sticky;
      top: 0;
      z-index: 10;
      background: #ffffff !important;
      color: #0B3558 !important;
      border-bottom: 1px solid #E1F3FF;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", sans-serif;
      min-height: 72px;
      display: flex;
      align-items: center;
    }

    .toolbar-left {
      display: flex;
      align-items: center;
      gap: 12px;
      position: relative;
    }

    .app-logo {
      height: 88px;
      width: auto;
      object-fit: contain;
      max-width: 480px;
    }

    .toolbar-breadcrumbs {
      margin-left: 8px;
    }

    app-toolbar-breadcrumbs {
      color: #0B3558;
    }

    .toolbar-right {
      margin-left: auto;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    @media (max-width: 768px) {
      .toolbar-title {
        font-size: 1rem;
      }

      .toolbar-tagline {
        font-size: 0.7rem;
      }

      .app-logo {
        height: 72px;
        max-width: 400px;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppBarComponent {
  @Input() appName = 'Mobilispect';
  @Input() logoUrl = '/mobilispect_full_logo.svg';
  @Input() breadcrumbs: Breadcrumb[] = [];

  @Output() refresh = new EventEmitter<void>();
  @Output() breadcrumbSelected = new EventEmitter<BreadcrumbSelection>();
}

export type { Breadcrumb, BreadcrumbSelection } from './app-breadcrumbs.component';
