import { AbstractControl, FormArray, FormGroup } from '@angular/forms';
import { NodoCampo, NodoFormulario } from '../esquema/modelos';
import { FormularioConstruido } from '../formulario/constructor';
import { vacio } from '../formulario/validadores';

function valorValido(nodo: NodoCampo): unknown {
  switch (nodo.tipo) {
    case 'booleano':
      return true;
    case 'fecha':
      return '2026-01-15';
    case 'enumerado': {
      const valores = nodo.opciones.map((opcion) => opcion.valor);
      // Declarar el elemento presente muestra sus indicadores, que es el caso más completo.
      return valores.includes('PRESENTE') ? 'PRESENTE' : valores[0];
    }
    case 'seleccionMultiple':
      return [nodo.opciones[0].valor];
    case 'entero':
    case 'numero': {
      let valor = nodo.minimo ?? 1;
      if (nodo.minimoExclusivo !== undefined) valor = nodo.minimoExclusivo + 1;
      if (nodo.maximo !== undefined) valor = Math.min(valor, nodo.maximo);
      return nodo.tipo === 'entero' ? Math.ceil(valor) : valor;
    }
    case 'texto':
      return 'x'.repeat(Math.max(nodo.longitudMinima ?? 1, 1));
    case 'uuid':
      return undefined;
  }
}

/**
 * Llena todos los campos visibles con valores válidos. Repite hasta que no aparezcan campos
 * nuevos, porque responder una condición (por ejemplo, la existencia de un elemento) muestra otros.
 */
export function llenarFormulario(construido: FormularioConstruido): void {
  for (let pasada = 0; pasada < 8; pasada++) {
    const cambios = llenar(construido.esquema.raiz, construido.form, construido);
    if (cambios === 0) return;
  }
}

function llenar(
  nodo: NodoFormulario,
  control: AbstractControl,
  construido: FormularioConstruido,
): number {
  if (construido.ocultos.has(control)) return 0;

  if (nodo.clase === 'campo') {
    if (control.disabled || !vacio(control.value as unknown)) return 0;
    const valor = valorValido(nodo);
    if (valor === undefined) return 0;
    control.setValue(valor);
    return 1;
  }

  let cambios = 0;
  if (nodo.clase === 'objeto') {
    const grupo = control as FormGroup;
    for (const hijo of nodo.hijos) {
      const hija = grupo.controls[hijo.clave];
      if (hija) cambios += llenar(hijo, hija, construido);
    }
    return cambios;
  }

  for (const instancia of (control as FormArray).controls) {
    cambios += llenar(nodo.elemento, instancia, construido);
  }
  return cambios;
}
