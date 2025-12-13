import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { RegionService } from '../../feeds/services/region.service';
import { MetropolitanRegionDetail } from '../../feeds/models/region.models';
import { BrandCardComponent } from '../../shared/components/brand-card.component';
import { BrandSectionComponent } from '../../shared/components/brand-section.component';
import { Observable } from 'rxjs';
import { AgencyListResponse } from '../../transit-frequency/services/agency.service';
import { AgencyCardComponent } from '../../transit-frequency/components/agency-card/agency-card.component';
import {AgencyService} from "../../agencies/services/agency.service";

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
    }
  }
}
