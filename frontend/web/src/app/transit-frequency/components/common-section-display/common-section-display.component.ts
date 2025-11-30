import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CommonSectionDto, CombinedFrequencyDto } from '../../services/common-section.service';

@Component({
  selector: 'app-common-section-display',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="sections" role="list">
      @for (section of sections; track section.id) {
        <div class="section" role="listitem">
          <div class="title">
            <span>Stops: {{ section.stopCount }}</span>
            <small>{{ section.stopPattern }}</small>
          </div>
          <div class="details">
            <span>Variants: {{ section.variants.length }}</span>
            @if (combined?.[section.id]) {
              <span class="headway">
                @if (!combined?.[section.id]?.isIrregular && combined?.[section.id]?.averageHeadwayMinutes) {
                  {{ combined?.[section.id]?.averageHeadwayMinutes }} min avg
                } @else {
                  Variable schedule
                }
              </span>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .sections { display: flex; flex-direction: column; gap: 12px; }
    .section { padding: 12px; border: 1px solid var(--mat-sys-outline, #e2e8f0); border-radius: 12px; }
    .title { font-weight: 700; }
    .details { display: flex; gap: 12px; margin-top: 6px; color: var(--mat-sys-on-surface-variant, #475569); }
    .headway { font-weight: 600; }
    :host-context(.dark-theme) .section { border-color: rgba(148, 163, 184, 0.3); }
    :host-context(.dark-theme) .details { color: var(--mat-sys-on-surface-variant, #cbd5e1); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CommonSectionDisplayComponent {
  @Input() sections: CommonSectionDto[] = [];
  @Input() combined: Record<string, CombinedFrequencyDto> | null = null;
}
