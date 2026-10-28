import '@angular/compiler';
import { Injector } from '@angular/core';
import Dexie from 'dexie';
import { indexedDB, IDBKeyRange } from 'fake-indexeddb';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthStore } from '../core/services/auth.store';
import { InspeccionBorradorResponse } from '../features/inspecciones/models/inspeccion.models';
import { sgpDb } from './db/sgp-db';
import { InspeccionesLocalesService } from './services/inspecciones-locales.service';

describe('Reconciliacion de guardados ya confirmados', () => {
  const originales = { ...Reflect.get(sgpDb, '_deps') };
  let injector: ReturnType<typeof Injector.create>;
  let servicio: InspeccionesLocalesService;
  let owner: string;
  const servidor: InspeccionBorradorResponse = {
    id: 'inspeccion',
    autorId: 'autor',
    puenteId: 'puente',
    estado: 'BORRADOR',
    version: 18,
    versionFormularioId: 'formulario',
    fechaInspeccion: '2026-10-19',
    identificacionCapturada: {},
    acompanantes: [],
    datos: { otros: { recomendaciones: { mantenimiento: 'Recomendacion confirmada' } } },
    actualizadoEn: '2026-10-28T20:12:00Z',
    codigoPuente: 'GT-01',
    nombrePuente: 'Puente',
    codigoVersionFormulario: 'v1',
    rolAutor: 'ESTUDIANTE',
    nombreAutor: 'Autor',
    dispositivoId: 'equipo',
    creadoEn: '2026-10-19T12:00:00Z',
    progreso: {
      totalCamposRequeridos: 1,
      totalCamposCompletados: 1,
      porcentajeTotal: 100,
      secciones: [],
    },
  };
  beforeEach(async () => {
    Dexie.dependencies.indexedDB = indexedDB;
    Dexie.dependencies.IDBKeyRange = IDBKeyRange;
    Object.assign(Reflect.get(sgpDb, '_deps'), { indexedDB, IDBKeyRange });
    await sgpDb.open();
    owner = 'autor';
    injector = Injector.create({
      providers: [
        InspeccionesLocalesService,
        { provide: AuthStore, useValue: { user: () => ({ id: owner }) } },
      ],
    });
    servicio = injector.get(InspeccionesLocalesService);
    await sgpDb.inspecciones.put({
      id: 'inspeccion',
      puenteId: 'puente',
      propietarioId: 'autor',
      estado: 'BORRADOR',
      sincronizado: false,
      actualizadoEn: servidor.actualizadoEn!,
      payload: { borrador: servidor },
    });
    await sgpDb.cola_sync.bulkPut(
      ['viejo', 'pendiente'].map((id, i) => ({
        id,
        entityId: 'inspeccion',
        tipo: 'INSPECCION' as const,
        propietarioId: 'autor',
        operacion: 'GUARDAR_BORRADOR' as const,
        idempotencyKey: id,
        estado: i ? ('PENDIENTE' as const) : ('INTERVENCION' as const),
        intentos: 0,
        creadoEn: '2026-10-28T17:21:00Z',
        payload: { version: 12 + i, datos: { anterior: true } },
      })),
    );
  });
  afterEach(async () => {
    injector.destroy();
    await sgpDb.delete();
    Object.assign(Reflect.get(sgpDb, '_deps'), originales);
  });
  it('retira guardados superados sin escribir ni borrar fotografias', async () => {
    expect(await servicio.reconciliarGuardadosConfirmados(servidor, 'autor')).toBe(2);
    expect(await sgpDb.cola_sync.count()).toBe(0);
    const local = await sgpDb.inspecciones.get('inspeccion');
    expect(local?.payload).toEqual({ borrador: servidor });
    expect(local?.sincronizado).toBe(true);
  });
  it('conserva la cola completa si hay datos locales distintos del servidor', async () => {
    await sgpDb.inspecciones.update('inspeccion', {
      payload: { borrador: { ...servidor, datos: { nuevo: 'sin guardar' } } },
    });
    expect(await servicio.reconciliarGuardadosConfirmados(servidor, 'autor')).toBe(0);
    expect(await sgpDb.cola_sync.count()).toBe(2);
  });
  it('no descarta guardados posteriores cuyo resultado no se ha confirmado', async () => {
    await sgpDb.cola_sync.update('pendiente', { creadoEn: '2026-10-28T21:00:00Z' });
    expect(await servicio.reconciliarGuardadosConfirmados(servidor, 'autor')).toBe(0);
    expect(await sgpDb.cola_sync.count()).toBe(2);
  });
  it('no altera una operacion en curso ni retira parcialmente las demas', async () => {
    await sgpDb.cola_sync.update('pendiente', { estado: 'ENVIANDO' });
    expect(await servicio.reconciliarGuardadosConfirmados(servidor, 'autor')).toBe(0);
    expect(await sgpDb.cola_sync.count()).toBe(2);
  });
  it('no confirma envios ni publicaciones pendientes', async () => {
    await sgpDb.cola_sync.update('pendiente', { operacion: 'ENVIAR_INSPECCION' });
    expect(await servicio.reconciliarGuardadosConfirmados(servidor, 'autor')).toBe(0);
    expect(await sgpDb.cola_sync.count()).toBe(2);
  });
  it('rechaza el cambio de sesion', async () => {
    owner = 'otro';
    expect(await servicio.reconciliarGuardadosConfirmados(servidor, 'autor')).toBe(0);
    expect(await sgpDb.cola_sync.count()).toBe(2);
  });
  it('conserva ediciones locales pendientes aunque los datos comparados coincidan', async () => {
    await sgpDb.inspecciones.update('inspeccion', { actualizacionLocalPendiente: true });
    expect(await servicio.reconciliarGuardadosConfirmados(servidor, 'autor')).toBe(0);
    expect(await sgpDb.cola_sync.count()).toBe(2);
  });
});
