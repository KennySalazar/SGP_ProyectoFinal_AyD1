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
}
