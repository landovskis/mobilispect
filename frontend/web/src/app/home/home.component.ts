import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="home-container">
      <h1>Mobilispect</h1>
      <p>Welcome to the Mobilispect application. The system is currently running.</p>
      <div class="status">
        <h2>System Status</h2>
        <p>✅ Frontend: Running on port 4200</p>
        <p>✅ Backend: Running on port 8080</p>
      </div>
    </div>
  `,
  styles: [`
    .home-container {
      padding: 2rem;
      max-width: 800px;
      margin: 0 auto;
    }

    h1 {
      color: #1976d2;
      margin-bottom: 1rem;
    }

    .status {
      margin-top: 2rem;
      padding: 1rem;
      background: #f5f5f5;
      border-radius: 4px;
    }

    .status h2 {
      margin-top: 0;
    }
  `]
})
export class HomeComponent {
}
