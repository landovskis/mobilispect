import { Component, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatMenuModule } from '@angular/material/menu';
import { ThemeService, Theme } from '../services/theme.service';

/**
 * ThemeToggleComponent - Constitutional Requirement (Principle III)
 *
 * Provides UI controls for switching between light, dark, and auto themes.
 * Displays current theme state and allows user preference selection.
 */
@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatMenuModule
  ],
  template: `
    <button
      mat-icon-button
      [matMenuTriggerFor]="themeMenu"
      [attr.aria-label]="'Switch theme - currently ' + currentThemeLabel()"
      matTooltip="Theme">
      <mat-icon>{{ themeIcon() }}</mat-icon>
    </button>

    <mat-menu #themeMenu="matMenu">
      <button
        mat-menu-item
        (click)="setTheme('light')"
        [class.active]="themeService.theme() === 'light'">
        <mat-icon>light_mode</mat-icon>
        <span>Light</span>
      </button>

      <button
        mat-menu-item
        (click)="setTheme('dark')"
        [class.active]="themeService.theme() === 'dark'">
        <mat-icon>dark_mode</mat-icon>
        <span>Dark</span>
      </button>

      <button
        mat-menu-item
        (click)="setTheme('auto')"
        [class.active]="themeService.theme() === 'auto'">
        <mat-icon>brightness_auto</mat-icon>
        <span>Auto (System)</span>
      </button>
    </mat-menu>
  `,
  styles: [`
    button.active {
      background-color: rgba(0, 0, 0, 0.04);
    }

    :host-context(.dark-theme) button.active {
      background-color: rgba(255, 255, 255, 0.08);
    }

    mat-icon {
      margin-right: 8px;
    }
  `]
})
export class ThemeToggleComponent {
  constructor(public themeService: ThemeService) {}

  // Computed icon based on current theme
  themeIcon = computed(() => {
    const theme = this.themeService.theme();
    switch (theme) {
      case 'light':
        return 'light_mode';
      case 'dark':
        return 'dark_mode';
      case 'auto':
        return 'brightness_auto';
    }
  });

  // Computed label for accessibility
  currentThemeLabel = computed(() => {
    const theme = this.themeService.theme();
    const effective = this.themeService.effectiveTheme();
    if (theme === 'auto') {
      return `Auto (${effective})`;
    }
    return theme.charAt(0).toUpperCase() + theme.slice(1);
  });

  setTheme(theme: Theme): void {
    this.themeService.setTheme(theme);
  }
}
