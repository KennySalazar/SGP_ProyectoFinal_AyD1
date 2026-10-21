import { indexedDB, IDBKeyRange } from 'fake-indexeddb';
import Dexie from 'dexie';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SgpDatabase } from '../../../offline/db/sgp-db';
import { PreparacionSalida } from '../models/salida.models';
import { SALIDA_DB, SalidaLocalService } from './salida-local.service';

function paquete(usuario = 'u1', id = 'salida1'): PreparacionSalida {
  return {
    id,
    usuarioId: usuario,
    dispositivoId: 'equipo',
    preparadoEn: '2026-10-20T01:00:00Z',
    numeroPuentes: 1,
    tamanoEstimadoBytes: 100,
    paquete: {
      formulario: { id: 'v3', codigo: 'v3', hashContenido: 'hash', esquema: { type: 'object' } },
      puentes: [
        {
          id: 'p1',
          codigo: 'GT-01',
          nombre: 'Puente',
          departamento: { id: 'd', nombre: 'Guatemala' },
          municipio: { id: 'm', nombre: 'Amatitlan' },
          latitud: 14,
          longitud: -90,
          activo: true,
        },
      ],
      inspecciones: [
        {
          id: 'i1',
          puenteId: 'p1',
          versionFormularioId: 'v2',
          publicadoEn: '2026-10-19T01:00:00Z',
          datos: { dano: 1 },
          datosPermanentes: { longitudM: 10 },
        },
      ],
      asignaciones: [],
    },
  };
}

