import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  inject,
  Input,
  Output,
  signal,
} from '@angular/core';

import {
  FrequencyDto,
  FrequencyService,
  RouteVariantDto,
} from '../../services/frequency.service';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';

@Component({
  selector: 'app-route-variant-card',
  standalone: true,
  imports: [CommonModule, BrandCardComponent],
  template: `
    <div
      class="variant"
      role="button"
      tabindex="0"
      (click)="selected.emit(variant.id)"
      (keydown.enter)="$event.preventDefault(); selected.emit(variant.id)"
      (keydown.space)="$event.preventDefault(); selected.emit(variant.id)"
    >
      <app-brand-card>
        <div class="title flex flex-col gap-0.5">
          <div class="variant-header flex flex-wrap items-center gap-2">
            <span>{{ variant.headsign || 'Variant' }}</span>
          </div>
          <ul class="stop-list m-0 ml-4 list-none">
            @for (stopName of stopNames; track $index; let i = $index) {
              <li class="stop-name">{{ stopName }}</li>
              @if (i < stopNames.length - 1 && stopSpacingLabel(i)) {
                <li class="stop-spacing">
                  <span class="spacing-label">{{ stopSpacingLabel(i) }}</span>
                </li>
              }
            }
          </ul>
          <div class="meta flex flex-wrap items-center gap-2 text-sm">
            @if (variant.firstDepartureTime && variant.lastDepartureTime) {
              <span
                class="schedule-badge rounded-full px-2 py-0.5 text-[0.75rem] font-semibold cursor-pointer hover:opacity-80"
                (click)="toggleSchedule($event)"
                title="Click to view complete schedule"
              >
                {{ formatSchedule(variant) }}
              </span>
            }
            @if (variant.classification) {
              <span class="classification-badge rounded-full px-2 py-0.5 text-[0.75rem] font-semibold">
                {{ formatClassification(variant.classification) }}
              </span>
            }
          </div>
          @if (showCompleteSchedule()) {
            <div class="complete-schedule mt-3 p-3 rounded-md">
              <div class="flex justify-between items-center mb-2">
                <h4 class="text-sm font-semibold m-0">Complete Schedule</h4>
                <button
                  class="close-btn text-sm"
                  (click)="toggleSchedule($event)"
                  aria-label="Close schedule"
                >
                  ✕
                </button>
              </div>
              @if (isLoadingSchedule()) {
                <p class="text-sm">Loading...</p>
              } @else if (completeDepartures().length > 0) {
                <div class="departures-grid">
                  @for (time of completeDepartures(); track time) {
                    <span class="departure-time">{{ formatDepartureTime(time) }}</span>
                  }
                </div>
              } @else {
                <p class="text-sm">No departure times available</p>
              }
            </div>
          }
        </div>
      </app-brand-card>
    </div>
  `,
  styles: [
    `
      .variant {
        border-bottom-color: var(--mat-sys-outline, #e2e8f0);
        border-right: 1px solid var(--mat-sys-outline, #e2e8f0);
        cursor: pointer;
        display: block;
        padding-right: 12px;
      }
      .stop-list {
        border-left: 10px solid var(--mat-sys-primary, #0b4f8a);
        color: var(--mat-sys-on-surface-variant, #64748b);
        font-size: 0.85rem;
        padding-left: 12px;
      }
      .stop-name {
        margin: 6px 0;
        position: relative;
      }
      .stop-name::before {
        background: var(--mat-sys-on-primary, #ffffff);
        border-radius: 999px;
        content: '';
        height: 8px;
        left: -21px;
        position: absolute;
        top: 0.45em;
        width: 8px;
      }
      .stop-spacing {
        color: var(--mat-sys-on-surface-variant, #94a3b8);
        font-size: 0.75rem;
        margin: 2px 0 6px;
        padding-left: 18px;
      }
      .spacing-label {
        display: inline-block;
        padding: 2px 6px;
        border-radius: 999px;
        background: rgba(148, 163, 184, 0.18);
        line-height: 1.2;
      }
      .schedule-badge {
        background: rgba(103, 58, 183, 0.12);
        color: #673ab7;
      }
      .classification-badge {
        background: rgba(34, 197, 94, 0.12);
        color: #166534;
      }
      :host-context(.dark-theme) .variant {
        border-bottom-color: rgba(148, 163, 184, 0.3);
        border-right-color: rgba(148, 163, 184, 0.3);
      }
      :host-context(.dark-theme) .stop-list {
        border-left-color: var(--mat-sys-primary, #0b4f8a);
        color: var(--mat-sys-on-surface-variant, #cbd5e1);
      }
      :host-context(.dark-theme) .stop-spacing {
        color: rgba(226, 232, 240, 0.75);
      }
      :host-context(.dark-theme) .spacing-label {
        background: rgba(148, 163, 184, 0.2);
      }
      :host-context(.dark-theme) .schedule-badge {
        background: rgba(156, 39, 176, 0.2);
        color: #ce93d8;
      }
      :host-context(.dark-theme) .classification-badge {
        background: rgba(34, 197, 94, 0.2);
        color: #bbf7d0;
      }
      .complete-schedule {
        background: rgba(103, 58, 183, 0.05);
        border: 1px solid rgba(103, 58, 183, 0.2);
      }
      .departures-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
        gap: 8px;
        max-height: 300px;
        overflow-y: auto;
      }
      .departure-time {
        background: rgba(103, 58, 183, 0.12);
        color: #673ab7;
        padding: 4px 8px;
        border-radius: 4px;
        text-align: center;
        font-size: 0.875rem;
        font-weight: 500;
      }
      .close-btn {
        background: transparent;
        border: none;
        cursor: pointer;
        color: var(--mat-sys-on-surface-variant, #64748b);
        font-size: 1.25rem;
        line-height: 1;
        padding: 0;
        width: 24px;
        height: 24px;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .close-btn:hover {
        opacity: 0.7;
      }
      :host-context(.dark-theme) .complete-schedule {
        background: rgba(156, 39, 176, 0.1);
        border: 1px solid rgba(156, 39, 176, 0.3);
      }
      :host-context(.dark-theme) .departure-time {
        background: rgba(156, 39, 176, 0.2);
        color: #ce93d8;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RouteVariantCardComponent {
  @Input({ required: true }) variant!: RouteVariantDto;
  @Input() frequencies: FrequencyDto[] = [];
  @Output() selected = new EventEmitter<string>();

  private readonly frequencyService = inject(FrequencyService);

  showCompleteSchedule = signal(false);
  isLoadingSchedule = signal(false);
  completeDepartures = signal<string[]>([]);

  get stopNames(): string[] {
    if (this.variant.stopNames && this.variant.stopNames.length > 0) {
      return this.variant.stopNames.filter(Boolean);
    }
    return this.variant.stopPattern.split('|').filter(Boolean);
  }

  stopSpacingLabel(index: number): string | null {
    const spacingMeters = this.variant.stopSpacingsMeters?.[index];
    if (spacingMeters === null || spacingMeters === undefined) {
      return null;
    }
    if (!Number.isFinite(spacingMeters) || spacingMeters <= 0) {
      return null;
    }
    if (spacingMeters < 1000) {
      return `${Math.round(spacingMeters)} m`;
    }
    return `${(spacingMeters / 1000).toFixed(2)} km`;
  }


  formatSchedule(variant: RouteVariantDto): string {
    if (!variant.firstDepartureTime || !variant.lastDepartureTime) {
      return 'Schedule: Not available';
    }

    const first = this.formatTime(variant.firstDepartureTime);
    const last = this.formatTime(variant.lastDepartureTime);
    const trips = variant.scheduleTripCount ? ` (${variant.scheduleTripCount} trips)` : '';

    return `${first} - ${last}${trips}`;
  }

  private formatTime(timeStr: string): string {
    // Parse time string (HH:mm:ss or HH:mm format)
    const parts = timeStr.split(':');
    if (parts.length < 2) return timeStr;

    const hour = parseInt(parts[0], 10);
    const minute = parts[1];

    // Convert to 12-hour format
    const period = hour >= 12 ? 'PM' : 'AM';
    const hour12 = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour;

    return `${hour12}:${minute} ${period}`;
  }

  toggleSchedule(event: Event): void {
    event.stopPropagation();

    if (!this.showCompleteSchedule()) {
      // Load complete schedule if not already loaded
      if (this.completeDepartures().length === 0) {
        this.isLoadingSchedule.set(true);
        this.frequencyService.getCompleteSchedule(this.variant.id).subscribe({
          next: (departures) => {
            this.completeDepartures.set(departures);
            this.isLoadingSchedule.set(false);
          },
          error: (err) => {
            console.error('Failed to load complete schedule:', err);
            this.isLoadingSchedule.set(false);
          },
        });
      }
    }

    this.showCompleteSchedule.set(!this.showCompleteSchedule());
  }

  formatDepartureTime(timeStr: string): string {
    return this.formatTime(timeStr);
  }

  formatClassification(value: string): string {
    return value
      .split('_')
      .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1).toLowerCase())
      .join(' ');
  }
}
