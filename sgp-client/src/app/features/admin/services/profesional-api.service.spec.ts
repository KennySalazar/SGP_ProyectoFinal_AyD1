import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ProfesionalApiService } from './profesional-api.service';

describe('Característica: API de profesionales externos HU002', () => {
  let api: ProfesionalApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ProfesionalApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('Escenario: listar pendientes envía verificado=false', () => {
    api.listar(false, 1, 10).subscribe();
    const request = http.expectOne((req) => req.url === '/api/v1/profesionales');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('verificado')).toBe('false');
    expect(request.request.params.get('pagina')).toBe('1');
    request.flush({ content: [] });
  });

  it('Escenario: listar todos no envía el filtro', () => {
    api.listar(null).subscribe();
    const request = http.expectOne((req) => req.url === '/api/v1/profesionales');
    expect(request.request.params.has('verificado')).toBe(false);
    request.flush({ content: [] });
  });

  it.each([-1, 0, 101])('Escenario: rechazar tamaño de página inválido %s', (tamanio) => {
    expect(() => api.listar(null, 0, tamanio)).toThrow(RangeError);
    http.expectNone((req) => req.url === '/api/v1/profesionales');
  });

  it('Escenario: verificar usa el endpoint del profesional elegido', () => {
    api.verificarColegiado('usuario-1').subscribe();
    const request = http.expectOne('/api/v1/profesionales/usuario-1/verificacion-colegiado');
    expect(request.request.method).toBe('POST');
    request.flush({});
  });
});
