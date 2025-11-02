import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MetropolitanRegion } from '../models/region.models';

@Component({
  selector: 'app-region-selector',
  standalone: true,
  imports: [
    CommonModule,
    MatFormFieldModule,
    MatSelectModule
  ],
  template: `
    <div class="region-selector-container">
      <mat-form-field class="region-selector" appearance="outline">
        <mat-label>Select Region</mat-label>
        <mat-select
          [value]="selectedRegionId"
          (selectionChange)="onRegionChange($event.value)"
          [disabled]="disabled">
          <mat-option *ngFor="let region of regions" [value]="region.regionOnestopId">
            {{ region.name }}
          </mat-option>
        </mat-select>
      </mat-form-field>
    </div>
  `,
  styles: [`
    .region-selector-container {
      padding: 16px 0;
      margin-bottom: 16px;
      border-bottom: 1px solid #e0e0e0;
    }

    :host-context(.dark-theme) .region-selector-container {
      border-bottom-color: rgba(255, 255, 255, 0.12);
    }

    .region-selector {
      width: 300px;
    }

    @media (max-width: 768px) {
      .region-selector {
        width: 100%;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegionSelectorComponent {
  @Input() regions: MetropolitanRegion[] = [];
  @Input() selectedRegionId: string | null = null;
  @Input() disabled = false;

  @Output() regionChange = new EventEmitter<string>();

  onRegionChange(regionId: string): void {
    this.regionChange.emit(regionId);
  }
}
