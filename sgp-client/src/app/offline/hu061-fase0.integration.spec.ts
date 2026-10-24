import { IDBKeyRange, indexedDB } from 'fake-indexeddb';
import Dexie from 'dexie';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../core/services/auth.store';
import { UserSession } from '../core/models/auth.models';
import { SALIDA_DB, SalidaLocalService } from '../features/salida/services/salida-local.service';
import { PreparacionSalida } from '../features/salida/models/salida.models';
import { IniciarInspeccionDialogComponent } from '../features/inspecciones/components/iniciar-inspeccion-dialog/iniciar-inspeccion-dialog.component';
import { ReporteFotograficoComponent } from '../features/inspecciones/components/reporte-fotografico/reporte-fotografico.component';
import { CompletarInspeccionPage } from '../features/inspecciones/pages/completar-inspeccion/completar-inspeccion.page';
import { MisInspeccionesPage } from '../features/inspecciones/pages/mis-inspecciones/mis-inspecciones.page';
import {
  GuardarBorradorRequest,
  IniciarInspeccionRequest,
  InspeccionBorradorResponse,
} from '../features/inspecciones/models/inspeccion.models';
import { LocalInspection, sgpDb } from './db/sgp-db';
import {
  BorradorLocalPayload,
  InspeccionesLocalesService,
} from './services/inspecciones-locales.service';
import { FotografiasLocalesService } from './services/fotografias-locales.service';
import { ConnectivityService } from './services/connectivity.service';
import { SincronizacionService } from './services/sincronizacion.service';

// Caracterización de fase 0: los casos llamados «reproduce» documentan defectos actuales.
// Se conserva el singleton de producción, con todas sus tablas y transacciones reales.
// Dexie usa exclusivamente fake-indexeddb en memoria; no se accede a datos del navegador.

const AUTOR = '0199a000-0000-7000-8000-000000000001';
const OTRO = '0199a000-0000-7000-8000-000000000002';
const PUENTE = '0199a000-0000-7000-8000-000000000003';
const FORMULARIO = '0199a000-0000-7000-8000-000000000004';
const INSPECCION = '0199a000-0000-7000-8000-000000000005';
const FECHA = '2026-10-20T12:00:00Z';
const dependenciasOriginales = { ...Reflect.get(sgpDb, '_deps') };

function usuario(id = AUTOR): UserSession {
  return {
    id,
    email: id + '@test.example',
    role: 'CATEDRATICO',
    verified: true,
    activated: true,
    active: true,
    twoFactorEnabled: false,
  };
}

function solicitud(): IniciarInspeccionRequest {
  return {
    id: INSPECCION,
    puenteId: PUENTE,
    dispositivoId: 'fase0-equipo',
    fechaInspeccion: '2026-10-20',
    confirmarDatosPermanentes: false,
  };
}

function paquete(autor = AUTOR): PreparacionSalida {
  return {
    id: 'salida-fase0',
    usuarioId: autor,
    dispositivoId: 'fase0-equipo',
    preparadoEn: FECHA,
    numeroPuentes: 1,
    tamanoEstimadoBytes: 100,
    paquete: {
      formulario: {
        id: FORMULARIO,
        codigo: 'sieca-fase0-v3',
        hashContenido: 'a'.repeat(64),
        esquema: { type: 'object', properties: { campo: { type: 'string' } } },
      },
      puentes: [
        {
          id: PUENTE,
          codigo: 'FASE0-01',
          nombre: 'Puente fase 0',
          departamento: { id: 'd', nombre: 'Guatemala' },
          municipio: { id: 'm', nombre: 'Amatitlan' },
          latitud: 14,
          longitud: -90,
          activo: true,
        },
      ],
      inspecciones: [],
      asignaciones: [],
    },
  };
}

