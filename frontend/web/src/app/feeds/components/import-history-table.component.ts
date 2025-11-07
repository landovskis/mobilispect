import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FeedImportSummary } from '../models/import.models';

/**
 * Import History Table Component
 *
 * Displays completed feed imports in a paginated table format with
 * status indicators and metadata.
 *
 * @example
 * ```html
 * <app-import-history-table
 *   [loading]="isLoading"
 *   [history]="imports"
 *   [totalItems]="total"
 *   [pageIndex]="0"
 *   [pageSize]="20"
 *   (pageChange)="loadPage($event)">
 * </app-import-history-table>
 * ```
 */
@Component({
  selector: 'app-import-history-table',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  template: `
    <div class="history-container">
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

      <!-- History Table -->
      <div *ngIf="!loading && history && history.length > 0" class="history-section">
        <div class="section-header" *ngIf="showHeader">
          <h3>
            <mat-icon>history</mat-icon>
            Completed Imports
          </h3>
        </div>

        <table mat-table [dataSource]="history" class="history-table">
          <!-- Feed Name Column -->
          <ng-container matColumnDef="feedName">
            <th mat-header-cell *matHeaderCellDef>Feed</th>
            <td mat-cell *matCellDef="let importItem">
              {{ importItem.feedName || importItem.feedOnestopId }}
            </td>
          </ng-container>

          <!-- Region Column -->
          <ng-container matColumnDef="region">
            <th mat-header-cell *matHeaderCellDef>Region</th>
            <td mat-cell *matCellDef="let importItem">
              {{ importItem.regionName || importItem.regionOnestopId }}
            </td>
          </ng-container>

          <!-- Status Column -->
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let importItem">
              <span [ngClass]="{
                'status-badge': true,
                'status-completed': importItem.status === 'COMPLETED',
                'status-failed': importItem.status === 'FAILED',
                'status-cancelled': importItem.status === 'CANCELLED'
              }">
                <mat-icon *ngIf="importItem.status === 'COMPLETED'">check_circle</mat-icon>
                <mat-icon *ngIf="importItem.status === 'FAILED'">error</mat-icon>
                <mat-icon *ngIf="importItem.status === 'CANCELLED'">cancel</mat-icon>
                {{ importItem.status }}
              </span>
            </td>
          </ng-container>

          <!-- Started At Column -->
          <ng-container matColumnDef="startedAt">
            <th mat-header-cell *matHeaderCellDef>Started</th>
            <td mat-cell *matCellDef="let importItem">
              {{ importItem.startedAt | date:'short' }}
            </td>
          </ng-container>

          <!-- Completed At Column -->
          <ng-container matColumnDef="completedAt">
            <th mat-header-cell *matHeaderCellDef>Completed</th>
            <td mat-cell *matCellDef="let importItem">
              {{ importItem.completedAt | date:'short' }}
            </td>
          </ng-container>

          <!-- File Size Column -->
          <ng-container matColumnDef="fileSize">
            <th mat-header-cell *matHeaderCellDef>Size</th>
            <td mat-cell *matCellDef="let importItem">
              {{ formatFileSize(importItem.fileSizeBytes) }}
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>

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
    </div>
  `,
  styles: [`
    .history-container {
      width: 100%;
    }

    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
      padding: 0 16px;
    }

    .section-header h3 {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
      font-size: 18px;
      font-weight: 500;
    }

    .loading-container {
      text-align: center;
      padding: 40px;
    }

    .loading-container p {
      margin-top: 20px;
      color: #666;
    }

    .history-section {
      margin-top: 24px;
    }

    .history-table {
      width: 100%;
      margin-bottom: 16px;
    }

    .empty-state {
      text-align: center;
      padding: 60px 20px;
      color: #666;
    }

    .empty-title {
      font-size: 18px;
      margin-top: 20px;
    }

    .empty-subtitle {
      color: #999;
    }

    .empty-icon {
      font-size: 64px;
      width: 64px;
      height: 64px;
      color: #999;
    }

    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 4px 10px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .status-badge mat-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }

    .status-completed {
      background-color: rgba(76, 175, 80, 0.1);
      color: #2e7d32;
    }

    .status-failed {
      background-color: rgba(244, 67, 54, 0.1);
      color: #c62828;
    }

    .status-cancelled {
      background-color: rgba(255, 152, 0, 0.1);
      color: #e65100;
    }

    @media (max-width: 768px) {
      .history-table {
        font-size: 0.875rem;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ImportHistoryTableComponent {
  @Input() loading = false;
  @Input() history: FeedImportSummary[] | null = [];
  @Input() totalItems = 0;
  @Input() pageIndex = 0;
  @Input() pageSize = 20;
  @Input() pageSizeOptions: number[] = [10, 20, 50, 100];
  @Input() displayedColumns: string[] = ['feedName', 'region', 'status', 'startedAt', 'completedAt', 'fileSize'];
  @Input() showHeader = true;

  @Output() pageChange = new EventEmitter<number>();

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
