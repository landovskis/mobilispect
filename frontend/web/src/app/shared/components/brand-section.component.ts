import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';

/**
 * Brand-styled section wrapper with optional collapsible behavior.
 */
@Component({
  selector: 'app-brand-section',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    <section class="brand-section" [class.collapsible]="collapsible">
      <header class="section-header">
        <div class="icon-wrap" *ngIf="icon">
          <mat-icon>{{ icon }}</mat-icon>
        </div>
        <div class="section-titles">
          <h3 class="section-title" *ngIf="title">{{ title }}</h3>
          <p class="section-subtitle" *ngIf="subtitle">{{ subtitle }}</p>
        </div>

        <div class="section-actions">
          <ng-content select="[section-actions]"></ng-content>
          <button
            *ngIf="collapsible"
            type="button"
            class="toggle"
            (click)="toggle()"
            [attr.aria-expanded]="expanded"
            [attr.aria-label]="expanded ? 'Collapse section' : 'Expand section'">
            <mat-icon [class.rotated]="expanded">expand_more</mat-icon>
          </button>
        </div>
      </header>

      <div class="section-body" *ngIf="!collapsible || expanded">
        <ng-content></ng-content>
      </div>
    </section>
  `,
  styles: [`
    .brand-section {
      background: var(--mat-sys-surface, #ffffff);
      border: 1px solid var(--mat-sys-outline, #d1d5db);
      border-radius: 16px;
      padding: 16px 20px;
      display: block;
    }

    .section-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 12px;
    }

    .icon-wrap {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: rgba(11, 79, 138, 0.1);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: var(--mat-sys-primary, #0b4f8a);
    }

    .section-titles {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .section-title {
      margin: 0;
      font-weight: 700;
      color: var(--mat-sys-primary, #0b4f8a);
      font-size: 1rem;
    }

    .section-subtitle {
      margin: 0;
      color: var(--mat-sys-on-surface-variant, #475569);
      font-size: 0.95rem;
    }

    .section-actions {
      margin-left: auto;
      display: inline-flex;
      align-items: center;
      gap: 8px;
    }

    .toggle {
      border: none;
      background: rgba(15, 118, 178, 0.08);
      color: var(--mat-sys-primary, #0b4f8a);
      border-radius: 10px;
      padding: 6px 8px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
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
