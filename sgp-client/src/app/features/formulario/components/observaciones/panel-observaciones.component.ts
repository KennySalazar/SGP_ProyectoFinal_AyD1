import { DOCUMENT, NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  model,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import {
  DestinoElementoObservacion,
  ObservacionPresentacion,
} from '../../models/observacion.models';

let secuenciaPanel = 0;

@Component({
  selector: 'app-panel-observaciones',
  standalone: true,
  imports: [TranslocoPipe, ButtonModule, DialogModule, NgTemplateOutlet],
  host: {
    '[class.lateral]': 'desplegable() && abierto() && !movil()',
    '[class.oculto]': 'desplegable() && !abierto()',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (desplegable() && movil()) {
      <p-dialog
        #dialogo
        [visible]="abierto()"
        (visibleChange)="cambiarVisibilidad($event)"
        [modal]="true"
        [focusTrap]="true"
        [focusOnShow]="false"
        [closeOnEscape]="true"
        [dismissableMask]="false"
        [showHeader]="false"
        [draggable]="false"
        [resizable]="false"
        appendTo="body"
        position="bottom"
        [header]="'observaciones.panel' | transloco"
        [style]="estiloHoja"
        [contentStyle]="{ padding: '0', minWidth: '0', overflow: 'hidden' }"
        (onShow)="enfocarCerrar()"
        (onHide)="restaurarFoco()"
      >
        <ng-container
          [ngTemplateOutlet]="contenido"
          [ngTemplateOutletContext]="{ tituloId: dialogo.ariaLabelledBy }"
        />
      </p-dialog>
    } @else if (!desplegable() || abierto()) {
      <aside
        tabindex="-1"
        [attr.aria-labelledby]="id + '-titulo'"
        (keydown.escape)="cerrarConEscape($event)"
      >
        <ng-container
          [ngTemplateOutlet]="contenido"
          [ngTemplateOutletContext]="{ tituloId: id + '-titulo' }"
        />
      </aside>
    }
    <ng-template #contenido let-tituloId="tituloId">
      <section class="surface-card panel">
        <header>
          <h3 [id]="tituloId">{{ 'observaciones.panel' | transloco }}</h3>
          @if (desplegable()) {
            <button
              #botonCerrar
              pButton
              type="button"
              severity="secondary"
              [outlined]="true"
              (click)="cerrarPanel()"
            >
              <i class="pi pi-times" aria-hidden="true"></i>
              <span>{{ 'observaciones.cerrar' | transloco }}</span>
            </button>
          }
        </header>
        <p class="resumen" role="status">
          {{
            'observaciones.resumen'
              | transloco: { total: observaciones().length, pendientes: pendientes() }
          }}
        </p>
        <div
          class="contenido"
          role="region"
          [attr.aria-label]="'observaciones.lista' | transloco"
          tabindex="0"
        >
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
        </div>
      </section>
    </ng-template>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
      max-width: 100%;
    }
    :host(.oculto) {
      display: none;
    }
    :host(.lateral) {
      position: sticky;
      top: 1rem;
      align-self: start;
      width: 24rem;
      flex: 0 1 24rem;
    }
    .panel {
      padding: 1rem;
      color: var(--sgp-navy-900);
      display: flex;
      flex-direction: column;
      min-width: 0;
      max-width: 100%;
      max-height: calc(100dvh - 2rem);
      box-sizing: border-box;
    }
    header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
    header h3 {
      margin: 0;
    }
    header,
    .resumen {
      flex-shrink: 0;
    }
    .contenido {
      min-height: 0;
      overflow-y: auto;
      overflow-x: hidden;
      overscroll-behavior: contain;
    }
    .contenido:focus-visible,
    button:focus-visible {
      outline: 3px solid var(--sgp-blue-600);
      outline-offset: -3px;
    }
    @media (max-width: 640px) {
      .panel {
        max-height: 85dvh;
        padding: 0.75rem;
        padding-bottom: max(0.75rem, env(safe-area-inset-bottom));
      }
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
      max-width: 100%;
      white-space: normal;
      overflow-wrap: anywhere;
    }
  `,
})
export class PanelObservacionesComponent {
  private readonly documento = inject(DOCUMENT);
  private readonly destruir = inject(DestroyRef);
  private readonly botonCerrar = viewChild<ElementRef<HTMLButtonElement>>('botonCerrar');
  private focoAnterior: HTMLElement | null = null;
  private estabaAbierto = false;
  protected readonly id = `panel-observaciones-${++secuenciaPanel}`;
  protected readonly movil = signal(false);
  protected readonly estiloHoja = {
    width: '100%',
    maxWidth: '100vw',
    maxHeight: '85dvh',
    margin: '0',
    borderRadius: '16px 16px 0 0',
  };
  /** Opt-in: el modo integrado anterior conserva su contenido y no captura el foco. */
  readonly desplegable = input(false);
  readonly abierto = model(false);
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

  constructor() {
    const media = this.documento.defaultView?.matchMedia?.('(max-width: 640px)');
    if (media) {
      const actualizar = () => this.movil.set(media.matches);
      actualizar();
      media.addEventListener('change', actualizar);
      this.destruir.onDestroy(() => media.removeEventListener('change', actualizar));
    }
    effect(() => {
      const activo = this.desplegable() && this.abierto();
      const esperaDialogo = this.desplegable() && this.movil();
      untracked(() => {
        if (activo && !this.estabaAbierto) {
          this.focoAnterior = this.documento.activeElement as HTMLElement | null;
        }
        if (!activo && this.estabaAbierto && !esperaDialogo) this.restaurarFoco();
        this.estabaAbierto = activo;
      });
    });
    effect(() => {
      const boton = this.botonCerrar();
      if (this.desplegable() && this.abierto() && !this.movil() && boton) {
        untracked(() => boton.nativeElement.focus());
      }
    });
    this.destruir.onDestroy(() => this.devolverFoco());
  }

  cerrarPanel(): void {
    this.abierto.set(false);
  }

  protected cambiarVisibilidad(visible: boolean): void {
    this.abierto.set(visible);
  }

  protected cerrarConEscape(evento: Event): void {
    if (!this.desplegable()) return;
    evento.preventDefault();
    evento.stopPropagation();
    this.cerrarPanel();
  }

  protected enfocarCerrar(): void {
    this.botonCerrar()?.nativeElement.focus();
  }

  protected restaurarFoco(): void {
    if (!this.desplegable() || !this.abierto()) this.devolverFoco();
  }

  private devolverFoco(): void {
    if (this.focoAnterior?.isConnected) this.focoAnterior.focus();
    this.focoAnterior = null;
  }
}
