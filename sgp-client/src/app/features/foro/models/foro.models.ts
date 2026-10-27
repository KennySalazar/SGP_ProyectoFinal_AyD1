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
export const LIMITE_MOTIVO_MODERACION = 500;

/** Roles que moderan el foro: ocultan comentarios y cierran hilos (RN-FOR-08). */
export const ROLES_MODERADORES = ['CATEDRATICO', 'ADMINISTRADOR'];

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
  /** Por qué un moderador cerró el hilo; nulo mientras está abierto (RN-FOR-08). */
  motivoCierre: string | null;
  totalComentarios: number;
}

/**
 * Comentario o respuesta; `padreId` es nulo en un comentario principal. `contenido` es nulo si se
 * eliminó o si está oculto y quien lee no modera el foro (RN-FOR-05, RN-FOR-08).
 */
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
  contenido: string | null;
  creadoEn: string;
  editadoEn: string | null;
  /** Hasta cuándo su autor puede editarlo, según la hora del servidor (RN-FOR-05). */
  editableHasta: string;
  eliminado: boolean;
  oculto: boolean;
  /** Solo llega a los moderadores. */
  motivoOcultamiento: string | null;
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

/** Inspección publicada sin revisión previa, para moderar su hilo a posteriori (RN-REV-02). */
export interface InspeccionSinRevision {
  inspeccionId: string;
  puenteId: string;
  puenteCodigo: string;
  puenteNombre: string;
  fechaInspeccion: string;
  publicadoEn: string;
  autorUsuario: string | null;
  autorRol: string;
  /** Nulo si su hilo aún no se abrió. */
  hiloId: string | null;
  totalComentarios: number;
}
