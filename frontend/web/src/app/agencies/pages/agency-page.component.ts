import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { AgencyService } from '../services/agency.service';
import { AgencySummary } from '../../transit-frequency/models/agency.model';
import { RouteDTO } from '../models/route.model';
import { BrandSectionComponent } from '../../shared/components/brand-section.component';
import { AgencyRouteCardComponent } from '../components/agency-route-card.component';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Component({
  selector: 'app-agency-page',
  standalone: true,
  imports: [CommonModule, RouterModule, BrandSectionComponent, AgencyRouteCardComponent],
  template: `
    <div class="flex flex-col gap-6">
      <app-brand-section
        [title]="(agency$ | async)?.name || 'Agency Details'"
        subtitle="Routes and stops served by this agency"
        icon="directions_bus">
        @if (agency$ | async; as agency) {
          <div class="agency-info flex flex-wrap gap-6 py-4">
            <div class="info-item flex flex-col gap-1">
              <span class="info-label">Total Routes:</span>
              <span class="info-value">{{ agency.routeCount }}</span>
            </div>
            @if (agency.averageHeadwayMinutes) {
              <div class="info-item flex flex-col gap-1">
                <span class="info-label">Average Headway:</span>
                <span class="info-value">{{ agency.averageHeadwayMinutes }} min</span>
              </div>
            }
          </div>
        } @else {
          <p>Loading agency details...</p>
        }
      </app-brand-section>

      <app-brand-section
        title="Routes"
        subtitle="Transit routes operated by this agency"
        icon="route">
        @if (routes$ | async; as routesResponse) {
          <div class="routes-list grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            @for (route of routesResponse.content; track route) {
              <app-agency-route-card
                [route]="route">
              </app-agency-route-card>
            }
          </div>
          @if (routesResponse.content.length === 0) {
            <p class="no-routes py-6 text-center italic">
              No routes found for this agency.
            </p>
          }
        } @else {
          <p>Loading routes...</p>
        }
      </app-brand-section>
    </div>
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

    .routes-list .route-item:nth-child(odd) {
      background: var(--mat-sys-surface-container-high, #f9fafb);
    }

    .no-routes {
      color: var(--mat-sys-on-surface-variant, #6b7280);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AgencyPageComponent implements OnInit {
  agency$!: Observable<AgencySummary>;
  routes$!: Observable<any>;

  private readonly route = inject(ActivatedRoute);
  private readonly agencyService = inject(AgencyService);

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
}
