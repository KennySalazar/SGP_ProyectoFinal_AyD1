import { AnclaForo } from '../models/foro.models';

/** Nombre de un ancla cuando quien usa el foro no sabe darle uno mejor: su último segmento. */
export function etiquetaPorOmision(ancla: AnclaForo): string {
  const segmento = ancla.elementoRef.split('/').at(-1) ?? ancla.elementoRef;
  return segmento.replace(/_/g, ' ');
}
