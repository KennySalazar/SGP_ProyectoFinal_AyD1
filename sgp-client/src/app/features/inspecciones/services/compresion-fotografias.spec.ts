import { afterEach, describe, expect, it, vi } from 'vitest';
import { comprimirFotografia } from './compresion-fotografias';

describe('HU-057: compresión en OffscreenCanvas del worker', () => {
  afterEach(() => vi.unstubAllGlobals());
  const bitmap = { width: 4000, height: 2000 } as ImageBitmap;
  function canvas(convertir: (options: ImageEncodeOptions) => Promise<Blob>) {
    const dibujar = vi.fn();
    const crear = vi.fn();
    vi.stubGlobal(
      'OffscreenCanvas',
      class {
        constructor(
          public width: number,
          public height: number,
        ) {
          crear(width, height);
        }
        getContext() {
          return { drawImage: dibujar };
        }
        convertToBlob = convertir;
      },
    );
    return crear;
  }
  it('mantiene proporción y límite 1600, usa WebP y reduce calidad hasta el objetivo', async () => {
    const convertir = vi
      .fn()
      .mockResolvedValueOnce(new Blob([new Uint8Array(500 * 1024)], { type: 'image/webp' }))
      .mockResolvedValue(new Blob([new Uint8Array(300 * 1024)], { type: 'image/webp' }));
    const crear = canvas(convertir);
    const foto = await comprimirFotografia(bitmap);
    expect(crear).toHaveBeenCalledWith(1600, 800);
    expect(foto.type).toBe('image/webp');
    expect(foto.size).toBe(300 * 1024);
    expect(convertir.mock.calls[1][0].quality).toBeLessThan(convertir.mock.calls[0][0].quality);
  });
  it('usa JPEG cuando el navegador no codifica WebP y admite imágenes sencillas pequeñas', async () => {
    canvas(
      async ({ type }) =>
        new Blob(['imagen'], { type: type === 'image/webp' ? 'image/png' : 'image/jpeg' }),
    );
    expect((await comprimirFotografia(bitmap)).type).toBe('image/jpeg');
  });
  it('rechaza resultados vacíos, demasiado grandes, formatos no admitidos y dimensiones excesivas', async () => {
    for (const blob of [
      new Blob(),
      new Blob([new Uint8Array(401 * 1024)], { type: 'image/jpeg' }),
      new Blob(['png'], { type: 'image/png' }),
    ]) {
      canvas(async () => blob);
      await expect(comprimirFotografia(bitmap)).rejects.toThrow('compresion');
    }
    await expect(
      comprimirFotografia({ width: 10000, height: 10000 } as ImageBitmap),
    ).rejects.toThrow('dimensiones');
  });
  it('no bloquea el hilo principal con un fallback sin OffscreenCanvas', async () => {
    vi.stubGlobal('OffscreenCanvas', undefined);
    await expect(comprimirFotografia(bitmap)).rejects.toThrow('procesamientoNoDisponible');
  });
  it('usa JPEG si el códec WebP lanza un error de soporte', async () => {
    canvas(async ({ type }) => {
      if (type === 'image/webp') throw new DOMException('no soportado', 'NotSupportedError');
      return new Blob(['jpeg'], { type: 'image/jpeg' });
    });
    expect((await comprimirFotografia(bitmap)).type).toBe('image/jpeg');
  });
});
