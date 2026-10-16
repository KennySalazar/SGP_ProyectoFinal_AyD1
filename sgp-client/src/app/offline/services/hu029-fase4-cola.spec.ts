import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, expect, vi } from 'vitest';
import { ejecutarGherkin } from '../../../testing/gherkin';
import source from './hu029-fase4-cola.feature';
import { sgpDb, SyncQueueItem } from '../db/sgp-db';
import { FotografiasLocalesService, FotografiaPendiente } from './fotografias-locales.service';
let servicio: FotografiasLocalesService;
let foto: FotografiaPendiente;
let operacion: SyncQueueItem;
let rechazo: unknown;
let recuperadas: FotografiaPendiente[];
beforeEach(() => {
  servicio = TestBed.inject(FotografiasLocalesService);
  rechazo = undefined;
  recuperadas = [];
  vi.spyOn(sgpDb, 'transaction').mockImplementation(
    (...args: unknown[]) => (args.at(-1) as () => Promise<unknown>)() as never,
  );
  vi.spyOn(sgpDb.fotos, 'get').mockImplementation(() => Promise.resolve(foto) as never);
  vi.spyOn(sgpDb.fotos, 'where').mockReturnValue({
    equals: () => ({ toArray: async () => [foto] }),
  } as never);
  vi.spyOn(sgpDb.cola_sync, 'get').mockImplementation(() => Promise.resolve(operacion) as never);
  vi.spyOn(sgpDb.cola_sync, 'update').mockImplementation((_id, cambios) => {
    operacion = { ...operacion, ...(cambios as Partial<SyncQueueItem>) };
    return Promise.resolve(1) as never;
  });
  vi.spyOn(sgpDb.fotos, 'delete');
  vi.spyOn(sgpDb.cola_sync, 'delete');
  vi.spyOn(sgpDb.cola_sync, 'put');
});
afterEach(() => vi.restoreAllMocks());
ejecutarGherkin(source, 'HU-029 fase 4: rechazos persistentes', [
  {
    patron: /^una fotografía pendiente del autor con su operación original$/,
    ejecutar: () => {
      foto = {
        id: 'foto',
        propietarioId: 'autor',
        inspeccionId: 'inspeccion',
        idempotencyKey: 'clave-estable',
        blob: new Blob(['jpeg']),
        mime: 'image/jpeg',
        sincronizado: false,
        metadatos: {
          id: 'foto',
          categoria: 'ACCESO',
          latitud: 14,
          longitud: -90,
          capturadoEn: '2026-10-16T12:00:00Z',
        },
      };
      operacion = {
        id: 'foto',
        entityId: 'foto',
        tipo: 'FOTO',
        idempotencyKey: 'clave-estable',
        intentos: 0,
        creadoEn: '2026-10-16T12:00:00Z',
      };
    },
  },
  {
    patron: /^registra el rechazo "([^"]+)" para el autor$/,
    ejecutar: (codigo) => servicio.registrarError('foto', 'autor', codigo),
  },
  {
    patron: /^el pendiente recuperado conserva blob UUID clave y rechazo "([^"]+)"$/,
    ejecutar: async (codigo) => {
      const nuevaInstancia = new FotografiasLocalesService();
      recuperadas = await nuevaInstancia.pendientes('autor', 'inspeccion');
      expect(recuperadas).toEqual([{ ...foto, ultimoError: codigo }]);
      expect(recuperadas[0].blob).toBe(foto.blob);
    },
  },
  {
    patron: /^la cola incrementa los intentos sin eliminar ni reemplazar la operación$/,
    ejecutar: () => {
      expect(operacion.intentos).toBe(1);
      expect(operacion.idempotencyKey).toBe('clave-estable');
      expect(operacion.creadoEn).toBe('2026-10-16T12:00:00Z');
      expect(sgpDb.fotos.delete).not.toHaveBeenCalled();
      expect(sgpDb.cola_sync.delete).not.toHaveBeenCalled();
      expect(sgpDb.cola_sync.put).not.toHaveBeenCalled();
    },
  },
  {
    patron: /^otro usuario intenta registrar un rechazo$/,
    ejecutar: async () => {
      try {
        await servicio.registrarError('foto', 'otro', 'formato');
      } catch (e) {
        rechazo = e;
      }
    },
  },
  {
    patron: /^rechaza el acceso y conserva la cola original$/,
    ejecutar: () => {
      expect(rechazo).toEqual(new Error('propietario'));
      expect(operacion.ultimoError).toBeUndefined();
      expect(operacion.intentos).toBe(0);
      expect(sgpDb.cola_sync.update).not.toHaveBeenCalled();
    },
  },
  {
    patron: /^otro usuario consulta los pendientes$/,
    ejecutar: async () => {
      recuperadas = await servicio.pendientes('otro', 'inspeccion');
    },
  },
  {
    patron: /^no obtiene fotografías ni lee sus operaciones$/,
    ejecutar: () => {
      expect(recuperadas).toEqual([]);
      expect(sgpDb.cola_sync.get).not.toHaveBeenCalled();
    },
  },
]);
