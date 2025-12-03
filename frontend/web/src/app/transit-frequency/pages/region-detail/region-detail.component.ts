import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { RegionService } from '../../../feeds/services/region.service';
import { MetropolitanRegionDetail } from '../../../feeds/models/region.models';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';

@Component({
  selector: 'app-region-detail',
  standalone: true,
  imports: [CommonModule, BrandCardComponent],
  template: `
    <app-brand-card *ngIf="region" [title]="region.name" [subtitle]="region.regionOnestopId">
      <div class="meta">
        <div><strong>Country:</strong> {{ region.adm0Name }}</div>
        <div><strong>State/Province:</strong> {{ region.adm1Name }}</div>
        <div><strong>Feeds:</strong> {{ region.feedCount }}</div>
        <div><strong>Auto-update:</strong> {{ region.autoUpdateEnabled ? 'Enabled' : 'Manual' }}</div>
      </div>
    </app-brand-card>
  `,
  styles: [`
    .meta { display: grid; gap: 8px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegionDetailComponent implements OnInit {
  region: MetropolitanRegionDetail | null = null;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly regionService: RegionService
  ) {}

  ngOnInit(): void {
    const regionId = this.route.snapshot.paramMap.get('regionId');
    if (regionId) {
      this.regionService.getRegion(regionId).subscribe(region => {
        this.region = region;
      });
    }
  }
}
