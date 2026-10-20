import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { NodoFormulario } from '../../motor/esquema/modelos';
import { DestinoElementoObservacion } from '../../models/observacion.models';
import { anclajeDeControl, CONTEXTO_OBSERVACIONES, mismoAnclaje } from './contexto-observaciones';
import { MarcadorObservacionesComponent } from './marcador-observaciones.component';

@Component({
  selector: 'app-anclaje-observaciones',
  standalone: true,
  imports: [TranslocoPipe, ButtonModule, MarcadorObservacionesComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (contexto; as c) {
    @if (destino(); as d) {
      <div
        class="anclaje"
        tabindex="-1"
        [attr.data-elemento-ref]="d.anclaje.elementoRef"
        [attr.data-instancia-id]="d.anclaje.instanciaElementoId ?? ''"
      >
        @if (cantidad() > 0) {
          <app-marcador-observaciones
            [cantidad]="cantidad()"
            [interactivo]="true"
            (abrir)="c.abrirPanel()"
          />
        }
        @if (c.puedeEscribir()) {
          <button
            pButton
            type="button"
            severity="secondary"
            [outlined]="true"
            (click)="c.seleccionarElemento(d)"
            [attr.aria-label]="
              'revision.detalle.observarElemento' | transloco: { elemento: d.etiqueta }
            "
          >
            <i class="pi pi-comment" aria-hidden="true"></i
            >{{ 'observaciones.crear' | transloco }} · {{ d.etiqueta }}
          </button>
        }
      </div>
    }
  }`,
  styles: `
    :host {
      display: contents;
    }
    .anclaje {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      min-width: 0;
      margin-block: 0.5rem;
    }
    button {
      min-width: 44px;
      min-height: 44px;
      max-width: 100%;
      white-space: normal;
      overflow-wrap: anywhere;
    }
    .anclaje:focus-visible {
      outline: 3px solid var(--sgp-blue-600);
      outline-offset: 2px;
    }
  `,
})
export class AnclajeObservacionesComponent {
  readonly contexto = inject(CONTEXTO_OBSERVACIONES, { optional: true });
  readonly nodo = input.required<NodoFormulario>();
  readonly control = input.required<AbstractControl>();
  readonly destino = computed<DestinoElementoObservacion | null>(() => {
    if (!this.contexto) return null;
    const anclaje = anclajeDeControl(this.control());
    return anclaje && this.contexto.anclajeDisponible(anclaje)
      ? { tipo: 'ELEMENTO', etiqueta: this.nodo().titulo, anclaje }
      : null;
  });
  readonly cantidad = computed(() => {
    const d = this.destino();
    return d
      ? (this.contexto?.observacionesPresentacion() ?? []).filter(
          (o) => o.destino.tipo === 'ELEMENTO' && mismoAnclaje(o.destino.anclaje, d.anclaje),
        ).length
      : 0;
  });
}
