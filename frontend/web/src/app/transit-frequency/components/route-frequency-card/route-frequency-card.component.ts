import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';
import { VariantListComponent } from '../variant-list/variant-list.component';
import { FrequencyChartComponent } from '../frequency-chart/frequency-chart.component';
import { CommonSectionDisplayComponent } from '../common-section-display/common-section-display.component';
import { FrequencyDto, RouteDto, RouteVariantDto } from '../../services/frequency.service';
import { CommonSectionDto, CombinedFrequencyDto } from '../../services/common-section.service';

@Component({
  selector: 'app-route-frequency-card',
  standalone: true,
  imports: [
    FormsModule,
    BrandCardComponent,
    VariantListComponent,
    FrequencyChartComponent,
    CommonSectionDisplayComponent
],
  template: `
    <app-brand-card [title]="route?.longName" [subtitle]="route?.shortName || undefined">
      <label class="date-picker" aria-label="Select service date">
        <span>Service date</span>
        <input
          type="date"
          [ngModel]="selectedDate"
          (ngModelChange)="onDateChange($event)" />
      </label>
      <app-variant-list
        [variants]="variants"
        (variantSelect)="onVariantSelect($event)">
      </app-variant-list>

      <app-frequency-chart
        [frequencies]="frequencies">
      </app-frequency-chart>

      <app-common-section-display
        [sections]="commonSections"
        [combined]="combinedBySection">
      </app-common-section-display>
    </app-brand-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RouteFrequencyCardComponent {
  @Input() route?: RouteDto;
  @Input() variants: RouteVariantDto[] = [];
  @Input() frequencies: FrequencyDto[] = [];
  @Input() commonSections: CommonSectionDto[] = [];
  @Input() combinedBySection: Record<string, CombinedFrequencyDto> = {};
  @Input() selectedDate?: string;
  @Output() variantSelect = new EventEmitter<string>();
  @Output() dateChange = new EventEmitter<string>();

  onVariantSelect(variantId: string): void {
    this.variantSelect.emit(variantId);
  }

  onDateChange(date: string): void {
    this.dateChange.emit(date);
  }
}
