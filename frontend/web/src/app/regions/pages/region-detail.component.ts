import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { RegionService } from '../../feeds/services/region.service';
import { MetropolitanRegionDetail, FeedStatus } from '../../feeds/models/region.models';
import { BrandSectionComponent } from '../../shared/components/brand-section.component';
import { Observable, combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';
import { AgencyListResponse } from '../../transit-frequency/services/agency.service';
import { AgencyCardComponent } from '../../transit-frequency/components/agency-card/agency-card.component';
import { AgencyService } from "../../agencies/services/agency.service";
import { BrandCardComponent } from '../../shared/components/brand-card.component';

interface RegionSummary {
  name: string;
  totalAgencies: number;
  totalActiveRoutes: number;
}

@Component({
  selector: 'app-region-detail',
  standalone: true,
  imports: [CommonModule, BrandSectionComponent, AgencyCardComponent, BrandCardComponent],
  template: `
    <div class="flex flex-col gap-6">
      <app-brand-section
        [title]="(summary$ | async)?.name || 'Summary'"
        subtitle="Overview of transit data in this region"
        icon="analytics">
        @if (summary$ | async; as summary) {
          <div class="summary-grid grid gap-4 md:grid-cols-2">
            <div class="summary-card rounded-xl border border-[var(--mat-sys-outline-variant,#e0e0e0)] bg-[var(--mat-sys-surface-container,#f5f5f5)] p-5 text-center">
              <div class="summary-value">{{ summary.totalAgencies }}</div>
              <div class="summary-label mt-2">Transit Agencies</div>
            </div>
            <div class="summary-card rounded-xl border border-[var(--mat-sys-outline-variant,#e0e0e0)] bg-[var(--mat-sys-surface-container,#f5f5f5)] p-5 text-center">
              <div class="summary-value">{{ summary.totalActiveRoutes }}</div>
              <div class="summary-label mt-2">Active Routes</div>
            </div>
          </div>
        } @else {
          <p>Loading summary...</p>
        }
      </app-brand-section>

      <app-brand-section
        title="Agencies"
        subtitle="Transit agencies serving this region"
        icon="business">
        @if (agencies$ | async; as agenciesResponse) {
          <div class="agencies-grid grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            @for (agency of agenciesResponse.content; track agency) {
              <app-agency-card
                [agency]="agency">
              </app-agency-card>
            }
          </div>
          @if (agenciesResponse.content.length === 0) {
            <p class="no-agencies py-4 text-center italic">
              No agencies found for this region.
            </p>
          }
        } @else {
          <div class="agencies-grid grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            @for (placeholder of loadingPlaceholders; track $index) {
              <app-brand-card [loading]="true"></app-brand-card>
            }
          </div>
        }
      </app-brand-section>
    </div>
    `,
  styles: [`
    .summary-value {
      font-size: 32px;
      font-weight: 600;
      color: var(--mat-sys-primary, #1976d2);
      line-height: 1.2;
    }

    .summary-label {
      font-size: 14px;
      font-weight: 500;
      color: var(--mat-sys-on-surface, #333);
    }

    .summary-sublabel {
      font-size: 12px;
      color: var(--mat-sys-on-surface-variant, #6b7280);
    }

    .no-agencies {
      color: var(--mat-sys-on-surface-variant, #6b7280);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegionDetailComponent implements OnInit {
  region$!: Observable<MetropolitanRegionDetail>;
  agencies$!: Observable<AgencyListResponse>;
  summary$!: Observable<RegionSummary>;
  loadingPlaceholders = Array.from({ length: 6 });

  private readonly route = inject(ActivatedRoute);
  private readonly regionService = inject(RegionService);
  private readonly agencyService = inject(AgencyService);

  ngOnInit(): void {
    const regionId = this.route.snapshot.paramMap.get('regionId');
    if (regionId) {
      this.region$ = this.regionService.getRegion(regionId);
      this.agencies$ = this.agencyService.listAgencies(0, 100, regionId).pipe(
        map(response => ({
          ...response,
          content: [...response.content].sort((a, b) => a.name.localeCompare(b.name))
        }))
      );

      // Compute summary from region and agencies data
      this.summary$ = combineLatest([this.region$, this.agencies$]).pipe(
        map(([region, agenciesResponse]) => {
          const agencies = agenciesResponse.content;

          // Sum active route counts across all agencies
          const totalActiveRoutes = agencies.reduce((sum, agency) => sum + agency.activeRouteCount, 0);

          return {
            name: region.name,
            totalAgencies: agencies.length,
            totalActiveRoutes
          };
        })
      );
    }
  }
}
