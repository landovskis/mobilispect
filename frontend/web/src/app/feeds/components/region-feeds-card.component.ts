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
    >
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

    .header-icon mat-icon {
      color: #fff;
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
