import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-stats-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="stats-badge rounded-2xl px-3 py-2">
      <div class="badge-title text-[0.72rem] font-semibold uppercase tracking-[0.08em]">
        {{ title }}
      </div>
      <div class="badge-value mt-1 flex items-baseline gap-1">
        <span class="badge-number text-lg font-bold">{{ number }}</span>
        @if (unit) {
          <span class="badge-unit text-[0.75rem] font-semibold uppercase tracking-[0.06em]">
            {{ unit }}
          </span>
        }
      </div>
    </div>
  `,
  styles: [`
    .stats-badge {
      background: var(--mat-sys-surface-variant, #eef2f7);
      color: var(--mat-sys-on-surface, #0f172a);
    }
    .badge-title {
      color: var(--mat-sys-on-surface-variant, #64748b);
    }
    .badge-unit {
      color: var(--mat-sys-on-surface-variant, #64748b);
    }
    :host-context(.dark-theme) .stats-badge {
      background: rgba(148, 163, 184, 0.15);
      color: var(--mat-sys-on-surface, #e2e8f0);
    }
    :host-context(.dark-theme) .badge-title,
    :host-context(.dark-theme) .badge-unit {
      color: var(--mat-sys-on-surface-variant, #cbd5e1);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StatsBadgeComponent {
  @Input({ required: true }) title!: string;
  @Input({ required: true }) number!: string | number;
  @Input() unit?: string;
}
