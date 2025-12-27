import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { FeedImportSummary } from '../models';
import { BrandButtonComponent } from '../../shared/components/brand-button.component';
import {BrandCardComponent} from '../../shared/components/brand-card.component';

@Component({
  selector: 'app-active-import-item',
  standalone: true,
  imports: [
    CommonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressBarModule,
    BrandButtonComponent,
    BrandCardComponent
  ],
  template: `
    <app-brand-card
      title="{{ importItem.feedName }}"
      subtitle="{{ importItem.regionName }}"
      badge="{{ importItem.status }}">
      <app-brand-button
        variant="destructive"
        size="sm"
        (click)="onCancelImport()"
        matTooltip="Stop import"
        class="stop-button">
        <mat-icon>stop_circle</mat-icon>
        Stop
      </app-brand-button>
    </app-brand-card>
  `,
  styles: [`
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ActiveImportCardComponent {
  @Input() importItem!: FeedImportSummary;

  @Output() cancelImport = new EventEmitter<string>();

  onCancelImport(): void {
    this.cancelImport.emit(this.importItem.id);
  }
}
