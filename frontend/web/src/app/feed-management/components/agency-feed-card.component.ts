import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatExpansionModule } from '@angular/material/expansion';
import { AgencyFeedGroup, FeedGroupingUtils } from '../models/agency-feed-group.model';
import { Feed, FeedStatus, FeedSpecType } from '../models/region.models';

/**
 * Agency Feed Card Component
 *
 * Displays multiple feeds from the same transit agency in a single card.
 * Shows a summary view with expandable details for individual feeds.
 *
 * @example
 * ```html
 * <app-agency-feed-card
 *   [agencyGroup]="group"
 *   (importFeed)="onImport($event)"
 *   (viewDetails)="onViewDetails($event)">
 * </app-agency-feed-card>
 * ```
 */
@Component({
  selector: 'app-agency-feed-card',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTooltipModule,
    MatExpansionModule
  ],
  template: `
    <mat-card class="agency-card" *ngIf="agencyGroup">
      <mat-card-header>
        <div class="agency-header">
          <mat-card-title class="agency-title">
            <mat-icon class="agency-icon">business</mat-icon>
            {{ agencyGroup.agencyName }}
          </mat-card-title>
          <mat-card-subtitle class="agency-subtitle">
            {{ agencyGroup.feeds.length }} feed{{ agencyGroup.feeds.length !== 1 ? 's' : '' }}
          </mat-card-subtitle>
        </div>
      </mat-card-header>

      <mat-card-content>
        <!-- Feed Types Summary -->
        <div class="feed-types-summary">
          <mat-chip-listbox aria-label="Feed types">
            <mat-chip
              *ngIf="agencyGroup.feedsByType.gtfs > 0"
              class="feed-type-chip feed-type-gtfs"
              [highlighted]="true">
              <mat-icon>{{ FeedGroupingUtils.getFeedTypeIcon(FeedSpecType.GTFS) }}</mat-icon>
              {{ agencyGroup.feedsByType.gtfs }} Static
            </mat-chip>
            <mat-chip
              *ngIf="agencyGroup.feedsByType.gtfsRt > 0"
              class="feed-type-chip feed-type-gtfs-rt"
              [highlighted]="true">
              <mat-icon>{{ FeedGroupingUtils.getFeedTypeIcon(FeedSpecType.GTFS_RT) }}</mat-icon>
              {{ agencyGroup.feedsByType.gtfsRt }} Realtime
            </mat-chip>
          </mat-chip-listbox>
        </div>

        <!-- Status and Meta Information -->
        <div class="feed-meta">
          <div class="meta-item" *ngIf="agencyGroup.hasActiveFeeds">
            <mat-icon class="status-icon active">check_circle</mat-icon>
            <span>Active</span>
          </div>
          <div class="meta-item" *ngIf="!agencyGroup.hasActiveFeeds">
            <mat-icon class="status-icon inactive">cancel</mat-icon>
            <span>Inactive</span>
          </div>
          <div class="meta-item" *ngIf="agencyGroup.hasAuthentication" matTooltip="Authentication configured">
            <mat-icon class="auth-icon">lock</mat-icon>
            <span>Auth</span>
          </div>
          <div class="meta-item" *ngIf="agencyGroup.lastUpdatedAt">
            <mat-icon>update</mat-icon>
            <span>{{ agencyGroup.lastUpdatedAt | date:'short' }}</span>
          </div>
        </div>

        <!-- Individual Feeds (Expandable) -->
        <mat-expansion-panel class="feeds-expansion" *ngIf="agencyGroup.feeds.length > 1">
          <mat-expansion-panel-header>
            <mat-panel-title>
              <mat-icon>list</mat-icon>
              View All Feeds
            </mat-panel-title>
          </mat-expansion-panel-header>

          <div class="individual-feeds-list">
            <div *ngFor="let feed of agencyGroup.feeds" class="individual-feed">
              <div class="feed-info">
                <mat-icon [class]="FeedGroupingUtils.getFeedTypeColorClass(feed.specType)">
                  {{ FeedGroupingUtils.getFeedTypeIcon(feed.specType) }}
                </mat-icon>
                <div class="feed-details">
                  <div class="feed-name">{{ feed.name }}</div>
                  <div class="feed-id">{{ feed.feedOnestopId }}</div>
                </div>
                <mat-chip
                  [class]="'status-chip status-' + feed.status.toLowerCase()"
                  class="feed-status-chip">
                  {{ FeedGroupingUtils.getFeedTypeLabel(feed.specType) }}
                </mat-chip>
              </div>
              <div class="feed-actions">
                <button
                  mat-icon-button
                  (click)="onViewDetails(feed)"
                  matTooltip="View details">
                  <mat-icon>info</mat-icon>
                </button>
                <button
                  mat-icon-button
                  color="primary"
                  (click)="onImportFeed(feed)"
                  [disabled]="feed.status !== FeedStatus.ACTIVE"
                  matTooltip="Import this feed">
                  <mat-icon>download</mat-icon>
                </button>
              </div>
            </div>
          </div>
        </mat-expansion-panel>
      </mat-card-content>

      <mat-card-actions>
        <!-- Primary action: Import primary feed -->
        <button
          mat-raised-button
          color="primary"
          (click)="onImportFeed(agencyGroup.primaryFeed)"
          [disabled]="!agencyGroup.hasActiveFeeds">
          <mat-icon>download</mat-icon>
          Import {{ FeedGroupingUtils.getFeedTypeLabel(agencyGroup.primaryFeed.specType) }}
        </button>

        <!-- Import all feeds -->
        <button
          mat-button
          *ngIf="agencyGroup.feeds.length > 1 && agencyGroup.hasActiveFeeds"
          (click)="onImportAll()"
          matTooltip="Import all active feeds from this agency">
          <mat-icon>download_for_offline</mat-icon>
          Import All
        </button>

        <!-- View details -->
        <button
          mat-button
          (click)="onViewDetails(agencyGroup.primaryFeed)">
          <mat-icon>info</mat-icon>
          Details
        </button>
      </mat-card-actions>
    </mat-card>
  `,
  styles: [`
    .agency-card {
      transition: transform 0.2s, box-shadow 0.2s;
      height: 100%;
      display: flex;
      flex-direction: column;
    }

    .agency-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 8px rgba(0,0,0,0.12);
    }

    .agency-header {
      width: 100%;
    }

    .agency-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 1.25rem;
      font-weight: 500;
    }

    .agency-icon {
      color: #1976d2;
    }

    .agency-subtitle {
      margin-left: 32px;
      font-size: 0.875rem;
      color: #666;
    }

    mat-card-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .feed-types-summary {
      display: flex;
      gap: 8px;
    }

    .feed-type-chip {
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .feed-type-chip mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }

    .feed-type-gtfs {
      background-color: rgba(33, 150, 243, 0.1);
      color: #1976d2;
    }

    .feed-type-gtfs-rt {
      background-color: rgba(255, 152, 0, 0.1);
      color: #f57c00;
    }

    .feed-meta {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      align-items: center;
    }

    .meta-item {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 0.875rem;
      color: #666;
    }

    .meta-item mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }

    .status-icon.active {
      color: #4caf50;
    }

    .status-icon.inactive {
      color: #f44336;
    }

    .auth-icon {
      color: #ff9800;
    }

    .feeds-expansion {
      margin-top: 8px;
    }

    .individual-feeds-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 8px 0;
    }

    .individual-feed {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px;
      border: 1px solid #e0e0e0;
      border-radius: 4px;
      background-color: #fafafa;
    }

    .feed-info {
      display: flex;
      align-items: center;
      gap: 12px;
      flex: 1;
    }

    .feed-details {
      flex: 1;
    }

    .feed-name {
      font-weight: 500;
      font-size: 0.875rem;
    }

    .feed-id {
      font-size: 0.75rem;
      color: #999;
    }

    .feed-status-chip {
      font-size: 0.75rem;
      min-height: 24px;
      padding: 0 8px;
    }

    .feed-actions {
      display: flex;
      gap: 4px;
    }

    mat-card-actions {
      padding: 16px;
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    @media (max-width: 768px) {
      .agency-title {
        font-size: 1.1rem;
      }

      .feed-meta {
        flex-direction: column;
        align-items: flex-start;
        gap: 8px;
      }

      mat-card-actions {
        flex-direction: column;
      }

      mat-card-actions button {
        width: 100%;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AgencyFeedCardComponent {
  @Input() agencyGroup!: AgencyFeedGroup;

  @Output() importFeed = new EventEmitter<Feed>();
  @Output() importAllFeeds = new EventEmitter<Feed[]>();
  @Output() viewDetails = new EventEmitter<Feed>();

  // Expose utilities and enums to template
  FeedGroupingUtils = FeedGroupingUtils;
  FeedStatus = FeedStatus;
  FeedSpecType = FeedSpecType;

  onImportFeed(feed: Feed): void {
    this.importFeed.emit(feed);
  }

  onImportAll(): void {
    const activeFeeds = this.agencyGroup.feeds.filter(f => f.status === FeedStatus.ACTIVE);
    this.importAllFeeds.emit(activeFeeds);
  }

  onViewDetails(feed: Feed): void {
    this.viewDetails.emit(feed);
  }
}
