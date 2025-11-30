import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BrandCardComponent } from '../../shared/components/brand-card.component';
import { BrandButtonComponent } from '../../shared/components/brand-button.component';
import { AgencyFeedGroup, FeedGroupingUtils } from '../models/agency-feed-group.model';
import { Feed, FeedStatus, FeedSpecType } from '../models/region.models';

@Component({
  selector: 'app-agency-feed-card',
  standalone: true,
  imports: [
    CommonModule,
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
        <div class="feed-types">
          <div class="section-label">
            <mat-icon>category</mat-icon>
            <span>Feed Types</span>
          </div>
          <mat-chip-listbox aria-label="Feed types">
            @if (agencyGroup.feedsByType.gtfs > 0) {
              <mat-chip class="feed-type-chip feed-type-gtfs" [highlighted]="true">
                <mat-icon>directions_bus</mat-icon>
                {{ agencyGroup.feedsByType.gtfs }} Static
              </mat-chip>
            }
            @if (agencyGroup.feedsByType.gtfsRt > 0) {
              <mat-chip class="feed-type-chip feed-type-gtfs-rt" [highlighted]="true">
                <mat-icon>update</mat-icon>
                {{ agencyGroup.feedsByType.gtfsRt }} Realtime
              </mat-chip>
            }
          </mat-chip-listbox>
        </div>

        <div card-footer>
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
  styles: [`
    .feed-types {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .section-label {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      font-size: 0.9rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--mat-sys-primary, #0b4f8a);
    }

    .feed-type-chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-weight: 600;
      padding: 6px 10px;
      border-radius: 999px;
      height: auto;
      line-height: 1.2;
    }

    .feed-type-gtfs {
      background: rgba(76, 175, 80, 0.15);
      color: #2e7d32;
    }

    .feed-type-gtfs-rt {
      background: rgba(41, 128, 185, 0.15);
      color: #1f6c9e;
    }
  `],
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
