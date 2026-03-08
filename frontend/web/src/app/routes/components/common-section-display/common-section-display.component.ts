import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

import { CombinedFrequencyDto, CommonSectionDto } from '../../services/common-section.service';

@Component({
  selector: 'app-common-section-display',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (sections.length === 0) {
      <p class="empty text-sm">No common sections available.</p>
    } @else {
      <div class="sections grid gap-3 md:grid-cols-2">
        @for (section of sections; track section.id) {
          <div class="section-card rounded-xl p-3">
            <div class="header flex flex-wrap items-center justify-between gap-2">
              <div class="title text-sm font-semibold">{{ section.stopCount }} stops</div>
              <div class="chip text-xs">{{ section.variants.length }} variants</div>
            </div>
            <div class="pattern text-xs">{{ section.stopPattern }}</div>
            @if (combined[section.id]) {
              <div class="metrics text-xs">
                <span>
                  Avg headway:
                  {{ formatHeadway(combined[section.id].averageHeadwayMinutes) }}
                </span>
                <span>Trips: {{ combined[section.id].tripCount }}</span>
                @if (combined[section.id].isIrregular) {
                  <span class="irregular">Irregular</span>
                }
              </div>
            } @else {
              <div class="metrics text-xs muted">No frequency data</div>
            }
          </div>
        }
      </div>
    }
  `,
  styles: [
    `
      .section-card {
        border: 1px solid var(--mat-sys-outline, #e2e8f0);
        background: var(--mat-sys-surface, #ffffff);
      }
      .title {
        color: var(--mat-sys-primary, #0b4f8a);
      }
      .chip {
        background: rgba(11, 79, 138, 0.12);
        color: var(--mat-sys-primary, #0b4f8a);
        border-radius: 999px;
        padding: 2px 8px;
      }
      .pattern {
        color: var(--mat-sys-on-surface-variant, #64748b);
        margin-top: 4px;
      }
      .metrics {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
        margin-top: 6px;
        color: var(--mat-sys-on-surface-variant, #475569);
      }
      .metrics.muted {
        color: var(--mat-sys-on-surface-variant, #94a3b8);
      }
      .irregular {
        color: #b45309;
        font-weight: 600;
      }
      :host-context(.dark-theme) .section-card {
        border-color: rgba(148, 163, 184, 0.3);
        background: rgba(15, 23, 42, 0.35);
      }
      :host-context(.dark-theme) .chip {
        background: rgba(59, 130, 246, 0.2);
        color: #e2e8f0;
      }
      :host-context(.dark-theme) .pattern,
      :host-context(.dark-theme) .metrics {
        color: rgba(226, 232, 240, 0.75);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommonSectionDisplayComponent {
  @Input() sections: CommonSectionDto[] = [];
  @Input() combined: Record<string, CombinedFrequencyDto> = {};

  formatHeadway(value?: number | null): string {
    if (value === null || value === undefined) return 'N/A';
    return `${value.toFixed(1)} min`;
  }
}
