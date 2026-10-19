import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { AbstractControl, FormGroup } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { NodoCampo, NodoFormulario, NodoObjeto } from '../../motor/esquema/modelos';
import { FormularioConstruido } from '../../motor/formulario/constructor';
import {
  activacionDe,
  esElementoEvaluable,
  estaRevisado,
  estadoElemento,
  gravedades,
  indicadoresDe,
  TipoEstadoElemento,
} from '../../motor/formulario/danos';
import { vacio } from '../../motor/formulario/validadores';
import { hijosVisibles } from '../../motor/formulario/visibles';
import { CampoFormularioComponent } from '../campo-formulario/campo-formulario.component';
import { NodoFormularioComponent } from '../nodo-formulario/nodo-formulario.component';
import { MarcadorObservacionesComponent } from '../observaciones/marcador-observaciones.component';
import { RenderizadorNodo } from '../renderizadores';

let secuencia = 0;

const ICONOS: Record<TipoEstadoElemento, string> = {
  pendiente: 'pi-circle',
  'en-proceso': 'pi-clock',
  sano: 'pi-check-circle',
  'con-danos': 'pi-exclamation-triangle',
  'no-presente': 'pi-ban',
};

/** «No aplica» y «Ausente» se ven distintos: no es lo mismo no tener un elemento que perderlo. */
const ICONOS_NO_PRESENTE: Record<string, string> = {
  no_aplica: 'pi-minus-circle',
  ausente: 'pi-times-circle',
};

/**
 * Captura de daños de un elemento (HU-034, DT-FE-09): una tarjeta con su estado, la existencia,
 * los datos que lo describen y una grilla de indicadores con controles táctiles de al menos
 * 44 × 44 px. «Sin daños en este elemento» (RN-INS-11) marca todo el grupo como sano de una vez.
 *
 * Recibe las entradas que el motor entrega a todo renderizador: `nodo`, `control` y `construido`.
 */
