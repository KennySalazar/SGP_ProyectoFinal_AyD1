import { describe, expect, it } from 'vitest';
import { LocalInspection, LocalPhoto, SyncQueueItem } from '../db/sgp-db';
import { copiaTecnica, errorSincronizacion, resumirSincronizacion } from './diagnostico-sync';

describe('HU-060: proyección del diagnóstico', () => {
  const local: LocalInspection = {
    id: 'i',
    propietarioId: 'a',
    puenteId: 'p',
    estado: 'BORRADOR',
    actualizadoEn: '',
    payload: {},
    sincronizado: true,
  };
  const foto: LocalPhoto = {
    id: 'f',
    propietarioId: 'a',
    inspeccionId: 'i',
    blob: new Blob(['imagen']),
    mime: 'image/jpeg',
    sincronizado: false,
  };
  const operacion: SyncQueueItem = {
    id: 'op',
    propietarioId: 'a',
    tipo: 'INSPECCION',
    entityId: 'i',
    idempotencyKey: 'secreto',
    intentos: 0,
    creadoEn: '',
  };
  it('cuenta cambios sin cola, fotos sin encolar e intenciones rechazadas', () => {
    const resultado = resumirSincronizacion(
      'a',
      [
        { ...local, actualizacionLocalPendiente: true },
        {
          ...local,
          id: 'rechazo',
          intencionFinal: { accion: 'publicar', estado: 'RECHAZADA', error: 'secreto' },
        },
      ],
      [foto],
      [],
    );
    expect(resultado.inspecciones).toBe(2);
    expect(resultado.fotografias).toBe(1);
    expect(resultado.elementos.every((i) => i.estado === 'INTERVENCION')).toBe(true);
    expect(JSON.stringify(resultado)).not.toContain('secreto');
  });
  it('deduplica inspecciones con varias operaciones y fotografías presentes en la cola', () => {
    const resultado = resumirSincronizacion(
      'a',
      [local],
      [foto],
      [
        operacion,
        { ...operacion, id: 'op2' },
        { ...operacion, id: 'foto', tipo: 'FOTO', entityId: 'f' },
      ],
    );
    expect(resultado.inspecciones).toBe(1);
    expect(resultado.fotografias).toBe(1);
    expect(resultado.bytesFotos).toBe(6);
  });
  it('excluye datos de otras cuentas y registros sin propietario incluso sin sesión', () => {
    expect(resumirSincronizacion('b', [local], [foto], [operacion]).elementos).toEqual([]);
    expect(resumirSincronizacion(undefined, [local], [foto], [operacion]).elementos).toEqual([]);
  });
  it('distingue agotamiento, error definitivo y envío sin permitir reintento activo', () => {
    const resultado = resumirSincronizacion(
      'a',
      [],
      [],
      [
        { ...operacion, intentos: 5, estado: 'INTERVENCION' },
        {
          ...operacion,
          id: 'definitivo',
          intentos: 1,
          estado: 'INTERVENCION',
          ultimoError: 'Bearer TOKEN contraseña privada',
        },
        { ...operacion, id: 'activo', estado: 'ENVIANDO' },
      ],
    );
    expect(resultado.elementos[0].motivo).toBe('offline.diagnostico.agotado');
    expect(resultado.elementos[1].motivo).toBe('offline.diagnostico.errores.general');
    expect(resultado.elementos[2].reintentable).toBe(false);
    expect(JSON.stringify(resultado)).not.toMatch(/TOKEN|privada|secreto/);
  });
  it('normaliza códigos desconocidos y proyecta la copia sin identificación ni texto libre', () => {
    expect(errorSincronizacion('Bearer token')).toBe('offline.diagnostico.errores.general');
    const resultado = JSON.stringify(
      copiaTecnica({
        version: 4,
        estado: 'BORRADOR',
        autorId: 'secreto',
        datos: {
          identificacion: { nombre: 'Privado' },
          ancho: 20,
          notas: 'password=contraseña',
          token: 'secreto',
        },
      }),
    );
    expect(resultado).toContain('20');
    expect(resultado).not.toMatch(/Privado|contraseña|secreto|identificacion/);
  });
});