describe('HU-061 fase 0: integración local y contratos HTTP', () => {
  let locales: InspeccionesLocalesService;
  let fotos: FotografiasLocalesService;
  let auth: AuthStore;
  let http: HttpTestingController;
  const online = signal(false);

  beforeEach(async () => {
    Dexie.dependencies.indexedDB = indexedDB;
    Dexie.dependencies.IDBKeyRange = IDBKeyRange;
    // El singleton se construye al importar, antes de beforeEach. Actualizar su adaptador
    // capturado evita sustituir tablas, transacciones o servicios por mocks.
    Object.assign(Reflect.get(sgpDb, '_deps'), { indexedDB, IDBKeyRange });
    await sgpDb.open();
    sessionStorage.clear();
    online.set(false);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: INSPECCION }) } },
        },
        { provide: TranslocoService, useValue: { translate: (clave: string) => clave } },
        { provide: SALIDA_DB, useValue: sgpDb },
        { provide: ConnectivityService, useValue: { online } },
      ],
    });
    TestBed.overrideComponent(IniciarInspeccionDialogComponent, { set: { template: '' } });
    TestBed.overrideComponent(ReporteFotograficoComponent, { set: { template: '' } });
    TestBed.overrideComponent(CompletarInspeccionPage, {
      set: { template: '{{ borrador()?.autorId }}' },
    });
    TestBed.overrideComponent(MisInspeccionesPage, { set: { template: '' } });
    locales = TestBed.inject(InspeccionesLocalesService);
    fotos = TestBed.inject(FotografiasLocalesService);
    auth = TestBed.inject(AuthStore);
    auth.setUser(usuario());
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(async () => {
    try {
      http?.verify();
    } finally {
      vi.restoreAllMocks();
      TestBed.resetTestingModule();
      await sgpDb.delete();
      Object.assign(Reflect.get(sgpDb, '_deps'), dependenciasOriginales);
      sessionStorage.clear();
    }
  });

  async function iniciar() {
    await locales.iniciarInspeccionLocal(
      solicitud(),
      AUTOR,
      'CATEDRATICO',
      'Autor fase 0',
      'Puente fase 0',
      'FASE0-01',
      'Guatemala',
      'Amatitlan',
      FORMULARIO,
    );
    return (await sgpDb.inspecciones.get(INSPECCION))!;
  }

  async function editar(local: LocalInspection) {
    const payload = local.payload as BorradorLocalPayload;
    const datos = { campo: 'edición que debe sobrevivir', identificacion: { ruta: 'RN-1' } };
    const request: GuardarBorradorRequest = { version: 0, datos };
    const editado: LocalInspection = {
      ...local,
      actualizacionLocalPendiente: true,
      sincronizado: false,
      payload: { ...payload, borrador: { ...payload.borrador, datos } },
    };
    await locales.guardarBorradorLocal(editado, request);
    return { datos, editado };
  }

  async function autenticarSincronizacion(id = AUTOR) {
    http.expectOne('/api/v1/auth/refresh').flush({ accessToken: 'token-solo-prueba' });
    await vi.waitFor(() => http.expectOne('/api/v1/auth/me').flush(usuario(id)));
  }

  it('control: conserva UUID, versión, datos y claves tras cerrar y abrir IndexedDB', async () => {
    const local = await iniciar();
    const { datos } = await editar(local);
    const colaAntes = await sgpDb.cola_sync.orderBy('creadoEn').toArray();
    sgpDb.close();
    await sgpDb.open();
    expect(await sgpDb.cola_sync.orderBy('creadoEn').toArray()).toEqual(colaAntes);
    const recuperado = (await locales.obtenerInspeccionLocal(INSPECCION))!;
    expect(recuperado.propietarioId).toBe(AUTOR);
    expect((recuperado.payload as BorradorLocalPayload).borrador).toMatchObject({
      id: INSPECCION,
      versionFormularioId: FORMULARIO,
      datos,
    });
    expect(colaAntes.map((op) => op.operacion)).toEqual(['CREAR_INSPECCION', 'GUARDAR_BORRADOR']);
    expect(colaAntes[0].idempotencyKey).toBe(INSPECCION);
  });

  it('E12: reproduce que guardarContexto reemplaza el borrador editado, aunque la cola conserva los datos', async () => {
    const anterior = await iniciar();
    const { datos } = await editar(anterior);
    const contextoAnterior = {
      ...(anterior.payload as BorradorLocalPayload).borrador,
      datos: { campo: 'valor anterior conocido por el reporte' },
    };
    await fotos.guardarContexto(AUTOR, INSPECCION, PUENTE, 'BORRADOR', contextoAnterior);
    const registro = (await sgpDb.inspecciones.get(INSPECCION))!;
    expect(registro.payload).toEqual(contextoAnterior);
    expect((registro.payload as BorradorLocalPayload).borrador).toBeUndefined();
    expect((registro.payload as InspeccionBorradorResponse).datos).not.toEqual(datos);
    expect(registro.sincronizado).toBe(true);
    expect(registro.actualizacionLocalPendiente).toBeUndefined();
    const guardado = (await sgpDb.cola_sync.toArray()).find(
      (op) => op.operacion === 'GUARDAR_BORRADOR',
    );
    expect((guardado?.payload as GuardarBorradorRequest).datos).toEqual(datos);
    const visible = (await locales.obtenerInspeccionLocal(INSPECCION))!;
    expect((visible.payload as InspeccionBorradorResponse).datos).toEqual(contextoAnterior.datos);
    expect(await locales.liberarInspeccionesSincronizadas(AUTOR)).toBe(0);
  });

  it('E12: reproduce pérdida de edición solo en caché al guardar contexto y liberar espacio', async () => {
    const anterior = await iniciar();
    await sgpDb.cola_sync.clear();
    const payload = anterior.payload as BorradorLocalPayload;
    await locales.guardarBorradorEnCache({
      ...anterior,
      actualizacionLocalPendiente: true,
      payload: { ...payload, borrador: { ...payload.borrador, datos: { campo: 'nuevo' } } },
    });
    await fotos.guardarContexto(AUTOR, INSPECCION, PUENTE, 'BORRADOR', payload.borrador);
    expect(await locales.liberarInspeccionesSincronizadas(AUTOR)).toBe(1);
    expect(await sgpDb.inspecciones.get(INSPECCION)).toBeUndefined();
  });

  it('E12: montar el reporte real reemplaza el payload de una edición encolada', async () => {
    const anterior = await iniciar();
    const { datos } = await editar(anterior);
    const contexto = (anterior.payload as BorradorLocalPayload).borrador;
    const fixture = TestBed.createComponent(ReporteFotograficoComponent);
    fixture.componentRef.setInput('inspeccion', contexto);
    fixture.detectChanges();
    await vi.waitFor(() =>
      http
        .expectOne(`/api/v1/inspecciones/${INSPECCION}/fotografias`)
        .error(new ProgressEvent('error')),
    );
    expect((await sgpDb.inspecciones.get(INSPECCION))?.payload).toEqual(contexto);
    const guardado = (await sgpDb.cola_sync.toArray()).find(
      (op) => op.operacion === 'GUARDAR_BORRADOR',
    );
    expect((guardado?.payload as GuardarBorradorRequest).datos).toEqual(datos);
    fixture.destroy();
  });

  it('E12: reproduce que liberar espacio ignora actualizacionLocalPendiente aun sin reporte fotográfico', async () => {
    const local = await iniciar();
    await sgpDb.cola_sync.clear();
    await locales.guardarBorradorEnCache({ ...local, actualizacionLocalPendiente: true });
    expect(await sgpDb.inspecciones.get(INSPECCION)).toMatchObject({
      sincronizado: true,
      actualizacionLocalPendiente: true,
    });
    expect(await locales.liberarInspeccionesSincronizadas(AUTOR)).toBe(1);
    expect(await sgpDb.inspecciones.get(INSPECCION)).toBeUndefined();
  });

  it('E13: listado aísla B, pero lectura directa por ID devuelve el borrador de A', async () => {
    await iniciar();
    auth.setUser(usuario(OTRO));
    expect(await locales.listarPorUsuario(auth.user()!.id)).toEqual([]);
    const ajeno = await locales.obtenerInspeccionLocal(INSPECCION);
    expect(ajeno?.propietarioId).toBe(AUTOR);
    expect((ajeno?.payload as BorradorLocalPayload).borrador.autorId).toBe(AUTOR);
  });

  it('E13: reproduce referencias de A devueltas por lecturas locales bajo sesión de B', async () => {
    await TestBed.inject(SalidaLocalService).guardar(AUTOR, paquete());
    auth.setUser(usuario(OTRO));
    expect(await TestBed.inject(SalidaLocalService).ultima(OTRO)).toBeUndefined();
    expect((await locales.obtenerPuentesPreparados())[0].propietarioId).toBe(AUTOR);
    expect((await locales.obtenerEsquemaActivoLocal())?.propietarioId).toBe(AUTOR);
  });

  it('E13: página real muestra autor de A al abrir su ID sin red bajo sesión B', async () => {
    await iniciar();
    auth.setUser(usuario(OTRO));
    const fixture = TestBed.createComponent(CompletarInspeccionPage);
    fixture.detectChanges();
    await vi.waitFor(() => expect(fixture.componentInstance.borrador()?.autorId).toBe(AUTOR));
    fixture.detectChanges();
    expect(fixture.componentInstance.soloLectura()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain(AUTOR);
    http.expectNone(`/api/v1/inspecciones/${INSPECCION}/borrador`);
    fixture.destroy();
  });

  it('control E13: B no lista ni confirma fotografías pendientes de A', async () => {
    await fotos.guardar({
      id: 'foto-fase0',
      propietarioId: AUTOR,
      inspeccionId: INSPECCION,
      blob: new Blob(['solo-prueba']),
      mime: 'image/jpeg',
      sincronizado: false,
      idempotencyKey: 'foto-clave-fase0',
      metadatos: {
        id: 'foto-fase0',
        categoria: 'ACCESO',
        latitud: 14,
        longitud: -90,
        capturadoEn: FECHA,
      },
    });
    auth.setUser(usuario(OTRO));
    expect(await fotos.pendientes(OTRO, INSPECCION)).toEqual([]);
    await expect(fotos.confirmar('foto-fase0', OTRO)).rejects.toThrow('propietario');
    expect(await sgpDb.fotos.get('foto-fase0')).toBeDefined();
  });

  it('control E13: sincronizar con sesión B no envía ni elimina operaciones de A', async () => {
    await iniciar();
    const colaAntes = await sgpDb.cola_sync.toArray();
    auth.setUser(usuario(OTRO));
    online.set(true);
    const corrida = TestBed.inject(SincronizacionService).sincronizarAhora();
    await autenticarSincronizacion(OTRO);
    await corrida;
    http.expectNone('/api/v1/inspecciones');
    expect(await sgpDb.cola_sync.toArray()).toEqual(colaAntes);
  });

  it('preparación: conserva origenId, pero diálogo offline usa la clave local como versión y puente', async () => {
    await TestBed.inject(SalidaLocalService).guardar(AUTOR, paquete());
    const [puenteLocal] = await locales.obtenerPuentesPreparados();
    expect(puenteLocal.origenId).toBe(PUENTE);
    expect(puenteLocal.id).toBe(`salida:${AUTOR}:${PUENTE}`);
    const listado = TestBed.createComponent(MisInspeccionesPage);
    listado.componentInstance.busquedaPuente.set('FASE0');
    listado.componentInstance.buscarPuentes();
    await vi.waitFor(() => expect(listado.componentInstance.resultadosPuente()).toHaveLength(1));
    listado.componentInstance.seleccionarPuente(listado.componentInstance.resultadosPuente()[0]);
    await vi.waitFor(() => expect(listado.componentInstance.puenteSeleccionado()).not.toBeNull());
    const fixture = TestBed.createComponent(IniciarInspeccionDialogComponent);
    const dialogo = fixture.componentInstance;
    dialogo.puente = listado.componentInstance.puenteSeleccionado();
    dialogo.form.patchValue({ fechaInspeccion: '2026-10-20' });
    dialogo.iniciarInspeccion();
    await vi.waitFor(() => expect(dialogo.resultado()).not.toBeNull());
    const resultado = dialogo.resultado()!;
    expect(resultado.versionFormularioId).toBe(`salida:${AUTOR}:${FORMULARIO}`);
    expect(resultado.puenteId).toBe(puenteLocal.id);
    const [operacion] = await sgpDb.cola_sync.toArray();
    expect((operacion.payload as IniciarInspeccionRequest).puenteId).toBe(puenteLocal.id);
    expect(operacion.payload).not.toHaveProperty('versionFormularioId');
    fixture.destroy();
    listado.destroy();
  });

  it('sincronización: conserva UUID y orden, pero descarta la versión canónica devuelta al crear', async () => {
    const local = await iniciar();
    online.set(true);
    const corrida = TestBed.inject(SincronizacionService).sincronizarAhora();
    await autenticarSincronizacion();
    await vi.waitFor(() => {
      const req = http.expectOne('/api/v1/inspecciones');
      expect(req.request.method).toBe('POST');
      expect(req.request.headers.get('Idempotency-Key')).toBe(INSPECCION);
      expect(req.request.body).toEqual(solicitud());
      expect(req.request.body).not.toHaveProperty('versionFormularioId');
      req.flush({
        ...(local.payload as BorradorLocalPayload).borrador,
        versionFormularioId: '0199a000-0000-7000-8000-000000000099',
      });
    });
    await corrida;
    const despues = (await sgpDb.inspecciones.get(INSPECCION))!;
    expect(despues.sincronizado).toBe(true);
    expect((despues.payload as BorradorLocalPayload).borrador.versionFormularioId).toBe(FORMULARIO);
    expect(await sgpDb.cola_sync.count()).toBe(0);
  });

  it('control: crea antes de guardar y conserva respuestas tras confirmaciones HTTP', async () => {
    const local = await iniciar();
    const { datos } = await editar(local);
    online.set(true);
    const corrida = TestBed.inject(SincronizacionService).sincronizarAhora();
    await autenticarSincronizacion();
    await vi.waitFor(() => {
      const req = http.expectOne('/api/v1/inspecciones');
      expect(req.request.body.id).toBe(INSPECCION);
      req.flush((local.payload as BorradorLocalPayload).borrador);
    });
    await vi.waitFor(() => {
      const req = http.expectOne(`/api/v1/inspecciones/${INSPECCION}/borrador`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body.datos).toEqual(datos);
      expect(req.request.headers.get('X-Sync-Conflict-Policy')).toBe('client-wins');
      req.flush({ ...(local.payload as BorradorLocalPayload).borrador, datos, version: 1 });
    });
    await corrida;
    expect(await sgpDb.cola_sync.count()).toBe(0);
    expect((await sgpDb.inspecciones.get(INSPECCION))?.payload).toMatchObject({
      borrador: { datos, version: 1 },
    });
  });
});
