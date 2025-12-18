import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { RouteDTO } from '../models/route.model';

@Component({
  selector: 'app-agency-route-card',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule],
  template: `
    <a
      class="route-item"
      [routerLink]="['/routes', route.id]"
      [attr.aria-label]="'View route ' + (route.shortName || route.longName)">
      <div class="route-badge" [class.inactive]="!route.active">
        <mat-icon aria-hidden="true">{{ getRouteTypeIconName(route.routeType) }}</mat-icon>
        <span class="route-short">{{ route.shortName || route.longName }}</span>
      </div>
      <div class="route-details">
        <div class="route-name">{{ route.longName }}</div>
        <div class="route-type">{{ getRouteTypeLabel(route.routeType) }}</div>
      </div>
      <div class="route-status" [class.active]="route.active">
        {{ route.active ? 'Active' : 'Inactive' }}
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

    .route-badge {
      min-width: 72px;
      padding: 8px 12px;
      border-radius: 6px;
      background: var(--mat-sys-primary, #1976d2);
      color: #ffffff;
      font-weight: 600;
      font-size: 14px;
      text-align: center;
      display: inline-flex;
      align-items: center;
      gap: 8px;
      justify-content: center;
    }

    .route-badge.inactive {
      background: var(--mat-sys-surface-variant, #ddd);
      color: #ffffff;
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

    .route-status {
      font-size: 12px;
      font-weight: 500;
      padding: 4px 12px;
      border-radius: 12px;
      background: var(--mat-sys-surface-variant, #e0e0e0);
      color: var(--mat-sys-on-surface-variant, #666);
    }

    .route-status.active {
      background: var(--mat-sys-tertiary-container, #c8e6c9);
      color: var(--mat-sys-on-tertiary-container, #1b5e20);
    }

    .route-item:hover {
      border-color: var(--mat-sys-primary, #1976d2);
      background: var(--mat-sys-surface-container-high, #eef5ff);
    }

    .route-short {
      display: inline-block;
      min-width: 32px;
      text-align: center;
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
