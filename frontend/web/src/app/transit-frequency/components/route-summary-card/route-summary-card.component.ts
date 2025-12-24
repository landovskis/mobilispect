import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

import { RouteDto } from '../../services/frequency.service';
import {BrandCardComponent} from '../../../shared/components/brand-card.component';

@Component({
  selector: 'app-route-summary-card',
  standalone: true,
  template: `
    <app-brand-card
      title="A"
    >
    <div class="route-info flex flex-wrap gap-6 py-4" aria-label="route summary">
      <div class="info-item flex flex-col gap-1">
        <span class="info-label">Route Number:</span>
        <span class="info-value">{{ route.shortName || 'N/A' }}</span>
      </div>
      <div class="info-item flex flex-col gap-1">
        <span class="info-label">Route Type:</span>
        <span class="info-value">{{ getRouteTypeLabel(route.routeType) }}</span>
      </div>
      <div class="info-item flex flex-col gap-1">
        <span class="info-label">Variants:</span>
        <span class="info-value">{{ variantsCount }}</span>
      </div>
      <div class="info-item flex flex-col gap-1">
        <span class="info-label">Status:</span>
        <span class="info-value" [class.active]="route.active">
          {{ route.active ? 'Active' : 'Inactive' }}
        </span>
      </div>
    </div></app-brand-card>
  `,
  styles: [`
    .info-label {
      font-size: 12px;
      font-weight: 500;
      color: var(--mat-sys-on-surface-variant, #6b7280);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .info-value {
      font-size: 20px;
      font-weight: 600;
      color: var(--mat-sys-on-surface, #333);
    }

    .info-value.active {
      color: var(--mat-sys-tertiary, #388e3c);
    }
  `],
  imports: [
    BrandCardComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RouteSummaryCardComponent {
  @Input({ required: true }) route!: RouteDto;
  @Input() variantsCount = 0;

  getRouteTypeLabel(routeType: string): string {
    const labels: Record<string, string> = {
      'TRAM': 'Tram/Light Rail',
      'SUBWAY': 'Subway/Metro',
      'RAIL': 'Rail',
      'BUS': 'Bus',
      'FERRY': 'Ferry',
      'CABLE_TRAM': 'Cable Tram',
      'AERIAL_LIFT': 'Aerial Lift',
      'FUNICULAR': 'Funicular',
      'TROLLEYBUS': 'Trolleybus',
      'MONORAIL': 'Monorail'
    };
    return labels[routeType] || routeType;
  }
}
