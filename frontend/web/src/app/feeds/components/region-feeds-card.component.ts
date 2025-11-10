import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MobilispectCardComponent } from '../../core/components/mobilispect-card.component';
import { RegionSelectorComponent } from './region-selector.component';
import { AgencyFeedCardComponent } from './agency-feed-card.component';
import { AgencyFeedGroup } from '../models/agency-feed-group.model';
import { MetropolitanRegion, Feed } from '../models/region.models';

@Component({
  selector: 'app-region-feeds-card',
  standalone: true,
  imports: [
    CommonModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MobilispectCardComponent,
    RegionSelectorComponent,
    AgencyFeedCardComponent
  ],
  template: `
    <app-mobilispect-card
      class="region-feeds-card"
      [style.--mobilispect-card-header-bg]="'#1e3a8a'"
      [style.--mobilispect-card-border-color]="'#1e3a8a'">
      <div card-header class="card-header-content">
        <div class="header-icon">
          <mat-icon>travel_explore</mat-icon>
        </div>
        <div class="header-text">
          <div class="header-title">Discover Feeds</div>
          <div class="header-subtitle">
            Choose a metropolitan region to explore its agencies and feeds.
          </div>
        </div>
      </div>

      <div card-content>
        <app-region-selector
          [regions]="regions"
          [selectedRegionId]="selectedRegionId"
          (regionChange)="regionChange.emit($event)"
        ></app-region-selector>

        @if (loadingFeeds) {
          <div class="loading-state" role="status" aria-live="polite">
            <mat-spinner diameter="40"></mat-spinner>
            <p>Loading feeds...</p>
          </div>
        } @else {
          @if (agencyGroups.length > 0) {
            <div class="feeds-grid">
              @for (group of agencyGroups; track group.agencyName) {
                <app-agency-feed-card
                  [agencyGroup]="group"
                  (importFeed)="importFeed.emit($event)"
                  (importAllFeeds)="importAllFeeds.emit($event)"
                  (viewDetails)="viewDetails.emit($event)">
                </app-agency-feed-card>
              }
            </div>
          } @else {
            <div class="empty-state">
              <mat-icon class="empty-icon">inbox</mat-icon>
              <h3>No feeds found</h3>
              <p>
                @if (selectedRegionId) {
                  No feeds are available for the selected region yet.
                } @else {
                  Select a region to view available transit feeds.
                }
              </p>
            </div>
          }
        }
      </div>
    </app-mobilispect-card>
  `,
  styles: [`
    .card-header-content {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 1rem 1.25rem;
      background: transparent;
    }

    .header-icon {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.2);
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .header-icon mat-icon {
      color: #fff;
    }

    .header-text {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .header-title {
      font-size: 1rem;
      font-weight: 600;
      color: #fff;
    }

    .header-subtitle {
      font-size: 0.85rem;
      color: rgba(255, 255, 255, 0.9);
    }

    .loading-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 32px 16px;
      color: #666;
      gap: 16px;
    }

    .feeds-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 16px;
      margin-top: 24px;
    }

    .empty-state {
      text-align: center;
      padding: 40px 20px;
      color: #666;
    }

    .empty-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: #ccc;
      margin-bottom: 16px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegionFeedsCardComponent {
  @Input() regions: MetropolitanRegion[] = [];
  @Input() selectedRegionId: string | null = null;
  @Input() regionFeeds: Feed[] = [];
  @Input() agencyGroups: AgencyFeedGroup[] = [];
  @Input() loadingFeeds = false;

  @Output() regionChange = new EventEmitter<string>();
  @Output() importFeed = new EventEmitter<Feed>();
  @Output() importAllFeeds = new EventEmitter<Feed[]>();
  @Output() viewDetails = new EventEmitter<Feed>();
}
