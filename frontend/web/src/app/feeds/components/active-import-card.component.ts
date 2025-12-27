import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { FeedImportSummary } from '../models';
import { BrandBadgeComponent } from '../../shared/components/brand-badge.component';
import { BrandButtonComponent } from '../../shared/components/brand-button.component';

@Component({
  selector: 'app-active-import-item',
  standalone: true,
  imports: [
    CommonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressBarModule,
    BrandBadgeComponent,
    BrandButtonComponent
  ],
  template: `
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
          (click)="onCancelImport()"
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
  `,
  styles: [`
    .import-item-card { background: var(--mat-sys-surface, #ffffff); border: 1px solid var(--mat-sys-outline-variant, #e2e8f0); border-radius: 12px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); transition: box-shadow 0.2s ease; }
    .import-item-card:hover { box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15); }
    .import-avatar { background: var(--mat-sys-primary, #0b4f8a); color: var(--mat-sys-on-primary, #fff); flex-shrink: 0; }
    .import-title { font-size: 1rem; font-weight: 700; color: var(--mat-sys-on-surface, #1a3a52); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .import-subtitle { font-size: 0.9rem; color: var(--mat-sys-on-surface-variant, #666); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .started-time { color: var(--mat-sys-on-surface-variant, #475569); font-size: 0.9rem; }
    .progress-percentage { font-size: 1.1rem; font-weight: 700; color: var(--mat-sys-primary, #0b4f8a); }
    .progress-step { font-size: 0.85rem; color: var(--mat-sys-on-surface-variant, #475569); font-style: italic; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ActiveImportCardComponent {
  @Input() importItem!: FeedImportSummary;

  @Output() cancelImport = new EventEmitter<string>();

  onCancelImport(): void {
    this.cancelImport.emit(this.importItem.id);
  }
}
