import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ReglaMantenimientoApiService } from './regla-mantenimiento-api.service';

describe('Característica: API de catálogo de reglas de mantenimiento', () => {
  let api: ReglaMantenimientoApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ReglaMantenimientoApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    try {
      http.verify();
    } finally {
      TestBed.resetTestingModule();
    }
  });

  it('Escenario: listar combina tipo, elemento, activa y paginación', () => {
    api
      .listar({
        versionId: 'version-1',
        tipo: 'CORRECTIVO',
        elemento: 'superestructura',
        activa: true,
        pagina: 0,
        tamanio: 10,
      })
      .subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/reglas-mantenimiento');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('versionId')).toBe('version-1');
    expect(req.request.params.get('tipo')).toBe('CORRECTIVO');
    expect(req.request.params.get('elemento')).toBe('superestructura');
    expect(req.request.params.get('activa')).toBe('true');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
  });

  it('Escenario: validar regla ejecuta POST al endpoint de validación', () => {
    const respuesta = {
      id: 'regla-123',
      provisional: false,
      estadoValidacion: 'Validada',
      validadoPorId: 'docente-1',
      validadoPorNombre: 'Kenny Salazar',
      validadoEn: '2026-10-24T02:30:00Z',
      rotuloValidacion: 'Validada por Kenny Salazar el 23/10/2026',
    };
    let resultado: unknown;
    api.validar('regla-123').subscribe((regla) => (resultado = regla));

    const req = http.expectOne('/api/v1/reglas-mantenimiento/regla-123/validar');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    req.flush(respuesta);
    expect(resultado).toEqual(respuesta);
  });

  it('Escenario: cambiar estado ejecuta PATCH con valor booleano', () => {
    api.cambiarEstado('regla-123', false).subscribe();

    const req = http.expectOne('/api/v1/reglas-mantenimiento/regla-123/estado');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ activa: false });
    req.flush({ id: 'regla-123', activa: false });
  });

  it('Escenario: sembrar semilla ejecuta POST al endpoint de siembra', () => {
    api.sembrar('version-1').subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/reglas-mantenimiento/sembrar');
    expect(req.request.method).toBe('POST');
    expect(req.request.params.get('versionId')).toBe('version-1');
    req.flush({ mensaje: 'Catálogo provisional procesado', reglasSembradas: 18 });
  });

  it('Escenario: resumen de versión consulta endpoint resumen', () => {
    api.obtenerResumen('version-1').subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/reglas-mantenimiento/resumen');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('versionId')).toBe('version-1');
    req.flush({
      versionFormularioId: 'version-1',
      tieneReglasActivas: true,
      totalReglas: 18,
      totalActivas: 18,
      totalProvisionales: 18,
      totalValidadas: 0,
      advertenciaSinReglasActivas: null,
    });
  });
  it('validar codifica el ID y propaga el conflicto de revision no vigente', () => {
    let estado: number | undefined;
    api.validar('regla/1').subscribe({ error: (err) => (estado = err.status) });
    http
      .expectOne('/api/v1/reglas-mantenimiento/regla%2F1/validar')
      .flush({ code: 'regla_no_vigente' }, { status: 409, statusText: 'Conflict' });
    expect(estado).toBe(409);
  });
});
