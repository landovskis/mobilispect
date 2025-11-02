import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-breadcrumbs',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    <div class="breadcrumbs-container" *ngIf="region">
      <div class="breadcrumbs">
        <span class="breadcrumb-item">Mobilispect</span>
        <mat-icon class="breadcrumb-icon">chevron_right</mat-icon>
        <span class="breadcrumb-item breadcrumb-region">{{ region }}</span>
      </div>
    </div>
  `,
  styles: [`
    .breadcrumbs-container {
      width: 100%;
      background-color: #f5f5f5;
      border-bottom: 1px solid #e0e0e0;
      padding: 12px 24px;
    }

    .breadcrumbs-container.dark {
      background-color: #2c2c2c;
      border-bottom: 1px solid #404040;
    }

    .breadcrumbs {
      display: flex;
      align-items: center;
      max-width: 1200px;
      margin: 0 auto;
      font-size: 0.875rem;
      color: #666;
    }

    .breadcrumbs-container.dark .breadcrumbs {
      color: #bbb;
    }

    .breadcrumb-item {
      font-weight: 400;
    }

    .breadcrumb-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      margin: 0 8px;
      color: #999;
    }

    .breadcrumbs-container.dark .breadcrumb-icon {
      color: #777;
    }

    .breadcrumb-region {
      font-weight: 500;
      color: #333;
    }

    .breadcrumbs-container.dark .breadcrumb-region {
      color: #fff;
    }
  `]
})
export class BreadcrumbsComponent {
  @Input() region: string | null = null;
}
