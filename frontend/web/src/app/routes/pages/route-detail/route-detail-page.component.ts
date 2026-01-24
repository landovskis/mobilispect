import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import {
  FrequencyService,
  FrequencyDto,
  RouteDto,
  RouteVariantDto,
} from '../../services/frequency.service';
import {
  CommonSectionService,
  CommonSectionDto,
  CombinedFrequencyDto,
} from '../../services/common-section.service';
import { RouteVariantCardComponent } from '../../components/route-variant-card/route-variant-card.component';
import { CommonSectionDisplayComponent } from '../../components/common-section-display/common-section-display.component';
import { RouteSummaryCardComponent } from '../../components/route-summary-card/route-summary-card.component';
import { Observable, tap } from 'rxjs';
import { BrandSectionComponent } from '../../../shared/components/brand-section.component';
import { MatTabsModule } from '@angular/material/tabs';

@Component({
  selector: 'app-route-detail-page',
  standalone: true,
  imports: [
    CommonModule,
    RouteVariantCardComponent,
    CommonSectionDisplayComponent,
    RouteSummaryCardComponent,
    BrandSectionComponent,
    MatTabsModule,
  ],
  template: `
    @if (route$ | async; as route) {
      <app-route-summary-card [route]="route"></app-route-summary-card>
    } @else {
      <p>Loading route details...</p>
    }

    <app-brand-section class="mt-6 block" title="Variants">
      @if (directionTabs.length > 1) {
        <mat-tab-group
          class="mb-4"
          [selectedIndex]="selectedDirectionIndex"
          (selectedIndexChange)="selectDirectionByIndex($event)"
        >
          @for (tab of directionTabs; track tab.key) {
            <mat-tab [label]="tab.label"></mat-tab>
          }
        </mat-tab-group>
      }
      <div class="grid gap-4 md:grid-cols-2" role="list">
        @for (variant of filteredVariants; track variant.id) {
          <app-route-variant-card
            [variant]="variant"
            [frequencies]="variant.id === lastVariantId ? frequencies : []"
            (selected)="loadFrequencies($event)"
          >
          </app-route-variant-card>
        }
      </div>

      <app-common-section-display
        [sections]="commonSections"
        [combined]="combinedBySection"
      >
      </app-common-section-display>
    </app-brand-section>
  `,
  styles: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RouteDetailPageComponent implements OnInit {
  route$!: Observable<RouteDto>;
  route?: RouteDto;
  variants: RouteVariantDto[] = [];
  frequencies: FrequencyDto[] = [];
  commonSections: CommonSectionDto[] = [];
  combinedBySection: Record<string, CombinedFrequencyDto> = {};
  lastVariantId?: string;
  selectedDirectionId: number | null = null;

  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly frequencyService = inject(FrequencyService);
  private readonly commonSectionService = inject(CommonSectionService);

  constructor() {}

  ngOnInit(): void {
    const routeId = this.activatedRoute.snapshot.paramMap.get('routeId');
    if (routeId) {
      this.route$ = this.frequencyService.getRoute(routeId).pipe(
        tap((route) => {
          this.route = route;
        }),
      );
      this.frequencyService.getVariants(routeId).subscribe((variants) => {
        this.variants = variants;
        if (variants.length > 0 && this.selectedDirectionId === null) {
          this.selectedDirectionId = this.directionTabs[0]?.id ?? null;
        }
        this.loadFirstVariantForDirection();
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
              });
          });
        });
    }
  }

  loadFrequencies(variantId: string): void {
    this.lastVariantId = variantId;
    this.frequencyService.getFrequencies(variantId).subscribe((freqs) => {
      this.frequencies = freqs;
    });
  }

  get directionTabs(): { id: number | null; label: string; key: string }[] {
    const ids = Array.from(
      new Set(this.variants.map((variant) => variant.directionId ?? null)),
    );
    const ordered: Array<number | null> = [];
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

  selectDirection(directionId: number | null): void {
    if (this.selectedDirectionId === directionId) return;
    this.selectedDirectionId = directionId;
    this.loadFirstVariantForDirection();
  }

  selectDirectionByIndex(index: number): void {
    const tab = this.directionTabs[index];
    if (!tab) return;
    this.selectDirection(tab.id);
  }

  get selectedDirectionIndex(): number {
    const index = this.directionTabs.findIndex(
      (tab) => tab.id === this.selectedDirectionId,
    );
    return index === -1 ? 0 : index;
  }

  private loadFirstVariantForDirection(): void {
    if (this.filteredVariants.length === 0) return;
    if (
      this.lastVariantId &&
      this.filteredVariants.some((variant) => variant.id === this.lastVariantId)
    ) {
      return;
    }
    this.loadFrequencies(this.filteredVariants[0].id);
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

  getRouteTypeLabel(routeType: string): string {
    switch (routeType) {
      case 'BUS':
        return 'Bus';
      case 'SUBWAY':
        return 'Subway/Metro';
      case 'TRAM':
        return 'Tram';
      case 'RAIL':
        return 'Rail';
      default:
        return routeType;
    }
  }
}
