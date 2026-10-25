import { NodoFormulario, EsquemaJson } from '../motor/esquema/modelos';
import { humanizar, resolverEsquema } from '../motor/esquema/resolutor';

export function nombresMapeo(esquema: EsquemaJson | null): Map<string, string> {
  const nombres = new Map<string, string>();
  if (!esquema) return nombres;
  try {
    const visitar = (nodo: NodoFormulario, ruta: string, titulos: string[]): void => {
      if (ruta) nombres.set(ruta, titulos.join(' > '));
      if (nodo.clase === 'objeto') {
        for (const hijo of nodo.hijos)
          visitar(hijo, `${ruta}/${hijo.clave}`, [
            ...titulos,
            hijo.titulo || humanizar(hijo.clave),
          ]);
      } else if (nodo.clase === 'arreglo') {
        visitar(nodo.elemento, `${ruta}/*`, titulos);
      }
    };
    visitar(resolverEsquema(esquema).raiz, '', []);
  } catch {
    return nombres;
  }
  return nombres;
}

export function etiquetaMapeo(
  ruta: string | null | undefined,
  nombres: Map<string, string>,
): string {
  if (!ruta) return '—';
  return nombres.get(ruta) || ruta.split('/').filter(Boolean).map(humanizar).join(' > ');
}
