import { InjectionToken, Signal } from '@angular/core';
import { AnclajeFormulario } from '../../utils/anclaje-formulario';

/**
 * Integración opcional del formulario con los comentarios anclados de una inspección publicada
 * (HU-064, RN-FOR-02). Sin proveedor el formulario mantiene su presentación habitual; quien lo
 * provee decide de dónde salen los totales y qué hace al elegir un elemento.
 */
export interface ContextoComentariosFormulario {
  readonly puedeComentar: Signal<boolean>;
  /** Comentarios del ancla, respuestas incluidas; 0 si no tiene. */
  totalDe(anclaje: AnclajeFormulario): number;
  /** Cómo se nombra el campo o elemento, con su instancia si es repetible («Pilas · Pila 2»). */
  etiquetaDe(anclaje: AnclajeFormulario): string;
  /** El usuario quiere ver o dejar comentarios sobre este campo o elemento. */
  seleccionar(anclaje: AnclajeFormulario): void;
}

export const CONTEXTO_COMENTARIOS = new InjectionToken<ContextoComentariosFormulario>(
  'Comentarios anclados del formulario',
);
