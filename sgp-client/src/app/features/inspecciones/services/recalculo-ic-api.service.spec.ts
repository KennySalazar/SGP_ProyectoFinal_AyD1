import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { RecalculoIcApiService } from './recalculo-ic-api.service';

describe('RecalculoIcApiService', () => {
  let api: RecalculoIcApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(RecalculoIcApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('consulta el historial de la inspeccion y codifica su identificador', () => {
    api.historial('i/1').subscribe();
    const req = http.expectOne('/api/v1/inspecciones/i%2F1/resultados-ic');
    expect(req.request.method).toBe('GET');
    req.flush({ original: {}, recalculos: [] });
  });
  it('pide el recalculo sin enviar pesos, version ni valores del frontend', () => {
    api.recalcular('i/1').subscribe();
    const req = http.expectOne('/api/v1/inspecciones/i%2F1/recalculos-ic');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    req.flush({ id: 'resultado' });
  });
});
