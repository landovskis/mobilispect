import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTabsModule } from '@angular/material/tabs';

@Component({
  selector: 'app-brand-tabs',
  standalone: true,
  imports: [CommonModule, MatTabsModule],
  template: `
    <mat-tab-group
      class="brand-tabs"
      [selectedIndex]="selectedIndex"
      (selectedIndexChange)="selectedIndexChange.emit($event)"
    >
      @for (label of tabs; track label) {
        <mat-tab [label]="label"></mat-tab>
      }
    </mat-tab-group>
  `,
  styles: [
    `
      .brand-tabs {
        --mat-tab-header-active-label-text-color: var(
          --mat-sys-primary,
          #0b4f8a
        );
        --mat-tab-header-active-focus-label-text-color: var(
          --mat-sys-primary,
          #0b4f8a
        );
        --mat-tab-header-active-hover-label-text-color: var(
          --mat-sys-primary,
          #0b4f8a
        );
        --mat-tab-header-active-ripple-color: var(--mat-sys-primary, #0b4f8a);
        --mat-tab-header-inactive-label-text-color: var(
          --mat-sys-on-surface-variant,
          #64748b
        );
        --mat-tab-header-inactive-hover-label-text-color: var(
          --mat-sys-primary,
          #0b4f8a
        );
        --mat-tab-header-active-indicator-color: var(
          --mat-sys-primary,
          #0b4f8a
        );
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BrandTabsComponent {
  @Input() tabs: string[] = [];
  @Input() selectedIndex = 0;
  @Output() selectedIndexChange = new EventEmitter<number>();
}
