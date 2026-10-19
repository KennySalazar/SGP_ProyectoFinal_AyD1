import { describe, expect, it } from 'vitest';
import { VersionFormulario } from '../models/formulario.models';
import {
  aplicarReglasIc,
  camposReglasIc,
  erroresReglasIc,
  reglasIcCompletas,
  valoresReglasIc,
} from './reglas-ic';

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
        { elemento_ref: '/otros/senalizacion/informativa', indicadores: ['existe', 'estado'] },
        { elemento_ref: '/otros/senalizacion/preventiva', indicadores: ['existe', 'estado'] },
        {
          elemento_ref: '/elementos_no_estructurales/pasamanos_barandal',
          indicadores: ['golpeado_longitud_m'],
        },
      ],
      reglas_evaluacion_ic: {
        version_algoritmo: 'ic-prueba',
        reglas_anulacion: [{ id: 'critica' }],
        extension: { conservar: true },
      },
    },
    versionAlgoritmoIc: 'ic-prueba',
    creadoPorId: 'u1',
    creadoEn: '',
    actualizadoEn: '',
    version: 0,
    pesos: [],
    mapeos: [],
  };
}

function completos(v: VersionFormulario) {
  const valores = valoresReglasIc(v);
  for (const campo of camposReglasIc(v))
    valores[campo.clave] =
      campo.tipo === 'texto' ? 'ic-prueba' : campo.tipo === 'agregacion' ? 'MAXIMO' : 0;
  valores[JSON.stringify(['rangos_estado', 'BUENO'])] = 75;
  valores[JSON.stringify(['rangos_estado', 'REGULAR'])] = 50;
  for (const campo of camposReglasIc(v).filter((c) => c.grupo === 'golpeado_longitud_m'))
    valores[campo.clave] = ['LEVE', 'MODERADO', 'SEVERO'].indexOf(campo.etiqueta) + 1;
  return valores;
}

describe('Reglas editables del IC', () => {
  it('incluye ambas respuestas booleanas, categorias y umbrales sin duplicar familias', () => {
    const campos = camposReglasIc(version());
    expect(campos.filter((c) => c.grupo === 'booleanos')).toHaveLength(2);
    expect(campos.filter((c) => c.grupo === 'categorias')).toHaveLength(2);
    expect(campos.filter((c) => c.grupo === 'golpeado_longitud_m')).toHaveLength(3);
    expect(campos.some((c) => c.ruta.at(-1) === 'senal.existe=true')).toBe(true);
    expect(campos.some((c) => c.ruta.at(-1) === 'senal.existe=false')).toBe(true);
  });
  it('mantiene los faltantes vacios y rechaza texto numerico o infinito', () => {
    const v = version();
    expect(reglasIcCompletas(v)).toBe(false);
    expect(Object.values(valoresReglasIc(v))).toContain(null);
    const valores = completos(v);
    const campo = camposReglasIc(v).find((c) => c.grupo === 'booleanos')!;
    for (const invalido of ['1', Infinity, -1, 4.1, null]) {
      valores[campo.clave] = invalido;
      expect(erroresReglasIc(camposReglasIc(v), valores)).toContain('numerosInvalidos');
    }
  });
  it('rechaza umbrales iguales y rangos invertidos', () => {
    const v = version();
    const valores = completos(v);
    const campos = camposReglasIc(v);
    expect(erroresReglasIc(campos, valores)).toEqual([]);
    valores[
      campos.find((c) => c.grupo === 'golpeado_longitud_m' && c.etiqueta === 'MODERADO')!.clave
    ] = 1;
    valores[JSON.stringify(['rangos_estado', 'REGULAR'])] = 80;
    expect(erroresReglasIc(campos, valores)).toEqual(
      expect.arrayContaining(['umbralesInvalidos', 'rangosInvalidos']),
    );
  });
  it('guarda claves con punto de forma literal y conserva catalogo, escala, anulaciones y extensiones', () => {
    const v = version();
    const original = structuredClone(v);
    const salida = aplicarReglasIc(v, completos(v));
    expect(v).toEqual(original);
    expect(salida['elementos']).toEqual(v.reglasEvaluacionIc['elementos']);
    expect(salida['reglas_evaluacion_ic']).toMatchObject({
      reglas_anulacion: [{ id: 'critica' }],
      extension: { conservar: true },
      conversion_indicadores: { booleanos: { 'senal.existe=true': 0, 'senal.existe=false': 0 } },
    });
    expect(reglasIcCompletas({ ...v, reglasEvaluacionIc: salida })).toBe(true);
  });
  it('solo exige las conversiones utilizadas por el catalogo', () => {
    const v = version();
    v.reglasEvaluacionIc['elementos'] = [
      { elemento_ref: '/vigas', indicadores: ['grietas', 'otros'] },
    ];
    expect(camposReglasIc(v).filter((c) => c.ruta[0] === 'conversion_indicadores')).toEqual([]);
  });
});
