import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { AbstractControl, FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { NodoCampo, OpcionCampo } from '../../motor/esquema/modelos';
import { FormularioConstruido } from '../../motor/formulario/constructor';
import { gravedades } from '../../motor/formulario/danos';

/** A partir de esta longitud máxima el texto se captura en un área de varias líneas. */
export const LONGITUD_TEXTO_LARGO = 300;

/** Un enumerado con pocas opciones cortas se responde con botones (un toque) y no con una lista. */
const MAXIMO_OPCIONES_EN_BOTONES = 4;
const MAXIMA_LONGITUD_ETIQUETA_EN_BOTONES = 14;
/** Desde esta longitud un nombre ya no cabe en una celda estrecha: el botón gana ancho. */
const ETIQUETA_LARGA = 12;

let secuencia = 0;

/** Un campo hoja del formulario: control según su tipo, etiqueta y mensaje de error. */
@Component({
  selector: 'app-campo-formulario',
  standalone: true,
  imports: [ReactiveFormsModule, TranslocoPipe, InputTextModule, SelectModule, TextareaModule],
  templateUrl: './campo-formulario.component.html',
  styleUrl: './campo-formulario.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampoFormularioComponent {
  private readonly transloco = inject(TranslocoService);

  readonly nodo = input.required<NodoCampo>();
  readonly control = input.required<AbstractControl>();
  readonly construido = input.required<FormularioConstruido>();
  /**
   * Dibuja el campo para la grilla de daños de un elemento (HU-034): un indicador con pocas
   * opciones se responde con botones de un toque, sean cuales sean sus etiquetas.
   */
  readonly modoGrilla = input(false);
  /**
   * Un indicador como fila de celdas sin texto visible: los nombres de las opciones los muestra
   * una sola vez el encabezado de la matriz del elemento (el texto sigue disponible para lectores
   * de pantalla). Cada celda mide al menos 44 px de alto.
   */
  readonly modoCeldas = input(false);

  /** Identificador único por instancia del componente (los tramos repiten el mismo campo). */
  protected readonly id = `campo-${++secuencia}`;
  protected readonly idError = `${this.id}-error`;

  protected readonly controlCampo = computed(() => this.control() as FormControl);
  /** Obligatorio ahora: dentro de un objeto opcional lo es solo cuando ese objeto ya se empezó a llenar. */
  protected readonly obligatorio = computed(() => {
    this.construido().revision();
    const nodo = this.nodo();
    return (nodo.obligatorio || nodo.condicional) && this.construido().exigido(this.control());
  });
  /**
   * Campo que puede dejarse vacío en cualquier caso. Los obligatorios de un bloque opcional aún no
   * empezado no cuentan aquí: el propio bloque ya se rotula como opcional.
   */
  protected readonly opcional = computed(() => {
    const nodo = this.nodo();
    return !nodo.obligatorio && !nodo.condicional && !nodo.soloLectura;
  });
  protected readonly valorActual = computed(() => {
    this.construido().revision();
    return this.control().value as unknown;
  });
  protected readonly deshabilitado = computed(() => {
    this.construido().revision();
    return this.control().disabled;
  });
  protected readonly seleccionadas = computed<unknown[]>(() => {
    const valor = this.valorActual();
    return Array.isArray(valor) ? valor : [];
  });
  /** Hay algo elegido que se pueda quitar (en cualquier campo de opciones, sea o no obligatorio). */
  protected readonly puedeQuitarSeleccion = computed(() => {
    if (this.deshabilitado()) return false;
    if (this.nodo().tipo === 'seleccionMultiple') return this.seleccionadas().length > 0;
    const valor = this.valorActual();
    return valor !== null && valor !== undefined && valor !== '';
  });
  protected readonly esTextoLargo = computed(
    () => (this.nodo().longitudMaxima ?? 0) >= LONGITUD_TEXTO_LARGO,
  );

  protected readonly opciones = computed(() =>
    this.nodo().opciones.map((opcion) => ({ ...opcion, etiqueta: this.etiquetaDe(opcion) })),
  );

  /** Alguna opción tiene un nombre largo («Asentamiento de estructura»): necesita botones más anchos. */
  protected readonly etiquetasLargas = computed(() =>
    this.opcionesRadio().some((opcion) => opcion.etiqueta.length > ETIQUETA_LARGA),
  );

  /** Un indicador de daño declara qué valor es «sano» (`x-sgp-sin-danos`). */
  protected readonly esIndicador = computed(() => this.nodo().sinDanos !== undefined);

  protected readonly enBotones = computed(() => {
    if (this.nodo().tipo !== 'enumerado') return false;
    const opciones = this.opciones();
    if (opciones.length > MAXIMO_OPCIONES_EN_BOTONES) return false;
    if (this.modoCeldas() && this.esIndicador()) return true;
    // En la grilla de daños todo indicador corto va en botones, aunque su etiqueta sea larga.
    if (this.modoGrilla() && this.esIndicador()) return true;
    return opciones.every(
      (opcion) => opcion.etiqueta.length <= MAXIMA_LONGITUD_ETIQUETA_EN_BOTONES,
    );
  });
  /** Opciones que se dibujan como botones de radio: Sí/No o un enumerado corto. */
  protected readonly opcionesRadio = computed(() =>
    this.nodo().tipo === 'booleano' ? this.opcionesBooleano() : this.opciones(),
  );

  /**
   * En un indicador, cada opción lleva su gravedad (`g0` es la sana; `g1` a `g3` suben): el color
   * ayuda, pero el nombre siempre se muestra y la opción elegida lleva una marca (DT-FE-13).
   */
  protected readonly opcionesConGravedad = computed(() => {
    const opciones = this.opcionesRadio();
    if (!this.esIndicador()) return opciones.map((opcion) => ({ ...opcion, gravedad: null }));

    const niveles = gravedades(opciones, this.nodo().sinDanos);
    return opciones.map((opcion, indice) => ({ ...opcion, gravedad: niveles[indice] }));
  });

  /**
   * Explicación de las opciones que la tienen en `formulario.ayudaOpciones.<valor>`. Solo los
   * enumerados cuyas opciones requieren aclaración (como la existencia de un elemento) muestran
   * la ayuda plegable.
   */
  protected readonly ayudas = computed(() => {
    if (this.nodo().tipo !== 'enumerado') return [];
    return this.opciones().flatMap((opcion) => {
      const clave = `formulario.ayudaOpciones.${opcion.valor}`;
      const texto = this.transloco.translate(clave);
      return texto === clave ? [] : [{ etiqueta: opcion.etiqueta, texto }];
    });
  });
  protected readonly ayudaAbierta = signal(false);
  protected readonly idAyuda = `${this.id}-ayuda`;

  protected alternarAyuda(): void {
    this.ayudaAbierta.update((abierta) => !abierta);
  }

  protected readonly opcionesBooleano = computed(() => [
    { valor: true, etiqueta: this.transloco.translate('formulario.dinamico.yes') },
    { valor: false, etiqueta: this.transloco.translate('formulario.dinamico.no') },
  ]);

  protected alternar(valor: string | number, marcada: boolean): void {
    const control = this.controlCampo();
    const elegidas = new Set(this.seleccionadas());
    if (marcada) elegidas.add(valor);
    else elegidas.delete(valor);

    // Se conserva el orden de las opciones del esquema, no el de los toques.
    control.setValue(
      this.nodo()
        .opciones.map((o) => o.valor)
        .filter((v) => elegidas.has(v)),
    );
    control.markAsDirty();
  }

  /** Elige una opción; volver a pulsar la ya elegida la desmarca. */
  protected elegir(valor: string | number | boolean): void {
    const control = this.controlCampo();
    control.setValue(this.valorActual() === valor ? null : valor);
    control.markAsDirty();
    control.markAsTouched();
  }

  /**
   * Con el teclado, Espacio o Enter marcan la opción enfocada y, si ya estaba marcada, la desmarcan
   * (las flechas ya mueven y marcan por sí solas). No se deja al navegador: sobre una opción ya
   * marcada no hace nada.
   */
  protected alTeclear(evento: KeyboardEvent, valor: string | number | boolean): void {
    if (evento.key !== ' ' && evento.key !== 'Spacebar' && evento.key !== 'Enter') return;
    evento.preventDefault();
    this.elegir(valor);
  }

  protected quitarSeleccion(): void {
    const control = this.controlCampo();
    control.setValue(this.nodo().tipo === 'seleccionMultiple' ? [] : null);
    control.markAsDirty();
    control.markAsTouched();
  }

  /** Al salir del campo se muestra su error: marcarlo como tocado no cambia ninguna señal. */
  protected alSalir(): void {
    this.construido().notificarCambio();
  }

  /** Mensaje del primer error, solo cuando el usuario ya interactuó con el campo. */
  protected readonly mensaje = computed(() => {
    this.construido().revision();
    const control = this.control();
    if (!control.errors || !(control.touched || control.dirty)) return null;

    const [clave, detalle] = Object.entries(control.errors)[0] as [string, unknown];
    const parametros: Record<string, unknown> =
      typeof detalle === 'object' && detalle !== null ? { ...detalle } : {};
    if ('valor' in parametros) {
      const opcion = this.nodo().opciones.find(
        (candidata) => candidata.valor === parametros['valor'],
      );
      parametros['valor'] = opcion ? this.etiquetaDe(opcion) : parametros['valor'];
    }
    return this.transloco.translate(`formulario.validacion.${clave}`, parametros);
  });

  /** Traduce la opción con `formulario.opciones.<valor>`; si no hay, usa la etiqueta del esquema. */
  private etiquetaDe(opcion: OpcionCampo): string {
    const clave = `formulario.opciones.${opcion.valor}`;
    const traduccion = this.transloco.translate(clave);
    return traduccion === clave ? opcion.etiqueta : traduccion;
  }
}
