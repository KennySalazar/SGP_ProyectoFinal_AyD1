import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { InspeccionApiService } from './inspeccion-api.service';

describe('InspeccionApiService', () => {
  let service: InspeccionApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InspeccionApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista las inspecciones propias con filtro y paginación', () => {
    service.listarMisInspecciones(2, 50, 'BORRADOR').subscribe();

    const request = http.expectOne('/api/v1/inspecciones?pagina=2&tamanio=50&estado=BORRADOR');
    expect(request.request.method).toBe('GET');
    request.flush({ content: [] });
  });

  it('elimina un borrador propio por su identificador', () => {
    service.eliminarBorrador('inspeccion/1').subscribe();

    const request = http.expectOne('/api/v1/inspecciones/inspeccion%2F1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
