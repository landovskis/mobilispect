import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { AgencyService } from '../../services/agency.service';
import { AgencySummary } from '../../models/agency-summary.model';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';
import { AgencySummaryCardComponent } from '../../components/agency-summary-card/agency-summary-card.component';

@Component({
  selector: 'app-region-list',
  standalone: true,
  imports: [CommonModule, BrandCardComponent, AgencySummaryCardComponent],
  template: `
    <app-brand-card title="Agencies" subtitle="Overview of agencies with route counts">
      <div class="grid">
        @for (agency of agencies; track agency.id) {
          <app-agency-summary-card [agency]="agency"></app-agency-summary-card>
        }
      </div>
    </app-brand-card>
  `,
  styles: [`
    .grid {
      display: grid;
      gap: 16px;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegionListComponent implements OnInit {
  agencies: AgencySummary[] = [];
  regionId?: string | null;

  constructor(
    private readonly agencyService: AgencyService,
    private readonly route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      this.regionId = params.get('regionId');
      this.loadAgencies();
    });
  }

  private loadAgencies(): void {
    this.agencyService.listAgencies(0, 50, this.regionId ?? undefined).subscribe(response => {
      this.agencies = response.content.sort((a, b) => b.routeCount - a.routeCount);
    });
  }
}
