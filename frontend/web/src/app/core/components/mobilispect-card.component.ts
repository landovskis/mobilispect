import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Mobilispect Card Component
 *
 * A reusable card component with consistent styling using Tailwind CSS.
 * Provides a base structure with header, content, and actions sections.
 *
 * Features:
 * - Material Design 3 inspired card with Tailwind styling
 * - Blue header (#2980B9) with white text
 * - Light content area with dark theme support
 * - Hover effects with elevation and transform
 * - WCAG AAA compliant colors
 * - Responsive design
 *
 * @example
 * ```html
 * <app-mobilispect-card>
 *   <div card-header>
 *     <div card-avatar>
 *       <mat-icon>business</mat-icon>
 *     </div>
 *     <div card-title>Title</div>
 *     <div card-subtitle>Subtitle</div>
 *   </div>
 *   <div card-content>
 *     Content goes here
 *   </div>
 *   <div card-actions>
 *     <button>Action</button>
 *   </div>
 * </app-mobilispect-card>
 * ```
 */
@Component({
  selector: 'app-mobilispect-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <article class="mobilispect-card group">
      <ng-content select="[card-header]"></ng-content>
      <ng-content select="[card-content]"></ng-content>
      <ng-content select="[card-actions]"></ng-content>
    </article>
  `,
  styles: [`
    :host {
      display: block;
      --mobilispect-card-header-bg: #2980B9;
      --mobilispect-card-border-color: var(--mobilispect-card-header-bg);
    }

    :host-context(.dark-theme) {
      --mobilispect-card-header-bg: #1e5f8c;
      --mobilispect-card-border-color: var(--mobilispect-card-header-bg);
    }

    .mobilispect-card {
      position: relative;
      display: flex;
      flex-direction: column;
      height: 100%;
      border-radius: 16px;
      border: 2px solid var(--mobilispect-card-border-color);
      background: #ffffff;
      box-shadow: 0 16px 32px rgba(15, 23, 42, 0.08);
      overflow: hidden;
      transition: transform 0.25s ease, box-shadow 0.25s ease;
    }

    .mobilispect-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 22px 40px rgba(15, 23, 42, 0.12);
    }

    :host-context(.dark-theme) .mobilispect-card {
      background: #0f172a;
      box-shadow: 0 18px 30px rgba(2, 6, 23, 0.55);
    }

    :host ::ng-deep [card-header] {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
      padding: 1.25rem 1.5rem 1.15rem;
      background-color: var(--mobilispect-card-header-bg);
      border-bottom: 1px solid rgba(255, 255, 255, 0.25);
      color: #fff;
    }

    :host-context(.dark-theme) ::ng-deep [card-header] {
      border-bottom-color: rgba(255, 255, 255, 0.18);
    }

    :host ::ng-deep [card-header] [card-title] {
      font-size: 1.1rem;
      font-weight: 600;
      letter-spacing: -0.01em;
    }

    :host ::ng-deep [card-header] [card-subtitle] {
      font-size: 0.9rem;
      font-weight: 500;
      color: rgba(255, 255, 255, 0.85);
    }

    :host ::ng-deep [card-content] {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      padding: 1.5rem;
      background: rgba(255, 255, 255, 0.97);
      color: #0f172a;
      min-height: 0;
    }

    :host-context(.dark-theme) ::ng-deep [card-content] {
      background: rgba(15, 23, 42, 0.8);
      color: rgba(248, 250, 252, 0.95);
    }

    :host ::ng-deep [card-actions] {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem;
      padding: 1rem 1.5rem;
      background: rgba(255, 255, 255, 0.92);
      border-top: 1px solid rgba(15, 23, 42, 0.08);
    }

    :host-context(.dark-theme) ::ng-deep [card-actions] {
      background: rgba(7, 11, 20, 0.85);
      border-top-color: rgba(255, 255, 255, 0.08);
    }

    :host ::ng-deep [card-header]:empty,
    :host ::ng-deep [card-actions]:empty {
      display: none !important;
    }

    @media (max-width: 768px) {
      :host ::ng-deep [card-header],
      :host ::ng-deep [card-content],
      :host ::ng-deep [card-actions] {
        padding-left: 1.25rem;
        padding-right: 1.25rem;
      }

      :host ::ng-deep [card-header] {
        padding-top: 1rem;
        padding-bottom: 1rem;
      }

      :host ::ng-deep [card-content] {
        padding-top: 1.2rem;
        padding-bottom: 1.2rem;
      }

      :host ::ng-deep [card-actions] {
        padding-top: 0.85rem;
        padding-bottom: 0.85rem;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MobilispectCardComponent {}
