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
    <div class="accordion flex flex-col gap-2" role="tablist">
      @for (item of items; let i = $index; track item.id) {
        <section
          class="accordion-item rounded-[14px] border border-[var(--ms-color-border,#d1d5db)] bg-white shadow-[0_8px_18px_rgba(11,79,138,0.06)]"
          [class.open]="item.open"
          [class.disabled]="item.disabled"
          >
          <button
            type="button"
            class="accordion-trigger flex w-full items-center justify-between gap-3 rounded-[14px] px-6 py-4 text-left"
            [attr.aria-expanded]="!!item.open"
            [attr.aria-controls]="item.id + '-panel'"
            [id]="item.id + '-header'"
            [disabled]="item.disabled"
            (click)="toggleItem(i)"
            >
            <div class="header-text flex min-w-0 flex-col gap-1">
              <span class="title">{{ item.title }}</span>
              @if (item.subtitle) {
                <span class="subtitle">{{ item.subtitle }}</span>
              }
            </div>
            <div class="header-meta ml-auto flex items-center gap-2.5">
              @if (item.badge) {
                <span class="badge rounded-full px-2.5 py-1 text-[0.85rem] font-bold">{{ item.badge }}</span>
              }
              <span class="chevron inline-block h-3 w-3 border-b-2 border-r-2" aria-hidden="true"></span>
            </div>
          </button>

          <div
            class="accordion-panel px-6 pb-4"
            role="region"
            [id]="item.id + '-panel'"
            [attr.aria-labelledby]="item.id + '-header'"
            [hidden]="!item.open"
            >
            @if (itemTemplate) {
              <ng-container [ngTemplateOutlet]="itemTemplate" [ngTemplateOutletContext]="{ item: item }"></ng-container>
            } @else {
              @if (item.content) {
                <p class="panel-text m-0">{{ item.content }}</p>
              }
            }
          </div>
        </section>
      }
    </div>
    `,
  styles: [`
    .accordion-item {
      background: #ffffff;
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
      border: none;
      background: transparent;
      cursor: pointer;
      color: var(--ms-color-ink, #111827);
      font-family: var(--ms-font-family, system-ui, sans-serif);
    }

    .accordion-trigger:focus-visible {
      outline: 2px solid var(--ms-color-station-yellow, #ffd54f);
      outline-offset: 3px;
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

    .badge {
      background: var(--ms-color-info-blue-light, #e1f3ff);
      color: var(--ms-color-primary, #0b4f8a);
    }

    .chevron {
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
      color: var(--ms-color-ink, #111827);
    }

    .panel-text {
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
