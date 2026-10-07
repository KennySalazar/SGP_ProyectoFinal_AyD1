import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { InvitacionApiService } from './invitacion-api.service';

describe('Característica: API de invitaciones HU001', () => {
  let api: InvitacionApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(InvitacionApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('Escenario: invitar envía correo y rol al endpoint de invitaciones', () => {
    api.invitar({ email: 'catedratico@usac.edu.gt', rol: 'CATEDRATICO' }).subscribe();
    const request = http.expectOne('/api/v1/invitaciones');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'catedratico@usac.edu.gt', rol: 'CATEDRATICO' });
    request.flush({});
  });

  it('Escenario: listar filtra por estado y paginación', () => {
    api.listar('VENCIDA', 2, 10).subscribe();
    const request = http.expectOne((req) => req.url === '/api/v1/invitaciones');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('estado')).toBe('VENCIDA');
    expect(request.request.params.get('pagina')).toBe('2');
    expect(request.request.params.get('tamanio')).toBe('10');
    request.flush({ content: [] });
  });

  it('Escenario: listar sin estado no envía el filtro', () => {
    api.listar(null).subscribe();
    const request = http.expectOne((req) => req.url === '/api/v1/invitaciones');
    expect(request.request.params.has('estado')).toBe(false);
    request.flush({ content: [] });
  });

  it.each([-1, 0, 101, 1.5])('Escenario: rechazar tamaño de página inválido %s', (tamanio) => {
    expect(() => api.listar(null, 0, tamanio)).toThrow(RangeError);
    http.expectNone((req) => req.url === '/api/v1/invitaciones');
  });

  it('Escenario: reenviar usa el endpoint de la invitación elegida', () => {
    api.reenviar('invitacion-1').subscribe();
    const request = http.expectOne('/api/v1/invitaciones/invitacion-1/reenvio');
    expect(request.request.method).toBe('POST');
    request.flush({});
  });
});
