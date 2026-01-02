import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  inject,
} from '@angular/core';

import { ActivatedRoute } from '@angular/router';
import {
  FrequencyService,
  RouteDto,
  RouteVariantDto,
} from '../../services/frequency.service';
import {
  CommonSectionService,
  CommonSectionDto,
  CombinedFrequencyDto,
} from '../../services/common-section.service';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';
import { RouteVariantCardComponent } from '../../components/route-variant-card/route-variant-card.component';
import { CommonSectionDisplayComponent } from '../../components/common-section-display/common-section-display.component';
import { finalize } from 'rxjs';
import { BrandTabsComponent } from '../../../shared/components/brand-tabs.component';

@Component({
  selector: 'app-route-frequency',
  standalone: true,
  imports: [
    BrandCardComponent,
    RouteVariantCardComponent,
    CommonSectionDisplayComponent,
    BrandTabsComponent,
  ],
  template: `
    <app-brand-card
      [title]="route?.longName"
      [subtitle]="route?.shortName || undefined"
      [loading]="isLoading"
    >
      @if (directionTabs.length > 1) {
        <app-brand-tabs
          class="mb-4 block"
          [tabs]="directionTabLabels"
          [selectedIndex]="selectedDirectionIndex"
          (selectedIndexChange)="selectDirectionByIndex($event)"
        >
        </app-brand-tabs>
      }
      <div class="grid gap-4 md:grid-cols-2" role="list">
        @if (isLoading) {
          <app-route-variant-card [loading]="true"></app-route-variant-card>
        } @else {
          @for (variant of filteredVariants; track variant.id) {
            <app-route-variant-card [variant]="variant">
            </app-route-variant-card>
          }
        }
      </div>

      <app-common-section-display
        [sections]="commonSections"
        [combined]="combinedBySection"
      >
      </app-common-section-display>
    </app-brand-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RouteFrequencyComponent implements OnInit {
  routeId!: string;
  route?: RouteDto;
  variants: RouteVariantDto[] = [];
  commonSections: CommonSectionDto[] = [];
  combinedBySection: Record<string, CombinedFrequencyDto> = {};
  selectedDirectionId: number | null = null;
  isLoading = true;
  private routeLoaded = false;
  private variantsLoaded = false;
  private sectionsLoaded = false;

  private readonly routeParams = inject(ActivatedRoute);
  private readonly frequencyService = inject(FrequencyService);
  private readonly commonSectionService = inject(CommonSectionService);
  private readonly cdr = inject(ChangeDetectorRef);

  ngOnInit(): void {
    this.routeParams.paramMap.subscribe((params) => {
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
        }),
      )
      .subscribe((route) => {
        this.route = route;
        this.variants = route.variants ?? [];
        if (this.variants.length > 0 && this.selectedDirectionId === null) {
          this.selectedDirectionId = this.directionTabs[0]?.id ?? null;
        }
        this.cdr.markForCheck();
      });
    this.commonSectionService
      .getCommonSectionsForRoute(this.routeId)
      .subscribe((sections) => {
        this.commonSections = sections;
        sections.forEach((section) => {
          this.commonSectionService
            .getCombinedFrequency(section.id, 'WEEKDAY_AM_PEAK')
            .subscribe((freq) => {
              if (freq) this.combinedBySection[section.id] = freq;
              this.cdr.markForCheck();
            });
        });
        this.sectionsLoaded = true;
        this.updateLoading();
        this.cdr.markForCheck();
      });
  }

  get directionTabs(): { id: number | null; label: string; key: string }[] {
    const ids = Array.from(
      new Set(this.variants.map((variant) => variant.directionId ?? null)),
    );
    const ordered: (number | null)[] = [];
    if (ids.includes(0)) ordered.push(0);
    if (ids.includes(1)) ordered.push(1);
    ids
      .filter((id) => id !== 0 && id !== 1 && id !== null)
      .forEach((id) => ordered.push(id));
    if (ids.includes(null)) ordered.push(null);
    return ordered.map((id) => ({
      id,
      label: id === null ? 'Unknown' : `Direction ${id}`,
      key: id === null ? 'unknown' : String(id),
    }));
  }

  get filteredVariants(): RouteVariantDto[] {
    if (this.selectedDirectionId === null) {
      return this.variants.filter(
        (variant) =>
          variant.directionId === null || variant.directionId === undefined,
      );
    }
    return this.variants.filter(
      (variant) => variant.directionId === this.selectedDirectionId,
    );
  }

  get directionTabLabels(): string[] {
    return this.directionTabs.map((tab) => tab.label);
  }

  selectDirection(directionId: number | null): void {
    if (this.selectedDirectionId === directionId) return;
    this.selectedDirectionId = directionId;
    this.cdr.markForCheck();
  }

  get selectedDirectionIndex(): number {
    const tabs = this.directionTabs;
    const matchIndex = tabs.findIndex(
      (tab) => tab.id === this.selectedDirectionId,
    );
    return matchIndex >= 0 ? matchIndex : 0;
  }

  selectDirectionByIndex(index: number): void {
    const tab = this.directionTabs[index];
    if (!tab) return;
    this.selectDirection(tab.id);
  }

  private updateLoading(): void {
    this.isLoading = !(
      this.routeLoaded &&
      this.variantsLoaded &&
      this.sectionsLoaded
    );
  }
}
