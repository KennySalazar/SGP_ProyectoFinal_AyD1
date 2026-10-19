import { InjectionToken, Signal } from '@angular/core';
import { AbstractControl, FormArray, FormGroup } from '@angular/forms';
import {
  DestinoElementoObservacion,
  ObservacionPresentacion,
} from '../../models/observacion.models';
import { AnclajeFormulario, crearAnclajeFormulario } from '../../utils/anclaje-formulario';

/** Integración opcional: sin proveedor el formulario mantiene su presentación habitual. */
export interface ContextoObservacionesFormulario {
  readonly puedeEscribir: Signal<boolean>;
  readonly observacionesPresentacion: Signal<readonly ObservacionPresentacion[]>;
  readonly destinoNavegacion: Signal<DestinoElementoObservacion | null>;
  anclajeDisponible(anclaje: AnclajeFormulario): boolean;
  seleccionarElemento(destino: DestinoElementoObservacion): void;
  abrirPanel(): void;
}
export const CONTEXTO_OBSERVACIONES = new InjectionToken<ContextoObservacionesFormulario>(
  'Observaciones del formulario',
);

/** Recupera claves reales y UUID desde la ascendencia local, nunca desde índices o búsquedas globales. */
export function anclajeDeControl(control: AbstractControl): AnclajeFormulario | null {
  const segmentos: string[] = [];
  let actual = control;
  let instancia: string | undefined;
  while (actual.parent) {
    const padre = actual.parent;
    if (padre instanceof FormArray) {
      if (instancia !== undefined || !(actual instanceof FormGroup)) return null;
      const id: unknown = actual.controls['instancia_id']?.value;
      if (typeof id !== 'string' || !id) return null;
      instancia = id;
    } else if (padre instanceof FormGroup) {
      const clave = Object.keys(padre.controls).find((k) => padre.controls[k] === actual);
      if (clave === undefined) return null;
      segmentos.unshift(clave);
    }
    actual = padre;
  }
  // El contenedor repetible necesita una instancia concreta: lo ofrece cada objeto hijo.
  if (control instanceof FormArray) return null;
  try {
    return crearAnclajeFormulario(segmentos, instancia);
  } catch {
    return null;
  }
}
export function mismoAnclaje(a: AnclajeFormulario, b: AnclajeFormulario): boolean {
  return a.elementoRef === b.elementoRef && a.instanciaElementoId === b.instanciaElementoId;
}
