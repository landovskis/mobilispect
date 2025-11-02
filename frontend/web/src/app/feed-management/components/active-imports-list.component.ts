import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FeedImportSummary } from '../models/import.models';
import { ProgressMonitorComponent } from './progress-monitor.component';

/**
 * Active Imports List Component
 *
 * Displays currently running imports with real-time progress monitoring,
 * bulk selection, and cancellation capabilities.
 *
 * @example
 * ```html
 * <app-active-imports-list
 *   [activeImports$]="activeImports$"
 *   [selectedImportIds]="selectedIds"
 *   (selectionChange)="handleSelection($event)"
 *   (bulkCancel)="cancelSelected()"
 *   (cancelImport)="cancelOne($event)">
 * </app-active-imports-list>
 * ```
 */
@Component({
  selector: 'app-active-imports-list',
  standalone: true,
  imports: [
    CommonModule,
    MatIconModule,
    MatButtonModule,
    MatCheckboxModule,
    MatTooltipModule,
    ProgressMonitorComponent
  ],
  template: `
    <div class="active-imports-section" *ngIf="(activeImports$ | async) as activeImports">
      <div *ngIf="activeImports.length > 0" class="active-imports-container">
        <!-- Header with bulk actions -->
        <div class="section-header">
          <h3>
            <mat-icon>download</mat-icon>
            Active Imports ({{ activeImports.length }})
          </h3>
          <div class="bulk-actions" *ngIf="selectedImportIds.size > 0">
            <span class="selection-count">{{ selectedImportIds.size }} selected</span>
            <button
              mat-raised-button
              color="warn"
              (click)="onBulkCancel()"
              [disabled]="selectedImportIds.size === 0"
            >
              <mat-icon>cancel</mat-icon>
              Cancel Selected
            </button>
          </div>
        </div>

        <!-- Active imports cards -->
        <div class="active-imports-list">
          <div *ngFor="let importItem of activeImports" class="active-import-card">
            <div class="card-header">
              <mat-checkbox
                [checked]="selectedImportIds.has(importItem.id)"
                (change)="onSelectionChange(importItem.id, $event.checked)"
                [attr.aria-label]="'Select ' + importItem.feedName"
              ></mat-checkbox>

              <div class="import-info">
                <div class="feed-name">{{ importItem.feedName }}</div>
                <div class="region-name">{{ importItem.regionName }}</div>
              </div>

              <div class="import-meta">
                <span class="status-badge status-{{ importItem.status.toLowerCase() }}">
                  {{ importItem.status }}
                </span>
                <span class="started-time">
                  Started: {{ importItem.startedAt | date:'short' }}
                </span>
              </div>

              <button
                mat-icon-button
                color="warn"
                (click)="onCancelImport(importItem.id)"
                matTooltip="Cancel import"
              >
                <mat-icon>cancel</mat-icon>
              </button>
            </div>

            <!-- Progress monitor -->
            <app-progress-monitor
              [importId]="importItem.id"
            ></app-progress-monitor>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .active-imports-section {
      margin-bottom: 32px;
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

    .bulk-actions {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .selection-count {
      font-size: 14px;
      color: #666;
    }

    .active-imports-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 0 16px;
    }

    .active-import-card {
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      padding: 16px;
      background: white;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    }

    .card-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 12px;
    }

    .import-info {
      flex: 1;
    }

    .feed-name {
      font-weight: 500;
      font-size: 16px;
    }

    .region-name {
      font-size: 13px;
      color: #666;
    }

    .import-meta {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .started-time {
      font-size: 12px;
      color: #666;
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

    .status-pending {
      background-color: rgba(33, 150, 243, 0.1);
      color: #1976d2;
    }

    .status-running {
      background-color: rgba(255, 193, 7, 0.1);
      color: #f57c00;
    }

    @media (max-width: 768px) {
      .card-header {
        flex-wrap: wrap;
      }

      .import-meta {
        flex-direction: column;
        align-items: flex-start;
        width: 100%;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ActiveImportsListComponent {
  @Input() activeImports$: Observable<FeedImportSummary[]> | null = null;
  @Input() selectedImportIds: Set<string> = new Set();

  @Output() selectionChange = new EventEmitter<{ id: string; selected: boolean }>();
  @Output() bulkCancel = new EventEmitter<void>();
  @Output() cancelImport = new EventEmitter<string>();

  onSelectionChange(id: string, selected: boolean): void {
    this.selectionChange.emit({ id, selected });
  }

  onBulkCancel(): void {
    this.bulkCancel.emit();
  }

  onCancelImport(id: string): void {
    this.cancelImport.emit(id);
  }
}
