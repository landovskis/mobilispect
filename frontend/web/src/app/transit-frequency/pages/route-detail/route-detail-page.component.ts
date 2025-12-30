import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FrequencyService, FrequencyDto, RouteDto, RouteVariantDto } from '../../services/frequency.service';
import { CommonSectionService, CommonSectionDto, CombinedFrequencyDto } from '../../services/common-section.service';
import { RouteVariantCardComponent } from '../../components/route-variant-card/route-variant-card.component';
import { CommonSectionDisplayComponent } from '../../components/common-section-display/common-section-display.component';
import { BrandTabsComponent } from '../../../shared/components/brand-tabs.component';
import { finalize } from 'rxjs';
import { BrandSectionComponent } from '../../../shared/components/brand-section.component';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';

@Component({
  selector: 'app-route-detail-page',
  standalone: true,
  imports: [
    CommonModule,
    RouteVariantCardComponent,
    CommonSectionDisplayComponent,
    BrandTabsComponent,
    BrandSectionComponent,
    BrandCardComponent
  ],
  template: `
    <app-brand-section title="Summary">
      <app-brand-card
        [loading]="routeLoading"
        [title]="route?.shortName && route?.longName ? (route?.shortName + ': ' + route?.longName) : (route?.longName || route?.shortName || 'Route Details')">
      </app-brand-card>
    </app-brand-section>

    <app-brand-section
      class="mt-6 block"
      title="Variants">
      @if (directionTabs.length > 1) {
        <app-brand-tabs
          class="mb-4 block"
          [tabs]="directionTabLabels"
          [selectedIndex]="selectedDirectionIndex"
          (selectedIndexChange)="selectDirectionByIndex($event)">
        </app-brand-tabs>
      }
      <div class="grid gap-4 md:grid-cols-2" role="list">
        @if (isVariantsLoading) {
          <app-route-variant-card [loading]="true"></app-route-variant-card>
        } @else {
          @for (variant of filteredVariants; track variant.id) {
            <app-route-variant-card
              [variant]="variant"
              [frequencies]="variant.id === lastVariantId ? frequencies : []"
              (select)="loadFrequencies($event)">
            </app-route-variant-card>
          }
        }
      </div>

      <app-common-section-display
        [sections]="commonSections"
        [combined]="combinedBySection">
      </app-common-section-display>
    </app-brand-section>
    `,
  styles: [],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RouteDetailPageComponent implements OnInit {
  route?: RouteDto;
  routeLoading = true;
  variantsLoading = true;
  variants: RouteVariantDto[] = [];
  frequencies: FrequencyDto[] = [];
  commonSections: CommonSectionDto[] = [];
  combinedBySection: Record<string, CombinedFrequencyDto> = {};
  lastVariantId?: string;
  selectedDirectionId: number | null = null;

  constructor(
    private readonly activatedRoute: ActivatedRoute,
    private readonly frequencyService: FrequencyService,
    private readonly commonSectionService: CommonSectionService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const routeId = this.activatedRoute.snapshot.paramMap.get('routeId');
    if (routeId) {
      this.routeLoading = true;
      this.frequencyService
        .getRoute(routeId)
        .pipe(
          finalize(() => {
            this.routeLoading = false;
            this.variantsLoading = false;
            this.cdr.markForCheck();
          })
        )
        .subscribe(route => {
          this.route = route;
          this.variants = route.variants ?? [];
          if (this.variants.length > 0 && this.selectedDirectionId === null) {
            this.selectedDirectionId = this.directionTabs[0]?.id ?? null;
          }
          this.loadFirstVariantForDirection();
          this.cdr.markForCheck();
        });
      this.commonSectionService.getCommonSectionsForRoute(routeId).subscribe(sections => {
        this.commonSections = sections;
        sections.forEach(section => {
          this.commonSectionService.getCombinedFrequency(section.id, 'WEEKDAY_AM_PEAK').subscribe(freq => {
            if (freq) this.combinedBySection[section.id] = freq;
            this.cdr.markForCheck();
          });
        });
        this.cdr.markForCheck();
      });
    }
  }

  loadFrequencies(variantId: string): void {
    this.lastVariantId = variantId;
    this.frequencyService.getFrequencies(variantId).subscribe(freqs => {
      this.frequencies = freqs;
    });
  }

  get directionTabs(): { id: number | null; label: string; key: string }[] {
    const ids = Array.from(new Set(this.variants.map(variant => variant.directionId ?? null)));
    const ordered: Array<number | null> = [];
    if (ids.includes(0)) ordered.push(0);
    if (ids.includes(1)) ordered.push(1);
    ids.filter(id => id !== 0 && id !== 1 && id !== null).forEach(id => ordered.push(id));
    if (ids.includes(null)) ordered.push(null);
    return ordered.map(id => ({
      id,
      label: this.getMostCommonHeadsign(id) ?? (id === null ? 'Unknown' : `Direction ${id}`),
      key: id === null ? 'unknown' : String(id)
    }));
  }

  get filteredVariants(): RouteVariantDto[] {
    if (this.selectedDirectionId === null) {
      return this.variants.filter(variant => variant.directionId === null || variant.directionId === undefined);
    }
    return this.variants.filter(variant => variant.directionId === this.selectedDirectionId);
  }

  get directionTabLabels(): string[] {
    return this.directionTabs.map(tab => tab.label);
  }

  selectDirection(directionId: number | null): void {
    if (this.selectedDirectionId === directionId) return;
    this.selectedDirectionId = directionId;
    this.loadFirstVariantForDirection();
    this.cdr.markForCheck();
  }

  get selectedDirectionIndex(): number {
    const tabs = this.directionTabs;
    const matchIndex = tabs.findIndex(tab => tab.id === this.selectedDirectionId);
    return matchIndex >= 0 ? matchIndex : 0;
  }

  selectDirectionByIndex(index: number): void {
    const tab = this.directionTabs[index];
    if (!tab) return;
    this.selectDirection(tab.id);
  }

  private loadFirstVariantForDirection(): void {
    if (this.filteredVariants.length === 0) return;
    if (this.lastVariantId && this.filteredVariants.some(variant => variant.id === this.lastVariantId)) {
      return;
    }
    this.loadFrequencies(this.filteredVariants[0].id);
  }

  get isVariantsLoading(): boolean {
    return this.routeLoading || this.variantsLoading;
  }

  private getMostCommonHeadsign(directionId: number | null): string | null {
    const counts = new Map<string, number>();
    this.variants.forEach(variant => {
      if ((variant.directionId ?? null) !== directionId) return;
      const headsign = variant.headsign?.trim();
      if (!headsign) return;
      counts.set(headsign, (counts.get(headsign) ?? 0) + 1);
    });

    let mostCommon: string | null = null;
    let maxCount = 0;
    counts.forEach((count, headsign) => {
      if (count > maxCount) {
        maxCount = count;
        mostCommon = headsign;
      }
    });

    return mostCommon;
  }

}
