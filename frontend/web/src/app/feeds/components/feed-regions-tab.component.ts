import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

import { MatIconModule } from '@angular/material/icon';
import { MetropolitanRegion } from '../models';
import { BrandCardComponent } from '../../shared/components/brand-card.component';
import { BrandButtonComponent } from '../../shared/components/brand-button.component';

@Component({
  selector: 'app-feed-regions-tab',
  standalone: true,
  imports: [
    MatIconModule,
    BrandCardComponent,
    BrandButtonComponent
],
  template: `
    <div class="tab-content py-6 max-md:py-4">
      <app-brand-card class="welcome-card mb-6">
          <p>
            Choose from available metropolitan regions to view and import their transit feeds.
          </p>
      </app-brand-card>

      @if (loading) {
        <div class="regions-grid mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          @for (placeholder of loadingPlaceholders; track $index) {
            <app-brand-card [loading]="true"></app-brand-card>
          }
        </div>
      } @else if (regions?.length) {
        <div class="regions-grid mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          @for (region of regions!; track region.regionOnestopId) {
            <app-brand-card
              class="region-card region-item transition-transform duration-200 hover:-translate-y-0.5"
            >
              <div class="flex items-center justify-between gap-3">
                <div>
                  <div class="text-lg font-semibold text-[var(--ms-color-primary,#0b4f8a)]">{{ region.name }}</div>
                  <div class="text-[var(--ms-color-muted,#6b7280)]">{{ region.feedCount }} feeds</div>
                </div>
                <mat-icon class="!text-3xl text-[var(--ms-color-primary,#0b4f8a)]">public</mat-icon>
              </div>

              <div class="mt-4">
                <app-brand-button
                  variant="primary"
                  [block]="true"
                  (click)="importRegion.emit(region)">
                  <mat-icon>download</mat-icon>
                  <span>Import</span>
                </app-brand-button>
              </div>
            </app-brand-card>
          }
        </div>
      } @else {
        <div class="empty-state mt-6">
          <div class="empty-content flex flex-col items-center px-5 py-10 text-center text-[#666]">
            <mat-icon class="empty-icon mb-4 text-[48px] text-[#ccc]">location_off</mat-icon>
            <h3 class="mb-2 font-medium m-0">No regions available</h3>
            <p class="text-[#999] m-0">Try refreshing data or check your connection.</p>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .region-card {
      transition: transform 0.2s, box-shadow 0.2s;
    }

    .region-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 8px rgba(0,0,0,0.12);
    }

  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FeedRegionsTabComponent {
  @Input() regions: MetropolitanRegion[] | null = [];
  @Input() loading = false;
  @Output() importRegion = new EventEmitter<MetropolitanRegion>();
  loadingPlaceholders = Array.from({ length: 6 });
}
