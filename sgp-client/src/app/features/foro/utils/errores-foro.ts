import { HttpErrorResponse } from '@angular/common/http';
import { CategoriaHilo } from '../models/foro.models';

/** Lo que el servidor explica de un rechazo: el detalle por campo y el motivo general. */
export interface ProblemaForo {
  campos: Record<string, string>;
  mensaje: string | null;
  codigo: string | null;
}

/** Lee un RFC 7807 del backend: los 422 traen `errores` por campo; los demás, un `detail`. */
export function leerProblema(err: HttpErrorResponse): ProblemaForo {
  const problema = err.error as {
    detail?: string;
    code?: string;
    errores?: { campo: string; mensaje: string }[];
  } | null;
  const campos: Record<string, string> = {};
  for (const error of problema?.errores ?? []) campos[error.campo] = error.mensaje;
  return {
    campos,
    mensaje: Object.keys(campos).length > 0 ? null : (problema?.detail ?? null),
    codigo: problema?.code ?? null,
  };
}

/** Severidad de la etiqueta de cada categoría; siempre va acompañada de su texto. */
export function severidadCategoria(categoria: CategoriaHilo): 'warn' | 'info' | 'secondary' {
  switch (categoria) {
    case 'ALERTA':
      return 'warn';
    case 'PROPUESTA_INTERVENCION':
      return 'info';
    default:
      return 'secondary';
  }
}
