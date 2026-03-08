import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RegionImportGroup } from '../models/region-import-group.model';
import { BrandCardComponent } from '../../shared/components/brand-card.component';
import { FeedImportRowComponent } from './feed-import-row.component';

/**
 * Card component for displaying region-level import information.
 *
 * Shows region name, feed count badge, aggregate progress bar, and a list
 * of feed import rows. This component groups multiple feed imports by region
 * for easier monitoring.
 *
 * Always expanded (no collapsible behavior) per user requirements.
 */
@Component({
  selector: 'app-region-import-card',
  standalone: true,
  imports: [CommonModule, MatProgressBarModule, BrandCardComponent, FeedImportRowComponent],
  template: `
    <app-brand-card
      [title]="regionGroup.regionName"
      titleIcon="public"
      [badge]="getBadgeText()"
      [hasFooter]="false"
    >
      <!-- Aggregate progress bar -->
      <div class="region-aggregate-progress">
        <div class="progress-header">
          <span class="progress-label">Overall Progress</span>
          <span class="progress-percentage"
            >{{ regionGroup.averageProgress | number: '1.0-1' }}%</span
          >
        </div>
        <mat-progress-bar
          [value]="regionGroup.averageProgress"
          mode="determinate"
          color="primary"
        ></mat-progress-bar>
      </div>

      <!-- Feed list -->
      <div
        class="feed-list"
        role="list"
        [attr.aria-label]="'Feeds importing for ' + regionGroup.regionName"
      >
        @for (feedImport of regionGroup.feedImports; track feedImport.id) {
          <app-feed-import-row [feedImport]="feedImport" (stopImport)="onStopImport($event)" />
        }
      </div>
    </app-brand-card>
  `,
  styles: [
    `
      .region-aggregate-progress {
        margin-bottom: 1.5rem;
        padding: 0.75rem;
        background: var(--ms-color-background, #f5f5f5);
        border-radius: 0.5rem;
        border: 1px solid var(--ms-color-border, #e0e0e0);
      }

      .progress-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 0.5rem;
      }

      .progress-label {
        font-size: 0.875rem;
        font-weight: 500;
        color: var(--mat-sys-on-surface-variant, #666);
      }

      .progress-percentage {
        font-size: 1rem;
        font-weight: 600;
        color: var(--mat-sys-primary, #1976d2);
      }

      .feed-list {
        margin-top: 1rem;
      }

      /* Dark theme support */
      :host-context(.dark-theme) .region-aggregate-progress {
        background: var(--ms-color-background, #2c2c2c);
        border-color: var(--ms-color-border, #424242);
      }

      :host-context(.dark-theme) .progress-label {
        color: var(--mat-sys-on-surface-variant, #aaa);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegionImportCardComponent {
  /** Region import group data containing feeds and aggregate info */
  @Input() regionGroup!: RegionImportGroup;

  /** Event emitted when user requests to cancel an import */
  @Output() cancelImport = new EventEmitter<string>();

  /**
   * Handles stop import request from child feed row component.
   * Propagates the event up to the parent container.
   *
   * @param importId - ID of the import to cancel
   */
  onStopImport(importId: string): void {
    this.cancelImport.emit(importId);
  }

  /**
   * Generates badge text showing feed count.
   * Handles singular vs plural forms.
   *
   * @returns Badge text (e.g., "2 feeds" or "1 feed")
   */
  getBadgeText(): string {
    const count = this.regionGroup.totalFeeds;
    return count === 1 ? '1 feed' : `${count} feeds`;
  }
}
