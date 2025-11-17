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
        <div class="toolbar-heading">
          <span class="toolbar-title">{{ appName }}</span>
          @if (breadcrumbs.length) {
            <app-toolbar-breadcrumbs
              [breadcrumbs]="breadcrumbs"
              (breadcrumbSelected)="breadcrumbSelected.emit($event)"
            ></app-toolbar-breadcrumbs>
          }
        </div>
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
      background-color: #2980B9 !important;
      color: white !important;
      position: sticky;
    }

    .toolbar-left {
      display: flex;
      align-items: center;
      gap: 12px;
      position: relative;
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
      height: 60px;
      width: auto;
      object-fit: contain;
    }

    .toolbar-title {
      font-size: 1.25rem;
      font-weight: 500;
      color: white !important;
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

      .app-logo {
        height: 48px;
        width: auto;
      }

      .toolbar-heading {
        flex-direction: column;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppBarComponent {
  @Input() appName = 'Mobilispect';
  @Input() logoUrl = '/logo.png';
  @Input() breadcrumbs: Breadcrumb[] = [];

  @Output() refresh = new EventEmitter<void>();
  @Output() breadcrumbSelected = new EventEmitter<BreadcrumbSelection>();
}

export type { Breadcrumb, BreadcrumbSelection } from './app-breadcrumbs.component';
