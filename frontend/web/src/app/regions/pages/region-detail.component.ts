import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { RegionService } from '../../feeds/services/region.service';
import { MetropolitanRegionDetail, FeedStatus } from '../../feeds/models/region.models';
import { BrandCardComponent } from '../../shared/components/brand-card.component';
import { BrandSectionComponent } from '../../shared/components/brand-section.component';
import { Observable, combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';
import { AgencyListResponse } from '../../transit-frequency/services/agency.service';
import { AgencyCardComponent } from '../../transit-frequency/components/agency-card/agency-card.component';
import { AgencyService } from "../../agencies/services/agency.service";

interface RegionSummary {
  name: string;
  totalAgencies: number;
  totalRoutes: number;
  totalActiveRoutes: number;
  activeFeeds: number;
  inactiveFeeds: number;
  errorFeeds: number;
}

@Component({
  selector: 'app-region-detail',
  standalone: true,
  imports: [CommonModule, BrandCardComponent, BrandSectionComponent, AgencyCardComponent],
  template: `
    <app-brand-card *ngIf="region$ | async as region" [title]="region.name" [subtitle]="region.regionOnestopId">
      <div class="meta">
        <div><strong>Country:</strong> {{ region.adm0Name }}</div>
        <div><strong>State/Province:</strong> {{ region.adm1Name }}</div>
        <div><strong>Feeds:</strong> {{ region.feedCount }}</div>
        <div><strong>Auto-update:</strong> {{ region.autoUpdateEnabled ? 'Enabled' : 'Manual' }}</div>
      </div>
    </app-brand-card>

    <app-brand-section
      [title]="(summary$ | async)?.name || 'Summary'"
      subtitle="Overview of transit data in this region"
      icon="analytics">
      <ng-container *ngIf="summary$ | async as summary; else loadingSummary">
        <div class="summary-grid">
          <div class="summary-card">
            <div class="summary-value">{{ summary.totalAgencies }}</div>
            <div class="summary-label">Transit Agencies</div>
          </div>
          <div class="summary-card">
            <div class="summary-value">{{ summary.totalRoutes }}</div>
            <div class="summary-label">Total Routes</div>
          </div>
          <div class="summary-card">
            <div class="summary-value">{{ summary.totalActiveRoutes }}</div>
            <div class="summary-label">Active Routes</div>
          </div>
          <div class="summary-card">
            <div class="summary-value">{{ summary.activeFeeds }}</div>
            <div class="summary-label">Active Feeds</div>
            <div class="summary-sublabel" *ngIf="summary.inactiveFeeds > 0 || summary.errorFeeds > 0">
              {{ summary.inactiveFeeds }} inactive, {{ summary.errorFeeds }} error
            </div>
          </div>
        </div>
      </ng-container>
      <ng-template #loadingSummary>
        <p>Loading summary...</p>
      </ng-template>
    </app-brand-section>

    <app-brand-section
      title="Agencies"
      subtitle="Transit agencies serving this region"
      icon="business">
      <ng-container *ngIf="agencies$ | async as agenciesResponse; else loading">
        <div class="agencies-grid">
          <app-agency-card
            *ngFor="let agency of agenciesResponse.content"
            [agency]="agency">
          </app-agency-card>
        </div>
        <p *ngIf="agenciesResponse.content.length === 0" class="no-agencies">
          No agencies found for this region.
        </p>
      </ng-container>
      <ng-template #loading>
        <p>Loading agencies...</p>
      </ng-template>
    </app-brand-section>
  `,
  styles: [`
    .meta { display: grid; gap: 8px; }

    app-brand-section {
      display: block;
      margin-top: 24px;
    }

    .summary-grid {
      display: grid;
      gap: 16px;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    }

    .summary-card {
      padding: 20px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container, #f5f5f5);
      border: 1px solid var(--mat-sys-outline-variant, #e0e0e0);
      text-align: center;
    }

    .summary-value {
      font-size: 32px;
      font-weight: 600;
      color: var(--mat-sys-primary, #1976d2);
      line-height: 1.2;
    }

    .summary-label {
      margin-top: 8px;
      font-size: 14px;
      font-weight: 500;
      color: var(--mat-sys-on-surface, #333);
    }

    .summary-sublabel {
      margin-top: 4px;
      font-size: 12px;
      color: var(--mat-sys-on-surface-variant, #6b7280);
    }

    .agencies-grid {
      display: grid;
      gap: 16px;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    }

    .no-agencies {
      color: var(--mat-sys-on-surface-variant, #6b7280);
      font-style: italic;
      text-align: center;
      padding: 16px 0;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegionDetailComponent implements OnInit {
  region$!: Observable<MetropolitanRegionDetail>;
  agencies$!: Observable<AgencyListResponse>;
  summary$!: Observable<RegionSummary>;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly regionService: RegionService,
    private readonly agencyService: AgencyService
  ) {}

  ngOnInit(): void {
    const regionId = this.route.snapshot.paramMap.get('regionId');
    if (regionId) {
      this.region$ = this.regionService.getRegion(regionId);
      this.agencies$ = this.agencyService.listAgencies(0, 100, regionId);

      // Compute summary from region and agencies data
      this.summary$ = combineLatest([this.region$, this.agencies$]).pipe(
        map(([region, agenciesResponse]) => {
          const agencies = agenciesResponse.content;

          // Count feeds by status
          const feedsByStatus = (region.feeds || []).reduce(
            (acc, feed) => {
              if (feed.status === FeedStatus.ACTIVE) acc.active++;
              else if (feed.status === FeedStatus.INACTIVE) acc.inactive++;
              else if (feed.status === FeedStatus.ERROR) acc.error++;
              return acc;
            },
            { active: 0, inactive: 0, error: 0 }
          );

          // Sum route counts across all agencies
          const totalRoutes = agencies.reduce((sum, agency) => sum + agency.routeCount, 0);
          const totalActiveRoutes = agencies.reduce((sum, agency) => sum + agency.activeRouteCount, 0);

          return {
            name: region.name,
            totalAgencies: agencies.length,
            totalRoutes,
            totalActiveRoutes,
            activeFeeds: feedsByStatus.active,
            inactiveFeeds: feedsByStatus.inactive,
            errorFeeds: feedsByStatus.error
          };
        })
      );
    }
  }
}
