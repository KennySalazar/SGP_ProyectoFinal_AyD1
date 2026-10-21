import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { EsquemaJson, NodoArreglo, NodoObjeto } from '../../motor/esquema/modelos';
import { resolverEsquema } from '../../motor/esquema/resolutor';
import { Avance, calcularAvance } from '../../motor/formulario/avance';
import { FormularioConstruido } from '../../motor/formulario/constructor';
import { resumirElementos, ResumenElementos } from '../../motor/formulario/danos';
import { RENDERIZADOR_ELEMENTO_EVALUABLE } from '../elemento-evaluable/elemento-evaluable.component';
import { NodoFormularioComponent } from '../nodo-formulario/nodo-formulario.component';
import { AnclajeComentariosComponent } from '../comentarios/anclaje-comentarios.component';
import { RENDERIZADORES_PREDETERMINADOS } from '../renderizadores';
import { RenderizadorNodoDirective } from '../renderizador-nodo.directive';
import { SeccionApoyo } from '../secciones-apoyo';

type EstadoSeccion = 'completa' | 'pendiente' | 'faltantes' | 'errores';

interface ResumenSeccion {
  nodo: NodoObjeto | NodoArreglo;
  avance: Avance;
  estado: EstadoSeccion;
  /** Elementos evaluables de la sección y cuántos ya se revisaron. */
  elementos: ResumenElementos;
}

/** Elemento de la navegación: una sección del esquema o una sección de apoyo. */
type EntradaNavegacion =
  | ({ tipo: 'esquema'; ruta: string; titulo: string; descripcion?: string } & ResumenSeccion)
  | { tipo: 'apoyo'; ruta: string; titulo: string; descripcion?: string; apoyo: SeccionApoyo };

type Resultado = { construido: FormularioConstruido } | { error: string } | null;

/**
 * Formulario de inspección generado en ejecución a partir del esquema JSON de la versión
 * (DT-FE-07, DT-FE-08): no contiene ninguna pregunta fija. Muestra una sección a la vez, con el
 * avance de cada una, y conserva los valores al navegar entre ellas.
 */
