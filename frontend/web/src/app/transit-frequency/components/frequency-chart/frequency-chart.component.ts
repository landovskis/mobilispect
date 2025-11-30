import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FrequencyDto } from '../../services/frequency.service';

@Component({
  selector: 'app-frequency-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="chart" *ngIf="frequencies?.length; else empty">
      @for (freq of frequencies; track freq.id) {
        <div class="row">
          <span class="period">{{ freq.timePeriod }}</span>
          <span class="headway">
            @if (!freq.isIrregular && freq.averageHeadwayMinutes !== null && freq.averageHeadwayMinutes !== undefined) {
              {{ freq.averageHeadwayMinutes }} min avg
            } @else {
              <span class="irregular">Variable schedule</span>
            }
          </span>
        </div>
      }
    </div>
    <ng-template #empty>
      <p class="muted">Select a variant to view frequencies.</p>
    </ng-template>
  `,
  styles: [`
    .chart { display: flex; flex-direction: column; gap: 8px; }
    .row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid var(--mat-sys-outline, #e2e8f0); }
    .period { font-weight: 600; }
    .headway { color: var(--mat-sys-on-surface-variant, #475569); }
    .irregular { color: #c2410c; font-weight: 600; }
    .muted { color: var(--mat-sys-on-surface-variant, #94a3b8); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FrequencyChartComponent {
  @Input() frequencies: FrequencyDto[] = [];
}
