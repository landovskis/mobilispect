import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
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
    ThemeToggleComponent
  ],
  template: `
    <mat-toolbar color="primary" class="app-toolbar">
      <div class="toolbar-left">
        <img [src]="logoUrl" [alt]="appName + ' Logo'" class="app-logo" />
        <span class="toolbar-title">{{ appName }}</span>
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

    .toolbar-left .toolbar-title {
      color: white !important;
    }

    .toolbar-left .mat-icon {
      color: rgba(255, 255, 255, 0.9) !important;
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
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppBarComponent {
  @Input() appName = 'Mobilispect';
  @Input() logoUrl = '/logo.png';
  @Input() showRefresh = true;
  @Input() activeImportsCount = 0;

  @Output() refresh = new EventEmitter<void>();

  onRefresh(): void {
    this.refresh.emit();
  }
}
