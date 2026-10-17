import { Type } from '@angular/core';

/**
 * Sección que no sale del esquema JSON pero forma parte del recorrido del formulario, como el
 * reporte fotográfico (RN-FRM-07), cuyos archivos gestiona el módulo de archivos y no el esquema.
 * Se agrega tras las secciones del esquema; no cuenta para el avance ni para la revisión.
 */
export interface SeccionApoyo {
  /** Identificador estable de la sección. */
  clave: string;
  titulo: string;
  descripcion?: string;
  /** Componente que se dibuja como contenido de la sección. */
  componente: Type<unknown>;
  /** Entradas que recibe el componente. */
  entradas?: Record<string, unknown>;
}
