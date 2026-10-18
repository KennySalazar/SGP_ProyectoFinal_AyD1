import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { InspeccionApiService } from './inspeccion-api.service';
describe('Contrato HTTP de fotografías', () => {
  let api: InspeccionApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(InspeccionApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('envía multipart y conserva clave y blob en cada reintento', () => {
    const blob = new Blob(['jpeg'], { type: 'image/jpeg' });
    const meta = {
      id: 'foto',
      categoria: 'ACCESO' as const,
      latitud: 14,
      longitud: -90,
      capturadoEn: '2026-10-15T12:00:00Z',
    };
    for (let i = 0; i < 2; i++) {
      api.adjuntarFotografia('inspeccion', blob, meta, 'clave-estable').subscribe();
      const req = http.expectOne('/api/v1/inspecciones/inspeccion/fotografias');
      expect(req.request.headers.get('Idempotency-Key')).toBe('clave-estable');
      expect(req.request.headers.has('Content-Type')).toBe(false);
      expect(req.request.body).toBeInstanceOf(FormData);
      expect(req.request.body.get('metadatos').type).toBe('application/json');
      expect(req.request.body.get('archivo').size).toBe(blob.size);
      req.flush(meta);
    }
  });
  it('solicita original y miniatura como blobs mediante el mismo servicio', () => {
    api.contenidoFotografia('i', 'f').subscribe();
    const a = http.expectOne('/api/v1/inspecciones/i/fotografias/f/contenido');
    expect(a.request.responseType).toBe('blob');
    a.flush(new Blob());
    api.contenidoFotografia('i', 'f', true).subscribe();
    http.expectOne('/api/v1/inspecciones/i/fotografias/f/miniatura').flush(new Blob());
    api.listarFotografias('i').subscribe();
    http.expectOne('/api/v1/inspecciones/i/fotografias').flush([]);
  });
});
