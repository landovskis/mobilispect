import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

import {
  CommonSectionDto,
  CombinedFrequencyDto,
} from '../../services/common-section.service';

@Component({
  selector: 'app-common-section-display',
  standalone: true,
  imports: [],
  templateUrl: './common-section-display.component.html',
  styleUrls: ['./common-section-display.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommonSectionDisplayComponent {
  @Input() sections: CommonSectionDto[] = [];
  @Input() combined: Record<string, CombinedFrequencyDto> | null = null;
}
