import { EsquemaJson, NodoFormulario } from '../motor/esquema/modelos';
import { humanizar, resolverEsquema } from '../motor/esquema/resolutor';

/** Campo o elemento de una inspección, con la instancia concreta si es repetible. */
export interface AnclaEtiquetable {
  readonly elementoRef: string;
  readonly instanciaElementoId?: string | null;
}

/** Separa los niveles de la ruta de un dato: «Sub-estructura › Estribo de entrada › Columnas». */
export const SEPARADOR_RUTA = ' › ';

/**
 * Nombra un ancla con su ruta completa, como la ve quien llena el formulario: de la sección hasta
 * el dato, con los títulos del esquema y, en un elemento repetible, qué instancia es («Sub-estructura
 * › Pilas · Pila 2 › Columnas › Existencia del elemento»). El nombre del último nivel casi nunca
 * basta: en el formulario real «Existencia del elemento» se repite 37 veces y «Hay» o «Descripción»
 * decenas, así que sin sus niveles de arriba no dicen a qué dato se refieren.
 *
 * Si el esquema no conoce un nivel, humaniza su clave en lugar de mostrar la referencia técnica.
 */
export function crearEtiquetadorAncla(
  esquema: EsquemaJson | null | undefined,
  datos: unknown,
): (ancla: AnclaEtiquetable) => string {
  const nodos = new Map<string, NodoFormulario>();
  if (esquema) {
    try {
      registrar(resolverEsquema(esquema).raiz.hijos, nodos);
    } catch {
      nodos.clear();
    }
  }
  return (ancla) => {
    const instancia = ancla.instanciaElementoId ?? null;
    const partes: string[] = [];
    let ruta = '';
    let dato: unknown = datos;
    for (const segmento of segmentos(ancla.elementoRef)) {
      ruta += '/' + segmento;
      dato = hijoDe(dato, segmento);
      const nodo = nodos.get(ruta);
      let titulo = nodo?.titulo ?? humanizar(segmento);
      if (nodo?.clase === 'arreglo' && instancia) {
        const indice = indiceDeInstancia(dato, instancia);
        if (indice >= 0) titulo = `${titulo} · ${nodo.nombreElemento} ${indice + 1}`;
        // Lo que sigue en la ruta pertenece a esa instancia.
        dato = indice >= 0 ? (dato as unknown[])[indice] : undefined;
      }
      // Un nodo y su instancia comparten título: no se repite.
      if (partes.at(-1) !== titulo) partes.push(titulo);
    }
    return partes.join(SEPARADOR_RUTA);
  };
}

function registrar(hijos: readonly NodoFormulario[], nodos: Map<string, NodoFormulario>): void {
  for (const nodo of hijos) {
    nodos.set('/' + nodo.ruta, nodo);
    if (nodo.clase === 'objeto') registrar(nodo.hijos, nodos);
    // La instancia comparte la ruta de la lista: solo se recorren sus hijos.
    if (nodo.clase === 'arreglo') registrar(nodo.elemento.hijos, nodos);
  }
}

function segmentos(referencia: string): string[] {
  return referencia
    .slice(1)
    .split('/')
    .map((segmento) => segmento.replace(/~1/g, '/').replace(/~0/g, '~'));
}

function hijoDe(dato: unknown, clave: string): unknown {
  return dato !== null && typeof dato === 'object' && !Array.isArray(dato)
    ? (dato as Record<string, unknown>)[clave]
    : undefined;
}

/** Posición de una instancia en su lista, según el orden en que se capturó; -1 si no está. */
function indiceDeInstancia(lista: unknown, instanciaId: string): number {
  if (!Array.isArray(lista)) return -1;
  return lista.findIndex(
    (item) =>
      typeof item === 'object' &&
      item !== null &&
      (item as Record<string, unknown>)['instancia_id'] === instanciaId,
  );
}
