export interface CoordenadaGeografica {
  latitud: number;
  longitud: number;
}

export interface PuntoMapa extends CoordenadaGeografica {
  id: string;
  titulo: string;
  /** Puente dado de baja: se dibuja atenuado y se rotula como inactivo. */
  inactivo?: boolean;
}

type Posicion = number[];
type Anillo = Posicion[];
type Poligono = Anillo[];

export interface LimiteGuatemala {
  type: 'FeatureCollection';
  features: Array<{
    type: 'Feature';
    geometry:
      | { type: 'Polygon'; coordinates: Poligono }
      | { type: 'MultiPolygon'; coordinates: Poligono[] };
  }>;
}

export function coordenadaValida(latitud: number | null, longitud: number | null): boolean {
  return (
    latitud !== null &&
    longitud !== null &&
    Number.isFinite(latitud) &&
    Number.isFinite(longitud) &&
    latitud >= -90 &&
    latitud <= 90 &&
    longitud >= -180 &&
    longitud <= 180
  );
}

export function zonaUtmGuatemala(longitud: number): '15N' | '16N' {
  return longitud < -90 ? '15N' : '16N';
}

/**
 * Incluye el interior y el borde del polígono.
 * Las posiciones GeoJSON tienen orden [longitud, latitud].
 */
export function estaDentroDeGuatemala(
  limite: LimiteGuatemala,
  coordenada: CoordenadaGeografica,
): boolean {
  if (!coordenadaValida(coordenada.latitud, coordenada.longitud)) {
    return false;
  }

  const punto = [coordenada.longitud, coordenada.latitud];

  return limite.features.some((feature) => {
    const geometria = feature.geometry;

    if (geometria.type === 'Polygon') {
      return dentroDePoligono(punto, geometria.coordinates);
    }

    return geometria.coordinates.some((poligono) => dentroDePoligono(punto, poligono));
  });
}

function dentroDePoligono(punto: Posicion, poligono: Poligono): boolean {
  const exterior = poligono[0];

  if (!exterior) return false;

  const posicionExterior = posicionEnAnillo(punto, exterior);

  if (posicionExterior === 'borde') return true;
  if (posicionExterior === 'fuera') return false;

  for (const hueco of poligono.slice(1)) {
    const posicion = posicionEnAnillo(punto, hueco);

    if (posicion === 'borde') return true;
    if (posicion === 'dentro') return false;
  }

  return true;
}

function posicionEnAnillo(punto: Posicion, anillo: Anillo): 'dentro' | 'fuera' | 'borde' {
  let dentro = false;

  for (let i = 0, j = anillo.length - 1; i < anillo.length; j = i++) {
    const inicio = anillo[j];
    const fin = anillo[i];

    if (estaEnSegmento(punto, inicio, fin)) return 'borde';

    const cruza =
      inicio[1] > punto[1] !== fin[1] > punto[1] &&
      punto[0] < ((fin[0] - inicio[0]) * (punto[1] - inicio[1])) / (fin[1] - inicio[1]) + inicio[0];

    if (cruza) dentro = !dentro;
  }

  return dentro ? 'dentro' : 'fuera';
}

function estaEnSegmento(punto: Posicion, inicio: Posicion, fin: Posicion): boolean {
  const dx = fin[0] - inicio[0];
  const dy = fin[1] - inicio[1];
  const longitudSegmento = Math.hypot(dx, dy);
  const tolerancia = 1e-10;

  if (longitudSegmento === 0) {
    return Math.hypot(punto[0] - inicio[0], punto[1] - inicio[1]) <= tolerancia;
  }

  const productoCruzado = (punto[0] - inicio[0]) * dy - (punto[1] - inicio[1]) * dx;

  return (
    Math.abs(productoCruzado) / longitudSegmento <= tolerancia &&
    punto[0] >= Math.min(inicio[0], fin[0]) - tolerancia &&
    punto[0] <= Math.max(inicio[0], fin[0]) + tolerancia &&
    punto[1] >= Math.min(inicio[1], fin[1]) - tolerancia &&
    punto[1] <= Math.max(inicio[1], fin[1]) + tolerancia
  );
}
