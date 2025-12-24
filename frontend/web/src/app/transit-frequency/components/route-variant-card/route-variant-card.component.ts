import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

import { RouteVariantDto } from '../../services/frequency.service';
import { BrandButtonComponent } from '../../../shared/components/brand-button.component';
import {BrandCardComponent} from '../../../shared/components/brand-card.component';

@Component({
  selector: 'app-route-variant-card',
  standalone: true,
  imports: [CommonModule, BrandButtonComponent, BrandCardComponent],
  template: `
    <div class="variant">
      <app-brand-card
        title="{{ variant.headsign || 'Variant' }}">
        <div class="title flex flex-col gap-0.5">
          <small>Stops: {{ variant.stopCount }}</small>
          <ul class="stop-list m-0 ml-4 list-none">
            @for (stopName of stopNames; track stopName) {
              <li class="stop-name">{{ stopName }}</li>
            }
          </ul>
          <div class="meta flex flex-wrap items-center gap-2 text-sm">
            <span class="spacing">{{ formatSpacing(variant) }}</span>
            @if (variant.stopSpacingClassification) {
              <span class="classification rounded-full px-2 py-0.5 text-[0.75rem] font-semibold" [ngClass]="classificationClass(variant.stopSpacingClassification)">
                {{ formatClassification(variant.stopSpacingClassification) }}
              </span>
            }
          </div>
        </div>
        <app-brand-button variant="primary" (click)="select.emit(variant.id)">
          View frequencies
        </app-brand-button>
      </app-brand-card>
    </div>
  `,
  styles: [`
    .variant {
      border-bottom-color: var(--mat-sys-outline, #e2e8f0);
      border-right: 1px solid var(--mat-sys-outline, #e2e8f0);
      display: block;
      padding-right: 12px;
    }
    .stop-list {
      border-left: 6px solid var(--mat-sys-outline, #e2e8f0);
      color: var(--mat-sys-on-surface-variant, #64748b);
      font-size: 0.85rem;
      padding-left: 12px;
    }
    .stop-name {
      margin: 6px 0;
      position: relative;
    }
    .stop-name::before {
      background: var(--mat-sys-primary, #0b4f8a);
      border-radius: 999px;
      content: '';
      height: 8px;
      left: -17px;
      position: absolute;
      top: 0.45em;
      width: 8px;
    }
    .spacing { color: var(--mat-sys-on-surface-variant, #64748b); }
    .classification { background: rgba(11, 79, 138, 0.12); color: #0b4f8a; }
    .classification.local { background: rgba(76, 175, 80, 0.15); color: #2e7d32; }
    .classification.rapid { background: rgba(255, 152, 0, 0.15); color: #ef6c00; }
    .classification.express { background: rgba(244, 67, 54, 0.15); color: #c62828; }
    :host-context(.dark-theme) .variant {
      border-bottom-color: rgba(148, 163, 184, 0.3);
      border-right-color: rgba(148, 163, 184, 0.3);
    }
    :host-context(.dark-theme) .stop-list {
      border-left-color: rgba(148, 163, 184, 0.3);
      color: var(--mat-sys-on-surface-variant, #cbd5e1);
    }
    :host-context(.dark-theme) .spacing { color: var(--mat-sys-on-surface-variant, #cbd5e1); }
    :host-context(.dark-theme) .classification { background: rgba(59, 130, 246, 0.2); color: #e2e8f0; }
    :host-context(.dark-theme) .classification.local { background: rgba(76, 175, 80, 0.2); color: #bbf7d0; }
    :host-context(.dark-theme) .classification.rapid { background: rgba(255, 152, 0, 0.2); color: #fed7aa; }
    :host-context(.dark-theme) .classification.express { background: rgba(244, 67, 54, 0.2); color: #fecaca; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RouteVariantCardComponent {
  @Input({ required: true }) variant!: RouteVariantDto;
  @Output() select = new EventEmitter<string>();

  get stopNames(): string[] {
    if (this.variant.stopNames && this.variant.stopNames.length > 0) {
      return this.variant.stopNames.filter(Boolean);
    }
    return this.variant.stopPattern.split('|').filter(Boolean);
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
