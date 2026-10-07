import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuditoriaApiService } from './auditoria-api.service';

describe('Característica: API de consulta de bitácora HU007', () => {
  let api: AuditoriaApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AuditoriaApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('Escenario: combinar usuario, rango de fechas y paginación', () => {
    // Dado un correo, un rango con zona horaria y una página de 100 registros
    const desde = '2026-10-06T00:00:00-06:00';
    const hasta = '2026-10-06T23:59:59.999999-06:00';
    // Cuando se consulta la bitácora
    api
      .consultar({ usuarioEmail: 'admin@ejemplo.com', desde, hasta, pagina: 1, tamanio: 100 })
      .subscribe();
    const request = http.expectOne((req) => req.url === '/api/v1/auditoria');
    // Entonces todos los filtros se envían en la misma petición de lectura
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('usuarioEmail')).toBe('admin@ejemplo.com');
    expect(request.request.params.get('desde')).toBe(desde);
    expect(request.request.params.get('hasta')).toBe(hasta);
    expect(request.request.params.get('pagina')).toBe('1');
    expect(request.request.params.get('tamanio')).toBe('100');
    request.flush({ content: [] });
  });

  it.each([-1, 0, 101, 1.5])('Escenario: rechazar tamaño de página inválido %s', (tamanio) => {
    // Dado un tamaño fuera del contrato
    // Cuando se consulta, entonces no se envía una petición inválida
    expect(() => api.consultar({ tamanio })).toThrow(RangeError);
    http.expectNone('/api/v1/auditoria');
  });

  it('Escenario: consultar exclusivamente el detalle elegido', () => {
    // Dado un identificador de registro
    // Cuando se abre el detalle, entonces se usa su endpoint de lectura
    api.detalle('registro-1').subscribe();
    const request = http.expectOne('/api/v1/auditoria/registro-1');
    expect(request.request.method).toBe('GET');
    request.flush({ cambios: [] });
  });
});
