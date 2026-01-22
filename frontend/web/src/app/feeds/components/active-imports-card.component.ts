import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { FeedImportSummary } from '../models';
import { BrandSectionComponent } from '../../shared/components/brand-section.component';
import { ActiveImportCardComponent } from './active-import-card.component';

/**
 * Active Imports Card Component
 *
 * Displays currently running imports in a card with real-time progress monitoring
 * and individual cancellation capabilities.
 *
 * @example
 * ```html
 * <app-active-imports-card
 *   [activeImports$]="activeImports$"
 *   (cancelImport)="cancelOne($event)">
 * </app-active-imports-card>
 * ```
 */
@Component({
  selector: 'app-active-imports-card',
  standalone: true,
  imports: [
    CommonModule,
    MatIconModule,
    BrandSectionComponent,
    ActiveImportCardComponent,
  ],
  template: `
    <app-brand-section
      class="active-imports-panel mb-6 block"
      title="Active Imports"
      subtitle="Running feed imports with real-time progress"
      icon="downloading"
      [collapsible]="true"
      [(expanded)]="isExpanded"
    >
      <div
        section-actions
        class="panel-actions inline-flex items-center gap-2.5"
      >
        @if (activeImports$ | async; as activeImports) {
          <span class="count-badge rounded-full px-2.5 py-1">{{
            activeImports.length
          }}</span>
        }
      </div>

      <!-- Active imports list -->
      @if (activeImports$ | async; as activeImports) {
        @if (activeImports.length > 0) {
          <div class="active-imports-list flex flex-col gap-4 p-1 max-md:p-2">
            @for (importItem of activeImports; track importItem.id) {
              <app-active-import-item
                [importItem]="importItem"
                (cancelImport)="onCancelImport($event)"
              />
            }
          </div>
        } @else {
          <div
            class="empty-state flex flex-col items-center gap-1.5 p-6 text-center"
          >
            <mat-icon class="empty-icon">cloud_done</mat-icon>
            <p class="empty-title m-0">No active imports</p>
            <p class="empty-subtitle max-w-[340px] m-0">
              Select a region to see its imports here.
            </p>
          </div>
        }
      }
    </app-brand-section>
  `,
  styles: [
    `
      .count-badge {
        background: var(--mat-sys-surface-variant, #e2e8f0);
        color: var(--mat-sys-primary, #0b4f8a);
        font-weight: 700;
        font-size: 0.85rem;
      }
      .empty-state {
        color: var(--mat-sys-on-surface-variant, #475569);
      }
      .empty-icon {
        font-size: 48px;
        width: 48px;
        height: 48px;
        color: #94a3b8;
      }
      .empty-title {
        font-weight: 700;
        color: var(--mat-sys-on-surface, #0f172a);
      }
      .empty-subtitle {
        color: var(--mat-sys-on-surface-variant, #475569);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActiveImportsCardComponent {
  @Input() activeImports$: Observable<FeedImportSummary[]> | null = null;

  @Output() cancelImport = new EventEmitter<string>();

  isExpanded = true;

  onCancelImport(id: string): void {
    this.cancelImport.emit(id);
  }
}
