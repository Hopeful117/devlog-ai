import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-loading-indicator',
  template: `
    <div class="loading-indicator" role="status" aria-live="polite">
      <span class="spinner" aria-hidden="true"></span>
      <span>{{ message }}</span>
    </div>
  `,
  styles: `
    .loading-indicator {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.8rem 1rem;
      border: 1px solid rgb(77 141 255 / 0.42);
      border-radius: var(--radius);
      background: rgb(77 141 255 / 0.09);
      color: var(--text-secondary);
    }
    .spinner {
      width: 1.15rem;
      height: 1.15rem;
      flex: 0 0 auto;
      border: 0.18rem solid rgb(115 167 255 / 0.25);
      border-top-color: var(--accent-hover);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .spinner {
        animation-duration: 1.8s;
      }
    }
  `,
})
export class LoadingIndicator {
  @Input() message = 'Processing…';
}
