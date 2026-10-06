import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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

  it('consulta departamentos con la paginación indicada', () => {
    service.listarDepartamentos(1, 20).subscribe();

    const request = http.expectOne((req) => req.url === '/api/v1/catalogos/departamentos');

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('pagina')).toBe('1');
    expect(request.request.params.get('tamanio')).toBe('20');

    request.flush({ content: [] });
  });

  it('consulta el catálogo público con filtros y paginación', () => {
    service
      .listarCatalogo({
        departamentoId: 'departamento-1',
        estado: 'Sin evaluar',
        pagina: 2,
        tamanio: 100,
      })
      .subscribe();

    const request = http.expectOne((req) => req.url === '/api/v1/puentes');

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('departamentoId')).toBe('departamento-1');
    expect(request.request.params.get('estado')).toBe('Sin evaluar');
    expect(request.request.params.get('pagina')).toBe('2');
    expect(request.request.params.get('tamanio')).toBe('100');

    request.flush({ content: [] });
  });

  it('omite filtros ausentes y usa la paginación predeterminada', () => {
    service.listarCatalogo().subscribe();

    const request = http.expectOne((req) => req.url === '/api/v1/puentes');

    expect(request.request.params.keys().sort()).toEqual(['pagina', 'tamanio']);
    expect(request.request.params.get('pagina')).toBe('0');
    expect(request.request.params.get('tamanio')).toBe('20');

    request.flush({ content: [] });
  });

  it.each([0, 101, -1, 1.5])('no solicita tamaños inválidos: %s', (tamanio) => {
    expect(() => service.listarCatalogo({ tamanio })).toThrow(RangeError);

    http.expectNone('/api/v1/puentes');
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

  it('resuelve la ubicación territorial usando latitud y longitud', () => {
    const respuesta = {
      latitud: 14.481,
      longitud: -90.615,
      zonaUtm: '15N',
      requiereSeleccion: false,
      candidatos: [
        {
          departamento: {
            id: 'departamento-1',
            codigoIne: '01',
            nombre: 'Guatemala',
          },
          municipio: {
            id: 'municipio-1',
            departamentoId: 'departamento-1',
            codigoIne: '0114',
            nombre: 'Amatitlán',
          },
        },
      ],
    };

    const recibido = vi.fn();

    service.resolverUbicacion(14.481, -90.615).subscribe(recibido);

    const request = http.expectOne((req) => req.url === '/api/v1/catalogos/ubicacion');

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('latitud')).toBe('14.481');
    expect(request.request.params.get('longitud')).toBe('-90.615');

    request.flush(respuesta);

    expect(recibido).toHaveBeenCalledTimes(1);
    expect(recibido).toHaveBeenCalledWith(respuesta);
  });

  it('envía los datos y la confirmación al registrar', () => {
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

  it('consulta el catalogo filtrando por activo', () => {
    service.listarCatalogo({ activo: false }).subscribe();

    const request = http.expectOne((req) => req.url === '/api/v1/puentes');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('activo')).toBe('false');

    request.flush({ content: [] });
  });

  it('consulta el catalogo filtrando por todos los puentes', () => {
    service.listarCatalogo({ todos: true }).subscribe();

    const request = http.expectOne((req) => req.url === '/api/v1/puentes');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('todos')).toBe('true');

    request.flush({ content: [] });
  });

  it('envia el motivo al dar de baja un puente', () => {
    service.darDeBaja('puente-1', 'demolido').subscribe();

    const request = http.expectOne('/api/v1/puentes/puente-1/baja');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ motivo: 'demolido' });

    request.flush({});
  });

  it('envia la peticion para reactivar un puente', () => {
    service.reactivar('puente-1').subscribe();

    const request = http.expectOne('/api/v1/puentes/puente-1/reactivar');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});

    request.flush({});
  });
});
