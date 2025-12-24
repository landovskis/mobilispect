import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FrequencyService, FrequencyDto, RouteDto, RouteVariantDto } from '../../services/frequency.service';
import { CommonSectionService, CommonSectionDto, CombinedFrequencyDto } from '../../services/common-section.service';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';
import { VariantListComponent } from '../../components/variant-list/variant-list.component';
import { FrequencyChartComponent } from '../../components/frequency-chart/frequency-chart.component';
import { CommonSectionDisplayComponent } from '../../components/common-section-display/common-section-display.component';
import { RouteSummaryCardComponent } from '../../components/route-summary-card/route-summary-card.component';
import { Observable, tap } from 'rxjs';

@Component({
  selector: 'app-route-detail-page',
  standalone: true,
  imports: [
    CommonModule,
    BrandCardComponent,
    VariantListComponent,
    FrequencyChartComponent,
    CommonSectionDisplayComponent,
    RouteSummaryCardComponent
  ],
  template: `
    @if (route$ | async; as route) {
      <app-route-summary-card [route]="route"></app-route-summary-card>
    } @else {
      <p>Loading route details...</p>
    }

    <app-brand-card
      class="mt-6 block"
      [title]="route?.longName"
      [subtitle]="route?.shortName || undefined">
      <app-variant-list
        [variants]="variants"
        (variantSelect)="loadFrequencies($event)">
      </app-variant-list>

      <app-frequency-chart
        [frequencies]="frequencies">
      </app-frequency-chart>

      <app-common-section-display
        [sections]="commonSections"
        [combined]="combinedBySection">
      </app-common-section-display>
    </app-brand-card>
    `,
  styles: [],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RouteDetailPageComponent implements OnInit {
  route$!: Observable<RouteDto>;
  route?: RouteDto;
  variants: RouteVariantDto[] = [];
  frequencies: FrequencyDto[] = [];
  commonSections: CommonSectionDto[] = [];
  combinedBySection: Record<string, CombinedFrequencyDto> = {};
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
      this.frequencyService.getVariants(routeId).subscribe(variants => {
        this.variants = variants;
      });
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

  loadFrequencies(variantId: string): void {
    this.lastVariantId = variantId;
    this.frequencyService.getFrequencies(variantId).subscribe(freqs => {
      this.frequencies = freqs;
    });
  }

}
