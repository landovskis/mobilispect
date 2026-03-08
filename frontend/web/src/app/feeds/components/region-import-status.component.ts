import { CommonModule } from '@angular/common';
import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RegionImportStatus, RegionImportStatusResponse } from '../models/import.models';

@Component({
  selector: 'app-region-import-status',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <section class="region-import-status" aria-live="polite">
      <div class="header flex items-center justify-between gap-3">
        <div class="title flex items-center gap-2">
          <mat-icon class="status-icon">sync</mat-icon>
          <span class="title-text">Region Import</span>
        </div>

        @if (loading) {
          <mat-spinner diameter="20"></mat-spinner>
        } @else {
          @if (status) {
            <span class="badge" [class]="statusClass">{{ statusLabel }}</span>
          } @else {
            <span class="badge idle">Idle</span>
          }
        }
      </div>

      @if (status) {
        <div class="progress mt-3">
          <div
            class="progress-track"
            role="progressbar"
            [attr.aria-valuenow]="progressPercent"
            aria-valuemin="0"
            aria-valuemax="100"
          >
            <div class="progress-bar" [style.width.%]="progressPercent"></div>
          </div>
          <div class="progress-meta mt-2 flex flex-wrap gap-4 text-sm">
            <span>{{ progressPercent }}% complete</span>
            <span>{{ completedTotal }} / {{ status.totalFeeds }} processed</span>
            <span>{{ status.failedCount }} failed</span>
            <span>{{ status.skippedCount }} skipped</span>
          </div>
        </div>
      }
    </section>
  `,
  styles: [
    `
      .region-import-status {
        border-radius: 14px;
        border: 1px solid var(--mat-sys-outline-variant, #e2e8f0);
        background: var(--mat-sys-surface-container, #f8fafc);
        padding: 16px;
      }

      .title-text {
        font-weight: 700;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        font-size: 0.75rem;
      }

      .status-icon {
        font-size: 20px;
      }

      .badge {
        border-radius: 999px;
        padding: 4px 12px;
        font-size: 0.75rem;
        font-weight: 600;
        letter-spacing: 0.04em;
        text-transform: uppercase;
      }

      .badge.running {
        background: rgba(2, 132, 199, 0.15);
        color: #075985;
      }

      .badge.completed {
        background: rgba(16, 185, 129, 0.18);
        color: #065f46;
      }

      .badge.partial {
        background: rgba(249, 115, 22, 0.18);
        color: #9a3412;
      }

      .badge.failed {
        background: rgba(239, 68, 68, 0.18);
        color: #991b1b;
      }

      .badge.pending {
        background: rgba(148, 163, 184, 0.2);
        color: #475569;
      }

      .badge.idle {
        background: rgba(148, 163, 184, 0.15);
        color: #64748b;
      }

      .progress-track {
        height: 8px;
        border-radius: 999px;
        background: rgba(148, 163, 184, 0.3);
        overflow: hidden;
      }

      .progress-bar {
        height: 100%;
        border-radius: 999px;
        background: linear-gradient(90deg, #0b4f8a 0%, #00a7c4 100%);
        transition: width 0.4s ease;
      }

      :host-context(.dark-theme) .region-import-status {
        background: rgba(15, 23, 42, 0.6);
        border-color: rgba(148, 163, 184, 0.3);
      }

      :host-context(.dark-theme) .progress-track {
        background: rgba(148, 163, 184, 0.4);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegionImportStatusComponent {
  @Input() status: RegionImportStatusResponse | null = null;
  @Input() loading = false;

  get completedTotal(): number {
    if (!this.status) return 0;
    return this.status.completedCount + this.status.failedCount + this.status.skippedCount;
  }

  get progressPercent(): number {
    if (!this.status || !this.status.totalFeeds) return 0;
    return Math.min(100, Math.round((this.completedTotal / this.status.totalFeeds) * 100));
  }

  get statusLabel(): string {
    if (!this.status) return 'Idle';
    switch (this.status.status) {
      case RegionImportStatus.PENDING:
        return 'Pending';
      case RegionImportStatus.RUNNING:
        return 'Running';
      case RegionImportStatus.COMPLETED:
        return 'Completed';
      case RegionImportStatus.PARTIAL_SUCCESS:
        return 'Partial';
      case RegionImportStatus.FAILED:
        return 'Failed';
      case RegionImportStatus.CANCELLED:
        return 'Cancelled';
      default:
        return 'Unknown';
    }
  }

  get statusClass(): string {
    if (!this.status) return 'idle';
    switch (this.status.status) {
      case RegionImportStatus.PENDING:
        return 'badge pending';
      case RegionImportStatus.RUNNING:
        return 'badge running';
      case RegionImportStatus.COMPLETED:
        return 'badge completed';
      case RegionImportStatus.PARTIAL_SUCCESS:
        return 'badge partial';
      case RegionImportStatus.FAILED:
        return 'badge failed';
      case RegionImportStatus.CANCELLED:
        return 'badge failed';
      default:
        return 'badge pending';
    }
  }
}
