import { TestBed } from '@angular/core/testing';
import { AuthStore } from '../core/services/auth.store';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { sgpDb, LocalInspection, LocalFormSchema, SyncQueueItem } from './db/sgp-db';
import {
  InspeccionesLocalesService,
  BorradorLocalPayload,
} from './services/inspecciones-locales.service';
import {
  IniciarInspeccionRequest,
  InspeccionBorradorResponse,
} from '../features/inspecciones/models/inspeccion.models';

describe('HU-042: Inspecciones Locales y Recuperación tras Recarga', () => {
  let service: InspeccionesLocalesService;
  const inspeccionesMemoria = new Map<string, LocalInspection>();
  const esquemasMemoria = new Map<string, LocalFormSchema>();
  const puentesMemoria = new Map<string, { id: string; codigo?: string; payload: unknown }>();
  const colaMemoria = new Map<string, SyncQueueItem>();

  beforeEach(() => {
    inspeccionesMemoria.clear();
    esquemasMemoria.clear();
    puentesMemoria.clear();
    colaMemoria.clear();

    vi.spyOn(sgpDb, 'transaction').mockImplementation((...args: unknown[]) => {
      const accion = args.at(-1) as () => Promise<unknown>;
      return accion() as never;
    });

    vi.spyOn(sgpDb.inspecciones, 'put').mockImplementation((item: LocalInspection) => {
      inspeccionesMemoria.set(item.id, item);
      return Promise.resolve(item.id) as never;
    });

    vi.spyOn(sgpDb.inspecciones, 'get').mockImplementation((id: unknown) => {
      return Promise.resolve(inspeccionesMemoria.get(String(id))) as never;
    });

    vi.spyOn(sgpDb.inspecciones, 'delete').mockImplementation((id: unknown) => {
      inspeccionesMemoria.delete(String(id));
      return Promise.resolve() as never;
    });

    vi.spyOn(sgpDb.inspecciones, 'toArray').mockImplementation(() => {
      return Promise.resolve([...inspeccionesMemoria.values()]) as never;
    });
    vi.spyOn(sgpDb.cola_sync, 'put').mockImplementation((item: SyncQueueItem) => {
      colaMemoria.set(item.id, item);
      return Promise.resolve(item.id) as never;
    });
    vi.spyOn(sgpDb.cola_sync, 'get').mockImplementation((id: unknown) => {
      return Promise.resolve(colaMemoria.get(String(id))) as never;
    });
    vi.spyOn(sgpDb.cola_sync, 'delete').mockImplementation((id: unknown) => {
      colaMemoria.delete(String(id));
      return Promise.resolve() as never;
    });
    vi.spyOn(sgpDb.cola_sync, 'orderBy').mockReturnValue({
      last: async () =>
        [...colaMemoria.values()].sort((a, b) => a.creadoEn.localeCompare(b.creadoEn)).at(-1),
    } as never);
    vi.spyOn(sgpDb.cola_sync, 'where').mockReturnValue({
      equals: (id: string) => ({
        toArray: async () => [...colaMemoria.values()].filter((item) => item.entityId === id),
        delete: async () => {
          for (const [key, item] of colaMemoria) if (item.entityId === id) colaMemoria.delete(key);
        },
      }),
    } as never);

    vi.spyOn(sgpDb.esquemas_formulario, 'put').mockImplementation((item: LocalFormSchema) => {
      esquemasMemoria.set(item.id, item);
      return Promise.resolve(item.id) as never;
    });
    vi.spyOn(sgpDb.esquemas_formulario, 'toArray').mockImplementation(
      () => Promise.resolve([...esquemasMemoria.values()]) as never,
    );
    vi.spyOn(sgpDb.esquemas_formulario, 'toCollection').mockReturnValue({
      modify: async (changes: Partial<LocalFormSchema> | ((schema: LocalFormSchema) => void)) => {
        for (const [id, schema] of esquemasMemoria) {
          if (typeof changes === 'function') changes(schema);
          else esquemasMemoria.set(id, { ...schema, ...changes });
        }
        return esquemasMemoria.size;
      },
    } as never);
    vi.spyOn(sgpDb.puentes, 'put').mockImplementation((item) => {
      puentesMemoria.set(item.id, item);
      return Promise.resolve(item.id) as never;
    });
    vi.spyOn(sgpDb.puentes, 'toArray').mockImplementation(
      () => Promise.resolve([...puentesMemoria.values()]) as never,
    );

    vi.spyOn(sgpDb.esquemas_formulario, 'get').mockImplementation((id: unknown) => {
      return Promise.resolve(esquemasMemoria.get(String(id))) as never;
    });

    vi.spyOn(sgpDb.esquemas_formulario, 'where').mockImplementation(((field: string) => {
      return {
        equals: (val: unknown) => ({
          toArray: async () =>
            [...esquemasMemoria.values()].filter(
              (e) => (e as unknown as Record<string, unknown>)[field] === val,
            ),
          first: async () =>
            [...esquemasMemoria.values()].find(
              (e) => (e as unknown as Record<string, unknown>)[field] === val,
            ),
        }),
      } as never;
    }) as never);

    TestBed.configureTestingModule({
      providers: [{ provide: AuthStore, useValue: { user: () => ({ id: 'user-1' }) } }],
    });
    service = TestBed.inject(InspeccionesLocalesService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('guarda una inspección en Dexie al iniciar sin conexión y conserva autor, fecha, GPS y version de formulario', async () => {
    const req: IniciarInspeccionRequest = {
      id: '018f6730-6b68-7182-9cb1-97cf67417088',
      puenteId: 'bridge-123',
      fechaInspeccion: '2026-10-20',
      dispositivoId: 'device-test-01',
      nombreAutor: 'Inspector Local',
      latitud: 14.835,
      longitud: -91.518,
      precisionGpsM: 5.5,
      confirmarDatosPermanentes: false,
    };

    const res = await service.iniciarInspeccionLocal(
      req,
      'user-1',
      'ESTUDIANTE',
      'Inspector Local',
      'Puente Los Altos',
      'P-001',
      'Quetzaltenango',
      'Quetzaltenango',
      'version-sieca-v1',
      null,
      false,
    );

    expect(res.id).toBe(req.id);
    expect(res.estado).toBe('BORRADOR');

    // Comprobar persistencia directa en Dexie
    const local = await service.obtenerInspeccionLocal(req.id!);
    expect(local).toBeDefined();
    expect(local?.id).toBe(req.id);
    expect(local?.sincronizado).toBe(false);
    const operacion = [...colaMemoria.values()][0];
    expect(operacion?.operacion).toBe('CREAR_INSPECCION');
    expect(operacion?.propietarioId).toBe('user-1');
    expect(operacion?.idempotencyKey).toBe(req.id);
    expect(local?.propietarioId).toBe('user-1');

    const payload = local?.payload as BorradorLocalPayload;
    expect(payload.borrador.version).toBe(0);
    expect(payload.borrador.nombrePuente).toBe('Puente Los Altos');
    expect(payload.borrador.fechaInspeccion).toBe('2026-10-20');
    expect(payload.versionFormularioId).toBe('version-sieca-v1');
    expect(payload.sinGps).toBe(false);
  });

  it('recupera el borrador con los cambios intactos simulando una recarga de página', async () => {
    const inspeccionId = '018f6730-6b68-7182-9cb1-97cf67417999';
    const initialItem: LocalInspection = {
      id: inspeccionId,
      puenteId: 'bridge-123',
      propietarioId: 'user-1',
      estado: 'BORRADOR',
      actualizadoEn: new Date().toISOString(),
      sincronizado: false,
      payload: {
        borrador: {
          id: inspeccionId,
          puenteId: 'bridge-123',
          codigoPuente: 'P-001',
          nombrePuente: 'Puente Los Altos',
          versionFormularioId: 'v1',
          codigoVersionFormulario: 'v1',
          autorId: 'user-1',
          rolAutor: 'ESTUDIANTE',
          nombreAutor: 'Estudiante 1',
          estado: 'BORRADOR',
          fechaInspeccion: '2026-10-20',
          dispositivoId: 'dev-1',
          version: 1,
          creadoEn: new Date().toISOString(),
          acompanantes: [],
          datos: {
            identificacion: { puente_km: 12.5 },
            superestructura: { vigas_tipo: 'Concreto' },
          },
          progreso: {
            totalCamposRequeridos: 10,
            totalCamposCompletados: 2,
            porcentajeTotal: 20,
            secciones: [],
          },
        },
        versionFormularioId: 'v1',
        dispositivoId: 'dev-1',
        autorId: 'user-1',
      },
    };

    // Guardado inicial
    await service.guardarInspeccionLocal(initialItem);

    // Simular que el usuario modificó otro campo y se guardó en Dexie
    const modificado = structuredClone(initialItem);
    const modPayload = modificado.payload as BorradorLocalPayload;
    modPayload.borrador.datos = {
      ...modPayload.borrador.datos,
      subestructura: { estribos_estado: 'Bueno' },
    };
    await service.guardarInspeccionLocal(modificado);

    // Simular "recarga de página": instanciar o solicitar de nuevo a Dexie
    const recuperado = await service.obtenerInspeccionLocal(inspeccionId);
    expect(recuperado).toBeDefined();

    const recuperadoPayload = recuperado?.payload as BorradorLocalPayload;
    expect(recuperadoPayload.borrador.datos).toEqual({
      identificacion: { puente_km: 12.5 },
      superestructura: { vigas_tipo: 'Concreto' },
      subestructura: { estribos_estado: 'Bueno' },
    });
  });

  it('no regresa la versión confirmada del servidor al encolar datos desde una página reanudada', async () => {
    const inspeccionId = 'inspection-version-regression';
    const borrador = {
      id: inspeccionId,
      puenteId: 'bridge-1',
      codigoPuente: 'P-001',
      nombrePuente: 'Puente local',
      versionFormularioId: 'form-v1',
      codigoVersionFormulario: 'v1',
      autorId: 'user-1',
      rolAutor: 'ESTUDIANTE',
      nombreAutor: 'Inspector',
      estado: 'BORRADOR',
      fechaInspeccion: '2026-10-20',
      dispositivoId: 'device-1',
      acompanantes: [],
      version: 4,
      creadoEn: '2026-10-20T10:00:00Z',
      progreso: {
        totalCamposRequeridos: 0,
        totalCamposCompletados: 0,
        porcentajeTotal: 0,
        secciones: [],
      },
      datos: { campo: 'confirmado' },
    };
    const local: LocalInspection = {
      id: inspeccionId,
      puenteId: borrador.puenteId,
      propietarioId: 'user-1',
      estado: 'BORRADOR',
      actualizadoEn: borrador.creadoEn,
      payload: {
        borrador,
        versionFormularioId: borrador.versionFormularioId,
        dispositivoId: borrador.dispositivoId,
        autorId: borrador.autorId,
      } satisfies BorradorLocalPayload,
      sincronizado: false,
    };
    inspeccionesMemoria.set(inspeccionId, local);

    await service.guardarBorradorLocal(
      {
        ...local,
        payload: {
          ...(local.payload as BorradorLocalPayload),
          borrador: { ...borrador, version: 3, datos: { campo: 'cambio local' } },
        },
      },
      { version: 3, datos: { campo: 'cambio local' } },
    );

    const guardado = (inspeccionesMemoria.get(inspeccionId)?.payload as BorradorLocalPayload)
      .borrador;
    expect(guardado.version).toBe(4);
    expect(guardado.datos).toEqual({ campo: 'cambio local' });
    expect([...colaMemoria.values()][0]?.estado).toBe('PENDIENTE');
  });

  it('actualiza una copia canónica solo si no hay ediciones pendientes', async () => {
    const canonico: InspeccionBorradorResponse = {
      id: 'inspection-canonical-refresh',
      puenteId: 'bridge-1',
      codigoPuente: 'P-001',
      nombrePuente: 'Puente',
      versionFormularioId: 'form-v1',
      codigoVersionFormulario: 'v1',
      autorId: 'user-1',
      rolAutor: 'ESTUDIANTE',
      nombreAutor: 'Inspector',
      estado: 'BORRADOR',
      fechaInspeccion: '2026-10-20',
      dispositivoId: 'device-1',
      acompanantes: [],
      version: 2,
      creadoEn: '2026-10-20T10:00:00Z',
      progreso: {
        totalCamposRequeridos: 0,
        totalCamposCompletados: 0,
        porcentajeTotal: 0,
        secciones: [],
      },
      datos: { campo: 'confirmado' },
    };

    expect(await service.actualizarBorradorCanonicoSiSinPendientes(canonico, 'user-1')).toBe(true);
    inspeccionesMemoria.set(canonico.id, {
      ...inspeccionesMemoria.get(canonico.id)!,
      estado: 'ENVIADA',
      intencionFinal: { accion: 'enviar', estado: 'CONFIRMADA' },
    });
    expect(await service.actualizarBorradorCanonicoSiSinPendientes(canonico, 'user-1')).toBe(true);
    expect(inspeccionesMemoria.get(canonico.id)?.intencionFinal).toBeUndefined();
    expect(inspeccionesMemoria.get(canonico.id)?.estado).toBe('BORRADOR');
    const edicionLocal: LocalInspection = {
      id: canonico.id,
      puenteId: canonico.puenteId,
      propietarioId: 'user-1',
      estado: 'BORRADOR',
      actualizadoEn: '2026-10-20T10:01:00Z',
      sincronizado: true,
      actualizacionLocalPendiente: true,
      payload: {
        borrador: { ...canonico, datos: { campo: 'edición sin conexión' } },
      },
    };
    await service.guardarBorradorEnCache(edicionLocal);

    expect(
      await service.actualizarBorradorCanonicoSiSinPendientes(
        { ...canonico, version: 3 },
        'user-1',
      ),
    ).toBe(false);
    expect(
      (inspeccionesMemoria.get(canonico.id)?.payload as BorradorLocalPayload).borrador.datos,
    ).toEqual({ campo: 'edición sin conexión' });
    expect(inspeccionesMemoria.get(canonico.id)?.actualizacionLocalPendiente).toBe(true);

    const operacion: SyncQueueItem = {
      id: 'save-pending',
      tipo: 'INSPECCION',
      entityId: canonico.id,
      propietarioId: 'user-1',
      operacion: 'GUARDAR_BORRADOR',
      payload: { version: 2, datos: { campo: 'edición sin conexión' } },
      idempotencyKey: 'pending-key',
      intentos: 0,
      creadoEn: '2026-10-20T10:01:00Z',
      estado: 'PENDIENTE',
    };
    colaMemoria.set(operacion.id, operacion);

    expect(
      await service.actualizarBorradorCanonicoSiSinPendientes(
        { ...canonico, version: 3, datos: { campo: 'otra versión del servidor' } },
        'user-1',
      ),
    ).toBe(false);
    expect(
      (inspeccionesMemoria.get(canonico.id)?.payload as BorradorLocalPayload).borrador.datos,
    ).toEqual({ campo: 'edición sin conexión' });
    expect(colaMemoria.get(operacion.id)).toEqual(operacion);
  });

  it('conserva campos locales al confirmar la sincronización y reabrir el borrador', async () => {
    const inspeccionId = 'inspection-form-data-roundtrip';
    const camposGuardados = {
      subestructura: { estribo_entrada: { estado: 'Bueno', observacion: 'Sin fisuras' } },
    };
    const borrador: InspeccionBorradorResponse = {
      id: inspeccionId,
      puenteId: 'bridge-1',
      codigoPuente: 'P-001',
      nombrePuente: 'Puente local',
      versionFormularioId: 'form-v1',
      codigoVersionFormulario: 'v1',
      autorId: 'user-1',
      rolAutor: 'ESTUDIANTE',
      nombreAutor: 'Inspector',
      estado: 'BORRADOR',
      fechaInspeccion: '2026-10-20',
      dispositivoId: 'device-1',
      version: 4,
      creadoEn: '2026-10-20T10:00:00Z',
      acompanantes: [],
      progreso: {
        totalCamposRequeridos: 0,
        totalCamposCompletados: 0,
        porcentajeTotal: 0,
        secciones: [],
      },
      datos: camposGuardados,
    };
    await service.guardarInspeccionLocal({
      id: inspeccionId,
      puenteId: 'bridge-1',
      propietarioId: 'user-1',
      estado: 'BORRADOR',
      actualizadoEn: '2026-10-20T10:01:00Z',
      payload: {
        borrador,
        versionFormularioId: 'form-v1',
        dispositivoId: 'device-1',
        autorId: 'user-1',
      },
      sincronizado: false,
    });
    colaMemoria.set('save-1', {
      id: 'save-1',
      tipo: 'INSPECCION',
      entityId: inspeccionId,
      propietarioId: 'user-1',
      idempotencyKey: 'same-key-on-retry',
      operacion: 'GUARDAR_BORRADOR',
      payload: { version: 4, datos: camposGuardados },
      intentos: 1,
      creadoEn: '2026-10-20T10:01:00Z',
    });

    await service.confirmarGuardado('save-1', inspeccionId, 'user-1', {
      ...borrador,
      version: 5,
      datos: { identificacion: { observacion: 'Confirmado por servidor' } },
    });

    const reabierta = await service.obtenerInspeccionLocal(inspeccionId);
    const recuperado = (reabierta?.payload as BorradorLocalPayload).borrador;
    expect(recuperado.datos).toEqual({
      identificacion: { observacion: 'Confirmado por servidor' },
    });
    expect(recuperado.version).toBe(5);
    expect(reabierta?.sincronizado).toBe(true);

    const cacheVacia = {
      ...reabierta!,
      sincronizado: false,
      payload: {
        ...(reabierta?.payload as BorradorLocalPayload),
        borrador: { ...recuperado, datos: {} },
      },
    };
    inspeccionesMemoria.set(inspeccionId, cacheVacia);
    colaMemoria.set('save-2', {
      id: 'save-2',
      tipo: 'INSPECCION',
      entityId: inspeccionId,
      propietarioId: 'user-1',
      idempotencyKey: 'stable-retry-key',
      operacion: 'GUARDAR_BORRADOR',
      payload: { version: 5, datos: camposGuardados },
      intentos: 1,
      creadoEn: '2026-10-20T10:02:00Z',
    });

    const restauradaOffline = await service.obtenerInspeccionLocal(inspeccionId);
    expect((restauradaOffline?.payload as BorradorLocalPayload).borrador.datos).toEqual(
      camposGuardados,
    );

    await service.confirmarGuardado('save-2', inspeccionId, 'user-1', {
      ...borrador,
      version: 6,
      datos: camposGuardados,
    });

    const recuperadaTrasRecarga = await service.obtenerInspeccionLocal(inspeccionId);
    expect((recuperadaTrasRecarga?.payload as BorradorLocalPayload).borrador.datos).toEqual(
      camposGuardados,
    );
    expect(recuperadaTrasRecarga?.sincronizado).toBe(true);
  });

  it('conserva la edición pendiente posterior y no reutiliza datos de la operación confirmada', async () => {
    const inspeccionId = 'inspection-newer-pending-edit';
    const datosOperacionConfirmada = { identificacion: { observacion: 'Versión enviada' } };
    const datosEdicionPosterior = { identificacion: { observacion: 'Edición posterior' } };
    const borrador: InspeccionBorradorResponse = {
      id: inspeccionId,
      puenteId: 'bridge-1',
      codigoPuente: 'P-001',
      nombrePuente: 'Puente local',
      versionFormularioId: 'form-v1',
      codigoVersionFormulario: 'v1',
      autorId: 'user-1',
      rolAutor: 'ESTUDIANTE',
      nombreAutor: 'Inspector',
      estado: 'BORRADOR',
      fechaInspeccion: '2026-10-20',
      dispositivoId: 'device-1',
      version: 4,
      creadoEn: '2026-10-20T10:00:00Z',
      acompanantes: [],
      progreso: {
        totalCamposRequeridos: 0,
        totalCamposCompletados: 0,
        porcentajeTotal: 0,
        secciones: [],
      },
      datos: datosOperacionConfirmada,
    };
    await service.guardarInspeccionLocal({
      id: inspeccionId,
      puenteId: 'bridge-1',
      propietarioId: 'user-1',
      estado: 'BORRADOR',
      actualizadoEn: '2026-10-20T10:01:00Z',
      payload: {
        borrador: { ...borrador, datos: datosEdicionPosterior },
        versionFormularioId: 'form-v1',
        dispositivoId: 'device-1',
        autorId: 'user-1',
      },
      sincronizado: false,
    });
    colaMemoria.set('save-confirmed', {
      id: 'save-confirmed',
      tipo: 'INSPECCION',
      entityId: inspeccionId,
      propietarioId: 'user-1',
      idempotencyKey: 'key-confirmed',
      operacion: 'GUARDAR_BORRADOR',
      payload: { version: 4, datos: datosOperacionConfirmada },
      intentos: 0,
      creadoEn: '2026-10-20T10:01:00Z',
    });
    colaMemoria.set('save-newer', {
      id: 'save-newer',
      tipo: 'INSPECCION',
      entityId: inspeccionId,
      propietarioId: 'user-1',
      idempotencyKey: 'key-newer',
      operacion: 'GUARDAR_BORRADOR',
      payload: { version: 5, datos: datosEdicionPosterior },
      intentos: 0,
      creadoEn: '2026-10-20T10:02:00Z',
    });

    await service.confirmarGuardado('save-confirmed', inspeccionId, 'user-1', {
      ...borrador,
      version: 5,
      datos: datosOperacionConfirmada,
    });

    const guardada = await service.obtenerInspeccionLocal(inspeccionId);
    expect((guardada?.payload as BorradorLocalPayload).borrador.datos).toEqual(
      datosEdicionPosterior,
    );
    expect(colaMemoria.has('save-confirmed')).toBe(false);
    expect(colaMemoria.has('save-newer')).toBe(true);
    expect(guardada?.sincronizado).toBe(false);
  });

  it('guarda y recupera el esquema congelado del formulario en Dexie', async () => {
    const esquema: LocalFormSchema = {
      id: 'version-sieca-3',
      version: '3.0',
      activo: true,
      schema: {
        tipo: 'formulario',
        secciones: [{ id: 'sec-1', titulo: 'Identificación' }],
      },
    };

    await service.guardarEsquemaLocal(esquema);

    const obtenido = await service.obtenerEsquemaLocal('version-sieca-3');
    expect(obtenido).toBeDefined();
    expect(obtenido?.version).toBe('3.0');
    expect(obtenido?.schema).toEqual(esquema.schema);

    await service.guardarEsquemaLocal({ ...esquema, id: 'version-sieca-4', version: '4.0' });
    expect((await service.obtenerEsquemaLocal(esquema.id))?.activo).toBe(false);
    expect((await service.obtenerEsquemaActivoLocal())?.origenId).toBe('version-sieca-4');
  });

  it('guarda puentes consultados para volver a seleccionarlos sin conexión', async () => {
    await service.guardarPuenteLocal({
      id: 'bridge-local',
      codigo: 'P-001',
      payload: { nombre: 'Puente local' },
    });

    expect(await service.obtenerPuentesPreparados()).toEqual([
      {
        id: 'cache:user-1:bridge-local',
        origenId: 'bridge-local',
        propietarioId: 'user-1',
        codigo: 'P-001',
        payload: { nombre: 'Puente local' },
      },
    ]);
  });

  it('lista inspecciones del usuario incluyendo las ya sincronizadas', async () => {
    inspeccionesMemoria.set('pendiente', {
      id: 'pendiente',
      puenteId: 'p1',
      propietarioId: 'user-1',
      estado: 'BORRADOR',
      actualizadoEn: '',
      payload: {},
      sincronizado: false,
    });
    inspeccionesMemoria.set('sincronizada', {
      id: 'sincronizada',
      puenteId: 'p2',
      propietarioId: 'user-1',
      estado: 'BORRADOR',
      actualizadoEn: '',
      payload: {},
      sincronizado: true,
    });
    inspeccionesMemoria.set('otro-usuario', {
      id: 'otro-usuario',
      puenteId: 'p3',
      propietarioId: 'user-2',
      estado: 'BORRADOR',
      actualizadoEn: '',
      payload: {},
      sincronizado: true,
    });

    const resultado = await service.listarPorUsuario('user-1');

    expect(resultado.map((item) => item.id)).toEqual(['pendiente', 'sincronizada']);
  });

  it('elimina un borrador local sin sincronizar de la base de datos Dexie', async () => {
    vi.spyOn(sgpDb.fotos, 'where').mockReturnValue({
      equals: () => ({ toArray: async () => [] }),
    } as never);
    vi.spyOn(sgpDb.fotos, 'bulkDelete').mockResolvedValue();
    vi.spyOn(sgpDb.cola_sync, 'toArray').mockImplementation(
      () => Promise.resolve([...colaMemoria.values()]) as never,
    );
    vi.spyOn(sgpDb.cola_sync, 'bulkDelete').mockImplementation((ids) => {
      for (const id of ids) colaMemoria.delete(String(id));
      return Promise.resolve() as never;
    });

    const id = '018f6730-local-to-delete';
    await service.guardarInspeccionLocal({
      id,
      puenteId: 'bridge-1',
      propietarioId: 'user-1',
      estado: 'BORRADOR',
      actualizadoEn: new Date().toISOString(),
      sincronizado: false,
      payload: {},
    });

    let encontrado = await service.obtenerInspeccionLocal(id);
    expect(encontrado).toBeDefined();

    await service.eliminarInspeccionLocal(id);

    encontrado = await service.obtenerInspeccionLocal(id);
    expect(encontrado).toBeUndefined();
  });

  it('permite recuperar un borrador local tanto con envoltura { borrador: ... } como con formato de borrador directo', async () => {
    // 1. Formato con envoltura { borrador: ... } (HU-042 iniciarInspeccionLocal)
    const idEnvoltorio = '018f6730-env';
    await service.guardarInspeccionLocal({
      id: idEnvoltorio,
      puenteId: 'bridge-1',
      propietarioId: 'user-1',
      estado: 'BORRADOR',
      actualizadoEn: new Date().toISOString(),
      sincronizado: false,
      payload: {
        borrador: {
          id: idEnvoltorio,
          puenteId: 'bridge-1',
          codigoPuente: 'P-001',
          nombrePuente: 'Puente Envoltorio',
          versionFormularioId: 'v1',
          codigoVersionFormulario: 'v1',
          autorId: 'user-1',
          rolAutor: 'ESTUDIANTE',
          nombreAutor: 'Estudiante',
          estado: 'BORRADOR',
          fechaInspeccion: '2026-10-20',
          dispositivoId: 'dev-1',
          version: 1,
          creadoEn: new Date().toISOString(),
          acompanantes: [],
          datos: { seccion1: { campo: 'valor1' } },
        },
        versionFormularioId: 'v1',
      },
    });

    // 2. Formato directo (almacenado como InspeccionBorradorResponse plano)
    const idDirecto = '018f6730-direct';
    await service.guardarInspeccionLocal({
      id: idDirecto,
      puenteId: 'bridge-2',
      propietarioId: 'user-1',
      estado: 'BORRADOR',
      actualizadoEn: new Date().toISOString(),
      sincronizado: true,
      payload: {
        id: idDirecto,
        puenteId: 'bridge-2',
        codigoPuente: 'P-002',
        nombrePuente: 'Puente Directo',
        versionFormularioId: 'v2',
        codigoVersionFormulario: 'v2',
        autorId: 'user-1',
        rolAutor: 'CATEDRATICO',
        nombreAutor: 'Profesor',
        estado: 'BORRADOR',
        fechaInspeccion: '2026-10-18',
        dispositivoId: 'dev-2',
        version: 1,
        creadoEn: new Date().toISOString(),
        acompanantes: [],
        datos: { seccion1: { campo: 'valor2' } },
      },
    });

    const normalizar = (item: LocalInspection | undefined) => {
      const raw = item?.payload as Record<string, unknown> | undefined;
      return (
        raw && typeof raw === 'object' && 'borrador' in raw && raw['borrador']
          ? raw['borrador']
          : raw
      ) as Record<string, unknown> | undefined;
    };

    const recuperadoEnv = await service.obtenerInspeccionLocal(idEnvoltorio);
    const borradorEnv = normalizar(recuperadoEnv);
    expect(borradorEnv).toBeDefined();
    expect(borradorEnv?.['fechaInspeccion']).toBe('2026-10-20');
    expect(borradorEnv?.['nombrePuente']).toBe('Puente Envoltorio');

    const recuperadoDirecto = await service.obtenerInspeccionLocal(idDirecto);
    const borradorDirecto = normalizar(recuperadoDirecto);
    expect(borradorDirecto).toBeDefined();
    expect(borradorDirecto?.['fechaInspeccion']).toBe('2026-10-18');
    expect(borradorDirecto?.['nombrePuente']).toBe('Puente Directo');
  });
});
