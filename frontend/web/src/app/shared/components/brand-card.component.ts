import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';


@Component({
  selector: 'app-brand-card',
  standalone: true,
  imports: [MatIconModule],
  template: `
    <section class="brand-card block rounded-2xl p-6" [class.border]="border" [class.is-loading]="loading">
      @if (loading) {
        <header class="card-header mb-3 flex items-start gap-2">
          <span class="skeleton skeleton-icon"></span>
          <div class="card-titles flex flex-1 flex-col gap-2">
            <span class="skeleton skeleton-title"></span>
            <span class="skeleton skeleton-subtitle"></span>
          </div>
          <span class="skeleton skeleton-badge"></span>
        </header>
        <div class="card-body text-inherit">
          <div class="skeleton-row skeleton"></div>
          <div class="skeleton-row skeleton"></div>
          <div class="skeleton-row skeleton short"></div>
        </div>
        @if (hasFooter) {
          <footer class="card-footer mt-4 flex items-center gap-3 border-t border-[var(--ms-color-border,#d1d5db)] pt-3">
            <span class="skeleton skeleton-footer"></span>
          </footer>
        }
      } @else {
        @if (title || badge) {
          <header class="card-header mb-3 flex items-start gap-2">
            @if (titleIcon) {
              <mat-icon
                class="card-title-icon"
                [attr.aria-label]="titleIconLabel || title"
                [attr.aria-hidden]="titleIconLabel || title ? null : true">
                {{ titleIcon }}
              </mat-icon>
            }
            <div class="card-titles flex flex-col gap-0.5">
              @if (title) {
                <h3 class="card-title m-0">{{ title }}</h3>
              }
              @if (subtitle) {
                <p class="card-subtitle m-0">{{ subtitle }}</p>
              }
            </div>
            @if (badge) {
              <span class="card-badge ml-auto rounded-full px-3 py-1 text-[0.85rem] font-bold">{{ badge }}</span>
            }
          </header>
        }

        <div class="card-body text-inherit">
          <ng-content></ng-content>
        </div>

        @if (hasFooter) {
          <footer class="card-footer mt-4 flex items-center gap-3 border-t border-[var(--ms-color-border,#d1d5db)] pt-3">
            <ng-content select="[card-footer]"></ng-content>
          </footer>
        }
      }
    </section>
    `,
  styles: [`
    .brand-card {
      background: var(--mat-sys-surface, var(--ms-color-background, #ffffff));
      color: var(--mat-sys-on-surface, var(--ms-color-ink, #111827));
      box-shadow: none;
      border: 1px solid var(--mat-sys-outline, var(--ms-color-border, #d1d5db));
    }

    .brand-card.border {
      border-color: var(--ms-color-border, #d1d5db);
    }

    .card-title {
      font-size: 1.1rem;
      font-weight: 700;
      color: var(--mat-sys-primary, var(--ms-color-primary, #0b4f8a));
      letter-spacing: 0.01em;
    }

    .card-title-icon {
      margin-top: 2px;
      color: currentColor;
      font-size: 24px;
      line-height: 1;
      font-variation-settings: 'FILL' 1, 'wght' 600, 'GRAD' 0, 'opsz' 24;
    }

    .card-subtitle {
      font-size: 0.95rem;
      color: var(--mat-sys-on-surface-variant, var(--ms-color-muted, #6b7280));
    }

    .card-badge {
      background: var(--ms-color-info-blue-light, #e1f3ff);
      color: var(--mat-sys-primary, var(--ms-color-primary, #0b4f8a));
    }

    /* Utility styles inspired by agency card */
    .section-label {
      font-size: 0.9rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--mat-sys-primary, #0b4f8a);
    }

    .feed-type-chip {
      font-weight: 600;
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

    .card-body {
      color: inherit;
    }

    .is-loading .card-body,
    .is-loading .card-header,
    .is-loading .card-footer {
      pointer-events: none;
    }

    .skeleton {
      display: block;
      position: relative;
      overflow: hidden;
      border-radius: 999px;
      background: linear-gradient(
        90deg,
        rgba(203, 213, 225, 0.35) 0%,
        rgba(203, 213, 225, 0.7) 45%,
        rgba(203, 213, 225, 0.35) 100%
      );
      background-size: 220% 100%;
      animation: skeleton-shimmer 1.4s ease-in-out infinite;
    }

    .skeleton-row {
      height: 12px;
      border-radius: 6px;
      margin-bottom: 12px;
    }

    .skeleton-row.short {
      width: 60%;
    }

    .skeleton-icon {
      width: 24px;
      height: 24px;
      border-radius: 8px;
      margin-top: 2px;
    }

    .skeleton-title {
      height: 16px;
      width: 60%;
      border-radius: 8px;
    }

    .skeleton-subtitle {
      height: 12px;
      width: 45%;
      border-radius: 8px;
    }

    .skeleton-badge {
      height: 22px;
      width: 72px;
      margin-left: auto;
      border-radius: 999px;
    }

    .skeleton-footer {
      height: 14px;
      width: 30%;
      border-radius: 8px;
    }

    @keyframes skeleton-shimmer {
      0% {
        background-position: 0% 0%;
      }
      100% {
        background-position: 200% 0%;
      }
    }

    :host-context(.dark-theme) .brand-card {
      background: var(--mat-sys-surface, var(--ms-color-surface-elevated, #0f172a));
      color: var(--mat-sys-on-surface, var(--ms-color-text-primary, #e5f1ff));
      border-color: var(--mat-sys-outline, rgba(148, 163, 184, 0.24));
      box-shadow: 0 12px 30px rgba(0, 0, 0, 0.35);
    }

    :host-context(.dark-theme) .card-title {
      color: var(--mat-sys-on-surface, var(--ms-color-text-primary, #e5f1ff));
    }

    :host-context(.dark-theme) .card-subtitle {
      color: var(--mat-sys-on-surface-variant, var(--ms-color-text-secondary, #94a3b8));
    }

    :host-context(.dark-theme) .card-badge {
      background: rgba(0, 167, 196, 0.18);
      color: var(--mat-sys-on-surface, var(--ms-color-text-primary, #e5f1ff));
    }

    :host-context(.dark-theme) .card-footer {
      border-top-color: rgba(148, 163, 184, 0.24);
    }

    :host-context(.dark-theme) .skeleton {
      background: linear-gradient(
        90deg,
        rgba(30, 41, 59, 0.8) 0%,
        rgba(51, 65, 85, 0.9) 45%,
        rgba(30, 41, 59, 0.8) 100%
      );
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BrandCardComponent {
  @Input() title?: string;
  @Input() subtitle?: string;
  @Input() badge?: string;
  @Input() titleIcon?: string;
  @Input() titleIconLabel?: string;
  @Input() border = true;
  @Input() hasFooter = false;
  @Input() loading = false;
}
