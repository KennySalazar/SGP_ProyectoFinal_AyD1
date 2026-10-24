import { Blob as BlobNode } from 'node:buffer';
import { ReferenciasLocalesService } from './services/referencias-locales.service';
import {
  CATEGORIAS_FOTO,
  FotografiaResponse,
} from '../features/inspecciones/models/fotografia.models';
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
} from '../features/inspecciones/models/inspeccion.models';
import { LocalInspection, sgpDb } from './db/sgp-db';
import {
  BorradorLocalPayload,
  InspeccionesLocalesService,
} from './services/inspecciones-locales.service';
import { FotografiasLocalesService } from './services/fotografias-locales.service';
import { ConnectivityService } from './services/connectivity.service';
import { SincronizacionService } from './services/sincronizacion.service';

// Regresiones de fase 0 y flujo final de HU-061 sobre IndexedDB real en memoria.
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

  async function prepararPublicacionPendiente() {
    const local = await iniciar();
    await evidencia(local);
    const datos = {
      campo: 'original',
      identificacion: { ruta: 'RN-1', departamento: 'Guatemala', municipio: 'Amatitlan' },
    };
    await locales.encolarAccionFinal(INSPECCION, 'publicar', { version: 0, datos });
    const final = (await sgpDb.cola_sync.toArray()).find(
      (op) => op.operacion === 'PUBLICAR_INSPECCION',
    )!;
    await sgpDb.cola_sync.filter((op) => op.id !== final.id).delete();
    return { final, borrador: { ...(local.payload as BorradorLocalPayload).borrador, datos } };
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

  it('E12: guardarContexto conserva el borrador editado y su cola', async () => {
    const anterior = await iniciar();
    const { datos } = await editar(anterior);
    const contextoAnterior = {
      ...(anterior.payload as BorradorLocalPayload).borrador,
      datos: { campo: 'valor anterior conocido por el reporte' },
    };
    await fotos.guardarContexto(AUTOR, INSPECCION, PUENTE, 'BORRADOR', contextoAnterior);
    const registro = (await sgpDb.inspecciones.get(INSPECCION))!;
    expect((registro.payload as BorradorLocalPayload).borrador.datos).toEqual(datos);
    expect(registro.sincronizado).toBe(false);
    expect(registro.actualizacionLocalPendiente).toBe(true);
    const guardado = (await sgpDb.cola_sync.toArray()).find(
      (op) => op.operacion === 'GUARDAR_BORRADOR',
    );
    expect((guardado?.payload as GuardarBorradorRequest).datos).toEqual(datos);
    const visible = (await locales.obtenerInspeccionLocal(INSPECCION))!;
    expect((visible.payload as BorradorLocalPayload).borrador.datos).toEqual(datos);
    expect(await locales.liberarInspeccionesSincronizadas(AUTOR)).toBe(0);
  });

  it('E12: conserva la edición solo en caché al guardar contexto y liberar espacio', async () => {
    const anterior = await iniciar();
    await sgpDb.cola_sync.clear();
    const payload = anterior.payload as BorradorLocalPayload;
    await locales.guardarBorradorEnCache({
      ...anterior,
      actualizacionLocalPendiente: true,
      payload: { ...payload, borrador: { ...payload.borrador, datos: { campo: 'nuevo' } } },
    });
    await fotos.guardarContexto(AUTOR, INSPECCION, PUENTE, 'BORRADOR', payload.borrador);
    expect(await locales.liberarInspeccionesSincronizadas(AUTOR)).toBe(0);
    expect(await sgpDb.inspecciones.get(INSPECCION)).toBeDefined();
  });

  it('E12: montar el reporte real conserva el payload de una edición encolada', async () => {
    const anterior = await iniciar();
    const { datos } = await editar(anterior);
    const contexto = (anterior.payload as BorradorLocalPayload).borrador;
    const fixture = TestBed.createComponent(ReporteFotograficoComponent);
    fixture.componentRef.setInput('inspeccion', contexto);
    fixture.detectChanges();
    await vi.waitFor(() => expect(fixture.componentInstance.inventarioCargado()).toBe(true));
    expect(
      ((await sgpDb.inspecciones.get(INSPECCION))?.payload as BorradorLocalPayload).borrador.datos,
    ).toEqual(datos);
    const guardado = (await sgpDb.cola_sync.toArray()).find(
      (op) => op.operacion === 'GUARDAR_BORRADOR',
    );
    expect((guardado?.payload as GuardarBorradorRequest).datos).toEqual(datos);
    fixture.destroy();
  });

  it('E12: liberar espacio protege actualizacionLocalPendiente aun sin reporte fotográfico', async () => {
    const local = await iniciar();
    await sgpDb.cola_sync.clear();
    await locales.guardarBorradorEnCache({ ...local, actualizacionLocalPendiente: true });
    expect(await sgpDb.inspecciones.get(INSPECCION)).toMatchObject({
      sincronizado: false,
      actualizacionLocalPendiente: true,
    });
    expect(await locales.liberarInspeccionesSincronizadas(AUTOR)).toBe(0);
    expect(await sgpDb.inspecciones.get(INSPECCION)).toBeDefined();
  });

  it('E13: listado y lectura directa por ID aíslan el borrador de A', async () => {
    await iniciar();
    auth.setUser(usuario(OTRO));
    expect(await locales.listarPorUsuario(auth.user()!.id)).toEqual([]);
    const ajeno = await locales.obtenerInspeccionLocal(INSPECCION);
    expect(ajeno).toBeUndefined();
  });

  it('E13: lecturas locales no devuelven referencias de A bajo sesión de B', async () => {
    await TestBed.inject(SalidaLocalService).guardar(AUTOR, paquete());
    auth.setUser(usuario(OTRO));
    expect(await TestBed.inject(SalidaLocalService).ultima(OTRO)).toBeUndefined();
    expect(await locales.obtenerPuentesPreparados()).toEqual([]);
    expect(await locales.obtenerEsquemaActivoLocal()).toBeUndefined();
  });

  it('E13: página real impide leer A al abrir su ID sin red bajo sesión B', async () => {
    await iniciar();
    auth.setUser(usuario(OTRO));
    const fixture = TestBed.createComponent(CompletarInspeccionPage);
    fixture.detectChanges();
    await vi.waitFor(() => expect(fixture.componentInstance.cargando()).toBe(false));
    fixture.detectChanges();
    expect(fixture.componentInstance.borrador()).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain(AUTOR);
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

  it('preparación: diálogo offline usa origenId para versión y puente', async () => {
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
    expect(resultado.versionFormularioId).toBe(FORMULARIO);
    expect(resultado.puenteId).toBe(PUENTE);
    const [operacion] = await sgpDb.cola_sync.toArray();
    expect((operacion.payload as IniciarInspeccionRequest).puenteId).toBe(PUENTE);
    expect(operacion.payload).toHaveProperty('versionFormularioId', FORMULARIO);
    fixture.destroy();
    listado.destroy();
  });

  it('sincronización: conserva UUID, orden y versión preparada al crear', async () => {
    const local = await iniciar();
    online.set(true);
    const corrida = TestBed.inject(SincronizacionService).sincronizarAhora();
    await autenticarSincronizacion();
    await vi.waitFor(() => {
      const req = http.expectOne('/api/v1/inspecciones');
      expect(req.request.method).toBe('POST');
      expect(req.request.headers.get('Idempotency-Key')).toBe(INSPECCION);
      expect(req.request.body).toEqual({ ...solicitud(), versionFormularioId: FORMULARIO });
      expect(req.request.body).toHaveProperty('versionFormularioId', FORMULARIO);
      req.flush({
        ...(local.payload as BorradorLocalPayload).borrador,
        versionFormularioId: FORMULARIO,
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
  async function evidencia(local: LocalInspection, pendientes = false) {
    const inventario: FotografiaResponse[] = CATEGORIAS_FOTO.filter((c) => c !== 'OTRA').map(
      (categoria) => ({
        id: categoria,
        inspeccionId: INSPECCION,
        categoria,
        latitud: 14,
        longitud: -90,
        capturadoEn: FECHA,
        mimeReal: 'image/jpeg',
        tamanoBytes: 100,
        anchoPx: 10,
        altoPx: 10,
      }),
    );
    if (pendientes) {
      for (const foto of inventario)
        await fotos.guardar({
          id: foto.id,
          inspeccionId: INSPECCION,
          propietarioId: AUTOR,
          blob: new BlobNode(['imagen']) as unknown as Blob,
          mime: 'image/jpeg',
          sincronizado: false,
          idempotencyKey: 'foto-' + foto.id,
          metadatos: foto,
        });
    } else await sgpDb.inspecciones.put({ ...local, fotosConfirmadas: inventario });
    return inventario;
  }

  it('encola una sola intención durable, mantiene BORRADOR y bloquea cambios posteriores', async () => {
    const local = await iniciar();
    await evidencia(local);
    await locales.encolarAccionFinal(INSPECCION, 'publicar', {
      version: 0,
      datos: { campo: 'confirmado' },
    });
    await locales.encolarAccionFinal(INSPECCION, 'publicar', {
      version: 0,
      datos: { campo: 'duplicado' },
    });
    const cola = await sgpDb.cola_sync.toArray();
    expect(cola.filter((op) => op.operacion === 'PUBLICAR_INSPECCION')).toHaveLength(1);
    const final = cola.find((op) => op.operacion === 'PUBLICAR_INSPECCION')!;
    sgpDb.close();
    await sgpDb.open();
    expect(await sgpDb.cola_sync.get(final.id)).toEqual(final);
    const actual = (await sgpDb.inspecciones.get(INSPECCION))!;
    expect(actual.estado).toBe('BORRADOR');
    expect(actual.intencionFinal?.estado).toBe('PENDIENTE');
    await expect(
      locales.guardarBorradorLocal(actual, { version: 0, datos: { campo: 'otra edición' } }),
    ).rejects.toThrow();
    expect(await locales.liberarInspeccionesSincronizadas(AUTOR)).toBe(0);
  });

  it('no encola sin evidencia mínima ni permite publicar a un estudiante', async () => {
    const local = await iniciar();
    await expect(
      locales.encolarAccionFinal(INSPECCION, 'publicar', { version: 0, datos: {} }),
    ).rejects.toThrow();
    await evidencia(local);
    auth.setUser({ ...usuario(), role: 'ESTUDIANTE' });
    await expect(
      locales.encolarAccionFinal(INSPECCION, 'publicar', { version: 0, datos: {} }),
    ).rejects.toThrow();
    await locales.encolarAccionFinal(INSPECCION, 'enviar', {
      version: 0,
      datos: { campo: 'estudiante' },
    });
    expect(
      (await sgpDb.cola_sync.toArray()).some((op) => op.operacion === 'ENVIAR_INSPECCION'),
    ).toBe(true);
  });

  it('sincroniza crear, datos, cuatro fotos y publicación en ese orden con claves estables', async () => {
    const local = await iniciar();
    const inventario = await evidencia(local, true);
    const datos = { campo: 'inspección completa' };
    await locales.encolarAccionFinal(INSPECCION, 'publicar', { version: 0, datos });
    const final = (await sgpDb.cola_sync.toArray()).find(
      (op) => op.operacion === 'PUBLICAR_INSPECCION',
    )!;
    online.set(true);
    // FormData de jsdom solo admite Blob de su propio realm.
    const append = FormData.prototype.append;
    vi.spyOn(FormData.prototype, 'append').mockImplementation(function (
      this: FormData,
      nombre: string,
      valor: string | Blob,
      archivo?: string,
    ) {
      if (typeof valor !== 'string' && !(valor instanceof Blob))
        valor = new Blob(['imagen'], { type: 'image/jpeg' });
      Reflect.apply(
        append,
        this,
        typeof valor === 'string' ? [nombre, valor] : [nombre, valor, archivo],
      );
    });
    const corrida = TestBed.inject(SincronizacionService).sincronizarAhora();
    await autenticarSincronizacion();
    await vi.waitFor(() =>
      http
        .expectOne('/api/v1/inspecciones')
        .flush((local.payload as BorradorLocalPayload).borrador),
    );
    await vi.waitFor(() => {
      const req = http.expectOne(`/api/v1/inspecciones/${INSPECCION}/borrador`);
      expect(req.request.body.datos).toEqual(datos);
      req.flush({ ...(local.payload as BorradorLocalPayload).borrador, datos, version: 1 });
    });
    for (const foto of inventario)
      await vi.waitFor(() => {
        http.expectNone(`/api/v1/inspecciones/${INSPECCION}/publicar`);
        const req = http.expectOne(`/api/v1/inspecciones/${INSPECCION}/fotografias`);
        expect(req.request.headers.get('Idempotency-Key')).toBe('foto-' + foto.id);
        req.flush(foto);
      });
    await vi.waitFor(() => {
      const req = http.expectOne(`/api/v1/inspecciones/${INSPECCION}/publicar`);
      expect(req.request.headers.get('Idempotency-Key')).toBe(final.idempotencyKey);
      req.flush({ id: INSPECCION, puenteId: PUENTE, estado: 'PUBLICADA' });
    });
    await corrida;
    expect(await sgpDb.cola_sync.count()).toBe(0);
    expect(await sgpDb.fotos.count()).toBe(0);
    expect((await sgpDb.inspecciones.get(INSPECCION))?.intencionFinal?.estado).toBe('CONFIRMADA');
    expect((await sgpDb.inspecciones.get(INSPECCION))?.fotosConfirmadas).toHaveLength(4);
  });

  it('un rechazo final conserva respuestas y permite una nueva intención corregida con otra clave', async () => {
    const local = await iniciar();
    await sgpDb.cola_sync.clear();
    await evidencia(local);
    await locales.encolarAccionFinal(INSPECCION, 'publicar', {
      version: 0,
      datos: { campo: 'original' },
    });
    const primera = (await sgpDb.cola_sync.toArray()).find(
      (op) => op.operacion === 'PUBLICAR_INSPECCION',
    )!;
    // Aislar el resultado final; creación/guardado se cubren en el recorrido anterior.
    await sgpDb.cola_sync
      .where('entityId')
      .equals(INSPECCION)
      .filter((op) => op.id !== primera.id)
      .delete();
    online.set(true);
    const corrida = TestBed.inject(SincronizacionService).sincronizarAhora();
    await autenticarSincronizacion();
    await vi.waitFor(() =>
      http
        .expectOne(`/api/v1/inspecciones/${INSPECCION}/publicar`)
        .flush(
          { detail: 'Falta evidencia en el servidor' },
          { status: 422, statusText: 'Unprocessable Entity' },
        ),
    );
    await corrida;
    const rechazada = (await sgpDb.inspecciones.get(INSPECCION))!;
    expect(rechazada.intencionFinal).toMatchObject({
      estado: 'RECHAZADA',
      error: 'Falta evidencia en el servidor',
    });
    expect((rechazada.payload as BorradorLocalPayload).borrador.datos).toEqual({
      campo: 'original',
    });
    await locales.encolarAccionFinal(INSPECCION, 'publicar', {
      version: 0,
      datos: { campo: 'corregido' },
    });
    const segunda = (await sgpDb.cola_sync.toArray()).find(
      (op) => op.operacion === 'PUBLICAR_INSPECCION',
    )!;
    expect(segunda.idempotencyKey).not.toBe(primera.idempotencyKey);
  });

  it('HU-061 aceptación: un rechazo 409 definitivo permite corregir un borrador no publicado', async () => {
    const local = await iniciar();
    await evidencia(local);
    const datos = {
      campo: 'original',
      identificacion: { ruta: 'RN-1', departamento: 'Guatemala', municipio: 'Amatitlan' },
    };
    await locales.encolarAccionFinal(INSPECCION, 'publicar', { version: 0, datos });
    const final = (await sgpDb.cola_sync.toArray()).find(
      (op) => op.operacion === 'PUBLICAR_INSPECCION',
    )!;
    // Las dependencias se prueban en el recorrido anterior; aquí solo se verifica el rechazo.
    await sgpDb.cola_sync.filter((op) => op.id !== final.id).delete();
    online.set(true);
    const corrida = TestBed.inject(SincronizacionService).sincronizarAhora();
    await autenticarSincronizacion();
    const motivo =
      'La inspeccion original ya tiene una correccion publicada; corrija esa correccion.';
    await vi.waitFor(() =>
      http
        .expectOne(`/api/v1/inspecciones/${INSPECCION}/publicar`)
        .flush(
          { code: 'correccion_ya_publicada', detail: motivo },
          { status: 409, statusText: 'Conflict' },
        ),
    );
    await vi.waitFor(() =>
      http.expectOne(`/api/v1/inspecciones/${INSPECCION}/borrador`).flush({
        ...(local.payload as BorradorLocalPayload).borrador,
        datos,
        estado: 'BORRADOR',
      }),
    );
    await vi.waitFor(() =>
      http
        .expectOne(`/api/v1/inspecciones/${INSPECCION}/publicada`)
        .flush({ code: 'inspeccion_no_publicada' }, { status: 404, statusText: 'Not Found' }),
    );
    await corrida;
    const rechazada = (await sgpDb.inspecciones.get(INSPECCION))!;
    // Comprobar conservación antes de exigir que se libere la edición.
    expect((rechazada.payload as BorradorLocalPayload).borrador.datos).toEqual(datos);
    expect(rechazada.fotosConfirmadas).toHaveLength(4);
    expect(rechazada.estado).toBe('BORRADOR');
    expect.soft(rechazada.intencionFinal).toMatchObject({ estado: 'RECHAZADA', error: motivo });
    await expect(
      locales.guardarBorradorLocal(rechazada, {
        version: 0,
        datos: { ...datos, campo: 'corregido' },
      }),
    ).resolves.toMatchObject({ id: expect.any(String), idempotencyKey: expect.any(String) });
    expect(await sgpDb.cola_sync.get(final.id)).toBeUndefined();
    await locales.encolarAccionFinal(INSPECCION, 'publicar', {
      version: 0,
      datos: { ...datos, campo: 'corregido' },
    });
    const nueva = (await sgpDb.cola_sync.toArray()).find(
      (op) => op.operacion === 'PUBLICAR_INSPECCION',
    )!;
    expect(nueva.idempotencyKey).not.toBe(final.idempotencyKey);
    expect((await sgpDb.inspecciones.get(INSPECCION))?.intencionFinal?.estado).toBe('PENDIENTE');
    expect((await sgpDb.inspecciones.get(INSPECCION))?.payload).toMatchObject({
      borrador: { datos: { ...datos, campo: 'corregido' } },
    });
  });

  it.each([
    { caso: 'no se confirma el borrador', borradorDisponible: false, autor: AUTOR, status: 404 },
    {
      caso: 'la consulta publicada falla con 503',
      borradorDisponible: true,
      autor: AUTOR,
      status: 503,
    },
    {
      caso: 'se pierde la conexión al consultar publicada',
      borradorDisponible: true,
      autor: AUTOR,
      status: 0,
    },
    {
      caso: 'el borrador pertenece a otro usuario',
      borradorDisponible: true,
      autor: OTRO,
      status: 404,
    },
  ])(
    'D1: conserva la intención y clave si $caso',
    async ({ borradorDisponible, autor, status }) => {
      const { final, borrador } = await prepararPublicacionPendiente();
      online.set(true);
      const corrida = TestBed.inject(SincronizacionService).sincronizarAhora();
      await autenticarSincronizacion();
      await vi.waitFor(() =>
        http
          .expectOne(`/api/v1/inspecciones/${INSPECCION}/publicar`)
          .flush(
            { code: 'correccion_ya_publicada', detail: 'Conflicto de publicación' },
            { status: 409, statusText: 'Conflict' },
          ),
      );
      await vi.waitFor(() => {
        const req = http.expectOne(`/api/v1/inspecciones/${INSPECCION}/borrador`);
        if (borradorDisponible) req.flush({ ...borrador, autorId: autor });
        else req.flush({}, { status: 404, statusText: 'Not Found' });
      });
      await vi.waitFor(() => {
        const req = http.expectOne(`/api/v1/inspecciones/${INSPECCION}/publicada`);
        if (status === 0) req.error(new ProgressEvent('error'));
        else req.flush({}, { status, statusText: 'Consulta sin resultado publicado' });
      });
      await corrida;
      expect(await sgpDb.cola_sync.get(final.id)).toMatchObject({
        estado: 'INTERVENCION',
        idempotencyKey: final.idempotencyKey,
      });
      const local = (await sgpDb.inspecciones.get(INSPECCION))!;
      expect(local.intencionFinal?.estado).toBe('PENDIENTE');
      expect(local.payload).toMatchObject({ borrador: { datos: borrador.datos } });
      expect(local.fotosConfirmadas).toHaveLength(4);
      await expect(locales.guardarBorradorLocal(local, { version: 0, datos: {} })).rejects.toThrow(
        'offline.hu061.locked',
      );
    },
  );

  it('D1: reconcilia una publicación confirmada sin volver a BORRADOR ni permitir edición', async () => {
    const { final, borrador } = await prepararPublicacionPendiente();
    online.set(true);
    const corrida = TestBed.inject(SincronizacionService).sincronizarAhora();
    await autenticarSincronizacion();
    await vi.waitFor(() =>
      http
        .expectOne(`/api/v1/inspecciones/${INSPECCION}/publicar`)
        .flush({ code: 'transicion_no_permitida' }, { status: 409, statusText: 'Conflict' }),
    );
    await vi.waitFor(() =>
      http
        .expectOne(`/api/v1/inspecciones/${INSPECCION}/borrador`)
        .flush({}, { status: 404, statusText: 'Not Found' }),
    );
    await vi.waitFor(() =>
      http.expectOne(`/api/v1/inspecciones/${INSPECCION}/publicada`).flush({
        ...borrador,
        estado: 'PUBLICADA',
      }),
    );
    await corrida;
    const local = (await sgpDb.inspecciones.get(INSPECCION))!;
    expect(local.estado).toBe('PUBLICADA');
    expect(local.payload).toMatchObject({ borrador: { estado: 'PUBLICADA' } });
    expect(local.intencionFinal?.estado).toBe('CONFIRMADA');
    expect(await sgpDb.cola_sync.get(final.id)).toBeUndefined();
    await expect(locales.guardarBorradorLocal(local, { version: 0, datos: {} })).rejects.toThrow(
      'offline.hu061.locked',
    );
  });

  it('D1: una publicación todavía en proceso conserva pendiente y reintenta con la misma clave', async () => {
    const { final } = await prepararPublicacionPendiente();
    online.set(true);
    const sync = TestBed.inject(SincronizacionService);
    const primera = sync.sincronizarAhora();
    await autenticarSincronizacion();
    await vi.waitFor(() =>
      http
        .expectOne(`/api/v1/inspecciones/${INSPECCION}/publicar`)
        .flush(
          { code: 'operacion_en_proceso' },
          { status: 409, statusText: 'Conflict', headers: { 'Retry-After': '60' } },
        ),
    );
    await primera;
    http.expectNone(`/api/v1/inspecciones/${INSPECCION}/borrador`);
    http.expectNone(`/api/v1/inspecciones/${INSPECCION}/publicada`);
    expect(await sgpDb.cola_sync.get(final.id)).toMatchObject({
      estado: 'PENDIENTE',
      idempotencyKey: final.idempotencyKey,
    });
    expect((await sgpDb.inspecciones.get(INSPECCION))?.intencionFinal?.estado).toBe('PENDIENTE');
    const segunda = sync.sincronizarAhora();
    await autenticarSincronizacion();
    await vi.waitFor(() => {
      const req = http.expectOne(`/api/v1/inspecciones/${INSPECCION}/publicar`);
      expect(req.request.headers.get('Idempotency-Key')).toBe(final.idempotencyKey);
      req.flush({ id: INSPECCION, puenteId: PUENTE, estado: 'PUBLICADA' });
    });
    await segunda;
    expect((await sgpDb.inspecciones.get(INSPECCION))?.intencionFinal?.estado).toBe('CONFIRMADA');
  });

  it('una foto en intervención impide ejecutar la acción final y conserva el blob', async () => {
    const local = await iniciar();
    await evidencia(local, true);
    await locales.encolarAccionFinal(INSPECCION, 'publicar', {
      version: 0,
      datos: { campo: 'completo' },
    });
    const cola = await sgpDb.cola_sync.toArray();
    for (const op of cola)
      if (!['PUBLICAR_INSPECCION', undefined].includes(op.operacion))
        await sgpDb.cola_sync.delete(op.id);
    for (const op of cola)
      if (op.tipo === 'FOTO') await sgpDb.cola_sync.update(op.id, { estado: 'INTERVENCION' });
    online.set(true);
    const corrida = TestBed.inject(SincronizacionService).sincronizarAhora();
    await autenticarSincronizacion();
    await corrida;
    http.expectNone(`/api/v1/inspecciones/${INSPECCION}/publicar`);
    expect(await sgpDb.fotos.count()).toBe(4);
    expect((await sgpDb.inspecciones.get(INSPECCION))?.intencionFinal?.estado).toBe('PENDIENTE');
  });

  it('SWR de asignaciones entrega caché propia, revalida y oculta datos al cambiar cuenta', async () => {
    const servicio = TestBed.inject(ReferenciasLocalesService);
    await sgpDb.asignaciones_usuario.put({ propietarioId: AUTOR, datos: [] });
    online.set(true);
    const vistas: unknown[] = [];
    servicio.misPuentesAsignados().subscribe((datos) => vistas.push(datos));
    await vi.waitFor(() => expect(vistas).toEqual([[]]));
    auth.setUser(usuario(OTRO));
    http.expectOne('/api/v1/asignaciones-puentes/mis-puentes').flush([{ id: 'de-A' }]);
    await vi.waitFor(async () =>
      expect((await sgpDb.asignaciones_usuario.get(AUTOR))?.datos).toEqual([{ id: 'de-A' }]),
    );
    expect(vistas).toEqual([[]]);
    expect(await sgpDb.asignaciones_usuario.get(OTRO)).toBeUndefined();
  });
  it('dos cuentas conservan copias independientes de la misma versión de formulario', async () => {
    await locales.guardarEsquemaLocal({
      id: FORMULARIO,
      version: '1',
      schema: { de: 'A' },
      activo: true,
    });
    auth.setUser(usuario(OTRO));
    await locales.guardarEsquemaLocal({
      id: FORMULARIO,
      version: '1',
      schema: { de: 'B' },
      activo: true,
    });
    expect((await locales.obtenerEsquemaLocal(FORMULARIO))?.schema).toEqual({ de: 'B' });
    auth.setUser(usuario());
    expect((await locales.obtenerEsquemaActivoLocal())?.schema).toEqual({ de: 'A' });
  });
  it('una operación desconocida queda para intervención y nunca se elimina silenciosamente', async () => {
    await sgpDb.cola_sync.put({
      id: 'desconocida',
      entityId: INSPECCION,
      propietarioId: AUTOR,
      tipo: 'INSPECCION',
      operacion: 'DESCONOCIDA' as never,
      idempotencyKey: 'clave-desconocida',
      intentos: 0,
      creadoEn: FECHA,
      estado: 'PENDIENTE',
    });
    online.set(true);
    const corrida = TestBed.inject(SincronizacionService).sincronizarAhora();
    await autenticarSincronizacion();
    await corrida;
    expect((await sgpDb.cola_sync.get('desconocida'))?.estado).toBe('INTERVENCION');
  });

  it('fija la versión y el cuerpo antes del primer envío y conserva ambos tras un resultado incierto', async () => {
    const local = await iniciar();
    const { datos } = await editar(local);
    await sgpDb.cola_sync.filter((op) => op.operacion === 'CREAR_INSPECCION').delete();
    online.set(true);
    const sync = TestBed.inject(SincronizacionService);
    const primera = sync.sincronizarAhora();
    await autenticarSincronizacion();
    let clave: string | null = null;
    await vi.waitFor(() => {
      const req = http.expectOne(`/api/v1/inspecciones/${INSPECCION}/borrador`);
      clave = req.request.headers.get('Idempotency-Key');
      expect(req.request.body.version).toBe(0);
      req.flush({}, { status: 401, statusText: 'Unauthorized' });
    });
    await primera;
    const guardado = (await sgpDb.inspecciones.get(INSPECCION))!;
    const payload = guardado.payload as BorradorLocalPayload;
    await sgpDb.inspecciones.update(INSPECCION, {
      payload: { ...payload, borrador: { ...payload.borrador, version: 9 } },
    });
    const segunda = sync.sincronizarAhora();
    await autenticarSincronizacion();
    await vi.waitFor(() => {
      const req = http.expectOne(`/api/v1/inspecciones/${INSPECCION}/borrador`);
      expect(req.request.headers.get('Idempotency-Key')).toBe(clave);
      expect(req.request.body).toMatchObject({ version: 0, datos });
      req.flush({ ...payload.borrador, datos, version: 1 });
    });
    await segunda;
    expect(await sgpDb.cola_sync.count()).toBe(0);
  });
  it('recupera una operación interrumpida que agotó intentos como intervención manual', async () => {
    await sgpDb.cola_sync.put({
      id: 'interrumpida',
      entityId: INSPECCION,
      propietarioId: AUTOR,
      tipo: 'INSPECCION',
      operacion: 'GUARDAR_BORRADOR',
      idempotencyKey: 'clave-interrumpida',
      intentos: 5,
      creadoEn: FECHA,
      iniciadoEn: FECHA,
      estado: 'ENVIANDO',
    });
    online.set(true);
    const corrida = TestBed.inject(SincronizacionService).sincronizarAhora();
    await autenticarSincronizacion();
    await corrida;
    expect((await sgpDb.cola_sync.get('interrumpida'))?.estado).toBe('INTERVENCION');
    http.expectNone(`/api/v1/inspecciones/${INSPECCION}/borrador`);
  });
});
