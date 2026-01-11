import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

type BrandButtonVariant = 'primary' | 'accent' | 'ghost' | 'destructive';
type BrandButtonSize = 'sm' | 'md';

@Component({
  selector: 'app-brand-button',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button
      type="button"
      class="brand-button inline-flex items-center justify-center gap-2 rounded-[10px] border font-bold tracking-[0.01em]"
      [ngClass]="[
        variant,
        size === 'sm'
          ? 'px-3 py-2 text-sm rounded-lg'
          : 'px-4 py-2.5 text-base',
        block ? 'w-full' : '',
      ]"
      [disabled]="disabled"
    >
      <ng-content></ng-content>
    </button>
  `,
  styles: [
    `
      .brand-button {
        --btn-bg: var(--ms-color-primary, #0b4f8a);
        --btn-text: #ffffff;
        --btn-border: transparent;
        border: 1px solid var(--btn-border);
        background: var(--btn-bg);
        color: var(--btn-text);
        font-family: var(--ms-font-family, system-ui, sans-serif);
        cursor: pointer;
        transition:
          transform 120ms ease,
          box-shadow 150ms ease,
          background 150ms ease,
          color 150ms ease,
          border-color 150ms ease;
        box-shadow: 0 10px 20px rgba(11, 79, 138, 0.12);
      }

      .brand-button:hover:not(:disabled) {
        transform: translateY(-1px);
        box-shadow: 0 12px 24px rgba(0, 167, 196, 0.18);
      }

      .brand-button:active:not(:disabled) {
        transform: translateY(0);
        box-shadow: 0 8px 18px rgba(11, 79, 138, 0.14);
      }

      .brand-button:focus-visible {
        outline: 2px solid var(--ms-color-station-yellow, #ffd54f);
        outline-offset: 3px;
      }

      .brand-button.primary {
        --btn-bg: var(--ms-color-primary, #0b4f8a);
        --btn-text: #ffffff;
        --btn-border: transparent;
      }

      .brand-button.accent {
        --btn-bg: var(--ms-color-primary-cyan, #00a7c4);
        --btn-text: #02131f;
        --btn-border: transparent;
      }

      .brand-button.ghost {
        --btn-bg: transparent;
        --btn-text: var(--ms-color-primary, #0b4f8a);
        --btn-border: var(--ms-color-border, #d1d5db);
        box-shadow: none;
      }

      .brand-button.destructive {
        --btn-bg: #dc2626;
        --btn-text: #ffffff;
        --btn-border: transparent;
        box-shadow: 0 10px 20px rgba(220, 38, 38, 0.12);
      }

      .brand-button.destructive:hover:not(:disabled) {
        box-shadow: 0 12px 24px rgba(220, 38, 38, 0.18);
      }

      .brand-button.destructive:active:not(:disabled) {
        box-shadow: 0 8px 18px rgba(220, 38, 38, 0.14);
      }

      .brand-button:disabled {
        opacity: 0.6;
        cursor: not-allowed;
        box-shadow: none;
      }

      :host-context(.dark-theme) .brand-button.ghost {
        --btn-text: var(--ms-color-text-primary, #e5f1ff);
        --btn-border: rgba(229, 241, 255, 0.25);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BrandButtonComponent {
  @Input() variant: BrandButtonVariant = 'primary';
  @Input() size: BrandButtonSize = 'md';
  @Input() block = false;
  @Input() disabled = false;
}
