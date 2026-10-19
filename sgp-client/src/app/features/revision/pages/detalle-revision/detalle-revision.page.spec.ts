import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../../../../core/services/auth.store';
import { FotografiasLocalesService } from '../../../../offline/services/fotografias-locales.service';
import { FormularioDinamicoComponent } from '../../../formulario/components/formulario-dinamico/formulario-dinamico.component';
import { EditorObservacionesComponent } from '../../../formulario/components/observaciones/editor-observaciones.component';
import { PanelObservacionesComponent } from '../../../formulario/components/observaciones/panel-observaciones.component';
import { DestinoElementoObservacion } from '../../../formulario/models/observacion.models';
import { traduccionesDePrueba } from '../../../formulario/motor/testing/traducciones';
import { InspeccionApiService } from '../../../inspecciones/services/inspeccion-api.service';
import { ProcesadorFotografiasService } from '../../../inspecciones/services/procesador-fotografias.service';
import {
  ListadoObservacionesResponse,
  ObservacionResponse,
  RevisionDetalleResponse,
} from '../../models/revision.models';
import { DetalleRevisionPage } from './detalle-revision.page';

const uuid1 = '019abc00-0000-7000-8000-000000000001';
const uuid2 = '019abc00-0000-7000-8000-000000000002';
const esquema = {
  type: 'object',
  required: ['identificacion', 'subestructura', 'tramos'],
  properties: {
    identificacion: {
      type: 'object',
      title: 'Identificación histórica',
      properties: { nombre: { type: 'string', title: 'Nombre histórico' } },
    },
    subestructura: {
      type: 'object',
      title: 'Subestructura',
      properties: {
        estribo: {
          type: 'object',
          title: 'Estribo',
          'x-sgp-evaluable': true,
          properties: { fisura: { type: 'boolean', title: 'Fisura', 'x-sgp-sin-danos': false } },
        },
      },
    },
    tramos: {
      type: 'array',
      title: 'Tramos',
      items: {
        type: 'object',
        properties: {
          instancia_id: { type: 'string', format: 'uuid', readOnly: true },
          longitud: { type: 'number', title: 'Longitud' },
        },
      },
    },
  },
};
const detalle: RevisionDetalleResponse = {
  revisionId: 'r',
  inspeccionId: 'i',
  estadoRevision: 'EN_CURSO',
  revisorId: 'revisor',
  numeroCiclo: 2,
  enviadaEn: '2026-10-18T12:00:00Z',
  autorId: 'autor',
  puenteId: 'puente',
  fechaInspeccion: '2026-10-17',
  versionFormularioId: 'v1',
  versionFormularioCodigo: 'Histórica-v1',
  esquemaFormulario: esquema,
  datos: {
    identificacion: { nombre: 'Capturado' },
    subestructura: { estribo: { fisura: false } },
    tramos: [
      { instancia_id: uuid1, longitud: 10 },
      { instancia_id: uuid2, longitud: 20 },
    ],
  },
  identificacionCapturada: { nombre_puente: 'Puente capturado' },
  icPreliminar: { indice: 12.5, estado: 'BUENO', etiqueta: 'PRELIMINAR' },
  fotografiasEndpoint: '/api/v1/inspecciones/i/fotografias',
  fotografiasCongeladas: false,
  observacionesEndpoint: '/api/v1/revisiones/r/observaciones',
};
const observacion: ObservacionResponse = {
  id: 'o',
  revisionId: 'r',
  destino: {
    tipo: 'ELEMENTO',
    elementoRef: '/tramos/longitud',
    instanciaElementoId: uuid2,
    categoriaFotografica: null,
  },
  texto: 'Comprobar longitud.',
  estado: 'PENDIENTE',
  creadoPorId: 'revisor',
  creadoEn: '2026-10-19T12:00:00Z',
};
const lista: ListadoObservacionesResponse = {
  revisionId: 'r',
  observaciones: [observacion, { ...observacion, id: 'o2', estado: 'RESUELTA' }],
  total: 2,
  pendientes: 1,
  general: { revisionId: 'r', texto: 'Comentario del ciclo' },
};

