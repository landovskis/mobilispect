import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MetropolitanRegion } from '../models/region.models';

@Component({
  selector: 'app-feed-regions-tab',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule
  ],
  template: `
    <div class="tab-content">
      <mat-card class="welcome-card">
        <mat-card-content>
          <p>
            Choose from available metropolitan regions to view and import their transit feeds.
          </p>
        </mat-card-content>
      </mat-card>

      <div class="regions-grid" *ngIf="regions?.length; else noRegions">
        <mat-card
          *ngFor="let region of regions"
          class="region-card region-item"
        >
          <mat-card-header>
            <mat-card-title>{{ region.name }}</mat-card-title>
            <mat-card-subtitle>{{ region.feedCount }} feeds</mat-card-subtitle>
          </mat-card-header>
          <mat-card-actions>
            <button
              mat-button
              (click)="viewFeeds.emit(region)"
              [attr.aria-label]="'View feeds for ' + region.name"
            >
              <mat-icon>visibility</mat-icon>
              View Feeds
            </button>
            <button
              mat-raised-button
              color="primary"
              (click)="importRegion.emit(region)"
              [attr.aria-label]="'Import feeds for ' + region.name"
            >
              <mat-icon>download</mat-icon>
              Import
            </button>
          </mat-card-actions>
        </mat-card>
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
  @Output() viewFeeds = new EventEmitter<MetropolitanRegion>();
  @Output() importRegion = new EventEmitter<MetropolitanRegion>();
}
