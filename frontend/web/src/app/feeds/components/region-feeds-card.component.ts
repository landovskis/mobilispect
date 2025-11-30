import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { RegionSelectorComponent } from './region-selector.component';
import { AgencyFeedCardComponent } from './agency-feed-card.component';
import { AgencyFeedGroup } from '../models/agency-feed-group.model';
import { MetropolitanRegion, Feed } from '../models/region.models';
import { BrandCardComponent } from '../../shared/components/brand-card.component';

@Component({
  selector: 'app-region-feeds-card',
  standalone: true,
  imports: [
    CommonModule,
    MatProgressSpinnerModule,
    MatIconModule,
    BrandCardComponent,
    RegionSelectorComponent,
    AgencyFeedCardComponent,
  ],
  template: `
    <app-brand-card
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
    </app-brand-card>
  `,
  styles: [`
    .feeds-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 24px;
      margin-top: 24px;
    }

    /* Responsive adjustments */
    @media (max-width: 768px) {
      .feeds-grid {
        grid-template-columns: 1fr;
        gap: 16px;
      }
    }

    @media (min-width: 769px) and (max-width: 1024px) {
      .feeds-grid {
        grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      }
    }

    @media (min-width: 1025px) {
      .feeds-grid {
        grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      }
    }

    .header-icon mat-icon {
      color: #fff;
    }

    .loading-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 48px 24px;
      gap: 16px;
    }

    .loading-state p {
      color: rgba(0, 0, 0, 0.6);
      font-size: 0.875rem;
    }

    :host-context(.dark-theme) .loading-state p {
      color: rgba(255, 255, 255, 0.7);
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 64px 24px;
      text-align: center;
    }

    .empty-state .empty-icon {
      font-size: 64px;
      width: 64px;
      height: 64px;
      color: rgba(0, 0, 0, 0.3);
      margin-bottom: 16px;
    }

    :host-context(.dark-theme) .empty-state .empty-icon {
      color: rgba(255, 255, 255, 0.3);
    }

    .empty-state h3 {
      margin: 0 0 8px 0;
      color: rgba(0, 0, 0, 0.7);
      font-size: 1.25rem;
      font-weight: 600;
    }

    :host-context(.dark-theme) .empty-state h3 {
      color: rgba(255, 255, 255, 0.87);
    }

    .empty-state p {
      margin: 0;
      color: rgba(0, 0, 0, 0.6);
      font-size: 0.875rem;
      max-width: 400px;
    }

    :host-context(.dark-theme) .empty-state p {
      color: rgba(255, 255, 255, 0.7);
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