@Component({
  selector: 'app-formulario-dinamico',
  standalone: true,
  imports: [
    TranslocoPipe,
    ButtonModule,
    NodoFormularioComponent,
    RenderizadorNodoDirective,
    AnclajeComentariosComponent,
  ],
  // La captura de daños por elemento (HU-034) viene incluida; quien use el formulario puede sumar
  // sus propios renderizadores con RENDERIZADORES_NODO, que tienen prioridad.
  providers: [
    { provide: RENDERIZADORES_PREDETERMINADOS, useValue: [RENDERIZADOR_ELEMENTO_EVALUABLE] },
  ],
  templateUrl: './formulario-dinamico.component.html',
  styleUrl: './formulario-dinamico.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioDinamicoComponent {
  /** Esquema JSON de la versión del formulario (el que entrega el backend). */
  readonly esquema = input.required<EsquemaJson>();
  /** Valores con los que se llena el formulario: datos precargados o una inspección guardada. */
  readonly valoresIniciales = input<Record<string, unknown> | null>(null);
  /** Muestra la inspección sin permitir cambios, con la estructura de su versión original. */
  readonly soloLectura = input(false);
  /** Secciones que no vienen del esquema (por ejemplo, el reporte fotográfico), tras las del esquema. */
  readonly seccionesApoyo = input<SeccionApoyo[]>([]);
  /** Renderiza todas las secciones simultáneamente para vista de impresión o exportación. */
  readonly modoImpresion = input(false);

  readonly valorChange = output<Record<string, unknown>>();
  readonly seccionCambiando = output<void>();
  /** `false` mientras algún campo visible incumple una regla del esquema. */
  readonly validoChange = output<boolean>();

  protected readonly resultado = signal<Resultado>(null);
  protected readonly indiceActivo = signal(0);
  private readonly encabezado = viewChild<ElementRef<HTMLElement>>('encabezado');
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly construido = computed(() => {
    const resultado = this.resultado();
    return resultado && 'construido' in resultado ? resultado.construido : null;
  });
  protected readonly error = computed(() => {
    const resultado = this.resultado();
    return resultado && 'error' in resultado ? resultado.error : null;
  });

  protected readonly secciones = computed<ResumenSeccion[]>(() => {
    const construido = this.construido();
    if (!construido) return [];
    construido.revision();

    return construido.esquema.secciones.map((nodo) => {
      const control = construido.form.controls[nodo.clave];
      const avance = control
        ? calcularAvance(nodo, control, construido.ocultos)
        : { obligatorios: 0, completos: 0, invalidos: 0 };
      const elementos = control
        ? resumirElementos(nodo, control, construido.ocultos)
        : { total: 0, revisados: 0 };
      return { nodo, avance, elementos, estado: estadoDe(avance, construido.revisado()) };
    });
  });

  /** Todo lo que se recorre: primero las secciones del esquema y después las de apoyo. */
  protected readonly entradas = computed<EntradaNavegacion[]>(() => [
    ...this.secciones().map((seccion): EntradaNavegacion => ({
      tipo: 'esquema',
      ruta: seccion.nodo.ruta,
      titulo: seccion.nodo.titulo,
      descripcion: seccion.nodo.descripcion,
      ...seccion,
    })),
    ...(this.construido() ? this.seccionesApoyo() : []).map((apoyo): EntradaNavegacion => ({
      tipo: 'apoyo',
      ruta: `apoyo/${apoyo.clave}`,
      titulo: apoyo.titulo,
      descripcion: apoyo.descripcion,
      apoyo,
    })),
  ]);

  protected readonly seccionActiva = computed(() => this.entradas()[this.indiceActivo()] ?? null);
  /** Lista de un elemento: obliga a recrear el contenido al cambiar de sección. */
  protected readonly seccionesAMostrar = computed(() => {
    if (this.modoImpresion()) {
      return this.entradas();
    }
    const activa = this.seccionActiva();
    return activa ? [activa] : [];
  });

  /** Avance de todo el formulario: campos obligatorios completos y valores con errores. */
  readonly avanceTotal = computed<Avance>(() =>
    this.secciones().reduce(
      (total, seccion) => ({
        obligatorios: total.obligatorios + seccion.avance.obligatorios,
        completos: total.completos + seccion.avance.completos,
        invalidos: total.invalidos + seccion.avance.invalidos,
      }),
      { obligatorios: 0, completos: 0, invalidos: 0 },
    ),
  );

  constructor() {
    // Reconstruye el formulario cuando cambia la versión del esquema o los valores de partida.
    effect((onCleanup) => {
      const esquema = this.esquema();
      const iniciales = this.valoresIniciales();

      const resultado = untracked(() => construir(esquema, iniciales));
      this.resultado.set(resultado);
      this.indiceActivo.set(0);

      onCleanup(() => {
        if ('construido' in resultado) resultado.construido.destruir();
      });
    });

    effect(() => {
      const construido = this.construido();
      const soloLectura = this.soloLectura();
      if (construido && construido.form.disabled !== soloLectura) {
        untracked(() => construido.establecerSoloLectura(soloLectura));
      }
    });

    effect(() => {
      const construido = this.construido();
      if (!construido) return;
      construido.revision();

      untracked(() => {
        this.valorChange.emit(construido.valor());
        this.validoChange.emit(construido.form.status !== 'INVALID');
      });
    });
  }

  /** Selecciona la sección histórica que contiene la referencia canónica, sin alterar valores. */
  mostrarAnclaje(elementoRef: string): boolean {
    const primera = elementoRef.split('/')[1]?.replace(/~1/g, '/').replace(/~0/g, '~');
    const indice = this.entradas().findIndex(
      (e) => e.tipo === 'esquema' && e.nodo.clave === primera,
    );
    if (indice < 0) return false;
    this.irA(indice);
    return true;
  }

  protected irA(indice: number): void {
    const total = this.entradas().length;
    if (indice < 0 || indice >= total || indice === this.indiceActivo()) return;
    this.seccionCambiando.emit();
    this.indiceActivo.set(indice);
    this.encabezado()?.nativeElement.focus({ preventScroll: false });
    // En pantallas angostas la lista de secciones se desplaza: la activa debe quedar a la vista.
    afterNextRender(
      () =>
        this.anfitrion.nativeElement
          .querySelector('.seccion.activa')
          ?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' }),
      { injector: this.injector },
    );
  }

  /**
   * Muestra los errores de todos los campos y lleva a la primera sección con pendientes.
   * Devuelve `true` si el formulario ya puede enviarse.
   */
  revisar(): boolean {
    const construido = this.construido();
    if (!construido) return false;

    construido.revisar();
    const valido = construido.form.status !== 'INVALID';

    const primera = this.secciones().findIndex((seccion) => seccion.estado !== 'completa');
    if (primera >= 0) this.irA(primera);
    // Con los errores ya dibujados, se lleva al usuario al primer campo que debe corregir.
    afterNextRender(() => this.enfocarPrimerProblema(), { injector: this.injector });
    return valido && primera < 0;
  }

  private enfocarPrimerProblema(): void {
    const campo = this.anfitrion.nativeElement.querySelector<HTMLElement>('.campo.invalido');
    if (!campo) return;

    const control = campo.querySelector<HTMLElement>(
      'input:not([disabled]), textarea, select, [tabindex="0"], button',
    );
    (control ?? campo).focus?.();
    campo.scrollIntoView?.({ block: 'center' });
  }

  /** Valor actual para el backend, sin lo oculto ni lo vacío. */
  valor(): Record<string, unknown> {
    return this.construido()?.valor() ?? {};
  }
}

function construir(
  esquema: EsquemaJson,
  iniciales: Record<string, unknown> | null,
): NonNullable<Resultado> {
  try {
    return { construido: new FormularioConstruido(resolverEsquema(esquema), iniciales) };
  } catch (e) {
    return { error: e instanceof Error ? e.message : String(e) };
  }
}

function estadoDe(avance: Avance, revisado: boolean): EstadoSeccion {
  if (avance.invalidos > 0) return 'errores';
  if (avance.completos >= avance.obligatorios) return 'completa';
  return revisado ? 'faltantes' : 'pendiente';
}
