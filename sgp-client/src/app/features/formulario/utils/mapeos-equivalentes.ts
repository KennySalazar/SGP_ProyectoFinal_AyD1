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
  const usadosOrigen = new Set(
    actuales.map((m) => m.elementoRefOrigen).filter((r): r is string => !!r),
  );
  const usadosDestino = new Set(
    actuales.map((m) => m.elementoRefDestino).filter((r): r is string => !!r),
  );
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

export function completarMapeosAutomaticos(
  analisis: AnalisisMapeo,
  actuales: DeclaracionMapeoRequest[],
  origen: Record<string, unknown>,
  destino: Record<string, unknown>,
): DeclaracionMapeoRequest[] {
  const resultado = completarEquivalencias(analisis, actuales, origen, destino);
  const usadosOrigen = new Set(
    resultado.map((m) => m.elementoRefOrigen).filter((r): r is string => !!r),
  );
  const usadosDestino = new Set(
    resultado.map((m) => m.elementoRefDestino).filter((r): r is string => !!r),
  );

  for (const rutaDestino of analisis.camposDestino) {
    if (usadosDestino.has(rutaDestino)) continue;
    const claveDestino = rutaDestino.split('/').filter(Boolean).pop();
    if (!claveDestino) continue;
    const matchOrigen = analisis.camposOrigen.find(
      (rutaOrigen) =>
        !usadosOrigen.has(rutaOrigen) &&
        rutaOrigen !== rutaDestino &&
        rutaOrigen.split('/').filter(Boolean).pop() === claveDestino,
    );
    if (matchOrigen) {
      resultado.push({
        elementoRefOrigen: matchOrigen,
        elementoRefDestino: rutaDestino,
        tipo: 'RENOMBRADO',
        observacion: 'Campo reubicado entre secciones',
      });
      usadosOrigen.add(matchOrigen);
      usadosDestino.add(rutaDestino);
    }
  }

  for (const ruta of analisis.camposDestino) {
    if (!usadosDestino.has(ruta)) {
      resultado.push({
        elementoRefOrigen: null,
        elementoRefDestino: ruta,
        tipo: 'NUEVO',
        observacion: 'Campo nuevo agregado en esta versión',
      });
      usadosDestino.add(ruta);
    }
  }

  for (const ruta of analisis.camposOrigen) {
    if (!usadosOrigen.has(ruta)) {
      resultado.push({
        elementoRefOrigen: ruta,
        elementoRefDestino: null,
        tipo: 'ELIMINADO',
        observacion: 'Campo no presente en esta versión',
      });
      usadosOrigen.add(ruta);
    }
  }

  return resultado;
}
