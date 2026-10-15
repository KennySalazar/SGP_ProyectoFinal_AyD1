import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { NodoArreglo, NodoCampo } from '../esquema/modelos';

/** Claves de error que produce el motor; la interfaz las traduce a mensajes en español. */
export type ClaveError =
  | 'requerido'
  | 'minimo'
  | 'maximo'
  | 'minimoExclusivo'
  | 'maximoExclusivo'
  | 'longitudMinima'
  | 'longitudMaxima'
  | 'entero'
  | 'fechaInvalida'
  | 'minimoElementos'
  | 'maximoElementos'
  | 'maximoSiContiene';

export function vacio(valor: unknown): boolean {
  return (
    valor === null ||
    valor === undefined ||
    valor === '' ||
    (Array.isArray(valor) && valor.length === 0)
  );
}

const FECHA_ISO = /^\d{4}-\d{2}-\d{2}$/;

function fechaValida(texto: string): boolean {
  if (!FECHA_ISO.test(texto)) return false;
  const fecha = new Date(`${texto}T00:00:00Z`);
  return !Number.isNaN(fecha.getTime()) && fecha.toISOString().startsWith(texto);
}

export const requerido: ValidatorFn = (control) =>
  vacio(control.value) ? { requerido: true } : null;

/**
 * Validadores de un campo: salvo la obligatoriedad, solo evalúan cuando hay valor. `exigido`
 * permite suspender la obligatoriedad mientras no corresponda, por ejemplo cuando el campo está
 * dentro de un objeto opcional que todavía no se empezó a llenar.
 */
export function validadoresDeCampo(nodo: NodoCampo, exigido?: () => boolean): ValidatorFn[] {
  const validadores: ValidatorFn[] = [];

  if (nodo.obligatorio || nodo.condicional) {
    validadores.push(exigido ? (control) => (exigido() ? requerido(control) : null) : requerido);
  }

  if (nodo.tipo === 'numero' || nodo.tipo === 'entero') {
    validadores.push((control) => {
      const valor = control.value as unknown;
      if (vacio(valor)) return null;

      const errores: ValidationErrors = {};
      if (typeof valor !== 'number' || !Number.isFinite(valor)) return { entero: true };
      if (nodo.tipo === 'entero' && !Number.isInteger(valor)) errores['entero'] = true;
      if (nodo.minimo !== undefined && valor < nodo.minimo) {
        errores['minimo'] = { minimo: nodo.minimo };
      }
      if (nodo.maximo !== undefined && valor > nodo.maximo) {
        errores['maximo'] = { maximo: nodo.maximo };
      }
      if (nodo.minimoExclusivo !== undefined && valor <= nodo.minimoExclusivo) {
        errores['minimoExclusivo'] = { minimo: nodo.minimoExclusivo };
      }
      if (nodo.maximoExclusivo !== undefined && valor >= nodo.maximoExclusivo) {
        errores['maximoExclusivo'] = { maximo: nodo.maximoExclusivo };
      }
      return Object.keys(errores).length > 0 ? errores : null;
    });
  }

  if (nodo.tipo === 'texto') {
    validadores.push((control) => {
      const valor = control.value as unknown;
      if (typeof valor !== 'string' || valor.length === 0) return null;

      const errores: ValidationErrors = {};
      if (nodo.longitudMinima !== undefined && valor.length < nodo.longitudMinima) {
        errores['longitudMinima'] = { minimo: nodo.longitudMinima };
      }
      if (nodo.longitudMaxima !== undefined && valor.length > nodo.longitudMaxima) {
        errores['longitudMaxima'] = { maximo: nodo.longitudMaxima };
      }
      return Object.keys(errores).length > 0 ? errores : null;
    });
  }

  if (nodo.tipo === 'fecha') {
    validadores.push((control) => {
      const valor = control.value as unknown;
      if (vacio(valor)) return null;
      return typeof valor === 'string' && fechaValida(valor) ? null : { fechaInvalida: true };
    });
  }

  if (nodo.tipo === 'seleccionMultiple') {
    validadores.push((control) => {
      const valor = control.value as unknown;
      if (!Array.isArray(valor) || valor.length === 0) return null;

      const errores: ValidationErrors = {};
      if (nodo.minimoElementos !== undefined && valor.length < nodo.minimoElementos) {
        errores['minimoElementos'] = { minimo: nodo.minimoElementos };
      }
      if (nodo.maximoElementos !== undefined && valor.length > nodo.maximoElementos) {
        errores['maximoElementos'] = { maximo: nodo.maximoElementos };
      }
      for (const regla of nodo.reglasLista) {
        if (valor.includes(regla.contiene) && valor.length > regla.maximo) {
          errores['maximoSiContiene'] = { valor: regla.contiene, maximo: regla.maximo };
        }
      }
      return Object.keys(errores).length > 0 ? errores : null;
    });
  }

  return validadores;
}

/** Validadores de una lista repetible: cantidad mínima y máxima de instancias. */
export function validadoresDeArreglo(nodo: NodoArreglo): ValidatorFn[] {
  return [
    (control: AbstractControl) => {
      const cantidad = (control.value as unknown[]).length;
      const errores: ValidationErrors = {};
      if (cantidad < nodo.minimoElementos) {
        errores['minimoElementos'] = { minimo: nodo.minimoElementos };
      }
      if (nodo.maximoElementos !== undefined && cantidad > nodo.maximoElementos) {
        errores['maximoElementos'] = { maximo: nodo.maximoElementos };
      }
      return Object.keys(errores).length > 0 ? errores : null;
    },
  ];
}
