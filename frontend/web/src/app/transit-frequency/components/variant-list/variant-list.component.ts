import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

import { RouteVariantDto } from '../../services/frequency.service';
import { RouteVariantCardComponent } from '../route-variant-card/route-variant-card.component';

@Component({
  selector: 'app-variant-list',
  standalone: true,
  imports: [CommonModule, RouteVariantCardComponent],
  template: `
    <div class="list flex flex-col gap-3" role="list">
      @for (variant of variants; track variant.id) {
        <app-route-variant-card
          [variant]="variant"
          (select)="select($event)">
        </app-route-variant-card>
      }
    </div>
  `,
  styles: [],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VariantListComponent {
  @Input() variants: RouteVariantDto[] = [];
  @Output() variantSelect = new EventEmitter<string>();

  select(id: string): void {
    this.variantSelect.emit(id);
  }
}
