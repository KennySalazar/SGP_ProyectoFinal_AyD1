import { AbstractControl, FormArray, FormGroup } from '@angular/forms';
import { NodoFormulario } from '../esquema/modelos';
import { ConjuntoOcultos, esOpcional, valorDelNodo } from './valor';
import { vacio } from './validadores';

export interface Avance {
  /** Campos visibles que hay que llenar para poder enviar. */
  obligatorios: number;
  /** De esos, los que ya tienen un valor válido. */
  completos: number;
  /** Campos con un valor que incumple alguna regla (rango, longitud, etc.). */
  invalidos: number;
}

export const AVANCE_VACIO: Avance = { obligatorios: 0, completos: 0, invalidos: 0 };

/** Cuenta cuántos campos obligatorios de una sección están completos. */
export function calcularAvance(
  nodo: NodoFormulario,
  control: AbstractControl,
  ocultos: ConjuntoOcultos,
): Avance {
  const avance: Avance = { obligatorios: 0, completos: 0, invalidos: 0 };
  acumular(nodo, control, ocultos, avance);
  return avance;
}

function acumular(
  nodo: NodoFormulario,
  control: AbstractControl,
  ocultos: ConjuntoOcultos,
  avance: Avance,
): void {
  if (ocultos.has(control)) return;

  switch (nodo.clase) {
    case 'campo': {
      // El identificador de instancia lo genera el cliente: no es algo que el usuario deba llenar.
      if (nodo.tipo === 'uuid') return;

      const conValor = !vacio(control.value as unknown);
      // Los controles deshabilitados (datos precargados) no ejecutan validadores.
      const valido = control.disabled || control.valid;

      if (nodo.obligatorio || nodo.condicional) {
        avance.obligatorios++;
        if (conValor && valido) avance.completos++;
      }
      if (conValor && !valido) avance.invalidos++;
      return;
    }
    case 'objeto': {
      // Un objeto opcional sin llenar no exige nada: se omite del valor que se envía.
      if (esOpcional(nodo) && valorDelNodo(nodo, control, ocultos) === undefined) return;

      const grupo = control as FormGroup;
      for (const hijo of nodo.hijos) {
        const hija = grupo.controls[hijo.clave];
        if (hija) acumular(hijo, hija, ocultos, avance);
      }
      return;
    }
    case 'arreglo': {
      const lista = control as FormArray;
      if (nodo.obligatorio && lista.length < nodo.minimoElementos) {
        avance.obligatorios += nodo.minimoElementos - lista.length;
      }
      for (const instancia of lista.controls) {
        acumular(nodo.elemento, instancia, ocultos, avance);
      }
      return;
    }
  }
}
