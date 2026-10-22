import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CalibracionAdicionalApiService } from './calibracion-adicional-api.service';

describe('CalibracionAdicionalApiService', () => {
  let api: CalibracionAdicionalApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(CalibracionAdicionalApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('guarda el arreglo completo de reglas en el endpoint especifico', () => {
    const reglas = [{ id: 'RN-IC-05a', pendiente: null }];
    api.guardarReglas('v/4', reglas, 2).subscribe();
    const req = http.expectOne('/api/v1/formularios/v%2F4/reglas-criticas');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ reglasAnulacion: reglas });
    expect(req.request.headers.get('If-Match')).toBe('2');
    req.flush({});
  });
  it('consulta el estado sin acta', () => {
    api.consultarActa('v4').subscribe();
    const req = http.expectOne('/api/v1/formularios/v4/acta-calibracion');
    expect(req.request.method).toBe('GET');
    req.flush({ estado: 'SIN_ACTA', acta: null });
  });
  it('envia el PDF como multipart con la parte archivo', () => {
    const archivo = new File(['%PDF'], 'acta.pdf', { type: 'application/pdf' });
    api.adjuntarActa('v4', archivo, 4).subscribe();
    const req = http.expectOne('/api/v1/formularios/v4/acta-calibracion');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.get('archivo')).toBe(archivo);
    expect(req.request.headers.get('If-Match')).toBe('4');
    expect(req.request.headers.has('Content-Type')).toBe(false);
    req.flush({});
  });
  it('descarga contenido privado usando el cliente autenticado', () => {
    api.descargarActa('v4').subscribe();
    const req = http.expectOne('/api/v1/formularios/v4/acta-calibracion/contenido');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['%PDF']));
  });
});
