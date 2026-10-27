import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ProcesadorFotografiasService,
  entradaExif,
  fechaCapturaExif,
  fechaExif,
} from './procesador-fotografias.service';
vi.mock('exifr', () => ({ parse: vi.fn(), gps: vi.fn() }));
import { parse, gps } from 'exifr';
describe('Procesamiento de fotografías', () => {
  let servicio: ProcesadorFotografiasService;
  beforeEach(() => {
    servicio = TestBed.inject(ProcesadorFotografiasService);
  });
  afterEach(() => vi.restoreAllMocks());
  it('usa zona EXIF y rechaza fechas ausentes, sin zona, imposibles o futuras', () => {
    expect(fechaExif('2026:10:14 10:20:30', '-06:00')).toBe('2026-10-14T16:20:30.000Z');
    for (const valor of [undefined, '2026:02:31 10:20:30', '2099:01:01 10:20:30'])
      expect(() => fechaExif(valor, '-06:00')).toThrow();
    expect(() => fechaExif('2026:10:14 10:20:30', undefined)).toThrow();
  });
  it('extrae TIFF de un contenedor WebP y rechaza chunks truncados', () => {
    const bytes = new Uint8Array(26);
    bytes.set(new TextEncoder().encode('RIFF'), 0);
    bytes.set(new TextEncoder().encode('WEBPEXIF'), 8);
    new DataView(bytes.buffer).setUint32(16, 6, true);
    bytes.set([73, 73, 42, 0, 0, 0], 20);
    expect([...entradaExif(bytes.buffer)]).toEqual([73, 73, 42, 0, 0, 0]);
    new DataView(bytes.buffer).setUint32(16, 999, true);
    expect(() => entradaExif(bytes.buffer)).toThrow();
  });
  it('no sustituye GPS ausente por ubicación actual', async () => {
    vi.mocked(parse).mockResolvedValue({
      DateTimeOriginal: '2026:10:14 10:20:30',
      OffsetTimeOriginal: '-06:00',
    });
    vi.mocked(gps).mockResolvedValue(undefined as never);
    const archivo = { size: 2, arrayBuffer: async () => new Uint8Array([255, 216]).buffer } as File;
    await expect(servicio.galeria(archivo)).rejects.toThrow('metadatos');
  });
  it('aplica orientación al decodificar y comprime hasta 1600 px sin perder metadatos', async () => {
    vi.mocked(parse).mockResolvedValue({
      DateTimeOriginal: '2026:10:14 10:20:30',
      OffsetTimeOriginal: '-06:00',
      Orientation: 6,
    });
    vi.mocked(gps).mockResolvedValue({ latitude: 14, longitude: -90 });
    const close = vi.fn();
    vi.stubGlobal(
      'createImageBitmap',
      vi.fn().mockResolvedValue({ width: 3000, height: 1500, close }),
    );
    const postMessage = vi.fn(function (this: { onmessage: (event: unknown) => void }) {
      queueMicrotask(() =>
        this.onmessage({ data: { blob: new Blob(['jpg'], { type: 'image/jpeg' }) } }),
      );
    });
    const terminate = vi.fn();
    vi.stubGlobal(
      'Worker',
      class {
        onmessage!: (event: unknown) => void;
        postMessage = postMessage;
        terminate = terminate;
      },
    );
    const crearCanvas = vi.spyOn(document, 'createElement');
    const resultado = await servicio.galeria({
      size: 2,
      arrayBuffer: async () => new Uint8Array([255, 216]).buffer,
    } as File);
    expect(postMessage).toHaveBeenCalledWith(expect.objectContaining({ width: 3000 }), [
      expect.objectContaining({ width: 3000 }),
    ]);
    expect(crearCanvas).not.toHaveBeenCalled();
    expect(terminate).toHaveBeenCalled();
    expect(resultado.capturadoEn).toBe('2026-10-14T16:20:30.000Z');
    expect(resultado.orientacion).toBe(6);
    expect(resultado.latitud).toBe(14);
    expect(close).toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it('acepta la fecha UTC registrada por GPS sin inferir la zona del dispositivo', () => {
    expect(fechaCapturaExif({ GPSDateStamp: '2026:10:14', GPSTimeStamp: [16, 20, 30] })).toBe(
      '2026-10-14T16:20:30.000Z',
    );
    expect(() => fechaCapturaExif({ DateTimeOriginal: '2026:10:14 10:20:30' })).toThrow(
      'metadatos',
    );
  });
});
