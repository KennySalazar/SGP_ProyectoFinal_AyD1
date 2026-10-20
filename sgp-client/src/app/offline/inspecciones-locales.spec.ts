import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { sgpDb, LocalInspection, LocalFormSchema } from './db/sgp-db';
import {
  InspeccionesLocalesService,
  BorradorLocalPayload,
} from './services/inspecciones-locales.service';
import { IniciarInspeccionRequest } from '../features/inspecciones/models/inspeccion.models';

describe('HU-042: Inspecciones Locales y Recuperación tras Recarga', () => {
  let service: InspeccionesLocalesService;
  const inspeccionesMemoria = new Map<string, LocalInspection>();
  const esquemasMemoria = new Map<string, LocalFormSchema>();

  beforeEach(() => {
    inspeccionesMemoria.clear();
    esquemasMemoria.clear();

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

    vi.spyOn(sgpDb.esquemas_formulario, 'put').mockImplementation((item: LocalFormSchema) => {
      esquemasMemoria.set(item.id, item);
      return Promise.resolve(item.id) as never;
    });

    vi.spyOn(sgpDb.esquemas_formulario, 'get').mockImplementation((id: unknown) => {
      return Promise.resolve(esquemasMemoria.get(String(id))) as never;
    });

    vi.spyOn(sgpDb.esquemas_formulario, 'where').mockImplementation((field: string) => {
      return {
        equals: (val: unknown) => ({
          toArray: async () =>
            [...esquemasMemoria.values()].filter(
              (e) => (e as Record<string, unknown>)[field] === val,
            ),
          first: async () =>
            [...esquemasMemoria.values()].find(
              (e) => (e as Record<string, unknown>)[field] === val,
            ),
        }),
      } as never;
    });

    service = new InspeccionesLocalesService();
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
      'user-uuid-1',
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
    const local = await service.obtenerInspeccionLocal(req.id);
    expect(local).toBeDefined();
    expect(local?.id).toBe(req.id);
    expect(local?.sincronizado).toBe(false);
    expect(local?.propietarioId).toBe('user-uuid-1');

    const payload = local?.payload as BorradorLocalPayload;
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
      propietarioId: 'user-uuid-1',
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
          autorId: 'user-uuid-1',
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
        autorId: 'user-uuid-1',
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
  });

  it('elimina un borrador local sin sincronizar de la base de datos Dexie', async () => {
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
});
