import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { FormularioApiService } from './formulario-api.service';

describe('FormularioApiService', () => {
  let service: FormularioApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(FormularioApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('consulta las versiones del formulario', () => {
    service.listarVersiones().subscribe();

    const request = http.expectOne('/api/v1/formularios');

    expect(request.request.method).toBe('GET');

    request.flush([]);
  });

  it('codifica el identificador al consultar una version', () => {
    service.obtenerPorId('version/1').subscribe();

    const request = http.expectOne('/api/v1/formularios/version%2F1');

    expect(request.request.method).toBe('GET');

    request.flush({});
  });

  it('envia los pesos asociados a la version seleccionada', () => {
    const pesos = [
      {
        elementoRef: '/subestructura/pilas',
        peso: 2,
        descripcion: 'Pilas',
      },
    ];

    service.calibrarPesos('version-1', pesos).subscribe();

    const request = http.expectOne('/api/v1/formularios/version-1/pesos');

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(pesos);

    request.flush([
      {
        id: 'peso-1',
        ...pesos[0],
        creadoEn: '2026-10-17T00:00:00Z',
      },
    ]);
  });

  it('envia los cuatro niveles al configurar la escala', () => {
    const body = {
      valores: {
        NO_PRESENTA: 0,
        LEVE: 1,
        MODERADO: 2,
        SEVERO: 4,
      },
    };

    service.configurarEscala('version-1', body).subscribe();

    const request = http.expectOne('/api/v1/formularios/version-1/escala');

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(body);

    request.flush({});
  });

  it('publica la version mediante su endpoint sin enviar nuevos pesos', () => {
    service.publicar('version-1', { mapeos: [] }).subscribe();

    const request = http.expectOne('/api/v1/formularios/version-1/publicar');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ mapeos: [] });

    request.flush({});
  });
});
