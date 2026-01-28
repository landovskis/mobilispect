import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouterModule } from '@angular/router';

import { CorridorDto } from '../../services/corridor.service';

@Component({
  selector: 'app-corridors-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    @if (corridors.length === 0) {
      <p class="empty text-sm">No corridors detected in this region.</p>
    } @else {
      <div class="corridors grid gap-4 md:grid-cols-2">
        @for (corridor of corridors; track corridor.id) {
          <div
            class="corridor-card rounded-xl p-4"
            [attr.aria-label]="
              corridor.stopCount +
              ' stops shared by ' +
              corridor.routes.length +
              ' routes'
            "
          >
            <div
              class="header flex flex-wrap items-center justify-between gap-2"
            >
              <div class="title text-sm font-semibold">
                {{ corridor.stopCount }} shared stops
              </div>
              <div class="chip text-xs">
                {{ corridor.routes.length }} routes
              </div>
            </div>
            <div class="routes-list mt-3 flex flex-wrap gap-2">
              @for (route of corridor.routes; track route.routeId) {
                <a
                  class="route-tag rounded-lg px-2 py-1 text-xs font-medium"
                  [routerLink]="['/routes', route.routeId]"
                  [attr.aria-label]="
                    (route.shortName || route.longName) + ' route details'
                  "
                >
                  {{ route.shortName || route.longName }}
                </a>
              }
            </div>
            <div class="pattern mt-2 text-xs">{{ corridor.stopPattern }}</div>
          </div>
        }
      </div>
    }
  `,
  styles: [
    `
      .corridor-card {
        border: 1px solid var(--mat-sys-outline, #e2e8f0);
        background: var(--mat-sys-surface, #ffffff);
        transition: box-shadow 150ms ease;
      }
      .corridor-card:hover {
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
      }
      .title {
        color: var(--mat-sys-primary, #0b4f8a);
      }
      .chip {
        background: rgba(11, 79, 138, 0.12);
        color: var(--mat-sys-primary, #0b4f8a);
        border-radius: 999px;
        padding: 2px 8px;
      }
      .route-tag {
        background: rgba(11, 79, 138, 0.08);
        color: var(--mat-sys-primary, #0b4f8a);
        text-decoration: none;
        cursor: pointer;
        transition:
          background 150ms ease,
          color 150ms ease;
      }
      .route-tag:hover {
        background: rgba(11, 79, 138, 0.2);
      }
      .pattern {
        color: var(--mat-sys-on-surface-variant, #64748b);
      }
      .empty {
        color: var(--mat-sys-on-surface-variant, #94a3b8);
      }

      :host-context(.dark-theme) .corridor-card {
        border-color: rgba(148, 163, 184, 0.3);
        background: rgba(15, 23, 42, 0.35);
      }
      :host-context(.dark-theme) .corridor-card:hover {
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
      }
      :host-context(.dark-theme) .chip {
        background: rgba(59, 130, 246, 0.2);
        color: #e2e8f0;
      }
      :host-context(.dark-theme) .route-tag {
        background: rgba(59, 130, 246, 0.15);
        color: #93c5fd;
      }
      :host-context(.dark-theme) .route-tag:hover {
        background: rgba(59, 130, 246, 0.3);
      }
      :host-context(.dark-theme) .pattern {
        color: rgba(226, 232, 240, 0.75);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CorridorsListComponent {
  @Input() corridors: CorridorDto[] = [];
}
