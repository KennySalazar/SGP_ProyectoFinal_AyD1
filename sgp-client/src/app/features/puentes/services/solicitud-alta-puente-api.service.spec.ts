import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { SolicitudAltaPuenteApiService } from './solicitud-alta-puente-api.service';

describe('SolicitudAltaPuenteApiService: HU010', () => {
  let service: SolicitudAltaPuenteApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SolicitudAltaPuenteApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envía la solicitud de alta por POST', () => {
    const request = {
      nombre: 'Puente',
      departamentoId: 'd',
      municipioId: 'm',
      ruta: 'CA-1',
      kilometraje: null,
      latitud: 14.6,
      longitud: -90.5,
      justificacion: 'No está en el catálogo',
    };

    service.crear(request).subscribe();

    const peticion = http.expectOne('/api/v1/solicitudes-puente');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(request);
    peticion.flush({});
  });

  it('consulta las solicitudes propias con paginación', () => {
    service.listarMias(2, 10).subscribe();

    const peticion = http.expectOne((req) => req.url === '/api/v1/solicitudes-puente/mias');
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.get('pagina')).toBe('2');
    expect(peticion.request.params.get('tamanio')).toBe('10');
    peticion.flush({});
  });
});
