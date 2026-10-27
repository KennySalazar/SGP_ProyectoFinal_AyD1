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

  it('lista las órdenes con todos los filtros combinados y estados repetidos', () => {
    service
      .listar(
        {
          puenteId: 'puente-1',
          estados: ['DESCARTADA', 'CANCELADA'],
          tipo: 'CORRECTIVO',
          prioridad: 'ALTA',
          desde: '2026-10-01',
          hasta: '2026-10-20',
        },
        1,
        100,
      )
      .subscribe();

    const request = http.expectOne((r) => r.url === base);
    const params = request.request.params;
    expect(request.request.method).toBe('GET');
    expect(params.getAll('estado')).toEqual(['DESCARTADA', 'CANCELADA']);
    expect(params.get('puenteId')).toBe('puente-1');
    expect(params.get('tipo')).toBe('CORRECTIVO');
    expect(params.get('prioridad')).toBe('ALTA');
    expect(params.get('desde')).toBe('2026-10-01');
    expect(params.get('hasta')).toBe('2026-10-20');
    expect(params.get('pagina')).toBe('1');
    expect(params.get('tamanio')).toBe('100');
    request.flush({ content: [] });
  });

  it('omite los filtros vacíos y pide los totales por estado', () => {
    service.totales({ tipo: null, prioridad: 'BAJA', desde: null }).subscribe();

    const request = http.expectOne(`${base}/totales?prioridad=BAJA`);
    expect(request.request.method).toBe('GET');
    request.flush({ total: 0, porEstado: {} });
  });

  it('crea una orden manual', () => {
    const orden = {
      puenteId: 'puente-1',
      tipo: 'CORRECTIVO' as const,
      descripcion: 'Reparar barandal',
      prioridad: 'ALTA' as const,
      elementoRef: null,
      inspeccionOrigenId: null,
    };
    service.crear(orden).subscribe();

    const request = http.expectOne(base);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(orden);
    request.flush({});
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
