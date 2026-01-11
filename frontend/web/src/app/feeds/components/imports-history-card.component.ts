import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FeedImportDetail } from '../models';
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
      class="history-panel mb-6 block"
      title="Import History"
      subtitle="Completed feed imports and metadata"
      icon="history"
      [collapsible]="true"
      [(expanded)]="isExpanded">
      <div section-actions class="panel-actions inline-flex items-center gap-2.5">
        @if (history && history.length > 0) {
          <span class="count-badge rounded-full px-2.5 py-1">{{ totalItems }}</span>
        }
      </div>

      <!-- Loading State -->
      @if (loading) {
        <div
          class="loading-container flex flex-col items-center justify-center gap-3 p-6 text-center"
          role="status"
          aria-live="polite"
        >
          <mat-spinner diameter="40"></mat-spinner>
          <p>Loading import history...</p>
        </div>
      }

      <!-- Empty State -->
      @if (!loading && (!history || history.length === 0)) {
        <div class="empty-state flex flex-col items-center gap-1.5 p-6 text-center">
          <mat-icon class="empty-icon">history</mat-icon>
          <p class="empty-title m-0">No import history available yet.</p>
          <p class="empty-subtitle max-w-[340px] m-0">
            Start an import to see it appear here.
          </p>
        </div>
      }

      <!-- History Cards List -->
      @if (!loading && history && history.length > 0) {
        <div class="history-container flex flex-col gap-4">
          <div class="history-list grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            @for (importItem of history; track importItem.id) {
              <app-brand-card
                class="history-item-card"
                [title]="importItem.feedName || importItem.feedOnestopId"
                [subtitle]="importItem.regionName">
                <div card-content class="history-card-content flex flex-col gap-3">
                  <div class="history-meta flex flex-wrap items-center gap-3">
                    <app-brand-badge
                      [variant]="statusToBadge(importItem.status)"
                      [label]="importItem.status | titlecase">
                    </app-brand-badge>

                    <span class="meta-item inline-flex items-center gap-1.5">
                      <mat-icon>schedule</mat-icon>
                      Started: {{ importItem.startedAt | date:'short' }}
                    </span>

                    @if (importItem.completedAt) {
                      <span class="meta-item inline-flex items-center gap-1.5">
                        <mat-icon>event_available</mat-icon>
                        Completed: {{ importItem.completedAt | date:'short' }}
                      </span>
                    }

                    @if (importItem.fileSizeBytes) {
                      <span class="meta-item inline-flex items-center gap-1.5">
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
    .history-meta { color: var(--mat-sys-on-surface-variant, #475569); }
    .count-badge { background: var(--mat-sys-surface-variant, #e2e8f0); color: var(--mat-sys-primary, #0b4f8a); font-weight: 700; font-size: 0.85rem; }
    .empty-state { color: var(--mat-sys-on-surface-variant, #475569); }
    .empty-icon { font-size: 48px; width: 48px; height: 48px; color: #94a3b8; }
    .empty-title { font-weight: 700; color: var(--mat-sys-on-surface, #0f172a); }
    .empty-subtitle { color: var(--mat-sys-on-surface-variant, #475569); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ImportsHistoryCardComponent {
  @Input() loading = false;
  @Input() history: FeedImportDetail[] | null = [];
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
