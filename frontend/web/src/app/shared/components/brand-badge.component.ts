import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';

type BrandBadgeVariant = 'good' | 'mixed' | 'bad' | 'neutral' | 'indeterminate';

@Component({
  selector: 'app-brand-badge',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    <span
      class="brand-badge inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[0.85rem] font-bold tracking-[0.01em] capitalize"
      [ngClass]="variant"
    >
      @if (icon) {
        <mat-icon class="text-[18px] leading-none" aria-hidden="true">{{ icon }}</mat-icon>
      }
      <span class="badge-label inline-flex items-center">{{ label || variant }}</span>
    </span>
  `,
  styles: [
    `
      .brand-badge {
        background: var(--mat-sys-surface-variant, #e2e8f0);
        color: var(--mat-sys-on-surface, #0f172a);
      }

      .brand-badge.good {
        background: rgba(34, 197, 94, 0.16);
        color: #15803d;
      }

      .brand-badge.mixed {
        background: rgba(249, 115, 22, 0.16);
        color: #c2410c;
      }

      .brand-badge.bad {
        background: rgba(239, 68, 68, 0.16);
        color: #b91c1c;
      }

      .brand-badge.neutral {
        background: rgba(59, 130, 246, 0.16);
        color: #1d4ed8;
      }

      .brand-badge.indeterminate {
        background: rgba(59, 130, 246, 0.16);
        color: #1d4ed8;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BrandBadgeComponent {
  @Input() variant: BrandBadgeVariant = 'neutral';
  @Input() label?: string;
  @Input() icon?: string;
}
