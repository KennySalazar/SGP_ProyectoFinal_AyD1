import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { SalidaApiService } from './salida-api.service';

describe('Contrato HTTP salida', () => {
  let api: SalidaApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(SalidaApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('envia filtros y pagina sin pedir todos los puentes de golpe', () => {
    api.buscar('GT-01', 'd1', 2).subscribe();
    const peticion = http.expectOne((r) => r.url.endsWith('/puentes'));
    expect(peticion.request.params.get('busqueda')).toBe('GT-01');
    expect(peticion.request.params.get('departamentoId')).toBe('d1');
    expect(peticion.request.params.get('pagina')).toBe('2');
    expect(peticion.request.params.get('tamanio')).toBe('20');
    peticion.flush({ content: [], totalElements: 0, totalPages: 0 });
  });
  it('envia siempre la clave conservada para registrar', () => {
    const cuerpo = { dispositivoId: 'equipo', puentesIds: ['p'] };
    api.preparar(cuerpo, 'uuid-v7').subscribe();
    const p = http.expectOne('/api/v1/preparaciones-salida');
    expect(p.request.headers.get('Idempotency-Key')).toBe('uuid-v7');
    expect(p.request.body).toEqual(cuerpo);
    p.flush({});
  });
  it('estima sin usar el endpoint que registra', () => {
    api.estimar({ dispositivoId: 'equipo', puentesIds: [] }).subscribe();
    const p = http.expectOne('/api/v1/preparaciones-salida/estimacion');
    expect(p.request.method).toBe('POST');
    p.flush({});
  });
});
