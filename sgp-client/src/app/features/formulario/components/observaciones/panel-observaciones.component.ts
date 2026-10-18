import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import {
  DestinoElementoObservacion,
  ObservacionPresentacion,
} from '../../models/observacion.models';

@Component({
  selector: 'app-panel-observaciones',
  standalone: true,
  imports: [TranslocoPipe, ButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside class="surface-card panel" [attr.aria-label]="'observaciones.panel' | transloco">
      <h3>{{ 'observaciones.panel' | transloco }}</h3>
      <p class="resumen" role="status">
        {{
          'observaciones.resumen'
            | transloco: { total: observaciones().length, pendientes: pendientes() }
        }}
      </p>
      <ul>
        @for (observacion of observaciones(); track observacion.id) {
          <li>
            @let destino = observacion.destino;
            <h4>
              {{
                destino.tipo === 'ELEMENTO'
                  ? destino.etiqueta
                  : ('observaciones.general' | transloco)
              }}
            </h4>
            <p class="texto">{{ observacion.texto }}</p>
            <p class="estado" [attr.data-estado]="observacion.estado">
              <i
                class="pi"
                [class.pi-clock]="observacion.estado === 'PENDIENTE'"
                [class.pi-check-circle]="observacion.estado === 'RESUELTA'"
                [class.pi-comment]="observacion.estado === null"
                aria-hidden="true"
              ></i>
              <span>{{
                (observacion.estado
                  ? 'observaciones.estados.' + observacion.estado
                  : 'observaciones.sinEstadoGeneral'
                ) | transloco
              }}</span>
            </p>
            <div class="acciones">
              @if (destino.tipo === 'ELEMENTO') {
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [outlined]="true"
                  (click)="navegar.emit(destino)"
                  [attr.aria-label]="
                    'observaciones.irA' | transloco: { elemento: destino.etiqueta }
                  "
                >
                  {{ 'observaciones.ir' | transloco }}
                </button>
              }
              @if (permitirEdicion()) {
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [outlined]="true"
                  (click)="editar.emit(observacion)"
                >
                  {{ 'observaciones.editar' | transloco }}
                </button>
              }
              @if (permitirEliminacion()) {
                <button
                  pButton
                  type="button"
                  severity="danger"
                  [outlined]="true"
                  (click)="eliminar.emit(observacion)"
                >
                  {{ 'observaciones.eliminar' | transloco }}
                </button>
              }
            </div>
          </li>
        } @empty {
          <li>{{ 'observaciones.vacio' | transloco }}</li>
        }
      </ul>
    </aside>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    .panel {
      padding: 1rem;
      color: var(--sgp-navy-900);
    }
    h3,
    h4 {
      margin-top: 0;
    }
    ul {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    li {
      padding: 1rem 0;
      border-top: 1px solid var(--sgp-border);
    }
    p,
    h4 {
      overflow-wrap: anywhere;
    }
    .texto {
      white-space: pre-wrap;
    }
    .estado,
    .acciones {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
    }
    .estado {
      color: var(--sgp-steel-700);
    }
    button {
      min-width: 44px;
      min-height: 44px;
    }
  `,
})
export class PanelObservacionesComponent {
  readonly observaciones = input<readonly ObservacionPresentacion[]>([]);
  /** Solo controla la presentación; no sustituye la futura autorización del servidor. */
  readonly permitirEdicion = input(false);
  readonly permitirEliminacion = input(false);
  readonly navegar = output<DestinoElementoObservacion>();
  readonly editar = output<ObservacionPresentacion>();
  readonly eliminar = output<ObservacionPresentacion>();
  protected readonly pendientes = computed(
    () => this.observaciones().filter((observacion) => observacion.estado === 'PENDIENTE').length,
  );
}
