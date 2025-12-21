import { Component, EventEmitter, Input, Output, ChangeDetectionStrategy } from '@angular/core';

import { RouteVariantDto } from '../../services/frequency.service';
import { BrandButtonComponent } from '../../../shared/components/brand-button.component';

@Component({
  selector: 'app-variant-list',
  standalone: true,
  imports: [BrandButtonComponent],
  template: `
    <div class="list flex flex-col gap-3" role="list">
      @for (variant of variants; track variant.id) {
        <div class="variant flex items-center justify-between border-b py-2" role="listitem">
          <div class="title flex flex-col gap-0.5">
            <span>{{ variant.headsign || 'Variant' }}</span>
            <small>Stops: {{ variant.stopCount }}</small>
            <small class="pattern">Pattern: {{ variant.stopPattern }}</small>
            <div class="meta flex flex-wrap items-center gap-2 text-sm">
              <span class="spacing">{{ formatSpacing(variant) }}</span>
              @if (variant.stopSpacingClassification) {
                <span class="classification rounded-full px-2 py-0.5 text-[0.75rem] font-semibold" [ngClass]="classificationClass(variant.stopSpacingClassification)">
                  {{ formatClassification(variant.stopSpacingClassification) }}
                </span>
              }
            </div>
          </div>
          <app-brand-button variant="primary" (click)="select(variant.id)">
            View frequencies
          </app-brand-button>
        </div>
      }
    </div>
  `,
  styles: [`
    .variant { border-bottom-color: var(--mat-sys-outline, #e2e8f0); }
    .pattern { color: var(--mat-sys-on-surface-variant, #64748b); font-size: 0.85rem; }
    .spacing { color: var(--mat-sys-on-surface-variant, #64748b); }
    .classification { background: rgba(11, 79, 138, 0.12); color: #0b4f8a; }
    .classification.local { background: rgba(76, 175, 80, 0.15); color: #2e7d32; }
    .classification.rapid { background: rgba(255, 152, 0, 0.15); color: #ef6c00; }
    .classification.express { background: rgba(244, 67, 54, 0.15); color: #c62828; }
    :host-context(.dark-theme) .variant { border-bottom-color: rgba(148, 163, 184, 0.3); }
    :host-context(.dark-theme) .pattern { color: var(--mat-sys-on-surface-variant, #cbd5e1); }
    :host-context(.dark-theme) .spacing { color: var(--mat-sys-on-surface-variant, #cbd5e1); }
    :host-context(.dark-theme) .classification { background: rgba(59, 130, 246, 0.2); color: #e2e8f0; }
    :host-context(.dark-theme) .classification.local { background: rgba(76, 175, 80, 0.2); color: #bbf7d0; }
    :host-context(.dark-theme) .classification.rapid { background: rgba(255, 152, 0, 0.2); color: #fed7aa; }
    :host-context(.dark-theme) .classification.express { background: rgba(244, 67, 54, 0.2); color: #fecaca; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VariantListComponent {
  @Input() variants: RouteVariantDto[] = [];
  @Output() variantSelect = new EventEmitter<string>();

  select(id: string): void {
    this.variantSelect.emit(id);
  }

  formatSpacing(variant: RouteVariantDto): string {
    if (variant.averageStopSpacingKm === null || variant.averageStopSpacingKm === undefined) {
      return 'Avg spacing: Not available';
    }
    return `Avg spacing: ${variant.averageStopSpacingKm.toFixed(2)} km`;
  }

  formatClassification(classification: 'local' | 'rapid' | 'express'): string {
    return classification.charAt(0).toUpperCase() + classification.slice(1);
  }

  classificationClass(classification: 'local' | 'rapid' | 'express'): string {
    return classification;
  }
}
