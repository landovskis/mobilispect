import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { ProgressMonitorComponent } from './progress-monitor.component';
import { FeedImportSummary } from '../models';
import { BrandButtonComponent } from '../../shared/components/brand-button.component';

@Component({
  selector: 'app-feed-active-imports-tab',
  standalone: true,
  imports: [
    CommonModule,
    MatCheckboxModule,
    MatIconModule,
    ProgressMonitorComponent,
    BrandButtonComponent
  ],
  template: `
    <div class="tab-content py-6 max-md:py-4">
      @if (activeImports$ | async; as activeImports) {
        @if (activeImports.length > 0) {
          <div class="bulk-actions flex items-center gap-4 border-b border-[#e0e0e0] bg-[#f5f5f5] px-6 py-4 max-md:flex-col max-md:items-start max-md:gap-3">
            <mat-checkbox
              [checked]="allImportsSelected"
              [indeterminate]="someImportsSelected && !allImportsSelected"
              (change)="selectAllChange.emit($event.checked)"
            >
              Select All
            </mat-checkbox>
            <app-brand-button
              variant="ghost"
              size="sm"
              [disabled]="!selectedImportIds || selectedImportIds.size === 0"
              (click)="bulkCancel.emit()">
              <mat-icon>cancel</mat-icon>
              Cancel Selected ({{ selectedImportIds?.size || 0 }})
            </app-brand-button>
          </div>
        }

        @if (activeImports.length === 0) {
          <div class="no-imports px-5 py-10 text-center text-[#666]">
            <mat-icon class="no-imports-icon mb-4 text-[48px] text-[#ccc]">hourglass_empty</mat-icon>
            <p>No active imports at this time.</p>
            <p class="hint mt-2 text-sm text-[#999]">Start an import from the regions view to see real-time progress here.</p>
          </div>
        }

        @for (activeImport of activeImports; track activeImport.id) {
          <div
            class="active-import-card active-import-item mb-4"
          >
            <div class="import-header flex items-start gap-2 max-md:flex-col max-md:gap-1">
              <mat-checkbox
                [checked]="selectedImportIds?.has(activeImport.id)"
                (change)="selectionChange.emit({ id: activeImport.id, selected: $event.checked })"
                class="import-checkbox mr-3 max-md:mr-0"
              ></mat-checkbox>
              <h3 class="m-0 flex-1 font-semibold">
                <mat-icon class="import-icon mr-2 align-middle">download</mat-icon>
                {{ activeImport.feedName }}
              </h3>
              <p class="import-subtitle m-0 mt-1 flex-1 text-[#666] max-md:mt-0">
                {{ activeImport.regionName }} • Started: {{ activeImport.startedAt | date:'short' }}
              </p>
            </div>

            <app-progress-monitor
              [importId]="activeImport.id"
              [showActions]="true"
              [showConnectionStatus]="false"
              (cancelRequested)="cancelImport.emit($event)"
            ></app-progress-monitor>
          </div>
        }
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FeedActiveImportsTabComponent {
  @Input() activeImports$: Observable<FeedImportSummary[]> | null = null;
  @Input() selectedImportIds: ReadonlySet<string> | null = null;
  @Input() allImportsSelected = false;
  @Input() someImportsSelected = false;

  @Output() selectAllChange = new EventEmitter<boolean>();
  @Output() selectionChange = new EventEmitter<{ id: string; selected: boolean }>();
  @Output() bulkCancel = new EventEmitter<void>();
  @Output() cancelImport = new EventEmitter<string>();
}
