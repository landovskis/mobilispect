import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

import { FrequencyDto, RouteVariantDto } from '../../services/frequency.service';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';

@Component({
  selector: 'app-route-variant-card',
  standalone: true,
  imports: [CommonModule, BrandCardComponent],
  template: `
    <div
      class="variant"
      role="button"
      tabindex="0"
      [class.pointer-events-none]="loading"
      (click)="onSelect()"
      (keydown.enter)="$event.preventDefault(); onSelect()"
      (keydown.space)="$event.preventDefault(); onSelect()">
      <app-brand-card [loading]="loading">
        @if (!loading && variant) {
          <div class="title flex flex-col gap-0.5">
            <div class="variant-header flex flex-wrap items-center gap-2">
              <span>{{ variant.headsign || 'Variant' }}</span>
              <span class="spacing-badge rounded-full px-2 py-0.5 text-[0.75rem] font-semibold">
                {{ formatSpacing(variant) }}
              </span>
            </div>
            <ul class="stop-list m-0 ml-4 list-none">
              @for (stopName of stopNames; track stopName) {
                <li class="stop-name">{{ stopName }}</li>
              }
            </ul>
            @if (spacingSegments.length > 0) {
              <ul class="spacing-list m-0 ml-4 mt-2 list-none text-sm">
                @for (segment of spacingSegments; track segment.key) {
                  <li class="spacing-item">
                    {{ segment.label }}: {{ segment.meters }} m
                  </li>
                }
              </ul>
            }
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
    .spacing-list {
      color: var(--mat-sys-on-surface-variant, #64748b);
    }
    .spacing-item {
      margin: 4px 0;
    }
    .spacing-badge {
      background: rgba(11, 79, 138, 0.12);
      color: var(--mat-sys-primary, #0b4f8a);
    }
    .classification { background: rgba(11, 79, 138, 0.12); color: #0b4f8a; }
    .classification.local { background: rgba(76, 175, 80, 0.15); color: #2e7d32; }
    .classification.rapid { background: rgba(255, 152, 0, 0.15); color: #ef6c00; }
    .classification.express { background: rgba(244, 67, 54, 0.15); color: #c62828; }
    :host-context(.dark-theme) .variant {
      border-bottom-color: rgba(148, 163, 184, 0.3);
      border-right-color: rgba(148, 163, 184, 0.3);
    }
    :host-context(.dark-theme) .stop-list {
      border-left-color: var(--mat-sys-primary, #0b4f8a);
      color: var(--mat-sys-on-surface-variant, #cbd5e1);
    }
    :host-context(.dark-theme) .spacing-badge {
      background: rgba(59, 130, 246, 0.2);
      color: var(--mat-sys-on-surface, #e2e8f0);
    }
    :host-context(.dark-theme) .spacing-list {
      color: var(--mat-sys-on-surface-variant, #cbd5e1);
    }
    :host-context(.dark-theme) .classification { background: rgba(59, 130, 246, 0.2); color: #e2e8f0; }
    :host-context(.dark-theme) .classification.local { background: rgba(76, 175, 80, 0.2); color: #bbf7d0; }
    :host-context(.dark-theme) .classification.rapid { background: rgba(255, 152, 0, 0.2); color: #fed7aa; }
    :host-context(.dark-theme) .classification.express { background: rgba(244, 67, 54, 0.2); color: #fecaca; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RouteVariantCardComponent {
  @Input() variant?: RouteVariantDto;
  @Input() frequencies: FrequencyDto[] = [];
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

  get spacingSegments(): { key: string; label: string; meters: string }[] {
    if (!this.variant || !this.variant.stopSpacingMeters?.length) {
      return [];
    }
    const names = this.stopNames;
    const spacings = this.variant.stopSpacingMeters;
    return spacings.map((meters, index) => {
      const from = names[index] ?? `Stop ${index + 1}`;
      const to = names[index + 1] ?? `Stop ${index + 2}`;
      return {
        key: `${index}-${from}-${to}`,
        label: `${from} → ${to}`,
        meters: meters.toFixed(0)
      };
    });
  }

  formatSpacing(variant: RouteVariantDto): string {
    if (variant.averageStopSpacingMeters === null || variant.averageStopSpacingMeters === undefined) {
      return 'Avg spacing: Not available';
    }
    return `Avg spacing: ${variant.averageStopSpacingMeters.toFixed(0)} m`;
  }

  onSelect(): void {
    if (this.loading || !this.variant) return;
    this.select.emit(this.variant.id);
  }

  formatClassification(classification: 'local' | 'rapid' | 'express'): string {
    return classification.charAt(0).toUpperCase() + classification.slice(1);
  }

  classificationClass(classification: 'local' | 'rapid' | 'express'): string {
    return classification;
  }
}
