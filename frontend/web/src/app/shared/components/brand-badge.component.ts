import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

type BrandBadgeVariant = 'good' | 'mixed' | 'bad' | 'neutral';

@Component({
  selector: 'app-brand-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="brand-badge" [ngClass]="variant">
      {{ label || variant }}
    </span>
  `,
  styles: [`
    .brand-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: 999px;
      font-weight: 700;
      font-size: 0.85rem;
      letter-spacing: 0.01em;
      text-transform: capitalize;
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
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BrandBadgeComponent {
  @Input() variant: BrandBadgeVariant = 'neutral';
  @Input() label?: string;
}
