import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { AgencyService } from '../services/agency.service';
import { AgencySummary } from '../../transit-frequency/models/agency.model';
import { RouteDTO } from '../models/route.model';
import { BrandSectionComponent } from '../../shared/components/brand-section.component';
import { MatIconModule } from '@angular/material/icon';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { getRouteTypeIcon } from '../../transit-frequency/models/route-type.model';

@Component({
  selector: 'app-agency-page',
  standalone: true,
  imports: [CommonModule, RouterModule, BrandSectionComponent, MatIconModule],
  template: `
    <app-brand-section
      [title]="(agency$ | async)?.name || 'Agency Details'"
      subtitle="Routes and stops served by this agency"
      icon="directions_bus">
      <ng-container *ngIf="agency$ | async as agency; else loadingAgency">
        <div class="agency-info">
          <div class="info-item">
            <span class="info-label">Total Routes:</span>
            <span class="info-value">{{ agency.routeCount }}</span>
          </div>
          <div class="info-item" *ngIf="agency.averageHeadwayMinutes">
            <span class="info-label">Average Headway:</span>
            <span class="info-value">{{ agency.averageHeadwayMinutes }} min</span>
          </div>
        </div>
      </ng-container>
      <ng-template #loadingAgency>
        <p>Loading agency details...</p>
      </ng-template>
    </app-brand-section>

    <app-brand-section
      title="Routes"
      subtitle="Transit routes operated by this agency"
      icon="route">
      <ng-container *ngIf="routes$ | async as routesResponse; else loadingRoutes">
        <div class="routes-list">
          <a
            *ngFor="let route of routesResponse.content"
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
        </div>
        <p *ngIf="routesResponse.content.length === 0" class="no-routes">
          No routes found for this agency.
        </p>
      </ng-container>
      <ng-template #loadingRoutes>
        <p>Loading routes...</p>
      </ng-template>
    </app-brand-section>
  `,
  styles: [`
    app-brand-section:not(:first-child) {
      display: block;
      margin-top: 24px;
    }

    .agency-info {
      display: flex;
      gap: 24px;
      flex-wrap: wrap;
      padding: 16px 0;
    }

    .info-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

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

    .routes-list {
      display: grid;
      gap: 12px;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    }

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

    .routes-list .route-item:nth-child(odd) {
      background: var(--mat-sys-surface-container-high, #f9fafb);
    }

    .no-routes {
      color: var(--mat-sys-on-surface-variant, #6b7280);
      font-style: italic;
      text-align: center;
      padding: 24px 0;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AgencyPageComponent implements OnInit {
  agency$!: Observable<AgencySummary>;
  routes$!: Observable<any>;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly agencyService: AgencyService
  ) {}

  ngOnInit(): void {
    const agencyId = this.route.snapshot.paramMap.get('agencyId');
    if (agencyId) {
      this.agency$ = this.agencyService.getAgency(agencyId);
      this.routes$ = this.agencyService.listRoutesByAgency(agencyId, 0, 500).pipe(
        map(response => ({
          ...response,
          content: this.sortRoutes(response.content)
        }))
      );
    }
  }

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

  private sortRoutes(routes: RouteDTO[]): RouteDTO[] {
    return [...routes].sort((a, b) => {
      const keyA = this.getRouteSortKey(a);
      const keyB = this.getRouteSortKey(b);

      if (keyA.number !== undefined && keyB.number !== undefined && keyA.number !== keyB.number) {
        return keyA.number - keyB.number;
      }

      if (keyA.number !== undefined && keyB.number === undefined) return -1;
      if (keyA.number === undefined && keyB.number !== undefined) return 1;

      return keyA.text.localeCompare(keyB.text, undefined, { numeric: true, sensitivity: 'base' });
    });
  }

  private getRouteSortKey(route: RouteDTO): { number?: number; text: string } {
    const shortName = route.shortName?.trim() || '';
    const longName = route.longName?.trim() || '';
    const numericValue = shortName !== '' ? Number(shortName) : NaN;

    return {
      number: Number.isNaN(numericValue) ? undefined : numericValue,
      text: shortName || longName || route.id
    };
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

    return icons[routeType] || getRouteTypeIcon(routeType as any);
  }
}
