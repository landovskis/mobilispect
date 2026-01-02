import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnInit,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import {
  FrequencyService,
  RouteDto,
  RouteHourlyStatsDto,
  RouteVariantDto,
} from '../../services/frequency.service';
import {
  CommonSectionService,
  CommonSectionDto,
  CombinedFrequencyDto,
} from '../../services/common-section.service';
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
    BrandCardComponent,
  ],
  template: `
    <app-brand-section title="Summary">
      <app-brand-card
        [loading]="routeLoading"
        [title]="
          route?.shortName && route?.longName
            ? route?.shortName + ': ' + route?.longName
            : route?.longName || route?.shortName || 'Route Details'
        "
        [badge]="routeClassificationLabel"
      >
      </app-brand-card>
    </app-brand-section>

    <app-brand-section class="mt-6 block" title="Hourly Trips">
      <app-brand-card [loading]="routeLoading" title="Trips per hour">
        @if (!routeLoading) {
          @if (dayTypeTabs.length > 1) {
            <app-brand-tabs
              class="mb-4 block"
              [tabs]="dayTypeTabLabels"
              [selectedIndex]="selectedDayTypeIndex"
              (selectedIndexChange)="selectDayTypeByIndex($event)"
            >
            </app-brand-tabs>
          }
          <div class="hourly-chart mt-4">
            <div class="hourly-bars grid grid-cols-12 gap-2 md:grid-cols-24">
              @for (bar of hourlyTripBars; track bar.hour) {
                <div class="hour-bar flex flex-col items-center gap-2">
                  <div class="bar-track">
                    <div
                      class="bar-fill"
                      [style.height.%]="bar.heightPercent"
                    ></div>
                  </div>
                  <span class="bar-label text-[0.7rem] font-semibold">{{
                    bar.hour
                  }}</span>
                  <span class="bar-value text-[0.7rem]">{{
                    bar.tripCount
                  }}</span>
                </div>
              }
            </div>
          </div>
        }
      </app-brand-card>
    </app-brand-section>

    <app-brand-section class="mt-6 block" title="Average Speed">
      <app-brand-card [loading]="routeLoading" title="Speed per hour">
        @if (!routeLoading) {
          @if (dayTypeTabs.length > 1) {
            <app-brand-tabs
              class="mb-4 block"
              [tabs]="dayTypeTabLabels"
              [selectedIndex]="selectedDayTypeIndex"
              (selectedIndexChange)="selectDayTypeByIndex($event)"
            >
            </app-brand-tabs>
          }
          <div class="hourly-chart mt-4">
            <div class="hourly-bars grid grid-cols-12 gap-2 md:grid-cols-24">
              @for (bar of hourlySpeedBars; track bar.hour) {
                <div class="hour-bar flex flex-col items-center gap-2">
                  <div class="bar-track">
                    <div
                      class="bar-fill"
                      [style.height.%]="bar.heightPercent"
                    ></div>
                  </div>
                  <span class="bar-label text-[0.7rem] font-semibold">{{
                    bar.hour
                  }}</span>
                  <span class="bar-value text-[0.7rem]">{{
                    bar.speedLabel
                  }}</span>
                </div>
              }
            </div>
          </div>
        }
      </app-brand-card>
    </app-brand-section>

    <app-brand-section class="mt-6 block" title="Variants">
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
        @if (isVariantsLoading) {
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
    </app-brand-section>
  `,
  styles: [
    `
      .hourly-bars {
        align-items: end;
      }
      .bar-track {
        align-items: flex-end;
        background: rgba(148, 163, 184, 0.2);
        border-radius: 999px;
        display: flex;
        height: 84px;
        width: 12px;
      }
      .bar-fill {
        background: var(--mat-sys-primary, #0b4f8a);
        border-radius: 999px;
        width: 100%;
      }
      .bar-label {
        color: var(--mat-sys-on-surface-variant, #64748b);
      }
      .bar-value {
        color: var(--mat-sys-on-surface, #0f172a);
      }
      :host-context(.dark-theme) .bar-track {
        background: rgba(148, 163, 184, 0.25);
      }
      :host-context(.dark-theme) .bar-value {
        color: var(--mat-sys-on-surface, #e2e8f0);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RouteDetailPageComponent implements OnInit {
  route?: RouteDto;
  routeLoading = true;
  variantsLoading = true;
  variants: RouteVariantDto[] = [];
  hourlyStats: RouteHourlyStatsDto[] = [];
  commonSections: CommonSectionDto[] = [];
  combinedBySection: Record<string, CombinedFrequencyDto> = {};
  selectedDirectionId: number | null = null;
  selectedDayType: RouteHourlyStatsDto['dayType'] | null = null;

  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly frequencyService = inject(FrequencyService);
  private readonly commonSectionService = inject(CommonSectionService);
  private readonly cdr = inject(ChangeDetectorRef);

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
          }),
        )
        .subscribe((route) => {
          this.route = route;
          this.variants = route.variants ?? [];
          this.hourlyStats = route.hourlyStats ?? [];
          if (!this.selectedDayType && this.dayTypeTabs.length > 0) {
            this.selectedDayType = this.dayTypeTabs[0].id;
          }
          if (this.variants.length > 0 && this.selectedDirectionId === null) {
            this.selectedDirectionId = this.directionTabs[0]?.id ?? null;
          }
          this.cdr.markForCheck();
        });
      this.commonSectionService
        .getCommonSectionsForRoute(routeId)
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
          this.cdr.markForCheck();
        });
    }
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
      label:
        this.getMostCommonHeadsign(id) ??
        (id === null ? 'Unknown' : `Direction ${id}`),
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

  get dayTypeTabs(): {
    id: NonNullable<RouteHourlyStatsDto['dayType']>;
    label: string;
  }[] {
    const ordered: NonNullable<RouteHourlyStatsDto['dayType']>[] = [
      'WEEKDAY',
      'SATURDAY',
      'SUNDAY',
      'HOLIDAY',
    ];
    return ordered.map((dayType) => ({
      id: dayType,
      label: this.formatDayTypeLabel(dayType),
    }));
  }

  get dayTypeTabLabels(): string[] {
    return this.dayTypeTabs.map((tab) => tab.label);
  }

  get selectedDayTypeIndex(): number {
    const tabs = this.dayTypeTabs;
    const matchIndex = tabs.findIndex((tab) => tab.id === this.selectedDayType);
    return matchIndex >= 0 ? matchIndex : 0;
  }

  selectDayTypeByIndex(index: number): void {
    const tab = this.dayTypeTabs[index];
    if (!tab) return;
    if (this.selectedDayType === tab.id) return;
    this.selectedDayType = tab.id;
    this.cdr.markForCheck();
  }

  get routeClassificationLabel(): string | undefined {
    const variants = this.route?.variants ?? [];
    const classified = variants
      .map((variant) => variant.stopSpacingClassification)
      .filter(
        (
          value,
        ): value is NonNullable<RouteVariantDto['stopSpacingClassification']> =>
          value !== null && value !== undefined,
      );
    if (classified.length > 0) {
      const counts = new Map<
        NonNullable<RouteVariantDto['stopSpacingClassification']>,
        number
      >();
      classified.forEach((value) =>
        counts.set(value, (counts.get(value) ?? 0) + 1),
      );
      const [top] = [...counts.entries()].sort((a, b) => b[1] - a[1]);
      return top ? this.formatClassificationLabel(top[0]) : undefined;
    }
    const averages = variants
      .map((variant) => variant.averageStopSpacingMeters)
      .filter(
        (value): value is number => value !== null && value !== undefined,
      );
    if (averages.length === 0) {
      return undefined;
    }
    const averageMeters =
      averages.reduce((sum, value) => sum + value, 0) / averages.length;
    const classification = this.classifyStopSpacingMeters(averageMeters);
    return classification
      ? this.formatClassificationLabel(classification)
      : undefined;
  }

  private classifyStopSpacingMeters(
    averageStopSpacingMeters: number,
  ): NonNullable<RouteVariantDto['stopSpacingClassification']> | null {
    if (averageStopSpacingMeters >= 300 && averageStopSpacingMeters <= 700) {
      return 'local';
    }
    if (averageStopSpacingMeters >= 700 && averageStopSpacingMeters <= 1500) {
      return 'rapid';
    }
    if (averageStopSpacingMeters >= 1500 && averageStopSpacingMeters <= 3000) {
      return 'region-local';
    }
    if (averageStopSpacingMeters >= 3000 && averageStopSpacingMeters <= 10000) {
      return 'region-rapid';
    }
    if (
      averageStopSpacingMeters >= 10000 &&
      averageStopSpacingMeters <= 15000
    ) {
      return 'region-express';
    }
    return null;
  }

  private formatClassificationLabel(classification: string): string {
    switch (classification) {
      case 'region-local':
        return 'Region Local';
      case 'region-rapid':
        return 'Region Rapid';
      case 'region-express':
        return 'Region Express';
      default:
        return classification.charAt(0).toUpperCase() + classification.slice(1);
    }
  }

  private formatDayTypeLabel(
    dayType: NonNullable<RouteHourlyStatsDto['dayType']>,
  ): string {
    switch (dayType) {
      case 'WEEKDAY':
        return 'Weekday';
      case 'SATURDAY':
        return 'Saturday';
      case 'SUNDAY':
        return 'Sunday';
      case 'HOLIDAY':
        return 'Holiday';
    }
  }

  private normalizeDayType(
    dayType: RouteHourlyStatsDto['dayType'],
  ): NonNullable<RouteHourlyStatsDto['dayType']> {
    return dayType ?? 'WEEKDAY';
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

  get hourlyTripBars(): {
    hour: number;
    tripCount: number;
    heightPercent: number;
  }[] {
    const byHour = new Map<number, number>();
    this.filteredHourlyStats.forEach((item) => {
      byHour.set(item.hourOfDay, item.tripCount);
    });
    const values = Array.from(byHour.values());
    const max = values.length > 0 ? Math.max(...values) : 0;
    return Array.from({ length: 24 }).map((_, hour) => {
      const tripCount = byHour.get(hour) ?? 0;
      const heightPercent = max > 0 ? Math.round((tripCount / max) * 100) : 0;
      return { hour, tripCount, heightPercent };
    });
  }

  get hourlySpeedBars(): {
    hour: number;
    speedLabel: string;
    heightPercent: number;
  }[] {
    const byHour = new Map<number, number>();
    this.filteredHourlyStats.forEach((item) => {
      if (item.averageSpeedKph !== null && item.averageSpeedKph !== undefined) {
        byHour.set(item.hourOfDay, item.averageSpeedKph);
      }
    });
    const values = Array.from(byHour.values());
    const max = values.length > 0 ? Math.max(...values) : 0;
    return Array.from({ length: 24 }).map((_, hour) => {
      const speed = byHour.get(hour) ?? 0;
      const heightPercent = max > 0 ? Math.round((speed / max) * 100) : 0;
      return {
        hour,
        speedLabel: speed > 0 ? `${speed.toFixed(1)} km/h` : '—',
        heightPercent,
      };
    });
  }

  get isVariantsLoading(): boolean {
    return this.routeLoading || this.variantsLoading;
  }

  get filteredHourlyStats(): RouteHourlyStatsDto[] {
    const targetDayType =
      this.selectedDayType ??
      this.dayTypeTabs[0]?.id ??
      this.normalizeDayType(undefined);
    if (this.selectedDirectionId === null) {
      return this.hourlyStats.filter(
        (stat) =>
          (stat.directionId === null || stat.directionId === undefined) &&
          this.normalizeDayType(stat.dayType) === targetDayType,
      );
    }
    return this.hourlyStats.filter(
      (stat) =>
        stat.directionId === this.selectedDirectionId &&
        this.normalizeDayType(stat.dayType) === targetDayType,
    );
  }

  private getMostCommonHeadsign(directionId: number | null): string | null {
    const counts = new Map<string, number>();
    this.variants.forEach((variant) => {
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
