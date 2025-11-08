import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-toolbar-nav',
  standalone: true,
  imports: [CommonModule, MatButtonModule],
  template: `
    <div class="nav-links" *ngIf="navItems?.length">
      <button
        mat-button
        *ngFor="let item of navItems"
        [disabled]="item.active"
        [class.active]="item.active"
        (click)="onNavClick(item)"
      >
        {{ item.label }}
      </button>
    </div>
  `,
  styles: [`
    :host {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      width: 100%;
      position: relative;
    }

    .nav-links {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .nav-links button {
      color: rgba(255, 255, 255, 0.9);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      font-weight: 700;
      border-radius: 999px;
      padding: 8px 20px;
      border: 1px solid rgba(255, 255, 255, 0.35);
      background-color: rgba(255, 255, 255, 0.08);
      backdrop-filter: blur(6px);
      transition:
        background-color 0.2s ease,
        color 0.2s ease,
        border-color 0.2s ease,
        box-shadow 0.2s ease;
    }

    .nav-links button:not(.active):hover {
      background-color: rgba(255, 255, 255, 0.18);
      border-color: rgba(255, 255, 255, 0.6);
    }

    .nav-links button.active {
      background-color: #fff;
      color: #1e3a8a;
      border-color: #fff;
      box-shadow: 0 6px 20px rgba(0, 0, 0, 0.25);
    }

    @media (max-width: 768px) {
      .nav-links {
        display: none;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppToolbarNavComponent {
  @Input() navItems: ToolbarNavItem[] = [];
  @Output() navSelected = new EventEmitter<ToolbarNavItem>();

  onNavClick(item: ToolbarNavItem): void {
    this.navSelected.emit(item);
  }
}

export interface ToolbarNavItem {
  label: string;
  active?: boolean;
}
