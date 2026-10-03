import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CrearPuenteRequest } from '../models/puente.models';
import { PuenteApiService } from './puente-api.service';

describe('PuenteApiService', () => {
  let service: PuenteApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(PuenteApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('consulta departamentos con la paginacion indicada', () => {
    service.listarDepartamentos(1, 20).subscribe();

    const request = http.expectOne((req) => req.url === '/api/v1/catalogos/departamentos');

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('pagina')).toBe('1');
    expect(request.request.params.get('tamanio')).toBe('20');

    request.flush({ content: [] });
  });

  it('consulta municipios del departamento seleccionado', () => {
    service.listarMunicipios('departamento-1').subscribe();

    const request = http.expectOne(
      (req) => req.url === '/api/v1/catalogos/departamentos/departamento-1/municipios',
    );

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('pagina')).toBe('0');
    expect(request.request.params.get('tamanio')).toBe('100');

    request.flush({ content: [] });
  });

  it('envia los datos y la confirmacion al registrar', () => {
    const body: CrearPuenteRequest = {
      nombre: 'Puente HU9',
      departamentoId: 'departamento-1',
      municipioId: 'municipio-1',
      ruta: 'CA-9',
      kilometraje: null,
      latitud: 14.481,
      longitud: -90.615,
      confirmarCercania: true,
    };

    service.registrar(body).subscribe();

    const request = http.expectOne('/api/v1/puentes');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);

    request.flush({});
  });
});
