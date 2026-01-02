import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouteDto } from '../../services/frequency.service';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';

@Component({
  selector: 'app-route-summary-card',
  standalone: true,
  template: `
    <app-brand-card
      [loading]="loading"
      [title]="
        route?.shortName && route?.longName
          ? route?.shortName + ': ' + route?.longName
          : route?.longName || route?.shortName || 'Route Details'
      "
    >
    </app-brand-card>
  `,
  styles: [],
  imports: [BrandCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RouteSummaryCardComponent {
  @Input() route?: RouteDto;
  @Input() loading = false;
}
