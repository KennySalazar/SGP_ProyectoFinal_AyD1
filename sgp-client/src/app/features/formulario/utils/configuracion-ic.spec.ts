import { describe, expect, it } from 'vitest';
import {
  elementosCalibracion,
  escalaCalibracion,
  escalaValida,
  configuracionIcCompleta,
} from './configuracion-ic';
import { VersionFormulario } from '../models/formulario.models';
function version(): VersionFormulario {
  return {
    id: 'v1',
    codigo: 'v1',
    estado: 'BORRADOR',
    activa: false,
    esquema: {},
    vinculosAlmacenamiento: {},
    reglasEvaluacionIc: {
      elementos: [
        { elemento_ref: '/vigas', nombre: 'Vigas', peso: 10 },
        { elemento_ref: '/pilas', nombre: 'Pilas' },
      ],
      reglas_evaluacion_ic: {
        version_algoritmo: 'ic-prueba',
        agregacion: {
          indicadores_de_un_elemento: 'MAXIMO',
          instancias_repetibles: 'MAXIMO',
          elementos_ausentes_portantes: 4,
          elementos_ausentes_no_portantes: 4,
        },
        rangos_estado: { BUENO: 75, REGULAR: 50, MALO: 0 },
        escala_severidad: { NO_PRESENTA: 0, LEVE: 1, MODERADO: 2, SEVERO: 4 },
      },
    },
    versionAlgoritmoIc: 'ic1',
    creadoPorId: 'u1',
    creadoEn: '',
    actualizadoEn: '',
    version: 0,
    mapeos: [],
    pesos: [
      { id: 'p1', elementoRef: '/vigas', peso: 2, descripcion: 'Vigas guardadas', creadoEn: '' },
    ],
  };
}
describe('Configuracion IC por version', () => {
  it('combina catalogo y pesos guardados sin usar pesos no calibrados del JSON', () => {
    const v = version();
    const previo = JSON.stringify(v);
    expect(elementosCalibracion(v)).toEqual([
      { elementoRef: '/vigas', descripcion: 'Vigas guardadas', peso: 2 },
      { elementoRef: '/pilas', descripcion: 'Pilas', peso: null },
    ]);
    expect(JSON.stringify(v)).toBe(previo);
  });
  it('detecta configuracion parcial y exige la escala completa', () => {
    const v = version();
    expect(configuracionIcCompleta(v)).toBe(false);
    v.pesos.push({ id: 'p2', elementoRef: '/pilas', peso: 1, creadoEn: '' });
    expect(configuracionIcCompleta(v)).toBe(true);
    v.reglasEvaluacionIc['reglas_evaluacion_ic'] = {};
    expect(configuracionIcCompleta(v)).toBe(false);
  });
  it('cada version conserva sus pesos y escala', () => {
    const a = version();
    const b = version();
    b.pesos[0].peso = 9;
    expect(elementosCalibracion(a)[0].peso).toBe(2);
    expect(elementosCalibracion(b)[0].peso).toBe(9);
  });
  it('no convierte texto, nulos o escala ausente en valores validos', () => {
    const v = version();
    v.reglasEvaluacionIc['reglas_evaluacion_ic'] = {
      escala_severidad: { NO_PRESENTA: 0, LEVE: '1', MODERADO: null },
    };
    expect(escalaCalibracion(v)).toEqual({
      NO_PRESENTA: 0,
      LEVE: null,
      MODERADO: null,
      SEVERO: null,
    });
    expect(escalaValida(escalaCalibracion(v))).toBe(false);
  });
  it('no duplica referencias ni modifica la entrada ante catalogo irregular', () => {
    const v = version();
    v.reglasEvaluacionIc['elementos'] = [
      null,
      {},
      { elemento_ref: '' },
      { elemento_ref: '/vigas' },
      { elemento_ref: '/vigas' },
    ];
    expect(elementosCalibracion(v)).toHaveLength(1);
  });
});
