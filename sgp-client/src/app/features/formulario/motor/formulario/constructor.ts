import { signal } from '@angular/core';
import { AbstractControl, FormArray, FormControl, FormGroup } from '@angular/forms';
import { Subscription } from 'rxjs';
import { uuidV7 } from '../../../../offline/services/uuid-v7';
import { calcularVisibilidad } from '../esquema/condiciones';
import {
  EsquemaResuelto,
  NodoArreglo,
  NodoCampo,
  NodoFormulario,
  NodoObjeto,
} from '../esquema/modelos';
import { activacionDe, indicadoresDe } from './danos';
import { ConjuntoOcultos, esOpcional, valorDelNodo } from './valor';
import { vacio, validadoresDeArreglo, validadoresDeCampo } from './validadores';

type Datos = Record<string, unknown>;

function esDatos(valor: unknown): valor is Datos {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor);
}

/** Objeto opcional cuyos campos obligatorios solo se exigen si el objeto se empieza a llenar. */
interface AmbitoOpcional {
  nodo: NodoObjeto;
  grupo: FormGroup | null;
  exigentes: FormControl[];
}

export interface OpcionesConstruccion {
  /** Generador de identificadores de instancia; por omisión UUID v7 (DT-OFF-05). */
  generarId?: () => string;
}

/** Formulario reactivo construido en ejecución a partir del esquema (DT-FE-08). */
export class FormularioConstruido {
  readonly form: FormGroup;
  /** Controles ocultos por una condición `if/then/else`; no se validan ni se envían. */
  readonly ocultos: ConjuntoOcultos = new WeakSet<AbstractControl>();
  /**
   * Cambia con cada modificación del formulario (valor, visibilidad o estado de revisión). Los
   * componentes la leen para refrescar lo que depende de controles que no son señales.
   */
  readonly revision = signal(0);
  /** Pasa a `true` cuando se pide la revisión: lo pendiente se destaca en lugar de esperar. */
  readonly revisado = signal(false);

  private readonly suscripciones = new Subscription();
  private readonly ambitos: AmbitoOpcional[] = [];
  private readonly exigencias = new WeakMap<AbstractControl, () => boolean>();
  private readonly generarId: () => string;

  constructor(
    readonly esquema: EsquemaResuelto,
    valoresIniciales: Datos | null = null,
    opciones: OpcionesConstruccion = {},
  ) {
    this.generarId = opciones.generarId ?? uuidV7;
    this.form = this.crearObjeto(esquema.raiz, valoresIniciales ?? {}, null);
    this.actualizarVisibilidadInicial(esquema.raiz, this.form);
    this.revalidarExigencias(0);
    this.suscripciones.add(this.form.valueChanges.subscribe(() => this.notificarCambio()));
  }

  /** Marca todos los campos como tocados para que se muestren sus errores. */
  revisar(): void {
    this.revisado.set(true);
    this.form.markAllAsTouched();
    this.notificarCambio();
  }

  /** Valor para el backend, sin lo oculto ni lo vacío. */
  valor(): Datos {
    const valor = valorDelNodo(this.esquema.raiz, this.form, this.ocultos);
    return esDatos(valor) ? valor : {};
  }

  /** Deshabilita (o habilita) todo el formulario, por ejemplo para el modo de solo lectura. */
  establecerSoloLectura(soloLectura: boolean): void {
    if (soloLectura) {
      this.form.disable({ emitEvent: false });
    } else {
      this.form.enable({ emitEvent: false });
      this.restaurarSubarbol(this.esquema.raiz, this.form);
    }
    this.form.updateValueAndValidity({ emitEvent: false });
    this.notificarCambio();
  }

  /**
   * «Sin daños en este elemento» (RN-INS-11): en una sola operación declara que el elemento existe
   * y marca como sano cada uno de sus indicadores, con el valor que el esquema declara sano. Un
   * indicador que ya tenía un daño se sobrescribe: la acción afirma que el elemento está sano.
   */
  marcarSinDanos(nodo: NodoObjeto, grupo: FormGroup): void {
    const activacion = activacionDe(nodo);
    const decisor = activacion ? grupo.controls[activacion.clave] : undefined;
    if (activacion && decisor?.enabled) decisor.setValue(activacion.valor);

    // Declarar la existencia puede mostrar indicadores; un indicador puede mostrar otros.
    for (let pasada = 0; pasada < 3; pasada++) {
      let cambios = 0;
      for (const indicador of indicadoresDe(nodo)) {
        const control = grupo.controls[indicador.clave];
        if (!control || control.disabled || this.ocultos.has(control)) continue;

        const sano = Array.isArray(indicador.sinDanos)
          ? [...indicador.sinDanos]
          : indicador.sinDanos;
        if (JSON.stringify(control.value) === JSON.stringify(sano)) continue;
        control.setValue(sano);
        control.markAsDirty();
        cambios++;
      }
      if (cambios === 0) break;
    }
    this.notificarCambio();
  }

