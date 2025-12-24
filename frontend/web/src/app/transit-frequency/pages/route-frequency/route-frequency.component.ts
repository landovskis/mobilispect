import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';

import { ActivatedRoute } from '@angular/router';
import { FrequencyService, RouteDto, RouteVariantDto, FrequencyDto } from '../../services/frequency.service';
import { CommonSectionService, CommonSectionDto, CombinedFrequencyDto } from '../../services/common-section.service';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';
import { VariantListComponent } from '../../components/variant-list/variant-list.component';
import { FrequencyChartComponent } from '../../components/frequency-chart/frequency-chart.component';
import { CommonSectionDisplayComponent } from '../../components/common-section-display/common-section-display.component';

@Component({
  selector: 'app-route-frequency',
  standalone: true,
  imports: [BrandCardComponent, VariantListComponent, FrequencyChartComponent, CommonSectionDisplayComponent],
  template: `
    <app-brand-card
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
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RouteFrequencyComponent implements OnInit {
  routeId!: string;
  route?: RouteDto;
  variants: RouteVariantDto[] = [];
  frequencies: FrequencyDto[] = [];
  commonSections: CommonSectionDto[] = [];
  combinedBySection: Record<string, CombinedFrequencyDto> = {};

  constructor(
    private readonly routeParams: ActivatedRoute,
    private readonly frequencyService: FrequencyService,
    private readonly commonSectionService: CommonSectionService
  ) {}

  ngOnInit(): void {
    this.routeParams.paramMap.subscribe(params => {
      this.routeId = params.get('routeId') ?? '';
      this.loadRoute();
    });
  }

  private loadRoute(): void {
    if (!this.routeId) return;
    this.frequencyService.getRoute(this.routeId).subscribe(route => {
      this.route = route;
    });
    this.frequencyService.getVariants(this.routeId).subscribe(variants => {
      this.variants = variants;
    });
    this.commonSectionService.getCommonSectionsForRoute(this.routeId).subscribe(sections => {
      this.commonSections = sections;
      sections.forEach(section => {
        this.commonSectionService.getCombinedFrequency(section.id, 'WEEKDAY_AM_PEAK').subscribe(freq => {
          if (freq) this.combinedBySection[section.id] = freq;
        });
      });
    });
  }

  loadFrequencies(variantId: string): void {
    this.frequencyService.getFrequencies(variantId).subscribe(freqs => {
      this.frequencies = freqs;
    });
  }
}
