import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { RouteDTO } from '../models/route.model';
import { BrandBadgeComponent } from '../../shared/components/brand-badge.component';

@Component({
  selector: 'app-agency-route-card',
  standalone: true,
  imports: [RouterModule, MatIconModule, BrandBadgeComponent],
  template: `
    <a
      class="route-item"
      [routerLink]="['/routes', route.id]"
      [attr.aria-label]="'View route ' + (route.shortName || route.longName)">
      <app-brand-badge
        [variant]="route.active ? 'neutral' : 'indeterminate'"
        [icon]="getRouteTypeIconName(route.routeType)"
        [label]="route.shortName || route.longName">
      </app-brand-badge>
      <div class="route-details">
        <div class="route-name">{{ route.longName }}</div>
      </div>
    </a>
  `,
  styles: [`
    .route-item {
      text-decoration: none;
      color: inherit;
      display: grid;
      grid-template-columns: auto 1fr auto;
      gap: 12px;
      padding: 16px 14px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container, #f5f5f5);
      border: 1px solid var(--mat-sys-outline-variant, #e0e0e0);
      transition: border-color 0.2s ease, background-color 0.2s ease;
    }

    .route-details {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .route-name {
      font-size: 16px;
      font-weight: 500;
      color: var(--mat-sys-on-surface, #333);
    }

    .route-type {
      font-size: 13px;
      color: var(--mat-sys-on-surface-variant, #6b7280);
    }

    .route-item:hover {
      border-color: var(--mat-sys-primary, #1976d2);
      background: var(--mat-sys-surface-container-high, #eef5ff);
    }

    .route-badge mat-icon {
      color: #ffffff !important;
      font-size: 18px;
      line-height: 1;
      font-variation-settings: 'FILL' 1, 'wght' 600, 'GRAD' 0, 'opsz' 24;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AgencyRouteCardComponent {
  @Input({ required: true }) route!: RouteDTO;

  getRouteTypeIconName(routeType: string): string {
    const icons: Record<string, string> = {
      'TRAM': 'tram',
      'SUBWAY': 'subway',
      'RAIL': 'train',
      'BUS': 'directions_bus',
      'FERRY': 'directions_boat',
      'CABLE_TRAM': 'cable_car',
      'AERIAL_LIFT': 'cable_car',
      'FUNICULAR': 'tram',
      'TROLLEYBUS': 'electric_bus',
      'MONORAIL': 'train'
    };

    return icons[routeType] || 'directions_transit';
  }
}
