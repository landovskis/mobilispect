import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FeedImportSummary } from '../models';
import { BrandCardComponent } from '../../shared/components/brand-card.component';
import { BrandBadgeComponent } from '../../shared/components/brand-badge.component';
import { BrandSectionComponent } from '../../shared/components/brand-section.component';

/**
 * Imports History Card Component
 *
 * Displays completed feed imports in a card with paginated table format,
 * status indicators and metadata.
 *
 * @example
 * ```html
 * <app-imports-history-card
 *   [loading]="isLoading"
 *   [history]="imports"
 *   [totalItems]="total"
 *   [pageIndex]="0"
 *   [pageSize]="20"
 *   (pageChange)="loadPage($event)">
 * </app-imports-history-card>
 * ```
 */
@Component({
  selector: 'app-imports-history-card',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatIconModule,
    MatProgressSpinnerModule,
    BrandCardComponent,
    BrandBadgeComponent,
    BrandSectionComponent
  ],
  template: `
    <app-brand-section
      class="history-panel"
      title="Import History"
      subtitle="Completed feed imports and metadata"
      icon="history"
      [collapsible]="true"
      [(expanded)]="isExpanded">
      <div section-actions class="panel-actions">
        @if (history && history.length > 0) {
          <span class="count-badge">{{ totalItems }}</span>
        }
      </div>

      <!-- Loading State -->
      @if (loading) {
        <div
          class="loading-container"
          role="status"
          aria-live="polite"
        >
          <mat-spinner diameter="40"></mat-spinner>
          <p>Loading import history...</p>
        </div>
      }

      <!-- Empty State -->
      @if (!loading && (!history || history.length === 0)) {
        <div class="empty-state">
          <mat-icon class="empty-icon">history</mat-icon>
          <p class="empty-title">No import history available yet.</p>
          <p class="empty-subtitle">
            Start an import to see it appear here.
          </p>
        </div>
      }

      <!-- History Cards List -->
      @if (!loading && history && history.length > 0) {
          <div class="history-container">
          <div class="history-list">
            @for (importItem of history; track importItem.id) {
              <app-brand-card
                class="history-item-card"
                [title]="importItem.feedName || importItem.feedOnestopId"
                [subtitle]="importItem.regionName">
                <div card-content class="history-card-content">
                  <div class="history-meta">
                    <app-brand-badge
                      [variant]="statusToBadge(importItem.status)"
                      [label]="importItem.status | titlecase">
                    </app-brand-badge>

                    <span class="meta-item">
                      <mat-icon>schedule</mat-icon>
                      Started: {{ importItem.startedAt | date:'short' }}
                    </span>

                    @if (importItem.completedAt) {
                      <span class="meta-item">
                        <mat-icon>event_available</mat-icon>
                        Completed: {{ importItem.completedAt | date:'short' }}
                      </span>
                    }

                    @if (importItem.fileSizeBytes) {
                      <span class="meta-item">
                        <mat-icon>storage</mat-icon>
                        {{ formatFileSize(importItem.fileSizeBytes) }}
                      </span>
                    }
                  </div>
                </div>
              </app-brand-card>
            }
          </div>

          <!-- Paginator -->
          <mat-paginator
            [length]="totalItems"
            [pageSize]="pageSize"
            [pageIndex]="pageIndex"
            [pageSizeOptions]="pageSizeOptions"
            (page)="onPageChange($event)"
            showFirstLastButtons
          ></mat-paginator>
        </div>
      }
    </app-brand-section>
  `,
    styles: [`
    .history-panel { margin-bottom: 24px; display: block; }
    .panel-actions { display: inline-flex; align-items: center; gap: 10px; }
    .history-container { display: flex; flex-direction: column; gap: 16px; }
    .history-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
    .history-card-content { display: flex; flex-direction: column; gap: 12px; }
    .history-meta { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; color: var(--mat-sys-on-surface-variant, #475569); }
    .meta-item { display: inline-flex; align-items: center; gap: 6px; }
    .count-badge { padding: 4px 10px; border-radius: 999px; background: var(--mat-sys-surface-variant, #e2e8f0); color: var(--mat-sys-primary, #0b4f8a); font-weight: 700; font-size: 0.85rem; }
    .empty-state { text-align: center; padding: 24px; color: var(--mat-sys-on-surface-variant, #475569); display: flex; flex-direction: column; gap: 6px; align-items: center; }
    .empty-icon { font-size: 48px; width: 48px; height: 48px; color: #94a3b8; }
    .empty-title { margin: 0; font-weight: 700; color: var(--mat-sys-on-surface, #0f172a); }
    .empty-subtitle { margin: 0; color: var(--mat-sys-on-surface-variant, #475569); max-width: 340px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ImportsHistoryCardComponent {
  @Input() loading = false;
  @Input() history: FeedImportSummary[] | null = [];
  @Input() totalItems = 0;
  @Input() pageIndex = 0;
  @Input() pageSize = 20;
  @Input() pageSizeOptions: number[] = [10, 20, 50, 100];
  @Input() displayedColumns: string[] = ['feedName', 'region', 'status', 'startedAt', 'completedAt', 'fileSize'];
  @Input() showHeader = true;

  @Output() pageChange = new EventEmitter<number>();

  isExpanded = false; // Collapsed by default for history

  onPageChange(event: PageEvent): void {
    this.pageChange.emit(event.pageIndex);
  }

  formatFileSize(bytes: number | null): string {
    if (!bytes) return '-';

    const units = ['B', 'KB', 'MB', 'GB'];
    let size = bytes;
    let unitIndex = 0;

    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }

    return `${size.toFixed(1)} ${units[unitIndex]}`;
  }

  statusToBadge(status: string): 'good' | 'mixed' | 'bad' | 'neutral' {
    switch (status.toLowerCase()) {
      case 'completed':
        return 'good';
      case 'failed':
        return 'bad';
      case 'cancelled':
        return 'mixed';
      default:
        return 'neutral';
    }
  }
}
