import { ChangeDetectionStrategy, Component, Input, TemplateRef } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface BrandAccordionItem {
  id: string;
  title: string;
  subtitle?: string;
  badge?: string;
  content?: string;
  open?: boolean;
  disabled?: boolean;
}

@Component({
  selector: 'app-brand-accordion',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="accordion" role="tablist">
      @for (item of items; let i = $index; track item.id) {
        <section
          class="accordion-item"
          [class.open]="item.open"
          [class.disabled]="item.disabled"
          >
          <button
            type="button"
            class="accordion-trigger"
            [attr.aria-expanded]="!!item.open"
            [attr.aria-controls]="item.id + '-panel'"
            [id]="item.id + '-header'"
            [disabled]="item.disabled"
            (click)="toggleItem(i)"
            >
            <div class="header-text">
              <span class="title">{{ item.title }}</span>
              @if (item.subtitle) {
                <span class="subtitle">{{ item.subtitle }}</span>
              }
            </div>
            <div class="header-meta">
              @if (item.badge) {
                <span class="badge">{{ item.badge }}</span>
              }
              <span class="chevron" aria-hidden="true"></span>
            </div>
          </button>

          <div
            class="accordion-panel"
            role="region"
            [id]="item.id + '-panel'"
            [attr.aria-labelledby]="item.id + '-header'"
            [hidden]="!item.open"
            >
            @if (itemTemplate) {
              <ng-container [ngTemplateOutlet]="itemTemplate" [ngTemplateOutletContext]="{ item: item }"></ng-container>
            } @else {
              @if (item.content) {
                <p class="panel-text">{{ item.content }}</p>
              }
            }
          </div>
        </section>
      }
    </div>
    `,
  styles: [`
    .accordion {
      display: flex;
      flex-direction: column;
      gap: var(--ms-space-2, 8px);
    }

    .accordion-item {
      border: 1px solid var(--ms-color-border, #d1d5db);
      border-radius: 14px;
      background: #ffffff;
      box-shadow: 0 8px 18px rgba(11, 79, 138, 0.06);
      transition: box-shadow 150ms ease, border-color 150ms ease, transform 120ms ease;
    }

    .accordion-item.open {
      border-color: var(--ms-color-primary, #0b4f8a);
      box-shadow: 0 12px 24px rgba(11, 79, 138, 0.18);
      transform: translateY(-1px);
    }

    .accordion-item.disabled {
      opacity: 0.6;
    }

    .accordion-trigger {
      width: 100%;
      border: none;
      background: transparent;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--ms-space-3, 12px);
      padding: var(--ms-space-4, 16px) var(--ms-space-5, 24px);
      text-align: left;
      cursor: pointer;
      border-radius: 14px;
      color: var(--ms-color-ink, #111827);
      font-family: var(--ms-font-family, system-ui, sans-serif);
    }

    .accordion-trigger:focus-visible {
      outline: 2px solid var(--ms-color-station-yellow, #ffd54f);
      outline-offset: 3px;
    }

    .header-text {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: 0;
    }

    .title {
      font-weight: 700;
      font-size: 1rem;
      letter-spacing: 0.01em;
    }

    .subtitle {
      font-size: 0.9rem;
      color: var(--ms-color-muted, #6b7280);
    }

    .header-meta {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-left: auto;
    }

    .badge {
      padding: 4px 10px;
      border-radius: 999px;
      background: var(--ms-color-info-blue-light, #e1f3ff);
      color: var(--ms-color-primary, #0b4f8a);
      font-weight: 700;
      font-size: 0.85rem;
    }

    .chevron {
      display: inline-block;
      width: 12px;
      height: 12px;
      border-right: 2px solid currentColor;
      border-bottom: 2px solid currentColor;
      transform: rotate(45deg);
      transition: transform 150ms ease;
      color: var(--ms-color-muted, #6b7280);
    }

    .accordion-item.open .chevron {
      transform: rotate(225deg);
      color: var(--ms-color-primary, #0b4f8a);
    }

    .accordion-panel {
      padding: 0 var(--ms-space-5, 24px) var(--ms-space-4, 16px);
      color: var(--ms-color-ink, #111827);
    }

    .panel-text {
      margin: 0;
      line-height: 1.5;
      color: var(--ms-color-ink, #111827);
    }

    :host-context(.dark-theme) .accordion-item {
      background: var(--ms-color-surface-elevated, #0f172a);
      border-color: rgba(148, 163, 184, 0.24);
      box-shadow: 0 10px 20px rgba(0, 0, 0, 0.35);
    }

    :host-context(.dark-theme) .accordion-item.open {
      border-color: var(--ms-color-primary-cyan, #00a7c4);
      box-shadow: 0 14px 26px rgba(0, 167, 196, 0.35);
    }

    :host-context(.dark-theme) .accordion-trigger {
      color: var(--ms-color-text-primary, #e5f1ff);
    }

    :host-context(.dark-theme) .subtitle {
      color: var(--ms-color-text-secondary, #94a3b8);
    }

    :host-context(.dark-theme) .badge {
      background: rgba(0, 167, 196, 0.18);
      color: var(--ms-color-text-primary, #e5f1ff);
    }

    :host-context(.dark-theme) .chevron {
      color: var(--ms-color-text-secondary, #94a3b8);
    }

    :host-context(.dark-theme) .accordion-item.open .chevron {
      color: var(--ms-color-primary-cyan, #00a7c4);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BrandAccordionComponent {
  @Input() items: BrandAccordionItem[] = [];
  @Input() allowMultiple = true;
  @Input() itemTemplate?: TemplateRef<{ item: BrandAccordionItem }>;

  toggleItem(index: number): void {
    if (!this.items?.length) return;
    const next = [...this.items];
    if (this.allowMultiple) {
      next[index] = { ...next[index], open: !next[index].open };
    } else {
      next.forEach((item, i) => {
        next[i] = { ...item, open: i === index ? !item.open : false };
      });
    }
    this.items = next;
  }
}
