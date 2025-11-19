import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { map } from 'rxjs/operators';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule, MatTooltipModule],
  template: `
    <button
      mat-icon-button
      type="button"
      class="theme-toggle"
      [attr.aria-pressed]="(isDarkMode$ | async) ?? false"
      aria-label="Toggle application theme"
      matTooltip="Toggle light/dark theme"
      (click)="toggleTheme()"
    >
      <mat-icon aria-hidden="true">
        @if (isDarkMode$ | async) {
          light_mode
        } @else {
          dark_mode
        }
      </mat-icon>
    </button>
  `,
  styles: [`
    .theme-toggle {
      color: #fff;
    }

    :host-context(.dark-theme) .theme-toggle {
      color: #e2e8f0;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ThemeToggleComponent {
  private readonly themeService = inject(ThemeService);
  readonly isDarkMode$ = this.themeService.activeTheme$.pipe(map(theme => theme === 'dark'));

  toggleTheme(): void {
    this.themeService.toggle();
  }
}
