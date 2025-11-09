import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatChipsModule } from '@angular/material/chips';
import { FeedImportSummary } from '../models/import.models';
import { MobilispectCardComponent } from '../../core/components/mobilispect-card.component';

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
    MatChipsModule
  ],
  template: `
    <mat-expansion-panel class="history-panel" [expanded]="isExpanded" (expandedChange)="isExpanded = $event">
      <mat-expansion-panel-header class="panel-header">
        <mat-panel-title class="panel-title">
          <mat-icon>history</mat-icon>
          <span>Import History</span>
          <span class="count-badge" *ngIf="history && history.length > 0">{{ totalItems }}</span>
        </mat-panel-title>
      </mat-expansion-panel-header>

      <!-- Loading State -->
      <div
        *ngIf="loading"
        class="loading-container"
        role="status"
        aria-live="polite"
      >
        <mat-spinner diameter="40"></mat-spinner>
        <p>Loading import history...</p>
      </div>

      <!-- Empty State -->
      <div
        *ngIf="!loading && (!history || history.length === 0)"
        class="empty-state"
      >
        <mat-icon class="empty-icon">history</mat-icon>
        <p class="empty-title">No import history available yet.</p>
        <p class="empty-subtitle">
          Start an import to see it appear here.
        </p>
      </div>

      <!-- History Cards List -->
      <div *ngIf="!loading && history && history.length > 0" class="history-container">
        <div class="history-list">
          <app-mobilispect-card *ngFor="let importItem of history" class="history-item-card">
            <div card-header class="history-card-header">
              <div class="history-avatar" [ngClass]="{
                'avatar-completed': importItem.status === 'completed',
                'avatar-failed': importItem.status === 'failed',
                'avatar-cancelled': importItem.status === 'cancelled'
              }">
                <mat-icon *ngIf="importItem.status === 'completed'">check_circle</mat-icon>
                <mat-icon *ngIf="importItem.status === 'failed'">error</mat-icon>
                <mat-icon *ngIf="importItem.status === 'cancelled'">cancel</mat-icon>
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

                <span class="meta-item" *ngIf="importItem.completedAt">
                  <mat-icon>event_available</mat-icon>
                  Completed: {{ importItem.completedAt | date:'short' }}
                </span>

                <span class="meta-item" *ngIf="importItem.fileSizeBytes">
                  <mat-icon>storage</mat-icon>
                  {{ formatFileSize(importItem.fileSizeBytes) }}
                </span>
              </div>
            </div>
          </app-mobilispect-card>
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
    </mat-expansion-panel>
  `,
  styleUrls: ['../styles/card.styles.css'],
  styles: [`
    /* Expansion Panel Styles */
    .history-panel {
      margin-bottom: 24px;
      border-radius: 12px !important;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1) !important;
      border: 1px solid rgba(0, 0, 0, 0.12) !important;
    }

    :host-context(.dark-theme) .history-panel {
      border: 1px solid rgba(255, 255, 255, 0.12) !important;
    }

    .panel-header {
      background: #2980B9 !important;
      color: white !important;
      border-radius: 12px 12px 0 0 !important;
    }

    :host-context(.dark-theme) .panel-header {
      background: #1e5f8c !important;
    }

    .panel-title {
      display: flex !important;
      align-items: center !important;
      gap: 12px !important;
      font-weight: 600 !important;
      font-size: 1.1rem !important;
    }

    .panel-title mat-icon {
      color: white !important;
    }

    /* Loading & Empty States */

    .loading-container p {
      margin-top: 20px;
      color: #666;
    }

    :host-context(.dark-theme) .loading-container p {
      color: #aaa;
    }

    .empty-state {
      text-align: center;
      padding: 60px 20px;
      color: #666;
    }

    :host-context(.dark-theme) .empty-state {
      color: #aaa;
    }

    :host-context(.dark-theme) .empty-title {
      color: #e0e0e0;
    }

    :host-context(.dark-theme) .empty-subtitle {
      color: #888;
    }

    .empty-icon {
      font-size: 64px;
      width: 64px;
      height: 64px;
      color: #999;
    }

    :host-context(.dark-theme) .empty-icon {
      color: #666;
    }

    /* History List */
    .history-list {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
      gap: 16px;
      padding: 16px;
    }

    /* Individual History Item Cards */
    .history-item-card {
      border-radius: 8px !important;
      transition: all 0.2s ease;
    }

    .history-item-card:hover {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15) !important;
      transform: translateY(-2px);
    }

    .history-card-header {
      padding: 16px !important;
    }

    .history-avatar {
      color: white !important;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    :host-context(.dark-theme) .avatar-completed {
      background-color: #388E3C !important;
    }

    :host-context(.dark-theme) .avatar-failed {
      background-color: #D32F2F !important;
    }

    :host-context(.dark-theme) .avatar-cancelled {
      background-color: #F57C00 !important;
    }

    :host-context(.dark-theme) .history-title {
      color: #e0e0e0 !important;
    }

    :host-context(.dark-theme) .history-subtitle {
      color: #aaa !important;
    }

    .meta-item mat-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
      color: #2980B9;
    }

    :host-context(.dark-theme) .meta-item {
      color: #aaa;
    }

    :host-context(.dark-theme) .meta-item mat-icon {
      color: #64b5f6;
    }

    mat-chip {
      font-size: 12px !important;
      font-weight: 600 !important;
      text-transform: uppercase !important;
      letter-spacing: 0.5px !important;
      min-height: 28px !important;
    }
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
