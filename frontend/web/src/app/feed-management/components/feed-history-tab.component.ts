import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FeedImportSummary } from '../models/import.models';

@Component({
  selector: 'app-feed-history-tab',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  template: `
    <div class="tab-content">
      <div
        *ngIf="loading"
        class="loading-container"
        role="status"
        aria-live="polite"
      >
        <mat-spinner diameter="40"></mat-spinner>
        <p>Loading import history...</p>
      </div>

      <div
        *ngIf="!loading && (!history || history.length === 0)"
        class="empty-state"
      >
        <mat-icon class="empty-icon">history</mat-icon>
        <p class="empty-title">No import history available yet.</p>
        <p class="empty-subtitle">
          Start an import to see it appear here when completed.
        </p>
      </div>

      <div *ngIf="!loading && history && history.length > 0">
        <table mat-table [dataSource]="history" class="history-table">
          <ng-container matColumnDef="feedName">
            <th mat-header-cell *matHeaderCellDef>Feed</th>
            <td mat-cell *matCellDef="let importItem">
              {{ importItem.feedName || importItem.feedOnestopId }}
            </td>
          </ng-container>

          <ng-container matColumnDef="region">
            <th mat-header-cell *matHeaderCellDef>Region</th>
            <td mat-cell *matCellDef="let importItem">
              {{ importItem.regionName || importItem.regionOnestopId }}
            </td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let importItem">
              <span [ngClass]="{
                'status-badge': true,
                'status-completed': importItem.status === 'COMPLETED',
                'status-failed': importItem.status === 'FAILED',
                'status-cancelled': importItem.status === 'CANCELLED'
              }">
                {{ importItem.status }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="startedAt">
            <th mat-header-cell *matHeaderCellDef>Started</th>
            <td mat-cell *matCellDef="let importItem">
              {{ importItem.startedAt | date:'short' }}
            </td>
          </ng-container>

          <ng-container matColumnDef="completedAt">
            <th mat-header-cell *matHeaderCellDef>Completed</th>
            <td mat-cell *matCellDef="let importItem">
              {{ importItem.completedAt | date:'short' }}
            </td>
          </ng-container>

          <ng-container matColumnDef="records">
            <th mat-header-cell *matHeaderCellDef>Records</th>
            <td mat-cell *matCellDef="let importItem">
              {{ importItem.recordsImported | number }}
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>

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
    .tab-content {
      padding: 24px 0;
    }

    .loading-container {
      text-align: center;
      padding: 40px;
    }

    .loading-container p {
      margin-top: 20px;
      color: #666;
    }

    .history-table {
      width: 100%;
      margin-bottom: 16px;
    }

    .status-badge {
      display: inline-block;
      padding: 4px 10px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: #555;
      background-color: #e0e0e0;
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

    @media (max-width: 768px) {
      .tab-content {
        padding: 16px 0;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FeedHistoryTabComponent {
  @Input() loading = false;
  @Input() history: FeedImportSummary[] | null = [];
  @Input() totalItems = 0;
  @Input() pageIndex = 0;
  @Input() pageSize = 20;
  @Input() pageSizeOptions: number[] = [10, 20, 50, 100];
  @Input() displayedColumns: string[] = ['feedName', 'region', 'status', 'startedAt', 'completedAt', 'records'];

  @Output() pageChange = new EventEmitter<number>();

  onPageChange(event: PageEvent): void {
    this.pageChange.emit(event.pageIndex);
  }
}
