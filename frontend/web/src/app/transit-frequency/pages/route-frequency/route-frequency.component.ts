import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FrequencyService, RouteDto, RouteVariantDto, FrequencyDto } from '../../services/frequency.service';
import { CommonSectionService, CommonSectionDto, CombinedFrequencyDto } from '../../services/common-section.service';
import { RouteFrequencyCardComponent } from '../../components/route-frequency-card/route-frequency-card.component';

@Component({
  selector: 'app-route-frequency',
  standalone: true,
  imports: [CommonModule, RouteFrequencyCardComponent],
  template: `
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
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RouteFrequencyComponent implements OnInit {
  routeId!: string;
  route?: RouteDto;
  variants: RouteVariantDto[] = [];
  frequencies: FrequencyDto[] = [];
  commonSections: CommonSectionDto[] = [];
  combinedBySection: Record<string, CombinedFrequencyDto> = {};
  selectedDate?: string;

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
    this.frequencyService.getFrequencies(variantId, this.selectedDate).subscribe(freqs => {
      this.frequencies = freqs;
    });
  }

  onDateChange(date?: string): void {
    this.selectedDate = date;
    // reload last viewed variant if any
    if (this.variants.length > 0) {
      this.loadFrequencies(this.variants[0].id);
    }
  }
}
