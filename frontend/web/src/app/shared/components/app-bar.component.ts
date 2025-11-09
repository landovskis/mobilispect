import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ThemeToggleComponent } from '../../core/components/theme-toggle.component';
import { AppBreadcrumbsComponent, Breadcrumb, BreadcrumbSelection } from './app-breadcrumbs.component';
import { AppToolbarNavComponent, ToolbarNavItem } from './app-toolbar-nav.component';

@Component({
  selector: 'app-bar',
  standalone: true,
  imports: [
    CommonModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    AppBreadcrumbsComponent,
    AppToolbarNavComponent,
    ThemeToggleComponent
  ],
  template: `
    <mat-toolbar
      color="primary"
      class="app-toolbar"
      [class.mobile-nav-open]="isMobileNavOpen"
    >
      <div class="toolbar-left">
        <button
          mat-icon-button
          class="mobile-menu-button"
          (click)="toggleMobileNav()"
          [attr.aria-label]="isMobileNavOpen ? 'Close navigation' : 'Open navigation'"
        >
          <mat-icon>{{ isMobileNavOpen ? 'close' : 'menu' }}</mat-icon>
        </button>
        <img [src]="logoUrl" [alt]="appName + ' Logo'" class="app-logo" />
        <div class="toolbar-heading">
          <span class="toolbar-title">{{ appName }}</span>
          @if (breadcrumbs?.length) {
            <app-toolbar-breadcrumbs
              [breadcrumbs]="breadcrumbs"
              (breadcrumbSelected)="breadcrumbSelected.emit($event)"
            ></app-toolbar-breadcrumbs>
          }
        </div>
        @if (isMobileNavOpen && navItems?.length) {
          <div class="mobile-dropdown">
            @for (item of navItems; track item.label) {
              <button
                mat-button
                [disabled]="item.active"
                [class.active]="item.active"
                (click)="handleNavSelected(item)"
              >
                {{ item.label }}
              </button>
            }
          </div>
        }
      </div>

      @if (navItems?.length) {
        <div class="toolbar-center">
          <app-toolbar-nav
            [navItems]="navItems"
            (navSelected)="handleNavSelected($event)"
          ></app-toolbar-nav>
        </div>
      }

      <div class="toolbar-right">
        <ng-content select="[toolbar-actions]"></ng-content>
        <app-theme-toggle></app-theme-toggle>
      </div>
    </mat-toolbar>
  `,
  styles: [`
    .app-toolbar {
      position: sticky;
      top: 0;
      z-index: 10;
      background-color: #1e3a8a !important;
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
      height: 40px;
      width: 40px;
      object-fit: contain;
    }

    .toolbar-title {
      font-size: 1.25rem;
      font-weight: 500;
      color: white !important;
    }

    .mobile-menu-button {
      display: none;
      color: white;
    }

    .mobile-dropdown {
      position: absolute;
      top: 48px;
      left: 0;
      display: none;
      flex-direction: column;
      gap: 4px;
      padding: 8px;
      background-color: #1e3a8a;
      border-radius: 12px;
      box-shadow: 0 8px 20px rgba(0, 0, 0, 0.25);
      z-index: 20;
      width: max-content;
      min-width: 160px;
    }

    .mobile-dropdown button {
      color: #ffffff;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      font-weight: 700;
      border-radius: 999px;
      padding: 8px 20px;
      border: 1px solid rgba(255, 255, 255, 0.35);
      background-color: #1e3a8a;
      transition:
        background-color 0.2s ease,
        border-color 0.2s ease,
        box-shadow 0.2s ease;
    }

    .mobile-dropdown button:not(.active):hover {
      border-color: rgba(255, 255, 255, 0.7);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
    }

    .mobile-dropdown button.active {
      border-color: #ffffff;
      box-shadow: 0 6px 18px rgba(0, 0, 0, 0.3);
    }

    .toolbar-center {
      flex: 1;
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 0 12px;
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
        height: 32px;
        width: 32px;
      }

      .mobile-menu-button {
        display: inline-flex;
      }

      .toolbar-heading {
        flex-direction: column;
      }

      .toolbar-center {
        justify-content: flex-end;
      }

      .mobile-dropdown {
        display: flex;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppBarComponent {
  @Input() appName = 'Mobilispect';
  @Input() logoUrl = '/logo.png';
  @Input() breadcrumbs: Breadcrumb[] = [];
  @Input() navItems: ToolbarNavItem[] = [];

  @Output() refresh = new EventEmitter<void>();
  @Output() breadcrumbSelected = new EventEmitter<BreadcrumbSelection>();
  @Output() navSelected = new EventEmitter<ToolbarNavItem>();

  isMobileNavOpen = false;

  toggleMobileNav(): void {
    this.isMobileNavOpen = !this.isMobileNavOpen;
  }

  handleNavSelected(item: ToolbarNavItem): void {
    this.isMobileNavOpen = false;
    this.navSelected.emit(item);
  }
}

export type { Breadcrumb, BreadcrumbSelection } from './app-breadcrumbs.component';
export type { ToolbarNavItem } from './app-toolbar-nav.component';
