import { Component, Input, ChangeDetectionStrategy } from '@angular/core';

import { FrequencyDto } from '../../services/frequency.service';

@Component({
  selector: 'app-frequency-chart',
  standalone: true,
  imports: [],
  template: `
    @if (frequencies?.length) {
      <div class="chart">
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
    } @else {
      <p class="muted">Select a variant to view frequencies.</p>
    }
    `,
  styles: [`
    .chart { display: flex; flex-direction: column; gap: 8px; }
    .row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid var(--mat-sys-outline, #e2e8f0); }
    .period { font-weight: 600; }
    .headway { color: var(--mat-sys-on-surface-variant, #475569); }
    .irregular { color: #c2410c; font-weight: 600; }
    .muted { color: var(--mat-sys-on-surface-variant, #94a3b8); }

    :host-context(.dark-theme) .row { border-bottom-color: rgba(148, 163, 184, 0.3); }
    :host-context(.dark-theme) .headway { color: var(--mat-sys-on-surface-variant, #cbd5e1); }
    :host-context(.dark-theme) .muted { color: var(--mat-sys-on-surface-variant, #94a3b8); }
    :host-context(.dark-theme) .irregular { color: #f97316; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FrequencyChartComponent {
  @Input() frequencies: FrequencyDto[] = [];
}
