import { comprimirFotografia } from './compresion-fotografias';

addEventListener('message', async ({ data }: MessageEvent<ImageBitmap>) => {
  try {
    postMessage({ blob: await comprimirFotografia(data) });
  } catch (error) {
    const codigo = error instanceof Error ? error.message : '';
    postMessage({
      error: ['dimensiones', 'compresion', 'procesamientoNoDisponible'].includes(codigo)
        ? codigo
        : 'formato',
    });
  } finally {
    data.close();
  }
});
