import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { FeedImportSummary } from '../models/import.models';
import { BrandSectionComponent } from '../../shared/components/brand-section.component';
import { BrandBadgeComponent } from '../../shared/components/brand-badge.component';
import { BrandButtonComponent } from '../../shared/components/brand-button.component';

/**
 * Active Imports Card Component
 *
 * Displays currently running imports in a card with real-time progress monitoring
 * and individual cancellation capabilities.
 *
 * @example
 * ```html
 * <app-active-imports-card
 *   [activeImports$]="activeImports$"
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
    MatTooltipModule,
    MatProgressBarModule,
    BrandSectionComponent,
    BrandBadgeComponent,
    BrandButtonComponent
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
      </div>

      <!-- Active imports list -->
      @if (activeImports$ | async; as activeImports) {
        @if (activeImports.length > 0) {
          <div class="active-imports-list">
            @for (importItem of activeImports; track importItem.id) {
              <div class="import-item-card">
                <div class="import-card-header">
                  <div class="import-avatar">
                    <mat-icon>rss_feed</mat-icon>
                  </div>

                  <div class="import-info">
                    <div class="import-title-row">
                      <div class="import-title">
                        {{ importItem.feedName }}
                      </div>
                      <app-brand-badge
                        variant="indeterminate"
                        [label]="importItem.status"
                      />
                    </div>
                    <div class="import-subtitle">
                      {{ importItem.regionName }}
                    </div>
                  </div>

                  <app-brand-button
                    variant="destructive"
                    size="sm"
                    (click)="onCancelImport(importItem.id)"
                    matTooltip="Stop import"
                    class="stop-button"
                  >
                    <mat-icon>stop_circle</mat-icon>
                    Stop
                  </app-brand-button>
                </div>

                <div class="import-card-content">
                  <div class="import-meta">
                    <span class="started-time">
                      Started: {{ importItem.startedAt | date:'short' }}
                    </span>
                  </div>

                  @if (importItem.progress) {
                    <div class="progress-section">
                      <div class="progress-details">
                        <span class="progress-percentage">{{ importItem.progress.progressPercentage }}%</span>
                        <span class="progress-step">{{ importItem.progress.currentStep }}</span>
                      </div>
                      <mat-progress-bar
                        mode="determinate"
                        [value]="importItem.progress.progressPercentage"
                        color="primary">
                      </mat-progress-bar>
                    </div>
                  }
                </div>
              </div>
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
      }
    </app-brand-section>
  `,
    styles: [`
    .active-imports-panel { margin-bottom: 24px; display: block; }
    .panel-actions { display: inline-flex; align-items: center; gap: 10px; }
    .count-badge { padding: 4px 10px; border-radius: 999px; background: var(--mat-sys-surface-variant, #e2e8f0); color: var(--mat-sys-primary, #0b4f8a); font-weight: 700; font-size: 0.85rem; }
    .active-imports-list { display: flex; flex-direction: column; gap: 16px; padding: 4px; }
    .import-item-card { background: var(--mat-sys-surface, #ffffff); border: 1px solid var(--mat-sys-outline-variant, #e2e8f0); border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); transition: box-shadow 0.2s ease; }
    .import-item-card:hover { box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15); }
    .import-card-header { display: grid; grid-template-columns: auto 1fr auto; gap: 12px; align-items: center; margin-bottom: 12px; }
    .import-avatar { background: var(--mat-sys-primary, #0b4f8a); color: var(--mat-sys-on-primary, #fff); display: flex; align-items: center; justify-content: center; width: 40px; height: 40px; border-radius: 12px; flex-shrink: 0; }
    .import-info { min-width: 0; }
    .import-title-row { display: flex; align-items: center; gap: 8px; min-width: 0; }
    .import-title { font-size: 1rem; font-weight: 700; color: var(--mat-sys-on-surface, #1a3a52); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .import-subtitle { font-size: 0.9rem; color: var(--mat-sys-on-surface-variant, #666); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .import-card-content { display: flex; flex-direction: column; gap: 12px; }
    .import-meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
    .started-time { display: inline-flex; align-items: center; gap: 6px; color: var(--mat-sys-on-surface-variant, #475569); font-size: 0.9rem; }
    .progress-section { display: flex; flex-direction: column; gap: 8px; }
    .progress-details { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    .progress-percentage { font-size: 1.1rem; font-weight: 700; color: var(--mat-sys-primary, #0b4f8a); }
    .progress-step { font-size: 0.85rem; color: var(--mat-sys-on-surface-variant, #475569); font-style: italic; }
    .empty-state { padding: 24px; text-align: center; color: var(--mat-sys-on-surface-variant, #475569); display: flex; flex-direction: column; gap: 6px; align-items: center; }
    .empty-icon { font-size: 48px; width: 48px; height: 48px; color: #94a3b8; }
    .empty-title { margin: 0; font-weight: 700; color: var(--mat-sys-on-surface, #0f172a); }
    .empty-subtitle { margin: 0; color: var(--mat-sys-on-surface-variant, #475569); max-width: 340px; }
    @media (max-width: 768px) { .active-imports-list { padding: 8px; } .import-item-card { padding: 12px; } .import-card-header { grid-template-columns: auto 1fr auto; gap: 8px; } .import-avatar { width: 32px; height: 32px; } .import-meta { flex-direction: column; align-items: flex-start; } }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ActiveImportsCardComponent {
  @Input() activeImports$: Observable<FeedImportSummary[]> | null = null;

  @Output() cancelImport = new EventEmitter<string>();

  isExpanded = true;

  onCancelImport(id: string): void {
    this.cancelImport.emit(id);
  }
}
