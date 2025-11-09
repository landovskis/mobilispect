import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FeedImportSummary } from '../models/import.models';
import { ProgressMonitorComponent } from './progress-monitor.component';

/**
 * Active Imports Card Component
 *
 * Displays currently running imports in a card with real-time progress monitoring,
 * bulk selection, and cancellation capabilities.
 *
 * @example
 * ```html
 * <app-active-imports-card
 *   [activeImports$]="activeImports$"
 *   [selectedImportIds]="selectedIds"
 *   (selectionChange)="handleSelection($event)"
 *   (bulkCancel)="cancelSelected()"
 *   (cancelImport)="cancelOne($event)">
 * </app-active-imports-card>
 * ```
 */
@Component({
  selector: 'app-active-imports-card',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatCheckboxModule,
    MatTooltipModule,
    ProgressMonitorComponent
  ],
  template: `
    <mat-card class="app-card active-imports-card">
      <mat-card-header class="app-card-header">
        <mat-card-title class="app-card-title">
          <mat-icon>downloading</mat-icon>
          Active <span *ngIf="(activeImports$ | async) as activeImports">({{ activeImports.length }})</span>
        </mat-card-title>
        <div class="header-actions" *ngIf="selectedImportIds.size > 0">
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
      </mat-card-header>
      <mat-card-content class="app-card-content">
        <!-- Active imports list -->
        <div class="active-imports-list" *ngIf="(activeImports$ | async) as activeImports; else emptyState">
          <div *ngIf="activeImports.length > 0; else emptyState">
            <div *ngFor="let importItem of activeImports" class="active-import-item">
              <div class="import-item-header">
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

        <!-- Empty State -->
        <ng-template #emptyState>
          <div class="empty-state">
            <mat-icon class="empty-icon">cloud_done</mat-icon>
            <p class="empty-title">No active imports</p>
            <p class="empty-subtitle">
              Import feeds from the discovery tab to see them here.
            </p>
          </div>
        </ng-template>
      </mat-card-content>
    </mat-card>
  `,
  styleUrls: ['../styles/card.styles.css'],
  styles: [`
    /* Component-specific styles */
    .active-imports-card {
      margin-bottom: 24px;
    }

    .app-card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .app-card-title {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .selection-count {
      font-size: 14px;
      color: rgba(255, 255, 255, 0.9);
    }

    :host-context(.dark-theme) .selection-count {
      color: rgba(255, 255, 255, 0.9);
    }

    .active-imports-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .active-import-item {
      border: 1px solid rgba(0, 0, 0, 0.12);
      border-radius: 8px;
      padding: 16px;
      background: rgba(255, 255, 255, 0.98);
      box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    }

    :host-context(.dark-theme) .active-import-item {
      background: rgba(255, 255, 255, 0.05);
      border-color: rgba(255, 255, 255, 0.12);
    }

    .import-item-header {
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
      color: #1A3A52;
    }

    :host-context(.dark-theme) .feed-name {
      color: #e0e0e0;
    }

    .region-name {
      font-size: 13px;
      color: #666;
    }

    :host-context(.dark-theme) .region-name {
      color: #aaa;
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

    :host-context(.dark-theme) .started-time {
      color: #aaa;
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
      background-color: rgba(33, 150, 243, 0.15);
      color: #1565C0;
      border: 1px solid rgba(33, 150, 243, 0.3);
    }

    :host-context(.dark-theme) .status-pending {
      background-color: rgba(33, 150, 243, 0.25);
      color: #64b5f6;
      border-color: rgba(33, 150, 243, 0.5);
    }

    .status-running {
      background-color: rgba(243, 156, 18, 0.15);
      color: #8B5A00;
      border: 1px solid rgba(243, 156, 18, 0.3);
    }

    :host-context(.dark-theme) .status-running {
      background-color: rgba(243, 156, 18, 0.25);
      color: #ffb74d;
      border-color: rgba(243, 156, 18, 0.5);
    }

    /* Empty State */
    .empty-state {
      text-align: center;
      padding: 60px 20px;
      color: #666;
    }

    :host-context(.dark-theme) .empty-state {
      color: #aaa;
    }

    .empty-title {
      font-size: 18px;
      margin-top: 20px;
      color: #1A3A52;
    }

    :host-context(.dark-theme) .empty-title {
      color: #e0e0e0;
    }

    .empty-subtitle {
      color: #999;
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

    @media (max-width: 768px) {
      .import-item-header {
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
export class ActiveImportsCardComponent {
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
