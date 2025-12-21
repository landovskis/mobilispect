import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

import { MatIconModule } from '@angular/material/icon';

/**
 * Brand-styled section wrapper with optional collapsible behavior.
 */
@Component({
  selector: 'app-brand-section',
  standalone: true,
  imports: [MatIconModule],
  template: `
    <section class="brand-section block rounded-2xl px-5 py-4" [class.collapsible]="collapsible">
      <header class="section-header mb-3 flex items-center gap-3">
        @if (icon) {
          <div class="icon-wrap inline-flex h-9 w-9 items-center justify-center rounded-[10px]">
            <mat-icon>{{ icon }}</mat-icon>
          </div>
        }
        <div class="section-titles flex flex-col gap-0.5">
          @if (title) {
            <h3 class="section-title m-0">{{ title }}</h3>
          }
          @if (subtitle) {
            <p class="section-subtitle m-0">{{ subtitle }}</p>
          }
        </div>

        <div class="section-actions ml-auto inline-flex items-center gap-2">
          <ng-content select="[section-actions]"></ng-content>
          @if (collapsible) {
            <button
              type="button"
              class="toggle inline-flex items-center justify-center rounded-[10px] px-2 py-1.5"
              (click)="toggle()"
              [attr.aria-expanded]="expanded"
              [attr.aria-label]="expanded ? 'Collapse section' : 'Expand section'">
              <mat-icon [class.rotated]="expanded">expand_more</mat-icon>
            </button>
          }
        </div>
      </header>

      @if (!collapsible || expanded) {
        <div class="section-body">
          <ng-content></ng-content>
        </div>
      }
    </section>
    `,
  styles: [`
    .brand-section {
      background: var(--mat-sys-surface, #ffffff);
      border: 1px solid var(--mat-sys-outline, #d1d5db);
    }

    .icon-wrap {
      background: rgba(11, 79, 138, 0.1);
      color: var(--mat-sys-primary, #0b4f8a);
    }

    .section-title {
      font-weight: 700;
      color: var(--mat-sys-primary, #0b4f8a);
      font-size: 1rem;
    }

    .section-subtitle {
      color: var(--mat-sys-on-surface-variant, #475569);
      font-size: 0.95rem;
    }

    .toggle {
      border: none;
      background: rgba(15, 118, 178, 0.08);
      color: var(--mat-sys-primary, #0b4f8a);
      cursor: pointer;
    }

    .toggle mat-icon {
      transition: transform 150ms ease;
    }

    .toggle mat-icon.rotated {
      transform: rotate(180deg);
    }

    .section-body {
      color: var(--mat-sys-on-surface, #0f172a);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BrandSectionComponent {
  @Input() title?: string;
  @Input() subtitle?: string;
  @Input() collapsible = false;
  @Input() expanded = true;
  @Input() icon?: string;
  @Output() expandedChange = new EventEmitter<boolean>();

  toggle(): void {
    if (!this.collapsible) return;
    this.expanded = !this.expanded;
    this.expandedChange.emit(this.expanded);
  }
}
