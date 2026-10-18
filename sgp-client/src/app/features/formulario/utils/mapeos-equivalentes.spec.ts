import { describe, expect, it } from 'vitest';
import { AnalisisMapeo, DeclaracionMapeoRequest } from '../models/formulario.models';
import { completarEquivalencias } from './mapeos-equivalentes';

const analisis: AnalisisMapeo = {
  versionDestinoId: 'v4',
  versionDestinoCodigo: 'v4',
  camposOrigen: ['/nombre', '/edad', '/eliminado'],
  camposDestino: ['/nombre', '/edad', '/nuevo'],
  mapeosDeclarados: [],
  camposOrigenSinMapeo: [],
  mapeoCompleto: false,
};
const origen = {
  type: 'object',
  properties: {
    nombre: { type: 'string' },
    edad: { type: 'integer' },
    eliminado: { type: 'string' },
  },
};
const destino = {
  type: 'object',
  properties: {
    nombre: { type: 'string' },
    edad: { type: 'number' },
    nuevo: { type: 'string' },
  },
};

describe('completarEquivalencias', () => {
  it('propone solamente rutas comunes con definiciones iguales', () => {
    expect(completarEquivalencias(analisis, [], origen, destino)).toEqual([
      {
        elementoRefOrigen: '/nombre',
        elementoRefDestino: '/nombre',
        tipo: 'EQUIVALENTE',
        observacion: null,
      },
    ]);
  });
  it('conserva decisiones existentes y evita reutilizar destinos', () => {
    const actuales: DeclaracionMapeoRequest[] = [
      {
        elementoRefOrigen: '/anterior',
        elementoRefDestino: '/nombre',
        tipo: 'RENOMBRADO',
        observacion: 'Manual',
      },
      { elementoRefOrigen: '/edad', elementoRefDestino: null, tipo: 'ELIMINADO' },
    ];
    expect(completarEquivalencias(analisis, actuales, origen, destino)).toEqual(actuales);
    expect(actuales).toHaveLength(2);
  });
  it('repetir la accion no duplica propuestas', () => {
    const primera = completarEquivalencias(analisis, [], origen, destino);
    expect(completarEquivalencias(analisis, primera, origen, destino)).toEqual(primera);
  });
  it('ignora el orden de propiedades y admite formularios identicos', () => {
    const copia = { properties: origen.properties, type: 'object' };
    expect(
      completarEquivalencias(
        { ...analisis, camposDestino: analisis.camposOrigen },
        [],
        origen,
        copia,
      ),
    ).toHaveLength(3);
  });
});
