import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatChipsModule } from '@angular/material/chips';
import { FeedImportSummary } from '../models/import.models';
import { ProgressMonitorComponent } from './progress-monitor.component';
import { MobilispectCardComponent } from '../../core/components/mobilispect-card.component';

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
    MatIconModule,
    MatButtonModule,
    MatCheckboxModule,
    MatTooltipModule,
    MatExpansionModule,
    MatChipsModule,
    ProgressMonitorComponent,
    MobilispectCardComponent
  ],
  template: `
    <mat-expansion-panel class="active-imports-panel" [expanded]="isExpanded" (expandedChange)="isExpanded = $event">
      <mat-expansion-panel-header class="panel-header">
        <mat-panel-title class="panel-title">
          <mat-icon>downloading</mat-icon>
          <span>Active Imports</span>
          @if (activeImports$ | async; as activeImports) {
            <span class="count-badge">{{ activeImports.length }}</span>
          }
        </mat-panel-title>
        @if (selectedImportIds.size > 0) {
          <mat-panel-description class="panel-description">
            <span class="selection-count">{{ selectedImportIds.size }} selected</span>
            <button
              mat-icon-button
              color="warn"
              (click)="onBulkCancel(); $event.stopPropagation()"
              [disabled]="selectedImportIds.size === 0"
              matTooltip="Cancel selected imports"
            >
              <mat-icon>cancel</mat-icon>
            </button>
          </mat-panel-description>
        }
      </mat-expansion-panel-header>

      <!-- Active imports list -->
      @if (activeImports$ | async; as activeImports) {
        @if (activeImports.length > 0) {
          <div class="active-imports-list">
            @for (importItem of activeImports; track importItem.id) {
              <app-mobilispect-card class="import-item-card">
                <div card-header class="import-card-header">
                  <mat-checkbox
                    [checked]="selectedImportIds.has(importItem.id)"
                    (change)="onSelectionChange(importItem.id, $event.checked)"
                    [attr.aria-label]="'Select ' + importItem.feedName"
                  ></mat-checkbox>

                  <div class="import-avatar">
                    <mat-icon>rss_feed</mat-icon>
                  </div>

                  <div class="import-title" card-title>
                    {{ importItem.feedName }}
                  </div>

                  <div class="import-subtitle" card-subtitle>
                    {{ importItem.regionName }}
                  </div>

                  <button
                    mat-icon-button
                    color="warn"
                    (click)="onCancelImport(importItem.id)"
                    matTooltip="Cancel import"
                    class="cancel-button"
                  >
                    <mat-icon>cancel</mat-icon>
                  </button>
                </div>

                <div card-content class="import-card-content">
                  <div class="import-meta">
                    <mat-chip-set aria-label="Import status">
                      <mat-chip [ngClass]="{
                        'status-pending': importItem.status === 'pending',
                        'status-running': importItem.status === 'running'
                      }">
                        {{ importItem.status }}
                      </mat-chip>
                    </mat-chip-set>
                    <span class="started-time">
                      Started: {{ importItem.startedAt | date:'short' }}
                    </span>
                  </div>

                  <!-- Progress monitor -->
                  <app-progress-monitor
                    [importId]="importItem.id"
                  ></app-progress-monitor>
                </div>
              </app-mobilispect-card>
            }
          </div>
        } @else {
          <div class="empty-state">
            <mat-icon class="empty-icon">cloud_done</mat-icon>
            <p class="empty-title">No active imports</p>
            <p class="empty-subtitle">
              Import feeds from the discovery tab to see them here.
            </p>
          </div>
        }
      } @else {
        <div class="empty-state">
          <mat-icon class="empty-icon">cloud_done</mat-icon>
          <p class="empty-title">No active imports</p>
          <p class="empty-subtitle">
            Import feeds from the discovery tab to see them here.
          </p>
        </div>
      }
    </mat-expansion-panel>
  `,
  styleUrls: ['../styles/card.styles.css'],
  styles: [`
    /* Expansion Panel Styles */
    .active-imports-panel {
      margin-bottom: 24px;
      border-radius: 12px !important;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1) !important;
      border: 1px solid rgba(0, 0, 0, 0.12) !important;
    }

    :host-context(.dark-theme) .active-imports-panel {
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

    .count-badge {
      background: rgba(255, 255, 255, 0.25);
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.875rem;
      font-weight: 600;
    }

    .panel-description {
      display: flex !important;
      align-items: center !important;
      gap: 12px !important;
      justify-content: flex-end !important;
    }

    .selection-count {
      font-size: 14px;
      color: rgba(255, 255, 255, 0.9);
    }

    /* Active Imports List */
    .active-imports-list {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 16px;
    }

    /* Individual Import Item Cards */
    .import-item-card {
      border-radius: 8px !important;
      transition: all 0.2s ease;
    }

    .import-item-card:hover {
      box-shadow: 0 4px 12px rgba(0,0,0,0.15) !important;
      transform: translateY(-2px);
    }

    .import-card-header {
      position: relative;
      padding: 16px !important;
    }

    .import-card-header mat-checkbox {
      position: absolute;
      left: 16px;
      top: 50%;
      transform: translateY(-50%);
    }

    .import-avatar {
      background-color: #2980B9 !important;
      color: white !important;
      margin-left: 40px !important;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    :host-context(.dark-theme) .import-avatar {
      background-color: #1e5f8c !important;
    }

    .import-title {
      font-size: 1rem !important;
      font-weight: 600 !important;
      color: #1A3A52 !important;
    }

    :host-context(.dark-theme) .import-title {
      color: #e0e0e0 !important;
    }

    .import-subtitle {
      font-size: 0.875rem !important;
      color: #666 !important;
    }

    :host-context(.dark-theme) .import-subtitle {
      color: #aaa !important;
    }

    .cancel-button {
      position: absolute !important;
      right: 8px;
      top: 50%;
      transform: translateY(-50%);
    }

    .import-card-content {
      padding: 0 16px 16px 16px !important;
    }

    .import-meta {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 16px;
      flex-wrap: wrap;
    }

    .started-time {
      font-size: 0.8125rem;
      color: #666;
    }

    :host-context(.dark-theme) .started-time {
      color: #aaa;
    }

    mat-chip {
      font-size: 12px !important;
      font-weight: 600 !important;
      text-transform: uppercase !important;
      letter-spacing: 0.5px !important;
      min-height: 28px !important;
    }

    mat-chip.status-pending {
      background-color: rgba(33, 150, 243, 0.15) !important;
      color: #1565C0 !important;
      border: 1px solid rgba(33, 150, 243, 0.3) !important;
    }

    :host-context(.dark-theme) mat-chip.status-pending {
      background-color: rgba(33, 150, 243, 0.25) !important;
      color: #64b5f6 !important;
      border-color: rgba(33, 150, 243, 0.5) !important;
    }

    mat-chip.status-running {
      background-color: rgba(33, 150, 243, 0.15) !important;
      color: #1565C0 !important;
      border: 1px solid rgba(33, 150, 243, 0.3) !important;
    }

    :host-context(.dark-theme) mat-chip.status-running {
      background-color: rgba(33, 150, 243, 0.25) !important;
      color: #64b5f6 !important;
      border-color: rgba(33, 150, 243, 0.5) !important;
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

    /* Responsive */
    @media (max-width: 768px) {
      .panel-title {
        font-size: 1rem !important;
      }

      .import-meta {
        flex-direction: column;
        align-items: flex-start;
      }

      .import-card-header {
        padding-bottom: 60px !important;
      }

      .cancel-button {
        top: auto;
        bottom: 16px;
        transform: none;
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

  isExpanded = true;

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
