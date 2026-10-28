import { describe, expect, it } from 'vitest';
import { AnalisisMapeo, DeclaracionMapeoRequest } from '../models/formulario.models';
import { completarEquivalencias, completarMapeosAutomaticos } from './mapeos-equivalentes';

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

describe('completarMapeosAutomaticos', () => {
  it('completa equivalencias y asigna NUEVO a campos agregados y ELIMINADO a campos removidos', () => {
    const resultado = completarMapeosAutomaticos(analisis, [], origen, destino);
    expect(resultado).toEqual([
      {
        elementoRefOrigen: '/nombre',
        elementoRefDestino: '/nombre',
        tipo: 'EQUIVALENTE',
        observacion: null,
      },
      {
        elementoRefOrigen: null,
        elementoRefDestino: '/edad',
        tipo: 'NUEVO',
        observacion: 'Campo nuevo agregado en esta versión',
      },
      {
        elementoRefOrigen: null,
        elementoRefDestino: '/nuevo',
        tipo: 'NUEVO',
        observacion: 'Campo nuevo agregado en esta versión',
      },
      {
        elementoRefOrigen: '/edad',
        elementoRefDestino: null,
        tipo: 'ELIMINADO',
        observacion: 'Campo no presente en esta versión',
      },
      {
        elementoRefOrigen: '/eliminado',
        elementoRefDestino: null,
        tipo: 'ELIMINADO',
        observacion: 'Campo no presente en esta versión',
      },
    ]);
  });

  it('respeta decisiones previas y solo completa campos faltantes', () => {
    const actuales: DeclaracionMapeoRequest[] = [
      {
        elementoRefOrigen: '/edad',
        elementoRefDestino: '/edad',
        tipo: 'RENOMBRADO',
        observacion: 'Cambio de tipo',
      },
    ];
    const resultado = completarMapeosAutomaticos(analisis, actuales, origen, destino);
    expect(resultado[0]).toEqual(actuales[0]);
    // /edad no debe duplicarse como nuevo o eliminado
    expect(resultado.filter((m) => m.elementoRefDestino === '/edad')).toHaveLength(1);
    expect(resultado.filter((m) => m.elementoRefOrigen === '/edad')).toHaveLength(1);
    // /nuevo debe estar como NUEVO
    expect(resultado).toContainEqual({
      elementoRefOrigen: null,
      elementoRefDestino: '/nuevo',
      tipo: 'NUEVO',
      observacion: 'Campo nuevo agregado en esta versión',
    });
  });

  it('detecta reubicacion entre secciones como RENOMBRADO cuando coinciden las claves de campo', () => {
    const analisisReubicacion: AnalisisMapeo = {
      versionDestinoId: 'v2',
      versionDestinoCodigo: 'v2',
      camposOrigen: ['/seccion_a/pregunta_x'],
      camposDestino: ['/seccion_b/pregunta_x'],
      mapeosDeclarados: [],
      camposOrigenSinMapeo: [],
      mapeoCompleto: false,
    };
    const origenSec = {
      type: 'object',
      properties: {
        seccion_a: {
          type: 'object',
          properties: { pregunta_x: { type: 'string' } },
        },
      },
    };
    const destinoSec = {
      type: 'object',
      properties: {
        seccion_b: {
          type: 'object',
          properties: { pregunta_x: { type: 'string' } },
        },
      },
    };
    const res = completarMapeosAutomaticos(analisisReubicacion, [], origenSec, destinoSec);
    expect(res).toEqual([
      {
        elementoRefOrigen: '/seccion_a/pregunta_x',
        elementoRefDestino: '/seccion_b/pregunta_x',
        tipo: 'RENOMBRADO',
        observacion: 'Campo reubicado entre secciones',
      },
    ]);
  });

  it('es idempotente al ejecutarse multiples veces', () => {
    const primera = completarMapeosAutomaticos(analisis, [], origen, destino);
    const segunda = completarMapeosAutomaticos(analisis, primera, origen, destino);
    expect(segunda).toEqual(primera);
  });
});
