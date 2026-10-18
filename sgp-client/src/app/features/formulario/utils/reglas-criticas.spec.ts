import { describe, expect, it } from 'vitest';
import { actualizarCampoCritico, camposCriticos, criticasPendientes } from './reglas-criticas';
import { VersionFormulario } from '../models/formulario.models';

describe('reglas criticas', () => {
  it('conserva metadatos y actualiza listas sin alterar el original', () => {
    const original = {
      id: 'RN-IC-05a',
      pendiente: 'Revisar',
      estado_resultante: 'MALO',
      condicion: { indicadores: ['a'], algun_valor_en: ['SEVERO'] },
    };
    const copia = structuredClone(original);
    const campos = camposCriticos(original);
    actualizarCampoCritico(copia, campos[0], ' a \n b ');
    expect(copia.condicion.indicadores).toEqual(['a', 'b']);
    expect(original.condicion.indicadores).toEqual(['a']);
    expect(copia.pendiente).toBe('Revisar');
    expect(campos.every((c) => !['id', 'pendiente', 'estado_resultante'].includes(c.ruta[0]))).toBe(
      true,
    );
  });
  it('bloquea reglas pendientes y catalogos sin reglas', () => {
    const version = (reglas: unknown[]) =>
      ({
        reglasEvaluacionIc: { reglas_evaluacion_ic: { reglas_anulacion: reglas } },
      }) as unknown as VersionFormulario;
    expect(criticasPendientes(version([]))).toBe(true);
    expect(criticasPendientes(version([{ pendiente: 'Revision' }]))).toBe(true);
    expect(criticasPendientes(version([{ pendiente: null }]))).toBe(false);
  });
});
