import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { FeedImportSummary } from '../models/import.models';
import { ProgressMonitorComponent } from './progress-monitor.component';
import { BrandCardComponent } from '../../shared/components/brand-card.component';
import { BrandSectionComponent } from '../../shared/components/brand-section.component';

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

    MatCheckboxModule,
    MatTooltipModule,
    MatChipsModule,
    ProgressMonitorComponent,
    BrandCardComponent,
    BrandSectionComponent
  ],
  template: `
    <app-brand-section
      class="active-imports-panel"
      title="Active Imports"
      subtitle="Running feed imports with real-time progress"
      icon="downloading"
      [collapsible]="true"
      [(expanded)]="isExpanded">
      <div section-actions class="panel-actions">
        @if (activeImports$ | async; as activeImports) {
          <span class="count-badge">{{ activeImports.length }}</span>
        }
        @if (selectedImportIds.size > 0) {
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
        }
      </div>

      <!-- Active imports list -->
      @if (activeImports$ | async; as activeImports) {
        @if (activeImports.length > 0) {
          <div class="active-imports-list">
            @for (importItem of activeImports; track importItem.id) {
              <app-brand-card class="import-item-card">
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
              </app-brand-card>
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
    </app-brand-section>
  `,
    styles: [`
    .active-imports-panel { margin-bottom: 24px; display: block; }
    .panel-actions { display: inline-flex; align-items: center; gap: 10px; }
    .count-badge { padding: 4px 10px; border-radius: 999px; background: var(--mat-sys-surface-variant, #e2e8f0); color: var(--mat-sys-primary, #0b4f8a); font-weight: 700; font-size: 0.85rem; }
    .active-imports-list { display: flex; flex-direction: column; gap: 16px; padding: 4px; }
    .import-card-header { display: grid; grid-template-columns: auto 1fr auto; gap: 12px; align-items: center; }
    .import-avatar { background: var(--mat-sys-primary, #0b4f8a); color: var(--mat-sys-on-primary, #fff); display: flex; align-items: center; justify-content: center; width: 40px; height: 40px; border-radius: 12px; }
    .import-title { font-size: 1rem; font-weight: 700; color: var(--mat-sys-on-surface, #1a3a52); }
    .import-subtitle { font-size: 0.9rem; color: var(--mat-sys-on-surface-variant, #666); }
    .import-card-content { display: flex; flex-direction: column; gap: 12px; }
    .import-meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
    .started-time { display: inline-flex; align-items: center; gap: 6px; color: var(--mat-sys-on-surface-variant, #475569); font-size: 0.9rem; }
    .empty-state { padding: 24px; text-align: center; color: var(--mat-sys-on-surface-variant, #475569); display: flex; flex-direction: column; gap: 6px; align-items: center; }
    .empty-icon { font-size: 48px; width: 48px; height: 48px; color: #94a3b8; }
    .empty-title { margin: 0; font-weight: 700; color: var(--mat-sys-on-surface, #0f172a); }
    .empty-subtitle { margin: 0; color: var(--mat-sys-on-surface-variant, #475569); max-width: 340px; }
    @media (max-width: 768px) { .active-imports-list { padding: 8px; } .import-card-header { grid-template-columns: 1fr; gap: 8px; align-items: start; } .import-meta { flex-direction: column; align-items: flex-start; } }
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
