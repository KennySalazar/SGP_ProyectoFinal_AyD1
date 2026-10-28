import { Blob as BlobNode } from 'node:buffer';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { SincronizacionService } from './services/sincronizacion.service';
import { TokenRefreshService } from '../core/services/token-refresh.service';
import { InspeccionApiService } from '../features/inspecciones/services/inspeccion-api.service';
import { ConnectivityService } from './services/connectivity.service';
import { FotografiaResponse } from '../features/inspecciones/models/fotografia.models';
import { signal } from '@angular/core';
import Dexie from 'dexie';
import { IDBKeyRange, indexedDB } from 'fake-indexeddb';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../core/services/auth.store';
import { sgpDb, SgpDatabase } from './db/sgp-db';
import {
  FotografiasLocalesService,
  FotografiaPendiente,
} from './services/fotografias-locales.service';
import { InspeccionesLocalesService } from './services/inspecciones-locales.service';
import { StorageService } from './services/storage.service';

const deps = { ...Reflect.get(sgpDb, '_deps') };
const dependenciasDexie = { ...Dexie.dependencies };
const cacheOriginal = Reflect.get(sgpDb, '_options').cache;
describe('HU-057: transacciones fotográficas durables', () => {
  let servicio: FotografiasLocalesService;
  const user = signal({ id: 'autor' });
  const cambiosSesion$ = new Subject<string | null>();
  const api = { adjuntarFotografia: vi.fn() };
  const foto = (id = 'foto'): FotografiaPendiente => ({
    id,
    inspeccionId: 'inspeccion',
    propietarioId: 'autor',
    idempotencyKey: 'clave-' + id,
    blob: new BlobNode(['bytes-originales'], { type: 'image/jpeg' }) as unknown as Blob,
    mime: 'image/jpeg',
    sincronizado: false,
    metadatos: {
      id,
      categoria: 'ACCESO',
      latitud: 14,
      longitud: -90,
      capturadoEn: '2026-10-20T12:00:00Z',
      elementoRef: '/campo',
    },
  });
  beforeEach(async () => {
    Object.assign(Dexie.dependencies, { indexedDB, IDBKeyRange });
    // jsdom y Node usan clases Blob distintas. fake-indexeddb clona el Blob de Node;
    // desactivar únicamente la caché de lecturas evita que Dexie lo clone como objeto.
    // La durabilidad, las transacciones y las notificaciones liveQuery siguen siendo reales.
    Reflect.get(sgpDb, '_options').cache = 'disabled';
    Object.assign(Reflect.get(sgpDb, '_deps'), { indexedDB, IDBKeyRange });
    await sgpDb.open();
    user.set({ id: 'autor' });
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthStore, useValue: { user, cambiosSesion$, loadMe: () => of(user()) } },
        {
          provide: TokenRefreshService,
          useValue: { refresh: () => of({ accessToken: 'prueba' }) },
        },
        { provide: ConnectivityService, useValue: { online: () => true } },
        { provide: InspeccionApiService, useValue: api },
      ],
    });
    servicio = TestBed.inject(FotografiasLocalesService);
    await sgpDb.inspecciones.put({
      id: 'inspeccion',
      propietarioId: 'autor',
      puenteId: 'puente',
      estado: 'BORRADOR',
      actualizadoEn: '',
      payload: {},
      sincronizado: false,
    });
  });
  afterEach(async () => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
    await sgpDb.delete();
    Object.assign(Reflect.get(sgpDb, '_deps'), deps);
    Object.assign(Dexie.dependencies, dependenciasDexie);
    Reflect.get(sgpDb, '_options').cache = cacheOriginal;
  });
  it('sobrevive al cierre y reapertura con otra instancia, conservando Blob, GPS, fecha, UUID y clave', async () => {
    await servicio.guardar(foto());
    sgpDb.close();
    const reabierta = new SgpDatabase();
    Object.assign(Reflect.get(reabierta, '_deps'), { indexedDB, IDBKeyRange });
    await reabierta.open();
    const recuperada = (await reabierta.fotos.get('foto'))!;
    expect(recuperada.metadatos).toEqual(foto().metadatos);
    expect(recuperada.blob.size).toBe(foto().blob.size);
    expect((await reabierta.cola_sync.get('foto'))?.idempotencyKey).toBe('clave-foto');
    reabierta.close();
    await sgpDb.open();
  });
  it('serializa escrituras concurrentes y admite como máximo 60', async () => {
    await sgpDb.fotos.bulkPut(Array.from({ length: 59 }, (_, n) => foto(String(n))));
    const resultados = await Promise.allSettled([
      servicio.guardar(foto('60')),
      servicio.guardar(foto('61')),
    ]);
    expect(resultados.filter((r) => r.status === 'fulfilled')).toHaveLength(1);
    expect(await sgpDb.fotos.count()).toBe(60);
    expect(await sgpDb.cola_sync.count()).toBe(1);
  });
  it('elimina foto y cola juntas, exige propietario y bloquea envíos activos o inciertos', async () => {
    await servicio.guardar(foto());
    await expect(servicio.eliminar('foto', 'otra')).rejects.toThrow('propietario');
    await sgpDb.cola_sync.update('foto', { estado: 'ENVIANDO', intentos: 1 });
    await expect(servicio.eliminar('foto', 'autor')).rejects.toThrow('envioIncierto');
    await sgpDb.cola_sync.update('foto', { estado: 'PENDIENTE' });
    await expect(servicio.eliminar('foto', 'autor')).rejects.toThrow('envioIncierto');
    await sgpDb.cola_sync.update('foto', { intentos: 0 });
    await servicio.eliminar('foto', 'autor');
    expect(await sgpDb.fotos.count()).toBe(0);
    expect(await sgpDb.cola_sync.count()).toBe(0);
  });
  it('el borrado explícito del borrador limpia hijos; la limpieza automática conserva pendientes', async () => {
    await servicio.guardar(foto());
    const locales = TestBed.inject(InspeccionesLocalesService);
    expect(await locales.liberarInspeccionesSincronizadas('autor')).toBe(0);
    await locales.eliminarInspeccionLocal('inspeccion');
    expect(await sgpDb.fotos.count()).toBe(0);
    expect(await sgpDb.cola_sync.count()).toBe(0);
  });
  it('distingue una creación nunca enviada de un borrador remoto con cambios pendientes', async () => {
    const locales = TestBed.inject(InspeccionesLocalesService);
    expect(await locales.esBorradorSoloLocal('inspeccion')).toBe(false);
    await sgpDb.cola_sync.put({
      id: 'inicio',
      entityId: 'inspeccion',
      tipo: 'INSPECCION',
      propietarioId: 'autor',
      operacion: 'CREAR_INSPECCION',
      idempotencyKey: 'clave',
      intentos: 0,
      estado: 'PENDIENTE',
      creadoEn: '',
    });
    expect(await locales.esBorradorSoloLocal('inspeccion')).toBe(true);
    await sgpDb.cola_sync.update('inicio', { iniciadoEn: '2026-10-28T12:00:00Z' });
    await expect(locales.esBorradorSoloLocal('inspeccion')).rejects.toThrow('creacionPendiente');
  });

  it('bloquea el borrado durante la creación y permite eliminar tras confirmarla', async () => {
    await sgpDb.inspecciones.update('inspeccion', {
      payload: { versionFormularioId: 'v1', borrador: { datos: {} } },
    });
    const item = {
      id: 'inicio',
      entityId: 'inspeccion',
      tipo: 'INSPECCION' as const,
      propietarioId: 'autor',
      operacion: 'CREAR_INSPECCION' as const,
      idempotencyKey: 'clave',
      intentos: 0,
      estado: 'PENDIENTE' as const,
      creadoEn: '',
      payload: { id: 'inspeccion' },
    };
    await sgpDb.cola_sync.put(item);
    const respuesta = new Subject<unknown>();
    const crear = vi.fn().mockReturnValue(respuesta);
    Object.assign(api, { iniciarInspeccion: crear });
    const sync = TestBed.inject(SincronizacionService);
    const corrida = Reflect.get(sync, 'procesar').call(sync, item, 'autor');
    await vi.waitFor(() => expect(crear).toHaveBeenCalledOnce());
    const locales = TestBed.inject(InspeccionesLocalesService);
    await expect(locales.esBorradorSoloLocal('inspeccion')).rejects.toThrow('creacionPendiente');
    await expect(locales.eliminarInspeccionLocal('inspeccion', true)).rejects.toThrow(
      'creacionPendiente',
    );
    await expect(locales.confirmarEliminacionRemota('inspeccion', 'autor')).rejects.toThrow(
      'creacionPendiente',
    );
    expect(await sgpDb.inspecciones.count()).toBe(1);
    expect(await sgpDb.cola_sync.count()).toBe(1);
    respuesta.next({ id: 'inspeccion', versionFormularioId: 'v1' });
    respuesta.complete();
    await corrida;
    expect(await locales.esBorradorSoloLocal('inspeccion')).toBe(false);
    await locales.confirmarEliminacionRemota('inspeccion', 'autor');
    expect(await sgpDb.inspecciones.count()).toBe(0);
  });

  it('no envía una creación de una instantánea de cola descartada antes de reclamarla', async () => {
    const item = {
      id: 'inicio',
      entityId: 'inspeccion',
      tipo: 'INSPECCION' as const,
      propietarioId: 'autor',
      operacion: 'CREAR_INSPECCION' as const,
      idempotencyKey: 'clave',
      intentos: 0,
      estado: 'PENDIENTE' as const,
      creadoEn: '',
    };
    await sgpDb.cola_sync.put(item);
    await TestBed.inject(InspeccionesLocalesService).eliminarInspeccionLocal('inspeccion', true);
    const crear = vi.fn();
    Object.assign(api, { iniciarInspeccion: crear });
    const sync = TestBed.inject(SincronizacionService);
    await Reflect.get(sync, 'procesar').call(sync, item, 'autor');
    expect(crear).not.toHaveBeenCalled();
    expect(await sgpDb.inspecciones.count()).toBe(0);
  });

  it('bloquea una creación con resultado incierto incluso después de reiniciar los intentos', async () => {
    await sgpDb.cola_sync.put({
      id: 'inicio',
      entityId: 'inspeccion',
      tipo: 'INSPECCION',
      propietarioId: 'autor',
      operacion: 'CREAR_INSPECCION',
      idempotencyKey: 'clave',
      intentos: 0,
      estado: 'PENDIENTE',
      iniciadoEn: '2026-10-28T12:00:00Z',
      creadoEn: '',
    });
    const locales = TestBed.inject(InspeccionesLocalesService);
    await expect(locales.esBorradorSoloLocal('inspeccion')).rejects.toThrow('creacionPendiente');
    expect(await sgpDb.cola_sync.count()).toBe(1);
  });

  it('retira borrador remoto, fotos y cola de forma durable, conservando otras inspecciones', async () => {
    await servicio.guardar(foto());
    await sgpDb.inspecciones.update('inspeccion', { sincronizado: true });
    const otra = { ...(await sgpDb.inspecciones.get('inspeccion'))!, id: 'otra' };
    await sgpDb.inspecciones.put(otra);
    await sgpDb.cola_sync.put({
      id: 'guardado',
      entityId: 'inspeccion',
      tipo: 'INSPECCION',
      propietarioId: 'autor',
      operacion: 'GUARDAR_BORRADOR',
      idempotencyKey: 'clave',
      intentos: 2,
      estado: 'INTERVENCION',
      creadoEn: '',
    });
    const locales = TestBed.inject(InspeccionesLocalesService);
    await locales.confirmarEliminacionRemota('inspeccion', 'autor');
    // Un reintento de limpieza también debe funcionar.
    await locales.confirmarEliminacionRemota('inspeccion', 'autor');
    sgpDb.close();
    await sgpDb.open();
    expect(await sgpDb.inspecciones.get('inspeccion')).toBeUndefined();
    expect(await sgpDb.inspecciones.get('otra')).toBeDefined();
    expect(await sgpDb.fotos.count()).toBe(0);
    expect(await sgpDb.cola_sync.count()).toBe(0);
  });

  it('no elimina datos de otro propietario al confirmar una eliminación remota', async () => {
    await servicio.guardar(foto());
    await sgpDb.inspecciones.update('inspeccion', { propietarioId: 'otro' });
    await expect(
      TestBed.inject(InspeccionesLocalesService).confirmarEliminacionRemota('inspeccion', 'autor'),
    ).rejects.toThrow('offline.hu061.notEditable');
    expect(await sgpDb.inspecciones.count()).toBe(1);
    expect(await sgpDb.fotos.count()).toBe(1);
    expect(await sgpDb.cola_sync.count()).toBe(1);
  });

  it('rechaza cuota insuficiente antes de guardar y normaliza QuotaExceededError', async () => {
    const storage = TestBed.inject(StorageService);
    vi.spyOn(storage, 'usage').mockImplementation(async () => {
      storage.estimate.set({ usage: 999, quota: 1000 });
      return storage.estimate();
    });
    await expect(servicio.guardar(foto())).rejects.toThrow('espacioInsuficiente');
    expect(await sgpDb.fotos.count()).toBe(0);
    vi.spyOn(storage, 'prepararFotografia').mockResolvedValue();
    vi.spyOn(sgpDb.fotos, 'put').mockRejectedValue(new DOMException('quota', 'QuotaExceededError'));
    await expect(servicio.guardar(foto())).rejects.toThrow('espacioInsuficiente');
    expect(await sgpDb.cola_sync.count()).toBe(0);
  });
  it('reclama ENVIANDO atómicamente y bloquea un borrado durante la petición', async () => {
    await servicio.guardar(foto());
    const respuesta = new Subject<FotografiaResponse>();
    api.adjuntarFotografia.mockReturnValue(respuesta);
    const sync = TestBed.inject(SincronizacionService);
    const corrida = sync.sincronizarAhora();
    await vi.waitFor(async () =>
      expect((await sgpDb.cola_sync.get('foto'))?.estado).toBe('ENVIANDO'),
    );
    await expect(servicio.eliminar('foto', 'autor')).rejects.toThrow('envioIncierto');
    respuesta.next({
      ...foto().metadatos,
      inspeccionId: 'inspeccion',
      mimeReal: 'image/jpeg',
      tamanoBytes: 16,
      anchoPx: 100,
      altoPx: 100,
    } as FotografiaResponse);
    respuesta.complete();
    await corrida;
    expect(await sgpDb.fotos.count()).toBe(0);
    expect(await sgpDb.cola_sync.count()).toBe(0);
  });
  it('un rechazo conserva Blob y clave; el reintento usa exactamente la misma fotografía', async () => {
    await servicio.guardar(foto());
    api.adjuntarFotografia.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 422, error: { detail: 'no publicar credenciales' } }),
      ),
    );
    const sync = TestBed.inject(SincronizacionService);
    await sync.sincronizarAhora();
    expect((await sgpDb.cola_sync.get('foto'))?.estado).toBe('INTERVENCION');
    const antes = (await sgpDb.fotos.get('foto'))!;
    api.adjuntarFotografia.mockReturnValue(of({ ...foto().metadatos, inspeccionId: 'inspeccion' }));
    await sync.reintentar('foto');
    expect(api.adjuntarFotografia.mock.lastCall).toEqual([
      'inspeccion',
      antes.blob,
      antes.metadatos,
      antes.idempotencyKey,
    ]);
    expect(await sgpDb.fotos.count()).toBe(0);
  });
  it('conserva una foto incompleta y su operación para intervención en lugar de borrarla', async () => {
    await servicio.guardar(foto());
    await sgpDb.fotos.update('foto', { idempotencyKey: undefined });
    await TestBed.inject(SincronizacionService).sincronizarAhora();
    expect(await sgpDb.fotos.get('foto')).toBeDefined();
    expect((await sgpDb.cola_sync.get('foto'))?.codigoError).toBe('foto_local_incompleta');
  });

  it('recupera una foto sin cola usando su Blob y clave originales y exige propietario', async () => {
    await servicio.guardar(foto());
    await sgpDb.cola_sync.delete('foto');
    await expect(servicio.recuperarCola('foto', 'otra')).rejects.toThrow('datos');
    await expect(servicio.eliminar('foto', 'autor')).rejects.toThrow('envioIncierto');
    await servicio.recuperarCola('foto', 'autor');
    expect((await sgpDb.cola_sync.get('foto'))?.idempotencyKey).toBe('clave-foto');
    expect((await sgpDb.fotos.get('foto'))?.blob.size).toBe(foto().blob.size);
  });

  it('actualiza resumen y cola al editar IndexedDB o cambiar de usuario sin exponer la cuenta anterior', async () => {
    const sync = TestBed.inject(SincronizacionService);
    let ultimo = { inspecciones: -1, fotografias: -1 };
    let propietarios: (string | undefined)[] = [];
    const resumenSub = sync.resumen$.subscribe((r) => {
      ultimo = r;
    });
    const colaSub = sync.cola$.subscribe((r) => {
      propietarios = r.map((i) => i.propietarioId);
    });
    await vi.waitFor(() => expect(ultimo.inspecciones).toBe(1));
    await sgpDb.inspecciones.update('inspeccion', { sincronizado: true });
    await vi.waitFor(() => expect(ultimo.inspecciones).toBe(0));
    await servicio.guardar(foto());
    await vi.waitFor(() => expect(ultimo.fotografias).toBe(1));
    user.set({ id: 'otra' });
    cambiosSesion$.next('otra');
    await vi.waitFor(() => {
      expect(ultimo.fotografias).toBe(0);
      expect(propietarios).toEqual([]);
    });
    resumenSub.unsubscribe();
    colaSub.unsubscribe();
  });
  it('no guarda resultados vacíos, mayores de 400 KiB o con formato no admitido', async () => {
    for (const cambio of [
      { blob: new BlobNode([]) as unknown as Blob },
      { blob: new BlobNode([new Uint8Array(401 * 1024)]) as unknown as Blob },
      { mime: 'image/png' },
    ]) {
      await expect(servicio.guardar({ ...foto(), ...cambio })).rejects.toThrow('compresion');
    }
    expect(await sgpDb.fotos.count()).toBe(0);
    expect(await sgpDb.cola_sync.count()).toBe(0);
  });
  it('la limpieza de espacio respeta colas de fotos hijas y retira únicamente hijos confirmados', async () => {
    await sgpDb.inspecciones.update('inspeccion', { sincronizado: true });
    await sgpDb.fotos.put({ ...foto(), sincronizado: true });
    await sgpDb.cola_sync.put({
      id: 'foto',
      tipo: 'FOTO',
      entityId: 'foto',
      propietarioId: 'autor',
      idempotencyKey: 'clave-foto',
      intentos: 0,
      creadoEn: '',
    });
    const locales = TestBed.inject(InspeccionesLocalesService);
    expect(await locales.liberarInspeccionesSincronizadas('autor')).toBe(0);
    await sgpDb.cola_sync.delete('foto');
    await sgpDb.fotos.update('foto', { propietarioId: 'otra' });
    expect(await locales.liberarInspeccionesSincronizadas('autor')).toBe(0);
    await sgpDb.fotos.update('foto', { propietarioId: 'autor' });
    expect(await locales.liberarInspeccionesSincronizadas('autor')).toBe(1);
    expect(await sgpDb.fotos.count()).toBe(0);
  });
});
