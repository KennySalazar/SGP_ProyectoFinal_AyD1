import { InjectionToken, Type } from '@angular/core';
import { NodoFormulario } from '../motor/esquema/modelos';

/**
 * Permite sustituir cómo se dibuja un nodo del formulario sin tocar el motor. Por ejemplo, la
 * grilla táctil de daños (HU-034) reemplaza el grupo de un elemento evaluable.
 *
 * El componente registrado recibe como entradas `nodo`, `control` y `construido`.
 */
export interface RenderizadorNodo {
  coincide: (nodo: NodoFormulario) => boolean;
  componente: Type<unknown>;
}

export const RENDERIZADORES_NODO = new InjectionToken<RenderizadorNodo[]>('RENDERIZADORES_NODO');
