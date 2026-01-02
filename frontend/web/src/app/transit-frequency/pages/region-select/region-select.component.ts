import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
} from '@angular/core';

import { Router } from '@angular/router';
import { RegionService } from '../../../feeds/services/region.service';
import { MetropolitanRegion } from '../../../feeds/models/region.models';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';
import { BrandButtonComponent } from '../../../shared/components/brand-button.component';

@Component({
  selector: 'app-region-select',
  standalone: true,
  imports: [BrandCardComponent, BrandButtonComponent],
  template: `
    <app-brand-card
      title="Select a Region"
      subtitle="Showing regions with at least one imported feed"
      [loading]="isLoading"
    >
      <div class="regions flex flex-col gap-3" role="list">
        @for (region of regions; track region.regionOnestopId) {
          <div
            class="region flex items-center justify-between border-b py-2"
            role="listitem"
          >
            <div class="info flex flex-col gap-0.5">
              <div class="name">{{ region.name }}</div>
              <small>{{ region.adm0Name }} {{ region.adm1Name }}</small>
              <small>Feeds: {{ region.feedCount }}</small>
            </div>
            <app-brand-button
              variant="primary"
              (click)="select(region.regionOnestopId)"
            >
              View region
            </app-brand-button>
          </div>
        }
      </div>
    </app-brand-card>
  `,
  styles: [
    `
      .region {
        border-bottom-color: var(--mat-sys-outline, #e2e8f0);
      }
      .info {
        color: var(--mat-sys-on-surface, #0f172a);
      }
      .name {
        font-weight: 700;
      }
      :host-context(.dark-theme) .region {
        border-bottom-color: rgba(148, 163, 184, 0.3);
      }
      :host-context(.dark-theme) .info {
        color: var(--mat-sys-on-surface, #e5e7eb);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegionSelectComponent implements OnInit {
  regions: MetropolitanRegion[] = [];
  isLoading = true;

  private readonly regionService = inject(RegionService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.isLoading = true;
    this.regionService.listRegions().subscribe((regions) => {
      this.regions = regions.filter((region) => region.feedCount > 0);
      this.isLoading = false;
    });
  }

  select(regionId: string): void {
    this.router.navigate(['/transit-frequency/regions', regionId]);
  }
}