  /** Deshace «Sin daños» (o cualquier marca): deja los indicadores del elemento sin responder. */
  limpiarDanos(nodo: NodoObjeto, grupo: FormGroup): void {
    for (const indicador of indicadoresDe(nodo)) {
      const control = grupo.controls[indicador.clave];
      if (!control || control.disabled || vacio(control.value)) continue;

      control.setValue(indicador.tipo === 'seleccionMultiple' ? [] : null);
      control.markAsPristine();
      control.markAsUntouched();
    }
    this.notificarCambio();
  }

  /**
   * Indica si el campo es obligatorio en este momento. Dentro de un objeto opcional lo es solo
   * cuando ese objeto ya se empezó a llenar.
   */
  exigido(control: AbstractControl): boolean {
    return this.exigencias.get(control)?.() ?? true;
  }

  /**
   * Vacía un objeto, por ejemplo un bloque opcional que se empezó a llenar y se quiere descartar.
   * Los datos precargados y los identificadores generados se conservan.
   */
  limpiar(nodo: NodoObjeto, grupo: FormGroup): void {
    this.vaciar(nodo, grupo);
    this.notificarCambio();
  }

  agregarInstancia(nodo: NodoArreglo, lista: FormArray, valores: Datos = {}): FormGroup {
    const desde = this.ambitos.length;
    const instancia = this.crearObjeto(nodo.elemento, valores, null);
    this.actualizarVisibilidadInicial(nodo.elemento, instancia);
    this.revalidarExigencias(desde);
    lista.push(instancia);
    this.notificarCambio();
    return instancia;
  }

  quitarInstancia(lista: FormArray, indice: number): void {
    lista.removeAt(indice);
    this.notificarCambio();
  }

  notificarCambio(): void {
    this.revision.update((valor) => valor + 1);
  }

  destruir(): void {
    this.suscripciones.unsubscribe();
  }

  private crearControl(
    nodo: NodoFormulario,
    valor: unknown,
    ambito: AmbitoOpcional | null,
  ): AbstractControl {
    switch (nodo.clase) {
      case 'campo':
        return this.crearCampo(nodo, valor, ambito);
      case 'objeto':
        return this.crearObjeto(nodo, esDatos(valor) ? valor : {}, ambito);
      case 'arreglo':
        return this.crearArreglo(nodo, Array.isArray(valor) ? valor : []);
    }
  }

  private crearCampo(nodo: NodoCampo, valor: unknown, ambito: AmbitoOpcional | null): FormControl {
    let inicial: unknown = valor ?? null;
    if (nodo.tipo === 'seleccionMultiple') {
      inicial = Array.isArray(valor) ? [...valor] : [];
    } else if (nodo.tipo === 'uuid' && (valor === undefined || valor === null)) {
      inicial = this.generarId();
    }

    const exigido = ambito ? () => this.presente(ambito) : undefined;
    const control = new FormControl<unknown>(inicial, validadoresDeCampo(nodo, exigido));
    if (ambito && exigido && (nodo.obligatorio || nodo.condicional)) {
      this.exigencias.set(control, exigido);
      ambito.exigentes.push(control);
    }
    // Los datos precargados del inventario o de la sesión no se editan.
    if (nodo.soloLectura) control.disable({ emitEvent: false });
    return control;
  }

  private crearObjeto(nodo: NodoObjeto, valores: Datos, ambito: AmbitoOpcional | null): FormGroup {
    // Los obligatorios de un objeto opcional dependen de que ese objeto se haya empezado a llenar.
    const propio: AmbitoOpcional | null = esOpcional(nodo)
      ? { nodo, grupo: null, exigentes: [] }
      : ambito;

    const controles: Record<string, AbstractControl> = {};
    for (const hijo of nodo.hijos) {
      controles[hijo.clave] = this.crearControl(hijo, valores[hijo.clave], propio);
    }
    const grupo = new FormGroup(controles);

    if (propio && propio !== ambito) {
      propio.grupo = grupo;
      this.ambitos.push(propio);
      // Al llenar o vaciar el objeto cambia qué campos suyos son obligatorios.
      this.suscripciones.add(grupo.valueChanges.subscribe(() => this.revalidar(propio)));
    }

    // La visibilidad depende de los campos que deciden alguna condición del objeto.
    const decisores = new Set(
      nodo.reglas.flatMap((regla) => Object.keys(regla.cuando.propiedades)),
    );
    for (const clave of decisores) {
      const decisor = grupo.controls[clave];
      if (!decisor) continue;
      this.suscripciones.add(
        decisor.valueChanges.subscribe(() => this.actualizarVisibilidad(nodo, grupo)),
      );
    }

    return grupo;
  }

