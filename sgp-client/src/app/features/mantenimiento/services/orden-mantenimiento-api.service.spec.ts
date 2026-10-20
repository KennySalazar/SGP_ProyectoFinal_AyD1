import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { OrdenMantenimientoApiService } from './orden-mantenimiento-api.service';

describe('OrdenMantenimientoApiService', () => {
  let service: OrdenMantenimientoApiService;
  let http: HttpTestingController;
  const base = '/api/v1/ordenes-mantenimiento';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrdenMantenimientoApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista las órdenes con filtro de estado y paginación', () => {
    service.listar(1, 50, 'EN_EJECUCION').subscribe();

    const request = http.expectOne(`${base}?pagina=1&tamanio=50&estado=EN_EJECUCION`);
    expect(request.request.method).toBe('GET');
    request.flush({ content: [] });
  });

  it('cierra la orden como ejecutada con la versión leída', () => {
    service.cerrar('orden-1', 4).subscribe();

    const request = http.expectOne(`${base}/orden-1/cerrar`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ version: 4 });
    request.flush({});
  });

  it('registra la fecha de ejecución y el responsable', () => {
    const cuerpo = {
      fechaEjecucion: '2026-10-20',
      responsableUsuarioId: null,
      responsableNombre: 'Constructora Los Altos',
    };
    service.registrarEjecucion('orden-1', cuerpo).subscribe();

    const request = http.expectOne(`${base}/orden-1/ejecucion`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(cuerpo);
    request.flush({});
  });

  it('sube la evidencia como multipart con el elemento opcional', () => {
    service
      .adjuntarEvidencia('orden-1', new Blob(['x'], { type: 'image/jpeg' }), ' /drenaje ')
      .subscribe();

    const request = http.expectOne(`${base}/orden-1/evidencias`);
    const datos = request.request.body as FormData;
    expect(request.request.method).toBe('POST');
    expect(datos.get('archivo')).toBeInstanceOf(Blob);
    expect(datos.get('elementoRef')).toBe('/drenaje');
    request.flush({});
  });

  it('retira una evidencia con su motivo', () => {
    service.retirarEvidencia('orden-1', 'evidencia/1', 'Foto equivocada').subscribe();

    const request = http.expectOne(`${base}/orden-1/evidencias/evidencia%2F1/retirar`);
    expect(request.request.body).toEqual({ motivo: 'Foto equivocada' });
    request.flush(null);
  });

  it('pide la miniatura como blob al extremo autorizado', () => {
    service.imagenEvidencia('orden-1', 'evidencia-1', true).subscribe();

    const request = http.expectOne(`${base}/orden-1/evidencias/evidencia-1/miniatura`);
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob());
  });
});