describe('Salida local HU041', () => {
  let db: SgpDatabase;
  let local: SalidaLocalService;
  beforeEach(() => {
    Dexie.dependencies.indexedDB = indexedDB;
    Dexie.dependencies.IDBKeyRange = IDBKeyRange;
    db = new SgpDatabase('hu041-test-' + crypto.randomUUID());
    TestBed.configureTestingModule({ providers: [{ provide: SALIDA_DB, useValue: db }] });
    local = TestBed.inject(SalidaLocalService);
  });
  afterEach(async () => {
    vi.restoreAllMocks();
    try {
      await db.delete();
    } finally {
      TestBed.resetTestingModule();
    }
  });

  it('guarda esquema, puentes, previa y resumen en los almacenes existentes', async () => {
    const resumen = await local.guardar('u1', paquete());
    expect(resumen.numeroPuentes).toBe(1);
    expect((await db.puentes.toArray())[0].payload).toMatchObject({ id: 'p1' });
    expect((await db.esquemas_formulario.toArray())[0].schema).toEqual({ type: 'object' });
    expect((await db.inspecciones.toArray())[0]).toMatchObject({
      origen: 'PREPARACION',
      sincronizado: true,
      estado: 'PUBLICADA',
    });
  });

  it('conserva la salida anterior si falla una escritura intermedia', async () => {
    await local.guardar('u1', paquete());
    vi.spyOn(db.inspecciones, 'bulkPut').mockRejectedValueOnce(
      new DOMException('Sin espacio', 'QuotaExceededError'),
    );
    await expect(local.guardar('u1', paquete('u1', 'salida2'))).rejects.toThrow();
    expect((await local.ultima('u1'))?.id).toBe('salida1');
    expect(await db.puentes.count()).toBe(1);
    expect(await db.inspecciones.count()).toBe(1);
  });

  it('separa dos usuarios aunque preparen el mismo puente y formulario', async () => {
    await local.guardar('u1', paquete());
    await local.guardar('u2', paquete('u2', 'salida2'));
    expect(await db.puentes.count()).toBe(2);
    expect(await db.esquemas_formulario.count()).toBe(2);
    expect((await local.ultima('u1'))?.id).toBe('salida1');
    expect((await local.ultima('u2'))?.id).toBe('salida2');
    await local.quitar('u1');
    expect(await local.ultima('u1')).toBeUndefined();
    expect((await local.ultima('u2'))?.id).toBe('salida2');
    expect(await db.puentes.count()).toBe(1);
  });

  it('bloquea limpieza con inspeccion pendiente y conserva todo', async () => {
    await local.guardar('u1', paquete());
    await db.inspecciones.put({
      id: 'borrador',
      propietarioId: 'u1',
      puenteId: 'p1',
      estado: 'BORRADOR',
      sincronizado: false,
      actualizadoEn: '',
      payload: {},
    });
    await expect(local.quitar('u1')).rejects.toThrow('pendientes');
    expect(await db.puentes.count()).toBe(1);
    expect(await db.inspecciones.count()).toBe(2);
  });

  it('bloquea limpieza con foto pendiente aunque no exista inspeccion', async () => {
    await local.guardar('u1', paquete());
    await db.fotos.put({
      id: 'f',
      propietarioId: 'u1',
      inspeccionId: 'b',
      blob: new Blob(['x']),
      mime: 'image/jpeg',
      sincronizado: false,
    });
    await expect(local.quitar('u1')).rejects.toThrow('pendientes');
    expect(await db.fotos.count()).toBe(1);
  });

  it('no elimina cola huerfana o datos antiguos sin propietario', async () => {
    await local.guardar('u1', paquete());
    await db.cola_sync.put({
      id: 'q',
      tipo: 'INSPECCION',
      entityId: 'desconocida',
      idempotencyKey: 'k',
      intentos: 0,
      creadoEn: '',
    });
    await expect(local.quitar('u1')).rejects.toThrow('pendientes');
    expect(await db.cola_sync.count()).toBe(1);
  });

  it('conserva pendientes de otro usuario al limpiar los propios', async () => {
    await local.guardar('u1', paquete());
    await db.inspecciones.put({
      id: 'b2',
      propietarioId: 'u2',
      puenteId: 'p1',
      estado: 'BORRADOR',
      sincronizado: false,
      actualizadoEn: '',
      payload: {},
    });
    await local.quitar('u1');
    expect(await db.inspecciones.get('b2')).toBeDefined();
  });

  it('conserva formulario de inspecciones ya iniciadas al reemplazar la salida', async () => {
    await local.guardar('u1', paquete());
    await db.inspecciones.put({
      id: 'b',
      propietarioId: 'u1',
      puenteId: 'p1',
      estado: 'BORRADOR',
      sincronizado: false,
      actualizadoEn: '',
      payload: { versionFormularioId: 'v3' },
    });
    const nueva = paquete('u1', 'salida2');
    nueva.paquete.formulario.id = 'v4';
    nueva.paquete.formulario.codigo = 'v4';
    await local.guardar('u1', nueva);
    expect(await db.esquemas_formulario.count()).toBe(2);
    expect((await db.inspecciones.get('b'))?.payload).toEqual({ versionFormularioId: 'v3' });
  });

  it('conserva clave y cuerpo despues de cerrar y abrir la base', async () => {
    const cuerpo = { dispositivoId: 'equipo', puentesIds: ['p1'] };
    const primera = await local.solicitud('u1', cuerpo);
    db.close();
    await db.open();
    expect((await local.solicitud('u1', cuerpo)).clave).toBe(primera.clave);
    expect((await local.solicitud('u2', cuerpo)).clave).not.toBe(primera.clave);
    expect((await local.solicitud('u1', { ...cuerpo, puentesIds: ['p2'] })).clave).not.toBe(
      primera.clave,
    );
  });

  it('no reemplaza la salida anterior con un paquete parcial', async () => {
    await local.guardar('u1', paquete());
    const parcial = paquete('u1', 'incompleta');
    parcial.numeroPuentes = 2;
    await expect(local.guardar('u1', parcial)).rejects.toThrow('incompleta');
    expect((await local.ultima('u1'))?.id).toBe('salida1');
  });

  it('rechaza un paquete de otro usuario sin escribir', async () => {
    await expect(local.guardar('u2', paquete())).rejects.toThrow();
    expect(await db.puentes.count()).toBe(0);
  });

  it('migra una base version 2 sin perder borradores anteriores', async () => {
    const nombre = 'hu041-v2-' + crypto.randomUUID();
    const { default: Dexie } = await import('dexie');
    const vieja = new Dexie(nombre);
    vieja.version(1).stores({
      puentes: 'id,codigo,actualizadoEn',
      inspecciones: 'id,puenteId,estado,actualizadoEn,sincronizado',
      fotos: 'id,inspeccionId,sincronizado',
      esquemas_formulario: 'id,version,activo',
      cola_sync: 'id,tipo,entityId,creadoEn,intentos',
    });
    vieja.version(2).stores({ notificaciones: 'id,leidoEn,creadoEn', notificaciones_meta: 'key' });
    await vieja.table('inspecciones').put({ id: 'anterior', sincronizado: false });
    vieja.close();
    const nueva = new SgpDatabase(nombre);
    try {
      await nueva.open();
      expect(await nueva.inspecciones.get('anterior')).toBeDefined();
      expect(await nueva.preparaciones_salida.count()).toBe(0);
    } finally {
      await nueva.delete();
    }
  });
});
