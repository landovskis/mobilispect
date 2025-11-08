import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-toolbar-nav',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule],
  template: `
    <div class="nav-links" *ngIf="navItems?.length">
      <button
        mat-button
        *ngFor="let item of navItems"
        [disabled]="item.active"
        [class.active]="item.active"
        (click)="onNavClick(item)"
      >
        {{ item.label }}
      </button>
    </div>

    <div class="mobile-nav" *ngIf="navItems?.length">
      <button
        mat-icon-button
        class="menu-button"
        (click)="toggleMenu()"
        [attr.aria-label]="isMenuOpen ? 'Close navigation' : 'Open navigation'"
      >
        <mat-icon>{{ isMenuOpen ? 'close' : 'menu' }}</mat-icon>
      </button>

      <div class="mobile-menu" *ngIf="isMenuOpen">
        <button
          mat-button
          *ngFor="let item of navItems"
          [disabled]="item.active"
          [class.active]="item.active"
          (click)="onNavClick(item)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      width: 100%;
    }

    .nav-links {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .nav-links button,
    .mobile-menu button {
      color: rgba(255, 255, 255, 0.85);
      text-transform: none;
      font-weight: 600;
      border-radius: 999px;
      padding: 6px 16px;
      transition: background-color 0.2s ease, color 0.2s ease;
    }

    .nav-links button.active,
    .mobile-menu button.active {
      background-color: rgba(255, 255, 255, 0.18);
      color: #fff;
    }

    .mobile-nav {
      display: none;
      position: relative;
    }

    .mobile-menu {
      position: absolute;
      top: 40px;
      right: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 8px;
      background-color: rgba(30, 58, 138, 0.95);
      border-radius: 8px;
      box-shadow: 0 8px 20px rgba(0, 0, 0, 0.25);
      z-index: 20;
    }

    .menu-button {
      color: white;
    }

    @media (max-width: 768px) {
      .nav-links {
        display: none;
      }

      .mobile-nav {
        display: flex;
        align-items: center;
      }
    }

    @media (min-width: 769px) {
      .mobile-nav {
        display: none;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppToolbarNavComponent {
  @Input() navItems: ToolbarNavItem[] = [];
  @Output() navSelected = new EventEmitter<ToolbarNavItem>();

  isMenuOpen = false;

  toggleMenu(): void {
    this.isMenuOpen = !this.isMenuOpen;
  }

  onNavClick(item: ToolbarNavItem): void {
    this.isMenuOpen = false;
    this.navSelected.emit(item);
  }
}

export interface ToolbarNavItem {
  label: string;
  active?: boolean;
}
