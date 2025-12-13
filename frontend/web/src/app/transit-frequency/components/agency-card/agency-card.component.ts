import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AgencyDTO } from '../../models/agency.model';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';
import { BrandBadgeComponent } from '../../../shared/components/brand-badge.component';

@Component({
  selector: 'app-agency-card',
  standalone: true,
  imports: [CommonModule, BrandCardComponent, BrandBadgeComponent],
  template: `
    <app-brand-card [title]="agency.name" [badge]="agency.routeCount + ' routes'">
      <div class="meta" aria-label="agency details" role="list">
        <app-brand-badge
          role="listitem"
          variant="neutral"
          [label]="agency.activeRouteCount + ' active'"></app-brand-badge>
        @if (agency.routesByType && hasRoutes) {
          <div class="route-types">
            @for (type of routeTypes; track type) {
              @if (agency.routesByType[type] > 0) {
                <app-brand-badge
                  role="listitem"
                  variant="neutral"
                  [label]="formatRouteType(type) + ': ' + agency.routesByType[type]"></app-brand-badge>
              }
            }
          </div>
        }
      </div>
    </app-brand-card>
  `,
  styles: [`
    .meta {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .route-types {
      display: flex;
      gap: 4px;
      flex-wrap: wrap;
    }

    :host-context(.dark-theme) app-brand-card {
      color: var(--mat-sys-on-surface, #e5e7eb);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AgencyCardComponent {
  @Input() agency!: AgencyDTO;

  get hasRoutes(): boolean {
    return Object.values(this.agency.routesByType || {}).some(count => count > 0);
  }

  get routeTypes(): string[] {
    return Object.keys(this.agency.routesByType || {});
  }

  formatRouteType(type: string): string {
    return type.charAt(0).toUpperCase() + type.slice(1).toLowerCase();
  }
}
