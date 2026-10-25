import { describe, expect, it } from 'vitest';
import { mensajeCalibracion } from './error-calibracion';
const traducir = (clave: string) => clave;
describe('Errores de calibracion', () => {
  it('explica el conflicto sin mostrar el codigo tecnico', () => {
    expect(
      mensajeCalibracion(
        {
          status: 409,
          error: { code: 'concurrencia_conflicto', detail: 'tecnico' },
        },
        traducir,
      ),
    ).toBe('formulario.hu053.conflicto');
  });
  it('pide recargar cuando falta la version y explica la inmutabilidad', () => {
    expect(mensajeCalibracion({ status: 428 }, traducir)).toBe('formulario.hu053.conflicto');
    expect(mensajeCalibracion({ error: { code: 'version_inmutable' } }, traducir)).toBe(
      'formulario.hu053.inmutable',
    );
  });
  it('presenta el campo y motivo del 422 sin nombres de propiedades tecnicas', () => {
    expect(
      mensajeCalibracion(
        {
          status: 422,
          error: {
            errores: [
              {
                campo: 'reglas_evaluacion_ic.rangos_estado.REGULAR',
                mensaje: 'Debe ser menor que Bueno.',
              },
            ],
          },
        },
        traducir,
      ),
    ).toBe('Calculo del IC > Rangos de estado > Regular: Debe ser menor que Bueno.');
  });
  it('explica la falla de conexion y tolera respuestas sin detalle', () => {
    expect(mensajeCalibracion({ status: 0 }, traducir)).toBe('formulario.hu053.sinConexion');
    expect(mensajeCalibracion(null, traducir)).toBe('common.operationFailed');
  });
});
