import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { FeedImportSummary } from '../models';
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
      class="active-imports-panel mb-6 block"
      title="Active Imports"
      subtitle="Running feed imports with real-time progress"
      icon="downloading"
      [collapsible]="true"
      [(expanded)]="isExpanded">
      <div section-actions class="panel-actions inline-flex items-center gap-2.5">
        @if (activeImports$ | async; as activeImports) {
          <span class="count-badge rounded-full px-2.5 py-1">{{ activeImports.length }}</span>
        }
      </div>

      <!-- Active imports list -->
      @if (activeImports$ | async; as activeImports) {
        @if (activeImports.length > 0) {
          <div class="active-imports-list flex flex-col gap-4 p-1 max-md:p-2">
            @for (importItem of activeImports; track importItem.id) {
              <div class="import-item-card p-4 max-md:p-3">
                <div class="import-card-header mb-3 grid grid-cols-[auto,1fr,auto] items-center gap-3 max-md:gap-2">
                  <div class="import-avatar flex h-10 w-10 items-center justify-center rounded-xl max-md:h-8 max-md:w-8">
                    <mat-icon>rss_feed</mat-icon>
                  </div>

                  <div class="import-info min-w-0">
                    <div class="import-title-row flex min-w-0 items-center gap-2">
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

                <div class="import-card-content flex flex-col gap-3">
                  <div class="import-meta flex flex-wrap items-center justify-between gap-3 max-md:flex-col max-md:items-start">
                    <span class="started-time inline-flex items-center gap-1.5">
                      Started: {{ importItem.startedAt | date:'short' }}
                    </span>
                  </div>

                  @if (importItem.progress) {
                    <div class="progress-section flex flex-col gap-2">
                      <div class="progress-details flex items-center justify-between gap-3">
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
          <div class="empty-state flex flex-col items-center gap-1.5 p-6 text-center">
            <mat-icon class="empty-icon">cloud_done</mat-icon>
            <p class="empty-title m-0">No active imports</p>
            <p class="empty-subtitle max-w-[340px] m-0">
              Import feeds from the discovery tab to see them here.
            </p>
          </div>
        }
      }
    </app-brand-section>
  `,
    styles: [`
    .count-badge { background: var(--mat-sys-surface-variant, #e2e8f0); color: var(--mat-sys-primary, #0b4f8a); font-weight: 700; font-size: 0.85rem; }
    .import-item-card { background: var(--mat-sys-surface, #ffffff); border: 1px solid var(--mat-sys-outline-variant, #e2e8f0); border-radius: 12px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); transition: box-shadow 0.2s ease; }
    .import-item-card:hover { box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15); }
    .import-avatar { background: var(--mat-sys-primary, #0b4f8a); color: var(--mat-sys-on-primary, #fff); flex-shrink: 0; }
    .import-title { font-size: 1rem; font-weight: 700; color: var(--mat-sys-on-surface, #1a3a52); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .import-subtitle { font-size: 0.9rem; color: var(--mat-sys-on-surface-variant, #666); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .started-time { color: var(--mat-sys-on-surface-variant, #475569); font-size: 0.9rem; }
    .progress-percentage { font-size: 1.1rem; font-weight: 700; color: var(--mat-sys-primary, #0b4f8a); }
    .progress-step { font-size: 0.85rem; color: var(--mat-sys-on-surface-variant, #475569); font-style: italic; }
    .empty-state { color: var(--mat-sys-on-surface-variant, #475569); }
    .empty-icon { font-size: 48px; width: 48px; height: 48px; color: #94a3b8; }
    .empty-title { font-weight: 700; color: var(--mat-sys-on-surface, #0f172a); }
    .empty-subtitle { color: var(--mat-sys-on-surface-variant, #475569); }
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
