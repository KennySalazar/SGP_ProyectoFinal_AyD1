/** Lado mayor máximo y peso objetivo de una fotografía antes de subirla (RN-ARC-02). */
export const LADO_MAXIMO_PX = 1600;
export const PESO_OBJETIVO_BYTES = 400 * 1024;

const CALIDADES = [0.85, 0.75, 0.65, 0.55];

/**
 * Comprime una fotografía en el dispositivo: la reduce a 1600 px de lado mayor y la codifica como
 * JPEG bajando la calidad hasta acercarse a 400 KB. El servidor vuelve a validar el contenido.
 */
export async function comprimirImagen(archivo: File): Promise<Blob> {
  const mapa = await createImageBitmap(archivo, { imageOrientation: 'from-image' });
  try {
    const factor = Math.min(1, LADO_MAXIMO_PX / Math.max(mapa.width, mapa.height));
    const lienzo = document.createElement('canvas');
    lienzo.width = Math.max(1, Math.round(mapa.width * factor));
    lienzo.height = Math.max(1, Math.round(mapa.height * factor));
    const contexto = lienzo.getContext('2d');
    if (!contexto) {
      throw new Error('El navegador no permite procesar imágenes.');
    }
    contexto.fillStyle = '#ffffff';
    contexto.fillRect(0, 0, lienzo.width, lienzo.height);
    contexto.drawImage(mapa, 0, 0, lienzo.width, lienzo.height);

    let resultado: Blob | null = null;
    for (const calidad of CALIDADES) {
      resultado = await codificar(lienzo, calidad);
      if (resultado.size <= PESO_OBJETIVO_BYTES) {
        break;
      }
    }
    return resultado as Blob;
  } finally {
    mapa.close();
  }
}

function codificar(lienzo: HTMLCanvasElement, calidad: number): Promise<Blob> {
  return new Promise((resolver, rechazar) =>
    lienzo.toBlob(
      (blob) => (blob ? resolver(blob) : rechazar(new Error('No se pudo comprimir la imagen.'))),
      'image/jpeg',
      calidad,
    ),
  );
}
