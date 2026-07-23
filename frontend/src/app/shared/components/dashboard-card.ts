import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-dashboard-card',
  template: `
    <article class="dashboard-card" [class.dashboard-card--accent]="accent">
      <header>
        <div>
          @if (eyebrow) {
            <p class="dashboard-card__eyebrow">{{ eyebrow }}</p>
          }
          <h2>{{ title }}</h2>
        </div>
        <ng-content select="[card-icon]" />
      </header>
      <div class="dashboard-card__content"><ng-content /></div>
      <footer><ng-content select="[card-footer]" /></footer>
    </article>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    .dashboard-card {
      display: flex;
      min-height: 100%;
      flex-direction: column;
      padding: 1.15rem;
      border: 1px solid var(--border);
      border-radius: var(--radius-lg);
      background: linear-gradient(145deg, rgb(23 34 53 / 0.92), rgb(17 26 43 / 0.96));
      box-shadow: 0 12px 30px rgb(0 0 0 / 0.17);
      transition:
        border-color 160ms ease,
        transform 160ms ease;
    }
    .dashboard-card:hover {
      border-color: rgb(77 141 255 / 0.45);
      transform: translateY(-2px);
    }
    .dashboard-card--accent {
      border-top-color: var(--accent);
    }
    header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
    }
    h2 {
      margin: 0;
      font-size: 0.98rem;
      letter-spacing: 0;
    }
    .dashboard-card__eyebrow {
      margin: 0 0 0.25rem;
      color: var(--text-muted);
      font-size: 0.68rem;
      font-weight: 800;
      letter-spacing: 0.09em;
      text-transform: uppercase;
    }
    .dashboard-card__content {
      flex: 1;
      margin-top: 0.8rem;
    }
    footer:empty {
      display: none;
    }
    footer {
      margin-top: 1rem;
      padding-top: 0.8rem;
      border-top: 1px solid rgb(41 54 75 / 0.7);
    }
  `,
})
export class DashboardCard {
  @Input({ required: true }) title = '';
  @Input() eyebrow = '';
  @Input() accent = false;
}
