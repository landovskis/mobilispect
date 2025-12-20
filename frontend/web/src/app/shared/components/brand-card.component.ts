import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-brand-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="brand-card" [class.border]="border">
      <header *ngIf="title || badge" class="card-header">
        <div class="card-titles">
          <h3 class="card-title" *ngIf="title">{{ title }}</h3>
          <p class="card-subtitle" *ngIf="subtitle">{{ subtitle }}</p>
        </div>
        <span *ngIf="badge" class="card-badge">{{ badge }}</span>
      </header>

      <div class="card-body">
        <ng-content></ng-content>
      </div>

      <footer class="card-footer" *ngIf="hasFooter">
        <ng-content select="[card-footer]"></ng-content>
      </footer>
    </section>
  `,
  styles: [`
    .brand-card {
      background: var(--mat-sys-surface, var(--ms-color-background, #ffffff));
      color: var(--mat-sys-on-surface, var(--ms-color-ink, #111827));
      border-radius: 16px;
      padding: var(--ms-space-5, 24px);
      box-shadow: none;
      border: 1px solid var(--mat-sys-outline, var(--ms-color-border, #d1d5db));
      display: block;
    }

    .brand-card.border {
      border-color: var(--ms-color-border, #d1d5db);
    }

    .card-header {
      display: flex;
      align-items: flex-start;
      gap: var(--ms-space-2, 8px);
      margin-bottom: var(--ms-space-3, 12px);
    }

    .card-titles {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .card-title {
      margin: 0;
      font-size: 1.1rem;
      font-weight: 700;
      color: var(--mat-sys-primary, var(--ms-color-primary, #0b4f8a));
      letter-spacing: 0.01em;
    }

    .card-subtitle {
      margin: 0;
      font-size: 0.95rem;
      color: var(--mat-sys-on-surface-variant, var(--ms-color-muted, #6b7280));
    }

    .card-badge {
      margin-left: auto;
      padding: 4px 12px;
      border-radius: 999px;
      background: var(--ms-color-info-blue-light, #e1f3ff);
      color: var(--mat-sys-primary, var(--ms-color-primary, #0b4f8a));
      font-weight: 700;
      font-size: 0.85rem;
    }

    /* Utility styles inspired by agency card */
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

    .chips {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
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

    .card-body {
      display: block;
      color: inherit;
    }

    .card-footer {
      margin-top: var(--ms-space-4, 16px);
      padding-top: var(--ms-space-3, 12px);
      border-top: 1px solid var(--ms-color-border, #d1d5db);
      display: flex;
      align-items: center;
      gap: var(--ms-space-3, 12px);
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
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BrandCardComponent {
  @Input() title?: string;
  @Input() subtitle?: string;
  @Input() badge?: string;
  @Input() border = true;
  @Input() hasFooter = false;
}
