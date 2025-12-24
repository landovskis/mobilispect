import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FrequencyService, FrequencyDto, RouteDto, RouteVariantDto } from '../../services/frequency.service';
import { CommonSectionService, CommonSectionDto, CombinedFrequencyDto } from '../../services/common-section.service';
import { RouteFrequencyCardComponent } from '../../components/route-frequency-card/route-frequency-card.component';
import { RouteSummaryCardComponent } from '../../components/route-summary-card/route-summary-card.component';
import { Observable, tap } from 'rxjs';

@Component({
  selector: 'app-route-detail-page',
  standalone: true,
  imports: [CommonModule, RouteFrequencyCardComponent, RouteSummaryCardComponent],
  template: `
    @if (route$ | async; as route) {
      <app-route-summary-card [route]="route"></app-route-summary-card>
    } @else {
      <p>Loading route details...</p>
    }

    <app-route-frequency-card
      class="mt-6 block"
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
  styles: [],
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

}
