import { AbstractControl, FormArray, FormGroup } from '@angular/forms';
import { NodoCampo, NodoFormulario, NodoObjeto } from '../esquema/modelos';
import { vacio } from './validadores';
import { ConjuntoOcultos } from './valor';

/**
 * Captura de daños por elemento (HU-034). Un elemento evaluable agrupa sus indicadores de daño
 * (los campos que declaran qué es «sano» con `x-sgp-sin-danos`) y, a veces, un campo que decide si
 * el elemento existe. Nada de esto nombra un campo en particular: todo sale del esquema.
 */

/** Campo y valor con los que el elemento existe y se evalúa (p. ej. `existencia = PRESENTE`). */
export interface Activacion {
  clave: string;
  valor: unknown;
}

/** Indicadores de daño de un elemento: los campos que declaran su valor «sin daño». */
export function indicadoresDe(nodo: NodoObjeto): NodoCampo[] {
  return nodo.hijos.filter(
    (hijo): hijo is NodoCampo => hijo.clase === 'campo' && hijo.sinDanos !== undefined,
  );
}

/** Un elemento evaluable que se captura con la grilla de daños. */
export function esElementoEvaluable(nodo: NodoFormulario): boolean {
  return nodo.clase === 'objeto' && nodo.evaluable && indicadoresDe(nodo).length > 0;
}

/**
 * Condición que hace existir al elemento: una regla cuyo único decisor tiene un valor fijo, que
 * exige campos y que prohíbe todo lo demás en caso contrario. `null` si el elemento siempre existe.
 */
export function activacionDe(nodo: NodoObjeto): Activacion | null {
  for (const regla of nodo.reglas) {
    const condiciones = Object.entries(regla.cuando.propiedades);
    if (condiciones.length !== 1) continue;

    const [clave, restriccion] = condiciones[0];
    const apagaElResto = regla.sino.soloDiscriminadores || regla.sino.prohibidos.length > 0;
    if ('const' in restriccion && regla.entonces.requeridos.length > 0 && apagaElResto) {
      return { clave, valor: restriccion.const };
    }
  }
  return null;
}

export type TipoEstadoElemento = 'pendiente' | 'en-proceso' | 'sano' | 'con-danos' | 'no-presente';

export interface EstadoElemento {
  tipo: TipoEstadoElemento;
  /** Indicadores visibles que ya tienen respuesta. */
  marcados: number;
  /** Indicadores visibles. */
  total: number;
  /** Indicadores con un valor distinto de «sin daño». */
  danos: number;
  /** Valor de la existencia cuando el elemento no está presente (p. ej. «NO_APLICA»). */
  noPresente?: unknown;
}

/**
 * Estado de revisión de un elemento. Se deriva de lo capturado, así que editar cualquier
 * indicador de un elemento «sin daños» lo deja de marcar como sano y conserva el cambio.
 */
export function estadoElemento(
  nodo: NodoObjeto,
  grupo: FormGroup,
  ocultos: ConjuntoOcultos,
): EstadoElemento {
  const activacion = activacionDe(nodo);
  if (activacion) {
    const valor = grupo.controls[activacion.clave]?.value as unknown;
    if (!vacio(valor) && !equivalente(valor, activacion.valor)) {
      return { tipo: 'no-presente', marcados: 0, total: 0, danos: 0, noPresente: valor };
    }
  }

  const visibles = indicadoresDe(nodo).filter((indicador) => {
    const control = grupo.controls[indicador.clave];
    return control !== undefined && !ocultos.has(control);
  });

  let marcados = 0;
  let danos = 0;
  for (const indicador of visibles) {
    const valor = grupo.controls[indicador.clave].value as unknown;
    if (vacio(valor)) continue;
    marcados++;
    if (!equivalente(valor, indicador.sinDanos)) danos++;
  }

  const total = visibles.length;
  let tipo: TipoEstadoElemento;
  if (marcados === 0) tipo = 'pendiente';
  else if (danos > 0) tipo = 'con-danos';
  else tipo = marcados === total ? 'sano' : 'en-proceso';

  return { tipo, marcados, total, danos };
}

/** `true` si el elemento ya se revisó por completo (con daños, sin ellos o porque no está). */
export function estaRevisado(estado: EstadoElemento): boolean {
  return (
    estado.tipo === 'sano' ||
    estado.tipo === 'no-presente' ||
    (estado.tipo === 'con-danos' && estado.marcados === estado.total)
  );
}

export interface ResumenElementos {
  total: number;
  revisados: number;
}

/** Cuenta los elementos evaluables de una sección y cuántos ya se revisaron. */
export function resumirElementos(
  nodo: NodoFormulario,
  control: AbstractControl,
  ocultos: ConjuntoOcultos,
): ResumenElementos {
  const resumen: ResumenElementos = { total: 0, revisados: 0 };
  acumular(nodo, control, ocultos, resumen);
  return resumen;
}

function acumular(
  nodo: NodoFormulario,
  control: AbstractControl,
  ocultos: ConjuntoOcultos,
  resumen: ResumenElementos,
): void {
  if (ocultos.has(control)) return;

  if (nodo.clase === 'arreglo') {
    for (const instancia of (control as FormArray).controls) {
      acumular(nodo.elemento, instancia, ocultos, resumen);
    }
    return;
  }
  if (nodo.clase !== 'objeto') return;

  const grupo = control as FormGroup;
  if (esElementoEvaluable(nodo)) {
    resumen.total++;
    if (estaRevisado(estadoElemento(nodo, grupo, ocultos))) resumen.revisados++;
    return;
  }
  for (const hijo of nodo.hijos) {
    const hija = grupo.controls[hijo.clave];
    if (hija) acumular(hijo, hija, ocultos, resumen);
  }
}

/** Igualdad de valores de campos: escalares o listas de opciones sin importar el orden. */
export function equivalente(a: unknown, b: unknown): boolean {
  if (Array.isArray(a) && Array.isArray(b)) {
    return a.length === b.length && [...a].sort().join('\u0000') === [...b].sort().join('\u0000');
  }
  return a === b;
}

/**
 * Gravedad de cada opción de un indicador: `g0` es la sana y `g1` a `g3` suben. Con una sola
 * opción dañada es la grave; con varias, suben de leve a grave. Sirve para colorear (el nombre
 * siempre se muestra aparte, nunca solo el color).
 */
export function gravedades(opciones: readonly { valor: unknown }[], sano: unknown): string[] {
  const conDano = opciones.filter((opcion) => !equivalente(opcion.valor, sano));
  return opciones.map((opcion) => {
    if (equivalente(opcion.valor, sano)) return 'g0';
    const desde = conDano.length >= 3 ? 1 : 4 - conDano.length;
    return `g${Math.min(3, conDano.indexOf(opcion) + desde)}`;
  });
}
