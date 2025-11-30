import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatChipsModule } from '@angular/material/chips';
import { FeedImportSummary } from '../models/import.models';
import { BrandCardComponent } from '../../shared/components/brand-card.component';

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
    MatExpansionModule,
    MatChipsModule,
    BrandCardComponent
  ],
  template: `
    <mat-expansion-panel class="history-panel" [expanded]="isExpanded" (expandedChange)="isExpanded = $event">
      <mat-expansion-panel-header class="panel-header">
        <mat-panel-title class="panel-title">
          <mat-icon>history</mat-icon>
          <span>Import History</span>
          @if (history && history.length > 0) {
            <span class="count-badge">{{ totalItems }}</span>
          }
        </mat-panel-title>
      </mat-expansion-panel-header>

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
              <app-brand-card class="history-item-card">
                <div card-header class="history-card-header">
                  <div class="history-avatar" [ngClass]="{
                    'avatar-completed': importItem.status === 'completed',
                    'avatar-failed': importItem.status === 'failed',
                    'avatar-cancelled': importItem.status === 'cancelled'
                  }">
                    @if (importItem.status === 'completed') {
                      <mat-icon>check_circle</mat-icon>
                    } @else if (importItem.status === 'failed') {
                      <mat-icon>error</mat-icon>
                    } @else if (importItem.status === 'cancelled') {
                      <mat-icon>cancel</mat-icon>
                    }
                  </div>

                  <div class="card-title">
                    {{ importItem.feedName || importItem.feedOnestopId }}
                  </div>

                  <div class="card-subtitle">
                    {{ importItem.regionName }}
                  </div>
                </div>

                <div card-content class="history-card-content">
                  <div class="history-meta">
                    <mat-chip-set aria-label="Import status">
                      <mat-chip [class.chip-success]="importItem.status === 'completed'"
                                [class.chip-error]="importItem.status === 'failed'"
                                [class.chip-warning]="importItem.status === 'cancelled'">
                        {{ importItem.status }}
                      </mat-chip>
                    </mat-chip-set>

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
    </mat-expansion-panel>
  `,
    styles: [`
    .history-panel { margin-bottom: 24px; }
    .history-container { display: flex; flex-direction: column; gap: 16px; }
    .history-list { display: grid; gap: 12px; }
    .history-card-header { display: flex; align-items: center; gap: 12px; }
    .history-avatar { display: flex; align-items: center; justify-content: center; width: 40px; height: 40px; border-radius: 12px; background: var(--mat-sys-primary, #0b4f8a); color: var(--mat-sys-on-primary, #fff); }
    .history-card-content { display: flex; flex-direction: column; gap: 8px; }
    .history-meta { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
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
}