describe('Detalle de revisión HU-037', () => {
  let fixture: ComponentFixture<DetalleRevisionPage>;
  let http: HttpTestingController;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  const user = signal<{ id: string; role: string } | null>({ id: 'revisor', role: 'CATEDRATICO' });
  const fotos = { listarFotografias: vi.fn(), contenidoFotografia: vi.fn() };
  const locales = { guardarContexto: vi.fn(), pendientes: vi.fn() };
  beforeEach(async () => {
    user.set({ id: 'revisor', role: 'CATEDRATICO' });
    params = new BehaviorSubject(convertToParamMap({ revisionId: 'r' }));
    fotos.listarFotografias.mockReturnValue(of([]));
    fotos.contenidoFotografia.mockReturnValue(of(new Blob()));
    TestBed.configureTestingModule({
      imports: [DetalleRevisionPage, traduccionesDePrueba()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: params } },
        { provide: AuthStore, useValue: { user } },
        { provide: InspeccionApiService, useValue: fotos },
        { provide: FotografiasLocalesService, useValue: locales },
        { provide: ProcesadorFotografiasService, useValue: {} },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DetalleRevisionPage);
    fixture.detectChanges();
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
    vi.restoreAllMocks();
  });
  const html = () => fixture.nativeElement as HTMLElement;
  const page = () => fixture.componentInstance;
  async function responder(d = detalle, l = lista) {
    http.match('/api/v1/revisiones/r').forEach((r) => r.flush(structuredClone(d)));
    http.expectOne('/api/v1/revisiones/r/observaciones').flush(structuredClone(l));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }
  function destino(instancia = uuid2): DestinoElementoObservacion {
    return {
      tipo: 'ELEMENTO',
      etiqueta: 'Longitud',
      anclaje: { elementoRef: '/tramos/longitud', instanciaElementoId: instancia },
    };
  }
  it('muestra exclusivamente formulario histórico deshabilitado, IC PRELIMINAR y contadores', async () => {
    await responder();
    const form = fixture.debugElement.query(By.directive(FormularioDinamicoComponent))
      .componentInstance as FormularioDinamicoComponent;
    expect(form.esquema()).toEqual(esquema);
    expect(form.soloLectura()).toBe(true);
    expect(form.construido()?.form.disabled).toBe(true);
    expect(html().textContent).toContain('IC PRELIMINAR: 12.5');
    expect(html().textContent).toContain('Total: 2 · Pendientes: 1');
    expect(html().querySelector<HTMLInputElement>('input')?.value).toBe('Capturado');
    expect(fotos.listarFotografias).toHaveBeenLastCalledWith('i');
    expect(locales.guardarContexto).not.toHaveBeenCalled();
    http.expectNone((req) => req.url.includes('formulario') || req.url.includes('/borrador'));
  });
  it('muestra fotografías agrupadas y acciones explícitas por categoría', async () => {
    await responder();
    expect(html().querySelectorAll('[data-categoria-fotografica]')).toHaveLength(5);
    expect(html().textContent).toContain('No existe una instantánea fotográfica');
    page().observarCategoria('ACCESO');
    fixture.detectChanges();
    expect(page().editor()).toMatchObject({
      destino: { tipo: 'CATEGORIA_FOTOGRAFICA', categoriaFotografica: 'ACCESO' },
    });
  });
  it('navega al UUID exacto y muestra varias observaciones solo en ese elemento', async () => {
    await responder();
    page().navegar(destino());
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const refs = Array.from(
      html().querySelectorAll<HTMLElement>('[data-elemento-ref="/tramos/longitud"]'),
    );
    expect(refs).toHaveLength(2);
    expect(refs[0].dataset['instanciaId']).toBe(uuid1);
    expect(refs[1].dataset['instanciaId']).toBe(uuid2);
    expect(refs[0].querySelector('app-marcador-observaciones')).toBeNull();
    expect(refs[1].textContent).toContain('2 observaciones');
    expect(document.activeElement).toBe(refs[1]);
    refs[1].querySelector<HTMLButtonElement>(':scope > button')!.click();
    expect(page().editor()).toMatchObject({
      destino: { anclaje: { elementoRef: '/tramos/longitud', instanciaElementoId: uuid2 } },
    });
  });
  it('selecciona secciones, campos y elementos evaluables y abre indicadores plegados', async () => {
    await responder();
    expect(html().querySelector('[data-elemento-ref="/identificacion"]')).not.toBeNull();
    expect(html().querySelector('[data-elemento-ref="/identificacion/nombre"]')).not.toBeNull();
    page().navegar({
      tipo: 'ELEMENTO',
      etiqueta: 'Fisura',
      anclaje: { elementoRef: '/subestructura/estribo/fisura' },
    });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const indicador = html().querySelector<HTMLElement>(
      '[data-elemento-ref="/subestructura/estribo/fisura"]',
    )!;
    expect(indicador.closest('[hidden]')).toBeNull();
    expect(document.activeElement).toBe(indicador);
    expect(html().querySelector('[data-elemento-ref="/subestructura/estribo"]')).not.toBeNull();
  });
  it('crea mediante el editor existente y conserva UUID, sin enviar identidad o estado', async () => {
    await responder();
    page().seleccionarElemento(destino());
    fixture.detectChanges();
    const editor = fixture.debugElement.query(By.directive(EditorObservacionesComponent))
      .componentInstance as EditorObservacionesComponent;
    editor.texto.setValue('Revisar tramo');
    editor.prepararGuardado();
    const req = http.expectOne('/api/v1/revisiones/r/observaciones');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      texto: 'Revisar tramo',
      destino: {
        tipo: 'ELEMENTO',
        elementoRef: '/tramos/longitud',
        instanciaElementoId: uuid2,
        categoriaFotografica: null,
      },
    });
    req.flush(observacion);
    await responder();
    expect(page().editor()).toBeNull();
  });
  it('edita y elimina sin cambiar destino; contadores se actualizan desde el backend', async () => {
    await responder();
    const o = page().observacionesPresentacion()[0];
    page().editar(o);
    page().guardar({ modo: 'EDITAR', id: o.id, destino: o.destino, texto: 'Editada' });
    let req = http.expectOne('/api/v1/revisiones/r/observaciones/o');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ texto: 'Editada' });
    req.flush({ ...observacion, texto: 'Editada' });
    await responder();
    page().solicitarEliminacion(o);
    page().eliminar();
    req = http.expectOne('/api/v1/revisiones/r/observaciones/o');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    await responder(detalle, { ...lista, observaciones: [], total: 0, pendientes: 0 });
    expect(page().listado()?.total).toBe(0);
  });
  it('guarda categorías sin rutas ficticias y general mediante su endpoint separado', async () => {
    await responder();
    page().observarCategoria('CAUCE');
    page().guardar({
      modo: 'CREAR',
      texto: 'Falta vista',
      destino: { tipo: 'CATEGORIA_FOTOGRAFICA', categoriaFotografica: 'CAUCE', etiqueta: 'Cauce' },
    });
    const req = http.expectOne('/api/v1/revisiones/r/observaciones');
    expect(req.request.body.destino).toEqual({
      tipo: 'CATEGORIA_FOTOGRAFICA',
      categoriaFotografica: 'CAUCE',
      elementoRef: null,
      instanciaElementoId: null,
    });
    req.flush(observacion);
    await responder();
    page().editarGeneral();
    page().guardar({
      modo: 'EDITAR',
      id: 'r',
      texto: 'General nueva',
      destino: { tipo: 'GENERAL' },
    });
    const general = http.expectOne('/api/v1/revisiones/r/observaciones/general');
    expect(general.request.body).toEqual({ texto: 'General nueva' });
    general.flush({ revisionId: 'r', texto: 'General nueva' });
    await responder();
    page().solicitarEliminacion('GENERAL');
    page().eliminar();
    const borrar = http.expectOne('/api/v1/revisiones/r/observaciones/general');
    expect(borrar.request.body).toEqual({ texto: null });
    borrar.flush({ revisionId: 'r', texto: null });
    await responder();
  });
  it.each(['PENDIENTE', 'RESUELTA'] as const)('impide operaciones en ciclo %s', async (estado) => {
    await responder({ ...detalle, estadoRevision: estado });
    expect(page().puedeEscribir()).toBe(false);
    page().seleccionarElemento(destino());
    expect(page().editor()).toBeNull();
    expect(html().querySelector('[data-elemento-ref] button')).toBeNull();
  });
  it('ante 409 recarga y bloquea incluso si el ciclo aún indica EN_CURSO', async () => {
    await responder();
    page().seleccionarElemento(destino());
    page().guardar({ modo: 'CREAR', texto: 'Nota', destino: destino() });
    http
      .expectOne('/api/v1/revisiones/r/observaciones')
      .flush({ detail: 'Estado incompatible' }, { status: 409, statusText: 'Conflict' });
    await responder();
    expect(page().puedeEscribir()).toBe(false);
    expect(page().bloqueada()).toBe(true);
    expect(page().editor()).toBeNull();
  });
  it('asocia errores 422 de campo al editor y conserva el borrador', async () => {
    await responder();
    page().seleccionarElemento(destino());
    fixture.detectChanges();
    const editor = fixture.debugElement.query(By.directive(EditorObservacionesComponent))
      .componentInstance as EditorObservacionesComponent;
    editor.texto.setValue('Nota');
    editor.prepararGuardado();
    http.expectOne('/api/v1/revisiones/r/observaciones').flush(
      {
        errores: [{ campo: 'destino.instanciaElementoId', mensaje: 'No pertenece al repetible' }],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    fixture.detectChanges();
    expect(page().errorEditor()).toContain('No pertenece al repetible');
    expect(editor.texto.value).toBe('Nota');
    expect(html().querySelector('textarea')?.getAttribute('aria-describedby')).toContain(
      'servidor',
    );
  });
  it.each([401, 403, 404])('oculta el contenido privado ante HTTP %s', async (status) => {
    http
      .expectOne('/api/v1/revisiones/r')
      .flush({ detail: 'Sin acceso' }, { status, statusText: 'Error' });
    const listaReq = http.match('/api/v1/revisiones/r/observaciones');
    listaReq.forEach((r) => {
      if (!r.cancelled) r.flush(lista);
    });
    fixture.detectChanges();
    await fixture.whenStable();
    expect(page().detalle()).toBeNull();
    expect(page().puedeEscribir()).toBe(false);
    expect(html().querySelector('app-formulario-dinamico')).toBeNull();
  });
  it('no expone contenido ni controles de terceros aunque una respuesta incluya otro revisor', async () => {
    await responder({ ...detalle, revisorId: 'ajeno' });
    expect(page().estadoHttp()).toBe(404);
    expect(html().querySelector('app-formulario-dinamico')).toBeNull();
  });
  it('cancela solicitudes del ciclo anterior al cambiar la ruta', async () => {
    const antiguas = http.match(() => true);
    params.next(convertToParamMap({ revisionId: 'r2' }));
    expect(antiguas.every((r) => r.cancelled)).toBe(true);
    http.expectOne('/api/v1/revisiones/r2').flush({ ...detalle, revisionId: 'r2' });
    http.expectOne('/api/v1/revisiones/r2/observaciones').flush({ ...lista, revisionId: 'r2' });
    fixture.detectChanges();
    await fixture.whenStable();
    expect(page().detalle()?.revisionId).toBe('r2');
  });
  it('el panel usa sus eventos existentes y mantiene cierre/restauración de foco por teclado', async () => {
    await responder();
    const abrir = Array.from(html().querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => b.textContent?.trim() === 'Observaciones',
    )!;
    abrir.focus();
    abrir.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const panel = fixture.debugElement.query(By.directive(PanelObservacionesComponent))
      .componentInstance as PanelObservacionesComponent;
    expect(panel.desplegable()).toBe(true);
    expect(page().panelAbierto()).toBe(true);
    html()
      .querySelector('aside')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(page().panelAbierto()).toBe(false);
    expect(document.activeElement).toBe(abrir);
  });
});
