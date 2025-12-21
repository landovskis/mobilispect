import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BrandCardComponent } from '../../shared/components/brand-card.component';
import { BrandButtonComponent } from '../../shared/components/brand-button.component';
import { AgencyFeedGroup, FeedGroupingUtils } from '../models/agency-feed-group.model';
import { Feed, FeedStatus, FeedSpecType } from '../models';

@Component({
  selector: 'app-agency-feed-card',
  standalone: true,
  imports: [
    MatIconModule,
    MatChipsModule,
    MatTooltipModule,
    BrandCardComponent,
    BrandButtonComponent
],
  template: `
    @if (agencyGroup) {
      <app-brand-card
        [title]="agencyGroup.agencyName"
        [badge]="agencyGroup.feeds.length + ' feeds'"
        [hasFooter]="true">
        <div class="feed-types flex flex-col gap-3">
          <div class="section-label inline-flex items-center gap-2">
            <mat-icon>category</mat-icon>
            <span>Feed Types</span>
          </div>
          <mat-chip-listbox aria-label="Feed types" class="flex flex-wrap gap-2">
            @if (agencyGroup.feedsByType.gtfs > 0) {
              <mat-chip class="feed-type-chip feed-type-gtfs inline-flex items-center gap-1.5 rounded-full px-2.5 py-1" [highlighted]="true">
                <mat-icon>directions_bus</mat-icon>
                {{ agencyGroup.feedsByType.gtfs }} Static
              </mat-chip>
            }
            @if (agencyGroup.feedsByType.gtfsRt > 0) {
              <mat-chip class="feed-type-chip feed-type-gtfs-rt inline-flex items-center gap-1.5 rounded-full px-2.5 py-1" [highlighted]="true">
                <mat-icon>update</mat-icon>
                {{ agencyGroup.feedsByType.gtfsRt }} Realtime
              </mat-chip>
            }
          </mat-chip-listbox>
        </div>

        <div card-footer class="w-full">
          <app-brand-button
            variant="primary"
            [block]="true"
            (click)="onImport()"
            [disabled]="!agencyGroup.hasActiveFeeds"
            [matTooltip]="getImportTooltip()">
            <mat-icon>download</mat-icon>
            <span>Import{{ getActiveFeedsCount() > 1 ? ' All' : '' }}</span>
          </app-brand-button>
        </div>
      </app-brand-card>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AgencyFeedCardComponent {
  @Input() agencyGroup!: AgencyFeedGroup;

  @Output() importFeed = new EventEmitter<Feed>();
  @Output() importAllFeeds = new EventEmitter<Feed[]>();
  @Output() viewDetails = new EventEmitter<Feed>();

  FeedGroupingUtils = FeedGroupingUtils;
  FeedStatus = FeedStatus;
  FeedSpecType = FeedSpecType;

  onImport(): void {
    const activeFeeds = this.agencyGroup.feeds.filter(f => f.status === FeedStatus.ACTIVE);
    if (activeFeeds.length === 1) {
      this.importFeed.emit(activeFeeds[0]);
    } else if (activeFeeds.length > 1) {
      this.importAllFeeds.emit(activeFeeds);
    }
  }

  getImportTooltip(): string {
    const activeFeeds = this.agencyGroup.feeds.filter(f => f.status === FeedStatus.ACTIVE);
    if (activeFeeds.length === 0) return 'No active feeds available';
    if (activeFeeds.length === 1) return `Import ${activeFeeds[0].name}`;
    return `Import all ${activeFeeds.length} active feeds from this agency`;
  }

  getActiveFeedsCount(): number {
    return this.agencyGroup.feeds.filter(f => f.status === FeedStatus.ACTIVE).length;
  }
}
