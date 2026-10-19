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