@Component({
  selector: 'app-elemento-evaluable',
  standalone: true,
  imports: [
    TranslocoPipe,
    CampoFormularioComponent,
    NodoFormularioComponent,
    MarcadorObservacionesComponent,
  ],
  templateUrl: './elemento-evaluable.component.html',
  styleUrl: './elemento-evaluable.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ElementoEvaluableComponent {
  private readonly transloco = inject(TranslocoService);

  readonly nodo = input.required<NodoFormulario>();
  readonly control = input.required<AbstractControl>();
  readonly construido = input.required<FormularioConstruido>();
  readonly cantidadObservaciones = input(0);
  readonly observacionesInteractivas = input(false);
  readonly abrirObservaciones = output<void>();

  protected readonly idTitulo = `elemento-${++secuencia}`;

  protected readonly elemento = computed(() => this.nodo() as NodoObjeto);
  protected readonly grupo = computed(() => this.control() as FormGroup);

  protected readonly estado = computed(() => {
    this.construido().revision();
    return estadoElemento(this.elemento(), this.grupo(), this.construido().ocultos);
  });
  /** Valor de la existencia en minúsculas cuando el elemento no está presente (`ausente`…). */
  protected readonly subtipo = computed(() => {
    const estado = this.estado();
    return estado.tipo === 'no-presente' ? String(estado.noPresente).toLowerCase() : null;
  });
  protected readonly icono = computed(() => {
    const subtipo = this.subtipo();
    return (subtipo && ICONOS_NO_PRESENTE[subtipo]) || ICONOS[this.estado().tipo];
  });

  /** Tras pedir la revisión, un elemento sin revisar se destaca (con texto, no solo con color). */
  protected readonly sinRevisar = computed(
    () => this.construido().revisado() && !estaRevisado(this.estado()),
  );

  private readonly visibles = computed(() => {
    this.construido().revision();
    return hijosVisibles(this.elemento(), this.grupo(), this.construido().ocultos);
  });

  private readonly claveIndicadores = computed(
    () => new Set(indicadoresDe(this.elemento()).map((indicador) => indicador.clave)),
  );

  /** Datos que describen el elemento: la existencia, el material, las medidas… */
  private readonly descriptivos = computed(() =>
    this.visibles().filter(
      (hijo): hijo is NodoCampo =>
        hijo.clase === 'campo' && !this.claveIndicadores().has(hijo.clave),
    ),
  );
  /** El campo que decide si el elemento existe: siempre a la vista, porque es la primera decisión. */
  protected readonly existencia = computed(() => {
    const clave = activacionDe(this.elemento())?.clave;
    return this.descriptivos().find((hijo) => hijo.clave === clave) ?? null;
  });
  private readonly restoDescriptivos = computed(() =>
    this.descriptivos().filter((hijo) => hijo !== this.existencia()),
  );
  /** Medidas que se pueden dejar vacías: van plegadas hasta que hay algo que mostrar. */
  protected readonly medidas = computed(() =>
    this.restoDescriptivos().filter(
      (hijo) =>
        (hijo.tipo === 'numero' || hijo.tipo === 'entero') &&
        !hijo.obligatorio &&
        !hijo.condicional &&
        !hijo.soloLectura,
    ),
  );
  /** Datos principales del elemento (material, lado…). */
  protected readonly datos = computed(() =>
    this.restoDescriptivos().filter((hijo) => !this.medidas().includes(hijo)),
  );
  protected readonly medidasAbiertas = computed(() => {
    this.construido().revision();
    return this.medidas().some((hijo) => {
      const control = this.controlDe(hijo);
      return !vacio(control.value) || control.invalid;
    });
  });
  protected readonly indicadores = computed(() =>
    this.visibles().filter(
      (hijo): hijo is NodoCampo =>
        hijo.clase === 'campo' && this.claveIndicadores().has(hijo.clave),
    ),
  );
  /**
   * Indicadores que comparten las mismas opciones cortas (la severidad): se muestran como una
   * matriz, con los nombres de las opciones una sola vez como encabezado de columna. Cada
   * indicador queda en una fila de celdas táctiles en lugar de repetir sus botones con texto.
   */
  protected readonly matriz = computed(() => {
    const grupos = new Map<string, NodoCampo[]>();
    for (const campo of this.indicadores()) {
      const cortas =
        campo.tipo === 'enumerado' &&
        campo.opciones.length <= 5 &&
        campo.opciones.every((opcion) => this.etiquetaDe(campo, opcion.valor).length <= 12);
      if (!cortas) continue;
      const firma = campo.opciones.map((opcion) => opcion.valor).join('|');
      grupos.set(firma, [...(grupos.get(firma) ?? []), campo]);
    }

    const mayor = [...grupos.values()].sort((a, b) => b.length - a.length)[0];
    if (!mayor || mayor.length < 2) return null;

    const niveles = gravedades(mayor[0].opciones, mayor[0].sinDanos);
    return {
      campos: mayor,
      encabezados: mayor[0].opciones.map((opcion, indice) => ({
        etiqueta: this.etiquetaDe(mayor[0], opcion.valor),
        gravedad: niveles[indice],
      })),
    };
  });
  /** Indicadores que no entran en la matriz (booleanos, numéricos, opciones largas…). */
  protected readonly otrosIndicadores = computed(() => {
    const enMatriz = new Set(this.matriz()?.campos.map((campo) => campo.clave));
    return this.indicadores().filter((campo) => !enMatriz.has(campo.clave));
  });
  /** Bloques anidados, como «Otros (especificar)». */
  protected readonly bloques = computed(() =>
    this.visibles().filter((hijo) => hijo.clase !== 'campo'),
  );

  /** Lo que se oculta al plegar la tarjeta: todo menos el título, el estado y la existencia. */
  protected readonly hayDetalle = computed(
    () =>
      this.datos().length +
        this.medidas().length +
        this.indicadores().length +
        this.bloques().length >
      0,
  );
  protected readonly idDetalle = `${this.idTitulo}-detalle`;

  /** Datos obligatorios que todavía faltan: mientras haya, el detalle no se pliega solo. */
  private readonly faltantes = computed(() => {
    this.construido().revision();
    return [...this.datos(), ...this.indicadores()].filter((hijo) => {
      const control = this.controlDe(hijo);
      return (
        (hijo.obligatorio || hijo.condicional) &&
        this.construido().exigido(control) &&
        vacio(control.value)
      );
    }).length;
  });
  /** Un elemento sano o que no existe, y sin datos pendientes, ya no necesita ocupar pantalla. */
  private readonly abiertoPorOmision = computed(() => {
    const tipo = this.estado().tipo;
    const resuelto = tipo === 'sano' || tipo === 'no-presente';
    return this.hayDetalle() && !(resuelto && this.faltantes() === 0);
  });
  /** Lo que el usuario decidió con el botón; vuelve a ser automático al cambiar el estado. */
  private readonly decision = signal<boolean | null>(null);
  protected readonly abierto = computed(() => this.decision() ?? this.abiertoPorOmision());

  /** La acción no aplica si el elemento ya se declaró ausente o que no aplica, ni en solo lectura. */
  protected readonly puedeMarcarSinDanos = computed(
    () => this.estado().tipo !== 'no-presente' && !this.grupo().disabled,
  );
  protected readonly estaSano = computed(() => this.estado().tipo === 'sano');

  /** Texto que acompaña al icono del estado: el estado nunca se comunica solo con color. */
  protected readonly textoEstado = computed(() => {
    const estado = this.estado();
    switch (estado.tipo) {
      case 'pendiente':
        return this.transloco.translate('formulario.dinamico.elemento.pendiente');
      case 'en-proceso':
        return this.transloco.translate('formulario.dinamico.elemento.enProceso', {
          marcados: estado.marcados,
          total: estado.total,
        });
      case 'sano':
        return this.transloco.translate('formulario.dinamico.elemento.sano');
      case 'con-danos':
        return estado.marcados < estado.total
          ? this.transloco.translate('formulario.dinamico.elemento.conDanosFaltan', {
              faltan: estado.total - estado.marcados,
            })
          : this.transloco.translate('formulario.dinamico.elemento.conDanos');
      case 'no-presente':
        return this.etiquetaNoPresente(estado.noPresente);
    }
  });

  /** Lo que se responde con botones o con una lista ocupa su fila; los números se reparten. */
  protected ocupaFila(hijo: NodoCampo): boolean {
    return (
      hijo.tipo === 'booleano' || hijo.tipo === 'seleccionMultiple' || hijo.tipo === 'enumerado'
    );
  }

  /** El tipo de estado es un texto: solo cambia cuando el estado cambia de verdad. */
  private readonly tipoEstado = computed(() => this.estado().tipo);

  constructor() {
    effect(() => {
      this.tipoEstado();
      untracked(() => this.decision.set(null));
    });
  }

  protected alternarDetalle(): void {
    this.decision.set(!this.abierto());
  }

  protected controlDe(hijo: NodoFormulario): AbstractControl {
    return this.grupo().controls[hijo.clave];
  }

  protected alternarSinDanos(): void {
    if (this.estaSano()) {
      this.construido().limpiarDanos(this.elemento(), this.grupo());
    } else {
      this.construido().marcarSinDanos(this.elemento(), this.grupo());
    }
  }

  /** Nombre de una opción: `formulario.opciones.<valor>` o, si no hay traducción, el del esquema. */
  private etiquetaDe(campo: NodoCampo, valor: string | number): string {
    const clave = `formulario.opciones.${valor}`;
    const traduccion = this.transloco.translate(clave);
    return traduccion === clave
      ? (campo.opciones.find((opcion) => opcion.valor === valor)?.etiqueta ?? String(valor))
      : traduccion;
  }

  /** «No aplica», «Ausente»… según la opción de existencia elegida. */
  private etiquetaNoPresente(valor: unknown): string {
    const activacion = activacionDe(this.elemento());
    const decisor = this.elemento().hijos.find((hijo) => hijo.clave === activacion?.clave);
    if (decisor?.clase === 'campo') {
      const opcion = decisor.opciones.find((candidata) => candidata.valor === valor);
      if (opcion) {
        const clave = `formulario.opciones.${opcion.valor}`;
        const traduccion = this.transloco.translate(clave);
        return traduccion === clave ? opcion.etiqueta : traduccion;
      }
    }
    return this.transloco.translate('formulario.dinamico.elemento.noExiste');
  }
}

/** Registro del renderizador: todo elemento evaluable se captura con la grilla de daños. */
export const RENDERIZADOR_ELEMENTO_EVALUABLE: RenderizadorNodo = {
  coincide: esElementoEvaluable,
  componente: ElementoEvaluableComponent,
};
