import { Component } from '@angular/core';
import { AppBarComponent } from '../shared/components/app-bar.component';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [
    AppBarComponent
  ],
  template: `
    <app-bar
      appName="Mobilispect"
      logoUrl="/logo.png"
      [showRefresh]="false"
    ></app-bar>
  `,
  styles: []
})
export class AppHeader {
}
