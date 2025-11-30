import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MetropolitanRegion } from '../models/region.models';
import { BrandCardComponent } from '../../shared/components/brand-card.component';
import { BrandButtonComponent } from '../../shared/components/brand-button.component';

@Component({
  selector: 'app-feed-regions-tab',
  standalone: true,
  imports: [
    CommonModule,
    MatIconModule,
    BrandCardComponent,
    BrandButtonComponent
  ],
  template: `
    <div class="tab-content">
      <app-brand-card class="welcome-card">
          <p>
            Choose from available metropolitan regions to view and import their transit feeds.
          </p>
      </app-brand-card>

      @if (regions?.length) {
        <div class="regions-grid">
          @for (region of regions!; track region.regionOnestopId) {
            <app-brand-card
              class="region-card region-item"
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
        <div class="empty-state">
          <div class="empty-content">
            <mat-icon class="empty-icon">location_off</mat-icon>
            <h3>No regions available</h3>
            <p>Try refreshing data or check your connection.</p>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .tab-content {
      padding: 24px 0;
    }

    .welcome-card {
      margin-bottom: 24px;
    }

    .regions-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: 16px;
      margin-top: 16px;
    }

    .region-card {
      transition: transform 0.2s, box-shadow 0.2s;
    }

    .region-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 8px rgba(0,0,0,0.12);
    }

    .empty-state {
      margin-top: 24px;
    }

    .empty-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 40px 20px;
      text-align: center;
      color: #666;
    }

    .empty-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: #ccc;
      margin-bottom: 16px;
    }

    .empty-content h3 {
      margin: 0 0 8px 0;
      font-weight: 500;
    }

    .empty-content p {
      margin: 0;
      color: #999;
    }

    @media (max-width: 768px) {
      .tab-content {
        padding: 16px 0;
      }

      .regions-grid {
        grid-template-columns: 1fr;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FeedRegionsTabComponent {
  @Input() regions: MetropolitanRegion[] | null = [];
  @Output() importRegion = new EventEmitter<MetropolitanRegion>();
}
