import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { ThemeToggleComponent } from '../../core/components/theme-toggle.component';
import { AppBreadcrumbsComponent, Breadcrumb, BreadcrumbSelection } from './app-breadcrumbs.component';

@Component({
  selector: 'app-bar',
  standalone: true,
  imports: [
    CommonModule,
    MatToolbarModule,
    MatButtonModule,
    AppBreadcrumbsComponent,
    ThemeToggleComponent
  ],
  template: `
    <mat-toolbar color="primary" class="app-toolbar">
      <div class="toolbar-left">
        <img [src]="logoUrl" [alt]="appName + ' Logo'" class="app-logo" />
        <div class="toolbar-heading">
          <span class="toolbar-title">{{ appName }}</span>
          <app-toolbar-breadcrumbs
            *ngIf="breadcrumbs?.length"
            [breadcrumbs]="breadcrumbs"
            (breadcrumbSelected)="breadcrumbSelected.emit($event)"
          ></app-toolbar-breadcrumbs>
        </div>
      </div>

      <span class="toolbar-spacer"></span>

      <!-- Action Buttons -->
      <ng-content select="[toolbar-actions]"></ng-content>

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

    .toolbar-spacer {
      flex: 1;
    }
    @media (max-width: 768px) {
      .toolbar-title {
        font-size: 1rem;
      }

      .app-logo {
        height: 32px;
        width: 32px;
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
