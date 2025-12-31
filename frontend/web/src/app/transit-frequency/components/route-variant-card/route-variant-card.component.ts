import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

import { RouteVariantDto } from '../../services/frequency.service';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';
import { StatsBadgeComponent } from '../../../shared/components/stats-badge.component';

@Component({
  selector: 'app-route-variant-card',
  standalone: true,
  imports: [CommonModule, BrandCardComponent, StatsBadgeComponent],
  template: `
    <div
      class="variant"
      role="button"
      tabindex="0"
      [class.pointer-events-none]="loading"
      (click)="onSelect()"
      (keydown.enter)="$event.preventDefault(); onSelect()"
      (keydown.space)="$event.preventDefault(); onSelect()">
      <app-brand-card [loading]="loading"
        title="{{ stopRangeLabel }}">
        @if (!loading && variant) {
          <div class="title flex flex-col gap-0.5">
            <app-stats-badge
              title="Avg spacing"
              [number]="averageSpacingMeters"
              unit="m">
            </app-stats-badge>

            <ul class="stop-list m-0 ml-4 list-none">
              @for (stopName of stopNames; track $index) {
                <li class="stop-name">{{ stopName }}</li>
                @if (spacingByIndex[$index]) {
                  <li class="spacing-inline">
                    <span class="spacing-segment rounded-full px-2 py-0.5 text-[0.75rem] font-semibold">
                      {{ spacingByIndex[$index] }} m
                    </span>
                  </li>
                }
              }
            </ul>
            <div class="meta flex flex-wrap items-center gap-2 text-sm">
              @if (variant.stopSpacingClassification) {
                <span class="classification rounded-full px-2 py-0.5 text-[0.75rem] font-semibold" [ngClass]="classificationClass(variant.stopSpacingClassification)">
                  {{ formatClassification(variant.stopSpacingClassification) }}
                </span>
              }
            </div>
          </div>
        }
      </app-brand-card>
    </div>
  `,
  styles: [`
    .variant {
      border-bottom-color: var(--mat-sys-outline, #e2e8f0);
      border-right: 1px solid var(--mat-sys-outline, #e2e8f0);
      cursor: pointer;
      display: block;
      padding-right: 12px;
    }
    .stop-list {
      border-left: 10px solid var(--mat-sys-primary, #0b4f8a);
      color: var(--mat-sys-on-surface-variant, #64748b);
      font-size: 0.85rem;
      padding-left: 12px;
    }
    .stop-name {
      margin: 6px 0;
      position: relative;
    }
    .stop-name::before {
      background: var(--mat-sys-on-primary, #ffffff);
      border-radius: 999px;
      content: '';
      height: 8px;
      left: -21px;
      position: absolute;
      top: 0.45em;
      width: 8px;
    }
    .spacing-inline {
      margin: 2px 0 6px;
    }
    .spacing-segment {
      background: rgba(11, 79, 138, 0.08);
      color: var(--mat-sys-primary, #0b4f8a);
    }
    .classification { background: rgba(11, 79, 138, 0.12); color: #0b4f8a; }
    .classification.local { background: rgba(76, 175, 80, 0.15); color: #2e7d32; }
    .classification.rapid { background: rgba(255, 152, 0, 0.15); color: #ef6c00; }
    .classification.region-local { background: rgba(14, 165, 233, 0.15); color: #0369a1; }
    .classification.region-rapid { background: rgba(37, 99, 235, 0.18); color: #1e3a8a; }
    .classification.region-express { background: rgba(147, 51, 234, 0.18); color: #6b21a8; }
    .classification.express { background: rgba(244, 67, 54, 0.15); color: #c62828; }
    :host-context(.dark-theme) .variant {
      border-bottom-color: rgba(148, 163, 184, 0.3);
      border-right-color: rgba(148, 163, 184, 0.3);
    }
    :host-context(.dark-theme) .stop-list {
      border-left-color: var(--mat-sys-primary, #0b4f8a);
      color: var(--mat-sys-on-surface-variant, #cbd5e1);
    }
    :host-context(.dark-theme) .spacing-segment {
      background: rgba(59, 130, 246, 0.2);
      color: var(--mat-sys-on-surface, #e2e8f0);
    }
    :host-context(.dark-theme) .classification { background: rgba(59, 130, 246, 0.2); color: #e2e8f0; }
    :host-context(.dark-theme) .classification.local { background: rgba(76, 175, 80, 0.2); color: #bbf7d0; }
    :host-context(.dark-theme) .classification.rapid { background: rgba(255, 152, 0, 0.2); color: #fed7aa; }
    :host-context(.dark-theme) .classification.region-local { background: rgba(56, 189, 248, 0.25); color: #bae6fd; }
    :host-context(.dark-theme) .classification.region-rapid { background: rgba(96, 165, 250, 0.25); color: #bfdbfe; }
    :host-context(.dark-theme) .classification.region-express { background: rgba(192, 132, 252, 0.25); color: #e9d5ff; }
    :host-context(.dark-theme) .classification.express { background: rgba(244, 67, 54, 0.2); color: #fecaca; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RouteVariantCardComponent {
  @Input() variant?: RouteVariantDto;
  @Input() loading = false;
  @Output() select = new EventEmitter<string>();

  get stopNames(): string[] {
    if (!this.variant) {
      return [];
    }
    if (this.variant.stopNames && this.variant.stopNames.length > 0) {
      return this.variant.stopNames.filter(Boolean);
    }
    return this.variant.stopPattern.split('|').filter(Boolean);
  }

  get stopRangeLabel(): string {
    const names = this.stopNames;
    if (names.length === 0) {
      return this.variant?.headsign || 'Variant';
    }
    if (names.length === 1) {
      return names[0];
    }
    return `${names[0]} → ${names[names.length - 1]}`;
  }

  get spacingByIndex(): string[] {
    return this.variant?.stopSpacingMeters?.map((meters: number) => meters.toFixed(0)) ?? [];
  }

  get averageSpacingMeters(): string {
    if (!this.variant || this.variant.averageStopSpacingMeters === null || this.variant.averageStopSpacingMeters === undefined) {
      return '—';
    }
    return this.variant.averageStopSpacingMeters.toFixed(0);
  }

  onSelect(): void {
    if (this.loading || !this.variant) return;
    this.select.emit(this.variant.id);
  }

  formatClassification(
    classification:
      | 'local'
      | 'rapid'
      | 'region-local'
      | 'region-rapid'
      | 'region-express'
      | 'express'
  ): string {
    switch (classification) {
      case 'region-local':
        return 'Region Local';
      case 'region-rapid':
        return 'Region Rapid';
      case 'region-express':
        return 'Region Express';
      default:
        return classification.charAt(0).toUpperCase() + classification.slice(1);
    }
  }

  classificationClass(
    classification:
      | 'local'
      | 'rapid'
      | 'region-local'
      | 'region-rapid'
      | 'region-express'
      | 'express'
  ): string {
    return classification;
  }

}
