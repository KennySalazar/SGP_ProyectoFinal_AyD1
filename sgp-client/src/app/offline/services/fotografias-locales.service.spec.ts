import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { sgpDb, LocalPhoto, LocalInspection } from '../db/sgp-db';
import { FotografiasLocalesService, FotografiaPendiente } from './fotografias-locales.service';
const memoria = new Map<string, LocalPhoto>();
const contextos = new Map<string, LocalInspection>();
describe('Persistencia de reintentos fotográficos', () => {
  beforeEach(() => {
    memoria.clear();
    contextos.clear();
    vi.spyOn(sgpDb.inspecciones, 'put').mockImplementation((contexto: LocalInspection) => {
      contextos.set(contexto.id, contexto);
      return Promise.resolve(contexto.id) as never;
    });
    vi.spyOn(sgpDb.inspecciones, 'where').mockReturnValue({
      equals: (puente: string) => ({
        toArray: async () => [...contextos.values()].filter((c) => c.puenteId === puente),
      }),
    } as never);
    vi.spyOn(sgpDb, 'transaction').mockImplementation((...args: unknown[]) => {
      const accion = args.at(-1) as () => Promise<unknown>;
      return accion() as never;
    });
    vi.spyOn(sgpDb.fotos, 'get').mockImplementation(
      (id: unknown) => Promise.resolve(memoria.get(String(id))) as never,
    );
    vi.spyOn(sgpDb.fotos, 'put').mockImplementation((foto: LocalPhoto) => {
      memoria.set(foto.id, foto);
      return Promise.resolve(foto.id) as never;
    });
    vi.spyOn(sgpDb.fotos, 'delete').mockImplementation((id: unknown) => {
      memoria.delete(String(id));
      return Promise.resolve() as never;
    });
    vi.spyOn(sgpDb.fotos, 'where').mockReturnValue({
      equals: (id: string) => ({
        toArray: async () => [...memoria.values()].filter((f) => f.inspeccionId === id),
      }),
    } as never);
    vi.spyOn(sgpDb.cola_sync, 'put').mockResolvedValue('cola');
    vi.spyOn(sgpDb.cola_sync, 'delete').mockResolvedValue();
  });
  afterEach(() => vi.restoreAllMocks());
  const foto = (): FotografiaPendiente => ({
    id: 'f',
    inspeccionId: 'i',
    propietarioId: 'autor',
    idempotencyKey: 'clave',
    blob: new Blob(['comprimido']),
    mime: 'image/jpeg',
    sincronizado: false,
    metadatos: {
      id: 'f',
      categoria: 'ACCESO',
      latitud: 14,
      longitud: -90,
      capturadoEn: '2026-10-15T12:00:00Z',
    },
  });
  it('conserva clave, blob y metadatos y excluye datos de otro usuario o sin propietario', async () => {
    const servicio = TestBed.inject(FotografiasLocalesService);
    const original = foto();
    await servicio.guardar(original);
    memoria.set('legacy', { ...foto(), id: 'legacy', propietarioId: undefined });
    expect(await servicio.pendientes('autor', 'i')).toEqual([original]);
    expect(await servicio.pendientes('otro', 'i')).toEqual([]);
  });
  it('solo elimina el pendiente después de confirmar para el mismo usuario', async () => {
    const servicio = TestBed.inject(FotografiasLocalesService);
    await servicio.guardar(foto());
    await expect(servicio.confirmar('f', 'otro')).rejects.toThrow();
    expect(memoria.has('f')).toBe(true);
    await servicio.confirmar('f', 'autor');
    expect(memoria.has('f')).toBe(false);
  });

  it('solo ofrece continuar un contexto propio mientras existan fotos pendientes', async () => {
    const servicio = TestBed.inject(FotografiasLocalesService);
    const payload = { id: 'i', autorId: 'autor' };
    await servicio.guardarContexto('autor', 'i', 'puente', 'BORRADOR', payload);
    await servicio.guardar(foto());
    expect(await servicio.contextoPendiente('autor', 'puente')).toEqual(payload);
    expect(await servicio.contextoPendiente('otro', 'puente')).toBeNull();
    await servicio.confirmar('f', 'autor');
    expect(await servicio.contextoPendiente('autor', 'puente')).toBeNull();
  });
});
