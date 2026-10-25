/** Se ejecuta exclusivamente desde el worker; exportado para verificar el códec sin DOM. */
export async function comprimirFotografia(bitmap: ImageBitmap): Promise<Blob> {
  const { width, height } = bitmap;
  if (!width || !height || width * height > 40000000) throw new Error('dimensiones');
  if (typeof OffscreenCanvas === 'undefined') throw new Error('procesamientoNoDisponible');
  const escala = Math.min(1, 1600 / Math.max(width, height));
  const canvas = new OffscreenCanvas(
    Math.max(1, Math.round(width * escala)),
    Math.max(1, Math.round(height * escala)),
  );
  const contexto = canvas.getContext('2d');
  if (!contexto) throw new Error('procesamientoNoDisponible');
  contexto.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  for (const type of ['image/webp', 'image/jpeg']) {
    for (const quality of [0.94, 0.86, 0.78, 0.7, 0.62, 0.54, 0.46, 0.4]) {
      let blob: Blob;
      try {
        blob = await canvas.convertToBlob({ type, quality });
      } catch {
        if (type === 'image/webp') break;
        throw new Error('compresion');
      }
      if (!['image/webp', 'image/jpeg'].includes(blob.type)) break;
      // Una imagen sencilla puede ocupar menos de 200 KiB: no se rellena ni amplía.
      if (blob.size > 0 && blob.size <= 400 * 1024) return blob;
    }
  }
  throw new Error('compresion');
}
