import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';

import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { AppBreadcrumbsComponent, Breadcrumb, BreadcrumbSelection } from './app-breadcrumbs.component';

@Component({
  selector: 'app-bar',
  standalone: true,
  imports: [
    MatToolbarModule,
    MatIconModule,
    AppBreadcrumbsComponent
  ],
  template: `
    <mat-toolbar
      color="primary"
      class="app-toolbar sticky top-0 z-10 flex min-h-[72px] items-center border-b border-[#E1F3FF] bg-white text-[#0B3558]"
    >
      <div class="toolbar-left flex items-center gap-3 relative">
        <img [src]="logoUrl" [alt]="appName + ' Logo'" class="h-[88px] w-auto max-w-[480px] object-contain max-md:h-[72px] max-md:max-w-[400px]" />
        <app-toolbar-breadcrumbs
          class="toolbar-breadcrumbs ml-2"
          (breadcrumbSelected)="breadcrumbSelected.emit($event)"
        ></app-toolbar-breadcrumbs>
      </div>

      <div class="toolbar-right ml-auto flex items-center gap-2">
        <ng-content select="[toolbar-actions]"></ng-content>
      </div>
    </mat-toolbar>
  `,
  styles: [`
    .app-toolbar {
      background: #ffffff !important;
      color: #0B3558 !important;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", sans-serif;
    }

    app-toolbar-breadcrumbs {
      color: #0B3558;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppBarComponent {
  @Input() appName = 'Mobilispect';
  @Input() logoUrl = '/mobilispect_full_logo.svg';
  @Input() logoUrl = '/mobilispect_full_logo.svg';

  @Output() refresh = new EventEmitter<void>();
  @Output() breadcrumbSelected = new EventEmitter<BreadcrumbSelection>();
}

export type { Breadcrumb, BreadcrumbSelection } from './app-breadcrumbs.component';
