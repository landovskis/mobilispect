import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AgencySummary } from '../../models/agency-summary.model';
import { BrandBadgeComponent } from '../../../shared/components/brand-badge.component';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';

@Component({
  selector: 'app-agency-summary-card',
  standalone: true,
  imports: [CommonModule, BrandCardComponent, BrandBadgeComponent],
  template: `
    <app-brand-card [title]="agency?.name" [badge]="agency?.routeCount + ' routes'">
      <div class="meta" aria-label="agency summary" role="list">
        @if (agency?.averageHeadwayMinutes !== null && agency?.averageHeadwayMinutes !== undefined) {
          <app-brand-badge
            role="listitem"
            variant="neutral"
            [label]="agency?.averageHeadwayMinutes + ' min avg headway'"></app-brand-badge>
        } @else {
          <app-brand-badge role="listitem" variant="neutral" label="Headway TBD"></app-brand-badge>
        }
      </div>
    </app-brand-card>
  `,
  styles: [`
    .meta {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    :host-context(.dark-theme) app-brand-card {
      color: var(--mat-sys-on-surface, #e5e7eb);
    }
  `],
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AgencySummaryCardComponent {
  @Input() agency!: AgencySummary;
}
