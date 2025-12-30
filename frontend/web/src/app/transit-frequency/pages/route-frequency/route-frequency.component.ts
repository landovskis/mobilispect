import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';

import { ActivatedRoute } from '@angular/router';
import { FrequencyService, RouteDto, RouteVariantDto, FrequencyDto } from '../../services/frequency.service';
import { CommonSectionService, CommonSectionDto, CombinedFrequencyDto } from '../../services/common-section.service';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';
import { RouteVariantCardComponent } from '../../components/route-variant-card/route-variant-card.component';
import { CommonSectionDisplayComponent } from '../../components/common-section-display/common-section-display.component';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-route-frequency',
  standalone: true,
  imports: [BrandCardComponent, RouteVariantCardComponent, CommonSectionDisplayComponent],
  template: `
    <app-brand-card
      [title]="route?.longName"
      [subtitle]="route?.shortName || undefined"
      [loading]="isLoading">
      @if (directionTabs.length > 1) {
        <div class="mb-4 flex flex-wrap gap-2">
          @for (tab of directionTabs; track tab.key) {
            <button
              type="button"
              class="rounded-full border px-3 py-1 text-sm font-semibold"
              [class.border-[var(--mat-sys-primary,#0b4f8a)]]="tab.id === selectedDirectionId"
              [class.text-[var(--mat-sys-primary,#0b4f8a)]]="tab.id === selectedDirectionId"
              (click)="selectDirection(tab.id)">
              {{ tab.label }}
            </button>
          }
        </div>
      }
      <div class="grid gap-4 md:grid-cols-2" role="list">
        @if (isLoading) {
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
  lastVariantId?: string;
  selectedDirectionId: number | null = null;
  isLoading = true;
  private routeLoaded = false;
  private variantsLoaded = false;
  private sectionsLoaded = false;

  constructor(
    private readonly routeParams: ActivatedRoute,
    private readonly frequencyService: FrequencyService,
    private readonly commonSectionService: CommonSectionService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.routeParams.paramMap.subscribe(params => {
      this.routeId = params.get('routeId') ?? '';
      this.loadRoute();
    });
  }

  private loadRoute(): void {
    if (!this.routeId) return;
    this.routeLoaded = false;
    this.variantsLoaded = false;
    this.sectionsLoaded = false;
    this.updateLoading();
    this.frequencyService
      .getRoute(this.routeId)
      .pipe(
        finalize(() => {
          this.routeLoaded = true;
          this.variantsLoaded = true;
          this.updateLoading();
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
    this.commonSectionService.getCommonSectionsForRoute(this.routeId).subscribe(sections => {
      this.commonSections = sections;
      sections.forEach(section => {
        this.commonSectionService.getCombinedFrequency(section.id, 'WEEKDAY_AM_PEAK').subscribe(freq => {
          if (freq) this.combinedBySection[section.id] = freq;
          this.cdr.markForCheck();
        });
      });
      this.sectionsLoaded = true;
      this.updateLoading();
      this.cdr.markForCheck();
    });
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
      label: id === null ? 'Unknown' : `Direction ${id}`,
      key: id === null ? 'unknown' : String(id)
    }));
  }

  get filteredVariants(): RouteVariantDto[] {
    if (this.selectedDirectionId === null) {
      return this.variants.filter(variant => variant.directionId === null || variant.directionId === undefined);
    }
    return this.variants.filter(variant => variant.directionId === this.selectedDirectionId);
  }

  selectDirection(directionId: number | null): void {
    if (this.selectedDirectionId === directionId) return;
    this.selectedDirectionId = directionId;
    this.loadFirstVariantForDirection();
  }

  private loadFirstVariantForDirection(): void {
    if (this.filteredVariants.length === 0) return;
    if (this.lastVariantId && this.filteredVariants.some(variant => variant.id === this.lastVariantId)) {
      return;
    }
    this.loadFrequencies(this.filteredVariants[0].id);
  }

  private updateLoading(): void {
    this.isLoading = !(this.routeLoaded && this.variantsLoaded && this.sectionsLoaded);
  }
}
