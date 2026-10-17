import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { FormularioApiService } from './formulario-api.service';

describe('FormularioApiService', () => {
  let service: FormularioApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [FormularioApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FormularioApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('lista las versiones de formulario', () => {
    service.listarVersiones().subscribe((versiones) => {
      expect(versiones).toHaveLength(1);
      expect(versiones[0].codigo).toBe('SIECA-v1');
    });

    const req = httpTesting.expectOne('/api/v1/formularios');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: '1', codigo: 'SIECA-v1', estado: 'PUBLICADA', activa: true }]);
  });

  it('obtiene la versión activa', () => {
    service.obtenerActiva().subscribe((activa) => {
      expect(activa.codigo).toBe('SIECA-v1');
      expect(activa.version).toBe(1);
    });

    const req = httpTesting.expectOne('/api/v1/formularios/activo');
    expect(req.request.method).toBe('GET');
    req.flush({
      id: '1',
      codigo: 'SIECA-v1',
      version: 1,
      esquema: {},
      hashContenido: 'abc',
      publicadoEn: '2026-10-13T00:00:00Z',
    });
  });

  it('obtiene el esquema de una versión publicada por su id', () => {
    service.obtenerEsquemaPublicado('a b').subscribe((version) => {
      expect(version.codigo).toBe('SIECA-v0');
    });

    const req = httpTesting.expectOne('/api/v1/formularios/a%20b/esquema');
    expect(req.request.method).toBe('GET');
    req.flush({
      id: 'a b',
      codigo: 'SIECA-v0',
      version: 0,
      esquema: {},
      hashContenido: 'abc',
      publicadoEn: '2026-10-01T00:00:00Z',
    });
  });

  it('carga la plantilla inicial sieca', () => {
    service.cargarPlantillaSieca('SIECA-v1').subscribe((version) => {
      expect(version.codigo).toBe('SIECA-v1');
    });

    const req = httpTesting.expectOne('/api/v1/formularios/plantilla-sieca?codigo=SIECA-v1');
    expect(req.request.method).toBe('POST');
    req.flush({ id: '1', codigo: 'SIECA-v1', estado: 'BORRADOR', activa: false });
  });

  it('publica una versión', () => {
    service.publicar('v-1').subscribe((pub) => {
      expect(pub.estado).toBe('PUBLICADA');
      expect(pub.activa).toBe(true);
    });

    const req = httpTesting.expectOne('/api/v1/formularios/v-1/publicar');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'v-1', codigo: 'SIECA-v1', estado: 'PUBLICADA', activa: true });
  });
});
