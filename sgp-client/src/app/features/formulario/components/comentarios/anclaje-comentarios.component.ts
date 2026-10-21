import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { TranslocoService } from '@jsverse/transloco';
import { NodoFormulario } from '../../motor/esquema/modelos';
import { AnclajeFormulario } from '../../utils/anclaje-formulario';
import { anclajeDeControl } from '../observaciones/contexto-observaciones';
import { CONTEXTO_COMENTARIOS } from './contexto-comentarios';

/**
 * Un solo botón de icono junto al dato de un elemento de la inspección (RN-FOR-02): con el icono de
 * comentario para comentarlo y, si ya tiene comentarios, además su número. Al pulsarlo se lleva al
 * foro, a los comentarios de ese elemento o a escribir uno nuevo. Es solo un icono para no cargar
 * un formulario ya extenso, así que su nombre accesible dice qué hace y sobre qué elemento; el
 * conteo es texto, no solo color (DT-FE-13). Sin contexto no dibuja nada.
 */
@Component({
  selector: 'app-anclaje-comentarios',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (contexto; as c) {
    @if (anclaje(); as a) {
      @if (total() > 0 || c.puedeComentar()) {
        <div
          class="anclaje"
          [class.en-titulo]="enTitulo()"
          [attr.data-elemento-ref]="a.elementoRef"
          [attr.data-instancia-id]="a.instanciaElementoId ?? ''"
        >
          <button
            type="button"
            class="boton"
            [attr.aria-label]="nombreAccesible()"
            [attr.title]="nombreAccesible()"
            (click)="c.seleccionar(a)"
          >
            <span class="visual" [class.con-comentarios]="total() > 0">
              <i
                class="pi"
                [class.pi-comments]="total() > 0"
                [class.pi-comment]="total() === 0"
                aria-hidden="true"
              ></i>
              @if (total() > 0) {
                <span class="cantidad">{{ total() }}</span>
              }
            </span>
          </button>
        </div>
      }
    }
  }`,
  styles: `
    :host {
      display: contents;
    }
    .anclaje {
      display: flex;
      justify-content: flex-end;
      min-width: 0;
    }
    /* Junto al nombre del elemento: se lee como suyo y no agrega una fila. */
    .anclaje.en-titulo {
      display: inline-flex;
      align-items: center;
      justify-content: flex-start;
      vertical-align: middle;
    }
    /* El área pulsable mide 44 px; la parte visible es compacta. */
    .boton {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 44px;
      min-height: 44px;
      padding: 0;
      border: 0;
      background: transparent;
      color: var(--sgp-steel-700);
      font: inherit;
      font-weight: 700;
      cursor: pointer;
    }
    /* No ensancha la línea del nombre: su área extra sobresale sin empujar lo que la rodea. */
    .en-titulo .boton {
      margin-block: -0.7rem;
    }
    .visual {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      min-height: 30px;
      box-sizing: border-box;
      padding: 0.15rem 0.5rem;
      border: 1px solid transparent;
      border-radius: 10px;
    }
    .visual.con-comentarios {
      border-color: var(--sgp-border);
      background: var(--sgp-surface-soft);
      color: var(--sgp-navy-900);
    }
    .boton:hover .visual {
      border-color: var(--sgp-border);
      background: var(--sgp-surface-soft);
      color: var(--sgp-navy-900);
    }
    .boton:focus-visible {
      outline: none;
    }
    .boton:focus-visible .visual {
      outline: 3px solid var(--sgp-blue-600);
      outline-offset: 2px;
    }
  `,
})
export class AnclajeComentariosComponent {
  private readonly transloco = inject(TranslocoService);
  readonly contexto = inject(CONTEXTO_COMENTARIOS, { optional: true });
  readonly nodo = input.required<NodoFormulario>();
  readonly control = input.required<AbstractControl>();
  /** Va pegado al nombre del elemento, en su misma línea, en lugar de en una fila aparte. */
  readonly enTitulo = input(false);
  readonly anclaje = computed<AnclajeFormulario | null>(() =>
    this.contexto ? anclajeDeControl(this.control()) : null,
  );
  /** El título del nodo; en un elemento repetible, además qué instancia es. */
  readonly etiqueta = computed(() => {
    const anclaje = this.anclaje();
    return anclaje && this.contexto ? this.contexto.etiquetaDe(anclaje) : this.nodo().titulo;
  });
  readonly total = computed(() => {
    const anclaje = this.anclaje();
    return anclaje && this.contexto ? this.contexto.totalDe(anclaje) : 0;
  });
  /**
   * Con comentarios empieza por el texto visible («2 comentarios en…»); sin ellos, la acción
   * («Comentar…»).
   */
  protected nombreAccesible(): string {
    const total = this.total();
    const elemento = this.etiqueta();
    if (total === 0) return this.transloco.translate('foro.ancla.comentarElemento', { elemento });
    const conteo = this.transloco.translate(
      total === 1 ? 'foro.ancla.una' : 'foro.ancla.cantidad',
      { cantidad: total },
    );
    return `${conteo} ${this.transloco.translate('foro.ancla.en', { elemento })}`;
  }
}
