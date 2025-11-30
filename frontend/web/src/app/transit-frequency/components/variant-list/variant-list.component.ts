import { Component, EventEmitter, Input, Output, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouteVariantDto } from '../../services/frequency.service';
import { BrandButtonComponent } from '../../../shared/components/brand-button.component';

@Component({
  selector: 'app-variant-list',
  standalone: true,
  imports: [CommonModule, BrandButtonComponent],
  template: `
    <div class="list" role="list">
      @for (variant of variants; track variant.id) {
        <div class="variant" role="listitem">
          <div class="title">
            <span>{{ variant.headsign || 'Variant' }}</span>
            <small>Stops: {{ variant.stopCount }}</small>
            <small class="pattern">Pattern: {{ variant.stopPattern }}</small>
          </div>
          <app-brand-button variant="primary" (click)="select(variant.id)">
            View frequencies
          </app-brand-button>
        </div>
      }
    </div>
  `,
  styles: [`
    .list { display: flex; flex-direction: column; gap: 12px; }
    .variant { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--mat-sys-outline, #e2e8f0); }
    .title { display: flex; flex-direction: column; gap: 2px; }
    .pattern { color: var(--mat-sys-on-surface-variant, #64748b); font-size: 0.85rem; }
    :host-context(.dark-theme) .variant { border-bottom-color: rgba(148, 163, 184, 0.3); }
    :host-context(.dark-theme) .pattern { color: var(--mat-sys-on-surface-variant, #cbd5e1); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VariantListComponent {
  @Input() variants: RouteVariantDto[] = [];
  @Output() variantSelect = new EventEmitter<string>();

  select(id: string): void {
    this.variantSelect.emit(id);
  }
}
