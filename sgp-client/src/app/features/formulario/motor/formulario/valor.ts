import { AbstractControl, FormArray, FormGroup } from '@angular/forms';
import { NodoFormulario, NodoObjeto } from '../esquema/modelos';
import { vacio } from './validadores';

export type ConjuntoOcultos = WeakSet<AbstractControl>;

/** Un objeto opcional solo exige sus propios obligatorios cuando se empieza a llenar. */
export function esOpcional(nodo: NodoObjeto): boolean {
  return nodo.opcional && !nodo.condicional;
}

/**
 * Valor que se envía al backend: omite lo oculto por condiciones, los campos vacíos y los
 * objetos opcionales sin contenido. Los datos precargados (solo lectura) sí se incluyen.
 */
export function valorDelNodo(
  nodo: NodoFormulario,
  control: AbstractControl,
  ocultos: ConjuntoOcultos,
): unknown {
  if (ocultos.has(control)) return undefined;

  switch (nodo.clase) {
    case 'campo': {
      const valor = control.value as unknown;
      return vacio(valor) ? undefined : valor;
    }
    case 'objeto': {
      const grupo = control as FormGroup;
      const salida: Record<string, unknown> = {};
      for (const hijo of nodo.hijos) {
        const hija = grupo.controls[hijo.clave];
        if (!hija) continue;
        const valor = valorDelNodo(hijo, hija, ocultos);
        if (valor !== undefined) salida[hijo.clave] = valor;
      }
      const requerido = nodo.obligatorio || nodo.condicional;
      return Object.keys(salida).length === 0 && !requerido ? undefined : salida;
    }
    case 'arreglo': {
      const lista = control as FormArray;
      const salida = lista.controls
        .map((instancia) => valorDelNodo(nodo.elemento, instancia, ocultos))
        .filter((valor) => valor !== undefined);
      return salida.length === 0 && !nodo.obligatorio ? undefined : salida;
    }
  }
}
