import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-marcador-observaciones',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (interactivo()) {
      <button type="button" class="marcador" (click)="abrir.emit()">
        <i class="pi pi-comments" aria-hidden="true"></i>
        <span>{{
          (cantidad() === 1 ? 'observaciones.una' : 'observaciones.cantidad')
            | transloco: { cantidad: cantidad() }
        }}</span>
      </button>
    } @else {
      <span class="marcador">
        <i class="pi pi-comments" aria-hidden="true"></i>
        <span>{{
          (cantidad() === 1 ? 'observaciones.una' : 'observaciones.cantidad')
            | transloco: { cantidad: cantidad() }
        }}</span>
      </span>
    }
  `,
  styles: `
    :host {
      display: inline-block;
      max-width: 100%;
    }
    .marcador {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      min-width: 44px;
      min-height: 44px;
      max-width: 100%;
      box-sizing: border-box;
      overflow-wrap: anywhere;
      padding: 0.35rem 0.65rem;
      border: 1px solid var(--sgp-border);
      border-radius: 12px;
      background: var(--sgp-surface-soft);
      color: var(--sgp-navy-900);
      font: inherit;
    }
    button {
      cursor: pointer;
    }
    button:focus-visible {
      outline: 3px solid var(--sgp-blue-600);
      outline-offset: 2px;
    }
  `,
})
export class MarcadorObservacionesComponent {
  readonly cantidad = input(0);
  /** Pasivo por defecto: incorporarlo no habilita acciones en pantallas existentes. */
  readonly interactivo = input(false);
  readonly abrir = output<void>();
}
