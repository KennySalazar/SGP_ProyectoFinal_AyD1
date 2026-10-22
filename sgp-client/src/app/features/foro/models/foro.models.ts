/** Categorías en que se clasifica un hilo (RN-FOR-06). */
export type CategoriaHilo =
  'DISCUSION_TECNICA' | 'PROPUESTA_INTERVENCION' | 'CONSULTA' | 'REFERENCIA_DOCUMENTAL' | 'ALERTA';

export const CATEGORIAS_HILO: CategoriaHilo[] = [
  'DISCUSION_TECNICA',
  'PROPUESTA_INTERVENCION',
  'CONSULTA',
  'REFERENCIA_DOCUMENTAL',
  'ALERTA',
];

/** Límites que también aplica el servidor (422 si se exceden). */
export const LIMITE_TITULO_HILO = 120;
export const LIMITE_COMENTARIO = 2000;

/** Hilo del foro. El autor se identifica por su nombre de usuario, nulo si aún no lo tiene. */
export interface HiloForo {
  id: string;
  puenteId: string;
  inspeccionId: string | null;
  categoria: CategoriaHilo;
  titulo: string;
  autorId: string;
  autorUsuario: string | null;
  autorRol: string;
  creadoEn: string;
  actualizadoEn: string;
  cerrado: boolean;
  totalComentarios: number;
}

/** Comentario o respuesta; `padreId` es nulo en un comentario principal. */
export interface ComentarioForo {
  id: string;
  hiloId: string;
  padreId: string | null;
  /** Campo o elemento de la inspección al que se ancla (HU-064); nulo si no está anclado. */
  elementoRef: string | null;
  instanciaElementoId: string | null;
  autorId: string;
  autorUsuario: string | null;
  autorRol: string;
  contenido: string;
  creadoEn: string;
}

export interface CrearHiloRequest {
  titulo: string;
  categoria: CategoriaHilo;
  comentario: string;
}

export interface CrearComentarioRequest {
  contenido: string;
  padreId: string | null;
  /** Solo en el hilo de una inspección y en un comentario principal (RN-FOR-02). */
  elementoRef?: string | null;
  instanciaElementoId?: string | null;
}

/**
 * Campo o elemento de una inspección al que se ancla un comentario. `instanciaElementoId` solo
 * existe en los elementos repetibles (una pila concreta, un tramo concreto).
 */
export interface AnclaForo {
  elementoRef: string;
  instanciaElementoId: string | null;
}

/** Cuántos comentarios tiene un ancla, respuestas incluidas: alimenta el marcador junto al dato. */
export interface AnclaConTotal extends AnclaForo {
  total: number;
}

/** El hilo de una inspección publicada y el resumen de sus comentarios anclados (HU-064). */
export interface HiloInspeccionForo {
  hilo: HiloForo;
  anclas: AnclaConTotal[];
}

/**
 * Qué comentarios del hilo de una inspección se piden además de un elemento: los de una sección
 * (su clave, sin barras: «subestructura») o los que no están anclados. Se usa uno solo a la vez.
 */
export interface FiltroSeccion {
  seccion?: string;
  soloGenerales?: boolean;
}

/** Cuántos comentarios tiene una sección del formulario, respuestas incluidas. */
export interface SeccionConTotal {
  clave: string;
  titulo: string;
  total: number;
}

/** Lo que ofrece el selector del hilo de una inspección: todos, los generales o una sección. */
export interface OpcionesFiltroForo {
  todos: number;
  generales: number;
  secciones: SeccionConTotal[];
}

export function mismaAncla(a: AnclaForo, b: AnclaForo): boolean {
  return a.elementoRef === b.elementoRef && a.instanciaElementoId === b.instanciaElementoId;
}
