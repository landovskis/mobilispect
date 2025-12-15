import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { FrequencyService, RouteDto, RouteVariantDto, RouteHourlyFrequencyDto } from '../../services/frequency.service';
import { BrandSectionComponent } from '../../../shared/components/brand-section.component';
import { Observable, map } from 'rxjs';

@Component({
  selector: 'app-route-detail-page',
  standalone: true,
  imports: [CommonModule, BrandSectionComponent, FormsModule],
  template: `
    <app-brand-section
      [title]="(route$ | async)?.longName || 'Route Details'"
      [subtitle]="'Route ' + ((route$ | async)?.shortName || '')"
      icon="route">
      <ng-container *ngIf="route$ | async as route; else loadingRoute">
        <div class="route-info">
          <div class="info-item">
            <span class="info-label">Route Number:</span>
            <span class="info-value">{{ route.shortName || 'N/A' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">Route Type:</span>
            <span class="info-value">{{ getRouteTypeLabel(route.routeType) }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">Variants:</span>
            <span class="info-value">{{ (variants$ | async)?.length || 0 }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">Status:</span>
            <span class="info-value" [class.active]="route.active">
              {{ route.active ? 'Active' : 'Inactive' }}
            </span>
          </div>
        </div>
      </ng-container>
      <ng-template #loadingRoute>
        <p>Loading route details...</p>
      </ng-template>
    </app-brand-section>

    <app-brand-section
      title="Hourly Service Frequency"
      subtitle="Service headways throughout the day"
      icon="schedule">
      <div class="date-picker">
        <label for="serviceDate">Service Date:</label>
        <input
          id="serviceDate"
          type="date"
          [(ngModel)]="selectedDate"
          (change)="onDateChange()"
          class="date-input">
      </div>

      <ng-container *ngIf="hourlyFrequencies$ | async as frequencies; else loadingFrequencies">
        <div class="frequency-table">
          <div class="table-header">
            <div class="col-hour">Hour</div>
            <div class="col-trips">Trips</div>
            <div class="col-variants">Variants</div>
            <div class="col-headway">Avg Headway (min)</div>
            <div class="col-range">Min-Max (min)</div>
          </div>
          <div
            *ngFor="let freq of frequencies"
            class="table-row"
            [class.no-service]="freq.tripCount === 0">
            <div class="col-hour">{{ formatHour(freq.hourOfDay) }}</div>
            <div class="col-trips">{{ freq.tripCount }}</div>
            <div class="col-variants">{{ freq.variantCount }}</div>
            <div class="col-headway">
              <span *ngIf="freq.averageHeadwayMinutes !== null && freq.averageHeadwayMinutes !== undefined">
                {{ freq.averageHeadwayMinutes | number: '1.1-1' }}
              </span>
              <span *ngIf="freq.isIrregular && freq.tripCount > 0" class="irregular-badge">
                Irregular
              </span>
              <span *ngIf="freq.tripCount === 0" class="no-service-text">—</span>
            </div>
            <div class="col-range">
              <span *ngIf="freq.minHeadwayMinutes !== null && freq.maxHeadwayMinutes !== null">
                {{ freq.minHeadwayMinutes | number: '1.1-1' }} - {{ freq.maxHeadwayMinutes | number: '1.1-1' }}
              </span>
              <span *ngIf="freq.tripCount === 0" class="no-service-text">—</span>
            </div>
          </div>
        </div>
        <p *ngIf="frequencies.length === 0" class="no-data">
          No frequency data available for this date.
        </p>
      </ng-container>
      <ng-template #loadingFrequencies>
        <p>Loading frequency data...</p>
      </ng-template>
    </app-brand-section>
  `,
  styles: [`
    app-brand-section:not(:first-child) {
      display: block;
      margin-top: 24px;
    }

    .route-info {
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

    .info-value.active {
      color: var(--mat-sys-tertiary, #388e3c);
    }

    .date-picker {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 24px;
      padding: 16px 0;
    }

    .date-picker label {
      font-size: 14px;
      font-weight: 500;
      color: var(--mat-sys-on-surface, #333);
    }

    .date-input {
      padding: 8px 12px;
      border: 1px solid var(--mat-sys-outline-variant, #e0e0e0);
      border-radius: 6px;
      font-size: 14px;
      color: var(--mat-sys-on-surface, #333);
      background: var(--mat-sys-surface, #fff);
    }

    .date-input:focus {
      outline: 2px solid var(--mat-sys-primary, #1976d2);
      outline-offset: 0;
    }

    .frequency-table {
      display: flex;
      flex-direction: column;
      border: 1px solid var(--mat-sys-outline-variant, #e0e0e0);
      border-radius: 8px;
      overflow: hidden;
    }

    .table-header {
      display: grid;
      grid-template-columns: 100px 80px 80px 140px 160px;
      gap: 12px;
      padding: 12px 16px;
      background: var(--mat-sys-surface-variant, #f5f5f5);
      font-size: 12px;
      font-weight: 600;
      color: var(--mat-sys-on-surface-variant, #6b7280);
      text-transform: uppercase;
      letter-spacing: 0.5px;
      border-bottom: 1px solid var(--mat-sys-outline-variant, #e0e0e0);
    }

    .table-row {
      display: grid;
      grid-template-columns: 100px 80px 80px 140px 160px;
      gap: 12px;
      padding: 12px 16px;
      border-bottom: 1px solid var(--mat-sys-outline-variant, #e0e0e0);
      font-size: 14px;
      color: var(--mat-sys-on-surface, #333);
    }

    .table-row:last-child {
      border-bottom: none;
    }

    .table-row.no-service {
      background: var(--mat-sys-surface-container-low, #fafafa);
      color: var(--mat-sys-on-surface-variant, #999);
    }

    .table-row:hover:not(.no-service) {
      background: var(--mat-sys-surface-container, #f5f5f5);
    }

    .col-hour {
      font-weight: 500;
    }

    .col-trips,
    .col-variants,
    .col-headway,
    .col-range {
      text-align: right;
    }

    .irregular-badge {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 4px;
      background: var(--mat-sys-error-container, #ffebee);
      color: var(--mat-sys-on-error-container, #c62828);
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
    }

    .no-service-text {
      color: var(--mat-sys-on-surface-variant, #999);
    }

    .no-data {
      color: var(--mat-sys-on-surface-variant, #6b7280);
      font-style: italic;
      text-align: center;
      padding: 24px 0;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RouteDetailPageComponent implements OnInit {
  route$!: Observable<RouteDto>;
  variants$!: Observable<RouteVariantDto[]>;
  hourlyFrequencies$!: Observable<RouteHourlyFrequencyDto[]>;
  selectedDate: string = this.getDefaultDate();

  constructor(
    private readonly activatedRoute: ActivatedRoute,
    private readonly frequencyService: FrequencyService
  ) {}

  ngOnInit(): void {
    const routeId = this.activatedRoute.snapshot.paramMap.get('routeId');
    if (routeId) {
      this.route$ = this.frequencyService.getRoute(routeId);
      this.variants$ = this.frequencyService.getVariants(routeId);
      this.loadHourlyFrequencies(routeId);
    }
  }

  onDateChange(): void {
    const routeId = this.activatedRoute.snapshot.paramMap.get('routeId');
    if (routeId) {
      this.loadHourlyFrequencies(routeId);
    }
  }

  private loadHourlyFrequencies(routeId: string): void {
    this.hourlyFrequencies$ = this.frequencyService
      .getRouteHourlyFrequencies(routeId, this.selectedDate)
      .pipe(
        map(frequencies => frequencies.sort((a, b) => a.hourOfDay - b.hourOfDay))
      );
  }

  private getDefaultDate(): string {
    const today = new Date();
    return today.toISOString().split('T')[0];
  }

  formatHour(hour: number): string {
    const startHour = hour.toString().padStart(2, '0');
    const endHour = ((hour + 1) % 24).toString().padStart(2, '0');
    return `${startHour}:00-${endHour}:00`;
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
}
