import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AprobacionCalibracionApiService } from './aprobacion-calibracion-api.service';

describe('AprobacionCalibracionApiService', () => {
  let api: AprobacionCalibracionApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AprobacionCalibracionApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('consulta estado usando la version indicada y codifica el identificador', () => {
    api.estado('v/1').subscribe();
    const req = http.expectOne('/api/v1/formularios/v%2F1/estado-calibracion');
    expect(req.request.method).toBe('GET');
    req.flush({ estado: 'SIN_APROBAR' });
  });
  it('acepta una consulta de historial sin aprobacion', () => {
    const respuesta = vi.fn();
    api.consultar('v1').subscribe(respuesta);
    http.expectOne('/api/v1/formularios/v1/aprobacion-calibracion').flush(null);
    expect(respuesta).toHaveBeenCalledWith(null);
  });
  it('envia solo observaciones y avisa a otras vistas despues del exito', () => {
    const actualizada = vi.fn();
    api.actualizada.subscribe(actualizada);
    api.aprobar('v1', '  Conforme  ').subscribe();
    const req = http.expectOne('/api/v1/formularios/v1/aprobacion-calibracion');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ observaciones: 'Conforme' });
    expect(req.request.headers.has('If-Match')).toBe(false);
    expect(actualizada).not.toHaveBeenCalled();
    req.flush({ id: 'a' });
    expect(actualizada).toHaveBeenCalledWith('v1');
  });
  it('permite observaciones vacias sin adjuntar PDF', () => {
    api.aprobar('v1', '   ').subscribe();
    const req = http.expectOne('/api/v1/formularios/v1/aprobacion-calibracion');
    expect(req.request.body).toEqual({ observaciones: null });
    req.flush({ id: 'a' });
  });
  it('no anuncia aprobacion cuando el servidor devuelve conflicto', () => {
    const actualizada = vi.fn();
    api.actualizada.subscribe(actualizada);
    api.aprobar('v1', '').subscribe({ error: () => undefined });
    http
      .expectOne('/api/v1/formularios/v1/aprobacion-calibracion')
      .flush({}, { status: 409, statusText: 'Conflict' });
    expect(actualizada).not.toHaveBeenCalled();
  });
});
