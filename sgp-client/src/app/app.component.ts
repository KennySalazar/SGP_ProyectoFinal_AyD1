import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ApiErrorService } from './core/services/api-error.service';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, TranslocoPipe],
  template: `
    @if (errors.lastMessage(); as message) {
      <div class="global-error" role="alert">
        <span>{{ message }}</span>
        <button
          type="button"
          (click)="errors.clear()"
          [attr.aria-label]="'common.closeMessage' | transloco"
        >
          ×
        </button>
      </div>
    }
    <router-outlet />
  `,
  styles: [
    `
      .global-error {
        position: fixed;
        z-index: 1000;
        top: 1rem;
        right: 1rem;
        max-width: 32rem;
        display: flex;
        gap: 1rem;
        align-items: center;
        padding: 0.9rem 1rem;
        border: 1px solid #fecaca;
        border-radius: 0.8rem;
        background: #fff1f2;
        color: #9f1239;
        box-shadow: 0 12px 35px #0f172a22;
      }
      button {
        border: 0;
        background: transparent;
        cursor: pointer;
        font-size: 1.4rem;
        color: inherit;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
  readonly errors = inject(ApiErrorService);
}
