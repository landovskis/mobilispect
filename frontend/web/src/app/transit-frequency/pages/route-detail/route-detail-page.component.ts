import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FrequencyService, FrequencyDto, RouteDto, RouteVariantDto } from '../../services/frequency.service';
import { CommonSectionService, CommonSectionDto, CombinedFrequencyDto } from '../../services/common-section.service';
import { BrandSectionComponent } from '../../../shared/components/brand-section.component';
import { RouteFrequencyCardComponent } from '../../components/route-frequency-card/route-frequency-card.component';
import { Observable, tap } from 'rxjs';

@Component({
  selector: 'app-route-detail-page',
  standalone: true,
  imports: [CommonModule, BrandSectionComponent, RouteFrequencyCardComponent],
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

    <app-route-frequency-card
      [route]="route"
      [variants]="variants"
      [frequencies]="frequencies"
      [commonSections]="commonSections"
      [combinedBySection]="combinedBySection"
      [selectedDate]="selectedDate"
      (variantSelect)="loadFrequencies($event)"
      (dateChange)="onDateChange($event)">
    </app-route-frequency-card>
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

    app-route-frequency-card {
      display: block;
      margin-top: 24px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RouteDetailPageComponent implements OnInit {
  route$!: Observable<RouteDto>;
  variants$!: Observable<RouteVariantDto[]>;
  route?: RouteDto;
  variants: RouteVariantDto[] = [];
  frequencies: FrequencyDto[] = [];
  commonSections: CommonSectionDto[] = [];
  combinedBySection: Record<string, CombinedFrequencyDto> = {};
  selectedDate: string = this.getDefaultDate();
  private lastVariantId?: string;

  constructor(
    private readonly activatedRoute: ActivatedRoute,
    private readonly frequencyService: FrequencyService,
    private readonly commonSectionService: CommonSectionService
  ) {}

  ngOnInit(): void {
    const routeId = this.activatedRoute.snapshot.paramMap.get('routeId');
    if (routeId) {
      this.route$ = this.frequencyService.getRoute(routeId).pipe(
        tap(route => {
          this.route = route;
        })
      );
      this.variants$ = this.frequencyService.getVariants(routeId).pipe(
        tap(variants => {
          this.variants = variants;
        })
      );
      this.commonSectionService.getCommonSectionsForRoute(routeId).subscribe(sections => {
        this.commonSections = sections;
        sections.forEach(section => {
          this.commonSectionService.getCombinedFrequency(section.id, 'WEEKDAY_AM_PEAK').subscribe(freq => {
            if (freq) this.combinedBySection[section.id] = freq;
          });
        });
      });
    }
  }

  onDateChange(date?: string): void {
    this.selectedDate = date ?? this.selectedDate;
    if (this.lastVariantId) {
      this.loadFrequencies(this.lastVariantId);
    } else if (this.variants.length > 0) {
      this.loadFrequencies(this.variants[0].id);
    }
  }

  loadFrequencies(variantId: string): void {
    this.lastVariantId = variantId;
    this.frequencyService.getFrequencies(variantId, this.selectedDate).subscribe(freqs => {
      this.frequencies = freqs;
    });
  }

  private getDefaultDate(): string {
    const today = new Date();
    return today.toISOString().split('T')[0];
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
