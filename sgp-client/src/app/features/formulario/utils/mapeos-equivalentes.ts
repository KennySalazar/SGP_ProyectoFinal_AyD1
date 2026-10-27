import { AnalisisMapeo, DeclaracionMapeoRequest } from '../models/formulario.models';

function objeto(valor: unknown): valor is Record<string, unknown> {
  return valor !== null && typeof valor === 'object' && !Array.isArray(valor);
}

function campo(esquema: Record<string, unknown>, ruta: string): unknown {
  if (!ruta.startsWith('/')) return undefined;
  let nodo: unknown = esquema;
  for (const segmento of ruta.slice(1).split('/')) {
    if (objeto(nodo) && nodo['type'] === 'array') nodo = nodo['items'];
    if (!objeto(nodo) || nodo['$ref'] || !objeto(nodo['properties'])) return undefined;
    nodo = nodo['properties'][segmento.replace(/~1/g, '/').replace(/~0/g, '~')];
  }
  return nodo;
}

function normalizar(valor: unknown): unknown {
  if (Array.isArray(valor)) return valor.map(normalizar);
  if (!objeto(valor)) return valor;
  return Object.fromEntries(
    Object.keys(valor)
      .sort()
      .map((clave) => [clave, normalizar(valor[clave])]),
  );
}

export function completarEquivalencias(
  analisis: AnalisisMapeo,
  actuales: DeclaracionMapeoRequest[],
  origen: Record<string, unknown>,
  destino: Record<string, unknown>,
): DeclaracionMapeoRequest[] {
  const resultado = [...actuales];
  const usadosOrigen = new Set(actuales.map((m) => m.elementoRefOrigen));
  const usadosDestino = new Set(actuales.map((m) => m.elementoRefDestino));
  const destinos = new Set(analisis.camposDestino);
  const esquemaIdentico =
    JSON.stringify(normalizar(origen)) === JSON.stringify(normalizar(destino));
  for (const ruta of analisis.camposOrigen) {
    if (!destinos.has(ruta) || usadosOrigen.has(ruta) || usadosDestino.has(ruta)) continue;
    const anterior = campo(origen, ruta);
    const nuevo = campo(destino, ruta);
    if (
      !esquemaIdentico &&
      (anterior === undefined ||
        nuevo === undefined ||
        JSON.stringify(normalizar(anterior)) !== JSON.stringify(normalizar(nuevo)))
    )
      continue;
    resultado.push({
      elementoRefOrigen: ruta,
      elementoRefDestino: ruta,
      tipo: 'EQUIVALENTE',
      observacion: null,
    });
    usadosOrigen.add(ruta);
    usadosDestino.add(ruta);
  }
  return resultado;
}
