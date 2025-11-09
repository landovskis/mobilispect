import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MetropolitanRegion } from '../models/region.models';
import { MobilispectCardComponent } from '../../core/components/mobilispect-card.component';

@Component({
  selector: 'app-feed-regions-tab',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MobilispectCardComponent
  ],
  template: `
    <div class="tab-content">
      <app-mobilispect-card class="welcome-card">
        <div card-content>
          <p>
            Choose from available metropolitan regions to view and import their transit feeds.
          </p>
        </div>
      </app-mobilispect-card>

      <div class="regions-grid" *ngIf="regions?.length; else noRegions">
        <app-mobilispect-card
          *ngFor="let region of regions"
          class="region-card region-item"
        >
          <div card-header class="flex items-center justify-between gap-3">
            <div>
              <div card-title class="text-lg font-semibold text-white">{{ region.name }}</div>
              <div card-subtitle class="text-white/90">{{ region.feedCount }} feeds</div>
            </div>
            <mat-icon class="text-white !text-3xl">public</mat-icon>
          </div>

          <div card-actions>
            <button
              mat-raised-button
              color="primary"
              class="w-full !rounded-lg"
              (click)="importRegion.emit(region)"
            >
              <mat-icon>download</mat-icon>
              Import
            </button>
          </div>
        </app-mobilispect-card>
      </div>

      <ng-template #noRegions>
        <div class="empty-state">
          <div class="empty-content">
            <mat-icon class="empty-icon">location_off</mat-icon>
            <h3>No regions available</h3>
            <p>Try refreshing data or check your connection.</p>
          </div>
        </div>
      </ng-template>
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
