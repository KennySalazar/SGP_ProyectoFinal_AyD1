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

/**
 * Renderizadores que trae el propio formulario dinámico (la grilla de daños de un elemento). Van
 * aparte de `RENDERIZADORES_NODO` para que quien lo use pueda sumar los suyos, que tienen
 * prioridad, sin reemplazar a estos.
 */
export const RENDERIZADORES_PREDETERMINADOS = new InjectionToken<RenderizadorNodo[]>(
  'RENDERIZADORES_PREDETERMINADOS',
);
