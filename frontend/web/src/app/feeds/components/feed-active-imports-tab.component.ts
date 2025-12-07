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
    <div class="tab-content">
      @if (activeImports$ | async; as activeImports) {
        @if (activeImports.length > 0) {
          <div class="bulk-actions">
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
          <div class="no-imports">
            <mat-icon class="no-imports-icon">hourglass_empty</mat-icon>
            <p>No active imports at this time.</p>
            <p class="hint">Start an import from the regions view to see real-time progress here.</p>
          </div>
        }

        @for (activeImport of activeImports; track activeImport.id) {
          <div
            class="active-import-card active-import-item"
          >
            <div class="import-header">
              <mat-checkbox
                [checked]="selectedImportIds?.has(activeImport.id)"
                (change)="selectionChange.emit({ id: activeImport.id, selected: $event.checked })"
                class="import-checkbox"
              ></mat-checkbox>
              <h3>
                <mat-icon class="import-icon">download</mat-icon>
                {{ activeImport.feedName }}
              </h3>
              <p class="import-subtitle">
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
  styles: [`
    .tab-content {
      padding: 24px 0;
    }

    .bulk-actions {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px 24px;
      background-color: #f5f5f5;
      border-bottom: 1px solid #e0e0e0;
    }

    .no-imports {
      text-align: center;
      padding: 40px 20px;
      color: #666;
    }

    .no-imports-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: #ccc;
      margin-bottom: 16px;
    }

    .hint {
      font-size: 14px;
      color: #999;
      margin-top: 8px;
    }

    .active-import-card {
      margin-bottom: 16px;
    }

    .import-header {
      display: flex;
      align-items: flex-start;
      gap: 8px;
    }

    .import-checkbox {
      margin-right: 12px;
    }

    .import-header h3 {
      flex: 1;
      margin: 0;
      font-weight: 600;
    }

    .import-subtitle {
      flex: 1;
      margin: 4px 0 0 0;
      color: #666;
    }

    .import-icon {
      margin-right: 8px;
      vertical-align: middle;
    }

    @media (max-width: 768px) {
      .tab-content {
        padding: 16px 0;
      }

      .bulk-actions {
        flex-direction: column;
        align-items: flex-start;
        gap: 12px;
      }

      .import-header {
        flex-direction: column;
        gap: 4px;
      }

      .import-subtitle {
        margin-left: 0;
      }
    }
  `],
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
