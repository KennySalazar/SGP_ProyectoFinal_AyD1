import { EsquemaJson, NodoFormulario } from '../motor/esquema/modelos';
import { humanizar, resolverEsquema } from '../motor/esquema/resolutor';

/** Campo o elemento de una inspección, con la instancia concreta si es repetible. */
export interface AnclaEtiquetable {
  readonly elementoRef: string;
  readonly instanciaElementoId?: string | null;
}

/**
 * Nombra un ancla como la ve quien llena el formulario: el título del esquema y, en un elemento
 * repetible, qué instancia es («Pilas · Pila 2»). Si el esquema no tiene la ruta, humaniza su
 * último segmento en lugar de mostrar la referencia técnica.
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
    const base =
      nodos.get(ancla.elementoRef)?.titulo ?? humanizar(ultimoSegmento(ancla.elementoRef));
    if (!ancla.instanciaElementoId) return base;
    const instancia = ordinalDeInstancia(
      ancla.elementoRef,
      ancla.instanciaElementoId,
      nodos,
      datos,
    );
    return instancia ? `${base} · ${instancia.nombre} ${instancia.numero}` : base;
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

function ultimoSegmento(referencia: string): string {
  return segmentos(referencia).at(-1) ?? referencia;
}

/** Qué número de instancia es (la 2.ª pila…), según el orden en que se capturó. */
function ordinalDeInstancia(
  referencia: string,
  instanciaId: string,
  nodos: Map<string, NodoFormulario>,
  datos: unknown,
): { nombre: string; numero: number } | null {
  let actual = datos;
  let ruta = '';
  for (const segmento of segmentos(referencia)) {
    if (actual === null || typeof actual !== 'object') return null;
    ruta += '/' + segmento;
    actual = (actual as Record<string, unknown>)[segmento];
    const nodo = nodos.get(ruta);
    if (Array.isArray(actual) && nodo?.clase === 'arreglo') {
      const indice = actual.findIndex(
        (item) =>
          typeof item === 'object' &&
          item !== null &&
          (item as Record<string, unknown>)['instancia_id'] === instanciaId,
      );
      return indice >= 0 ? { nombre: nodo.nombreElemento, numero: indice + 1 } : null;
    }
  }
  return null;
}