  private crearArreglo(nodo: NodoArreglo, valores: unknown[]): FormArray {
    const lista = new FormArray<AbstractControl>([], validadoresDeArreglo(nodo));

    const instancias = valores.filter(esDatos);
    // Una lista que exige instancias empieza con las necesarias, vacías.
    while (instancias.length < nodo.minimoElementos) instancias.push({});

    for (const datos of instancias) {
      lista.push(this.crearObjeto(nodo.elemento, datos, null));
    }
    return lista;
  }

  private vaciar(nodo: NodoObjeto, grupo: FormGroup): void {
    for (const hijo of nodo.hijos) {
      const control = grupo.controls[hijo.clave];
      if (!control) continue;

      switch (hijo.clase) {
        case 'campo':
          if (hijo.soloLectura || hijo.tipo === 'uuid') break;
          control.setValue(hijo.tipo === 'seleccionMultiple' ? [] : null);
          control.markAsPristine();
          control.markAsUntouched();
          break;
        case 'objeto':
          this.vaciar(hijo, control as FormGroup);
          break;
        case 'arreglo': {
          const lista = control as FormArray;
          while (lista.length > hijo.minimoElementos) lista.removeAt(lista.length - 1);
          for (const instancia of lista.controls)
            this.vaciar(hijo.elemento, instancia as FormGroup);
          break;
        }
      }
    }
  }

  private presente(ambito: AmbitoOpcional): boolean {
    return (
      ambito.grupo !== null && valorDelNodo(ambito.nodo, ambito.grupo, this.ocultos) !== undefined
    );
  }

  private revalidar(ambito: AmbitoOpcional): void {
    for (const control of ambito.exigentes) {
      control.updateValueAndValidity({ emitEvent: false });
    }
  }

  /** Recalcula la validez de los obligatorios condicionados, una vez que existen los grupos. */
  private revalidarExigencias(desde: number): void {
    for (const ambito of this.ambitos.slice(desde)) this.revalidar(ambito);
  }

  /** Aplica la visibilidad a un objeto y a todos los objetos que contiene. */
  private actualizarVisibilidadInicial(nodo: NodoObjeto, grupo: FormGroup): void {
    this.actualizarVisibilidad(nodo, grupo);

    for (const hijo of nodo.hijos) {
      const control = grupo.controls[hijo.clave];
      if (!control) continue;
      if (hijo.clase === 'objeto') {
        this.actualizarVisibilidadInicial(hijo, control as FormGroup);
      } else if (hijo.clase === 'arreglo') {
        for (const instancia of (control as FormArray).controls) {
          this.actualizarVisibilidadInicial(hijo.elemento, instancia as FormGroup);
        }
      }
    }
  }

  private actualizarVisibilidad(nodo: NodoObjeto, grupo: FormGroup): void {
    if (nodo.reglas.length === 0) return;

    const visibles = calcularVisibilidad(nodo, (clave) => grupo.controls[clave]?.value);

    for (const hijo of nodo.hijos) {
      const control = grupo.controls[hijo.clave];
      if (!control) continue;

      const visible = visibles.get(hijo.clave) ?? true;
      if (visible) {
        this.ocultos.delete(control);
        if (control.disabled && !hijo.soloLectura) {
          control.enable({ emitEvent: false });
          // `enable` es recursivo: hay que volver a ocultar lo que sus propias condiciones ocultan y
          // a bloquear los datos precargados que contiene.
          if (hijo.clase === 'objeto') this.restaurarSubarbol(hijo, control as FormGroup);
          if (hijo.clase === 'arreglo') {
            for (const instancia of (control as FormArray).controls) {
              this.restaurarSubarbol(hijo.elemento, instancia as FormGroup);
            }
          }
        }
      } else {
        this.ocultos.add(control);
        if (control.enabled) control.disable({ emitEvent: false });
      }
    }

    grupo.updateValueAndValidity({ emitEvent: false });
  }

  private restaurarSubarbol(nodo: NodoObjeto, grupo: FormGroup): void {
    this.reaplicarSoloLecturaDeDatosPrecargados(nodo, grupo);
    this.actualizarVisibilidadInicial(nodo, grupo);
  }

  private reaplicarSoloLecturaDeDatosPrecargados(nodo: NodoObjeto, grupo: FormGroup): void {
    for (const hijo of nodo.hijos) {
      const control = grupo.controls[hijo.clave];
      if (!control) continue;
      if (hijo.clase === 'campo' && hijo.soloLectura) control.disable({ emitEvent: false });
      if (hijo.clase === 'objeto') {
        this.reaplicarSoloLecturaDeDatosPrecargados(hijo, control as FormGroup);
      }
      if (hijo.clase === 'arreglo') {
        for (const instancia of (control as FormArray).controls) {
          this.reaplicarSoloLecturaDeDatosPrecargados(hijo.elemento, instancia as FormGroup);
        }
      }
    }
  }
}

export function construirFormulario(
  esquema: EsquemaResuelto,
  valoresIniciales: Datos | null = null,
  opciones: OpcionesConstruccion = {},
): FormularioConstruido {
  return new FormularioConstruido(esquema, valoresIniciales, opciones);
}
