import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { RevisionApiService } from './revision-api.service';

describe('RevisionApiService', () => {
  let service: RevisionApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RevisionApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('combina los filtros de la bandeja con paginación', () => {
    service
      .listar({
        estado: 'ENVIADA',
        puente: ' Central ',
        estudiante: 'alumno',
        curso: 'Puentes',
        fechaDesde: '2026-10-01',
        fechaHasta: '2026-10-18',
        pagina: 2,
        tamanio: 100,
      })
      .subscribe();

    const request = http.expectOne((req) => req.url === '/api/v1/revisiones/bandeja');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('estado')).toBe('ENVIADA');
    expect(request.request.params.get('puente')).toBe('Central');
    expect(request.request.params.get('estudiante')).toBe('alumno');
    expect(request.request.params.get('curso')).toBe('Puentes');
    expect(request.request.params.get('fechaDesde')).toBe('2026-10-01');
    expect(request.request.params.get('fechaHasta')).toBe('2026-10-18');
    expect(request.request.params.get('pagina')).toBe('2');
    expect(request.request.params.get('tamanio')).toBe('100');
    request.flush({ content: [] });
  });

  it('consulta los borradores de estudiantes por separado', () => {
    service.listarBorradores(1, 50).subscribe();

    const request = http.expectOne('/api/v1/revisiones/borradores?pagina=1&tamanio=50');
    expect(request.request.method).toBe('GET');
    request.flush({ content: [] });
  });

  it('actualiza el contador accesible al consultar pendientes', () => {
    service.cargarContador().subscribe();

    const request = http.expectOne('/api/v1/revisiones/pendientes/contador');
    request.flush({ pendientes: 4 });
    expect(service.pendientes()).toBe(4);
  });

  it('inicia una revisión por su identificador codificado', () => {
    service.iniciar('revision/1').subscribe();

    const request = http.expectOne('/api/v1/revisiones/revision%2F1/iniciar');
    expect(request.request.method).toBe('POST');
    request.flush({ estado: 'EN_REVISION' });
  });
});

describe('RevisionApiService: contratos de HU-037', () => {
  let api: RevisionApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(RevisionApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('consulta detalle, lista, observación, contadores y general sin usar endpoints vivos', () => {
    api.detalle('r/1').subscribe();
    api.observaciones('r/1').subscribe();
    api.observacion('r/1', 'o/2').subscribe();
    api.contadorObservaciones('r/1').subscribe();
    api.observacionGeneral('r/1').subscribe();
    for (const sufijo of [
      '',
      '/observaciones',
      '/observaciones/o%2F2',
      '/observaciones/contador',
      '/observaciones/general',
    ]) {
      const req = http.expectOne('/api/v1/revisiones/r%2F1' + sufijo);
      expect(req.request.method).toBe('GET');
      req.flush({});
    }
  });
  it('envía solo destino y texto al crear, solo texto al editar y conserva el UUID', () => {
    const destino = {
      tipo: 'ELEMENTO' as const,
      elementoRef: '/tramos/longitud',
      instanciaElementoId: '019abc00-0000-7000-8000-000000000001',
      categoriaFotografica: null,
    };
    api.crearObservacion('r', { destino, texto: 'Revisar' }).subscribe();
    const crear = http.expectOne('/api/v1/revisiones/r/observaciones');
    expect(crear.request.method).toBe('POST');
    expect(crear.request.body).toEqual({ destino, texto: 'Revisar' });
    crear.flush({});
    api.editarObservacion('r', 'o', 'Cambio').subscribe();
    const editar = http.expectOne('/api/v1/revisiones/r/observaciones/o');
    expect(editar.request.method).toBe('PUT');
    expect(editar.request.body).toEqual({ texto: 'Cambio' });
    editar.flush({});
    api.eliminarObservacion('r', 'o').subscribe();
    const borrar = http.expectOne('/api/v1/revisiones/r/observaciones/o');
    expect(borrar.request.method).toBe('DELETE');
    borrar.flush(null);
    api.guardarGeneral('r', null).subscribe();
    const general = http.expectOne('/api/v1/revisiones/r/observaciones/general');
    expect(general.request.body).toEqual({ texto: null });
    expect(general.request.method).toBe('PUT');
    general.flush({ revisionId: 'r', texto: null });
  });
  it('usa los contratos privados del veredicto y la atención de observaciones', () => {
    api.historial('i').subscribe();
    http.expectOne('/api/v1/inspecciones/i/revisiones').flush([]);
    api.resultado('r').subscribe();
    http.expectOne('/api/v1/revisiones/r/resultado').flush({});
    api.estadoRevision('r').subscribe();
    http.expectOne('/api/v1/revisiones/r/estado-puente').flush({});
    const veredicto = {
      veredicto: 'CAMBIOS_SOLICITADOS' as const,
      calificacion: 80.25,
      observacionGeneral: 'Corregir',
      confirmacionEstado: null,
    };
    api.emitirVeredicto('r', veredicto).subscribe();
    let req = http.expectOne('/api/v1/revisiones/r/veredicto');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(veredicto);
    req.flush({});
    api.atenderCambios('r').subscribe();
    req = http.expectOne('/api/v1/revisiones/r/atender-cambios');
    expect(req.request.method).toBe('POST');
    req.flush({});
    api.resolverObservacion('r', 'obs', null).subscribe();
    req = http.expectOne('/api/v1/revisiones/r/observaciones/obs/resolver');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ respuesta: null });
    req.flush({});
  });
});
