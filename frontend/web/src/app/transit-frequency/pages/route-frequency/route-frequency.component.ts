import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FrequencyService, RouteDto, RouteVariantDto, FrequencyDto } from '../../services/frequency.service';
import { VariantListComponent } from '../../components/variant-list/variant-list.component';
import { FrequencyChartComponent } from '../../components/frequency-chart/frequency-chart.component';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';

@Component({
  selector: 'app-route-frequency',
  standalone: true,
  imports: [CommonModule, BrandCardComponent, VariantListComponent, FrequencyChartComponent],
  template: `
    <app-brand-card [title]="route?.longName" [subtitle]="route?.shortName">
      <app-variant-list
        [variants]="variants"
        (variantSelect)="loadFrequencies($event)">
      </app-variant-list>

      <app-frequency-chart
        [frequencies]="frequencies">
      </app-frequency-chart>
    </app-brand-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RouteFrequencyComponent implements OnInit {
  routeId!: string;
  route?: RouteDto;
  variants: RouteVariantDto[] = [];
  frequencies: FrequencyDto[] = [];

  constructor(
    private readonly routeParams: ActivatedRoute,
    private readonly frequencyService: FrequencyService
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
  }

  loadFrequencies(variantId: string): void {
    this.frequencyService.getFrequencies(variantId).subscribe(freqs => {
      this.frequencies = freqs;
    });
  }
}
