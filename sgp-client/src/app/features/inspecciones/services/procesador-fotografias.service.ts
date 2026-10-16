import { Injectable } from '@angular/core';
import { ImagenPreparada } from '../models/fotografia.models';

export function fechaExif(fecha: unknown, zona: unknown): string {
  if (
    typeof fecha !== 'string' ||
    typeof zona !== 'string' ||
    !/^\d{4}:\d{2}:\d{2} \d{2}:\d{2}:\d{2}$/.test(fecha) ||
    !/^[+-](0\d|1[0-4]):[0-5]\d$/.test(zona)
  )
    throw new Error('metadatos');
  const partes = fecha.split(/[: ]/).map(Number);
  const calendario = new Date(
    Date.UTC(partes[0], partes[1] - 1, partes[2], partes[3], partes[4], partes[5]),
  );
  if (
    calendario.getUTCFullYear() !== partes[0] ||
    calendario.getUTCMonth() + 1 !== partes[1] ||
    calendario.getUTCDate() !== partes[2] ||
    calendario.getUTCHours() !== partes[3] ||
    calendario.getUTCMinutes() !== partes[4] ||
    calendario.getUTCSeconds() !== partes[5] ||
    ((zona.startsWith('+14:') || zona.startsWith('-14:')) && !zona.endsWith(':00'))
  )
    throw new Error('metadatos');
  const iso = fecha.substring(0, 10).replaceAll(':', '-') + 'T' + fecha.substring(11) + zona;
  const instante = new Date(iso);
  if (!Number.isFinite(instante.getTime()) || instante.getTime() > Date.now() + 300000)
    throw new Error('metadatos');
  return instante.toISOString();
}
export function fechaCapturaExif(tags: Record<string, unknown>): string {
  if (tags['DateTimeOriginal'] && tags['OffsetTimeOriginal'])
    return fechaExif(tags['DateTimeOriginal'], tags['OffsetTimeOriginal']);
  const fecha = tags['GPSDateStamp'],
    hora = tags['GPSTimeStamp'];
  if (
    typeof fecha !== 'string' ||
    !Array.isArray(hora) ||
    hora.length !== 3 ||
    !hora.every((n) => typeof n === 'number' && Number.isFinite(n))
  )
    throw new Error('metadatos');
  return fechaExif(
    `${fecha} ${hora.map((n) => String(Math.floor(n)).padStart(2, '0')).join(':')}`,
    '+00:00',
  );
}

/** Extrae el bloque TIFF de WebP; exifr procesa JPEG/TIFF de forma nativa. */
export function entradaExif(bytes: ArrayBuffer): Uint8Array {
  const datos = new Uint8Array(bytes);
  const vista = new DataView(bytes);
  const texto = (inicio: number, fin: number) => String.fromCharCode(...datos.slice(inicio, fin));
  if (datos.length >= 12 && texto(0, 4) === 'RIFF' && texto(8, 12) === 'WEBP') {
    for (let pos = 12; pos + 8 <= datos.length;) {
      const tamano = vista.getUint32(pos + 4, true);
      if (tamano > datos.length - pos - 8) throw new Error('formato');
      if (texto(pos, pos + 4) === 'EXIF') {
        const inicio = texto(pos + 8, pos + 14) === 'Exif\0\0' ? pos + 14 : pos + 8;
        return datos.slice(inicio, pos + 8 + tamano);
      }
      pos += 8 + tamano + (tamano % 2);
    }
    throw new Error('metadatos');
  }
  if (datos[0] !== 255 || datos[1] !== 216) throw new Error('formato');
  return datos;
}
@Injectable({ providedIn: 'root' })
export class ProcesadorFotografiasService {
  async galeria(archivo: File): Promise<ImagenPreparada> {
    if (!archivo.size || archivo.size > 20 * 1024 * 1024) throw new Error('tamano');
    const entrada = entradaExif(await archivo.arrayBuffer());
    const exifr = await import('exifr');
    const tags: Record<string, unknown> = await exifr.parse(entrada, {
      pick: [
        'DateTimeOriginal',
        'OffsetTimeOriginal',
        'Orientation',
        'GPSDateStamp',
        'GPSTimeStamp',
      ],
      reviveValues: false,
      translateValues: false,
    });
    const gps = await exifr.gps(entrada);
    if (
      !gps ||
      !Number.isFinite(gps.latitude) ||
      !Number.isFinite(gps.longitude) ||
      Math.abs(gps.latitude) > 90 ||
      Math.abs(gps.longitude) > 180 ||
      !tags
    )
      throw new Error('metadatos');
    const capturadoEn = fechaCapturaExif(tags);
    const orientacion = typeof tags['Orientation'] === 'number' ? tags['Orientation'] : 1;
    // El navegador aplica EXIF una sola vez al decodificar; no se vuelve a rotar el canvas.
    const bitmap = await createImageBitmap(archivo, { imageOrientation: 'from-image' });
    try {
      return {
        blob: await this.comprimir(bitmap),
        latitud: gps.latitude,
        longitud: gps.longitude,
        capturadoEn,
        orientacion,
      };
    } finally {
      bitmap.close();
    }
  }
  async camara(video: HTMLVideoElement): Promise<ImagenPreparada> {
    if (!video.videoWidth || !video.videoHeight) throw new Error('camara');
    const posicion = await new Promise<GeolocationPosition>((resolve, reject) =>
      navigator.geolocation.getCurrentPosition(resolve, reject, {
        enableHighAccuracy: true,
        maximumAge: 0,
        timeout: 10000,
      }),
    );
    if (Math.abs(Date.now() - posicion.timestamp) > 30000) throw new Error('metadatos');
    const capturadoEn = new Date().toISOString();
    return {
      blob: await this.comprimir(video),
      latitud: posicion.coords.latitude,
      longitud: posicion.coords.longitude,
      capturadoEn,
      orientacion: 1,
    };
  }
  private async comprimir(fuente: ImageBitmap | HTMLVideoElement): Promise<Blob> {
    const ancho = 'videoWidth' in fuente ? fuente.videoWidth : fuente.width;
    const alto = 'videoHeight' in fuente ? fuente.videoHeight : fuente.height;
    if (!ancho || !alto || ancho * alto > 40000000) throw new Error('dimensiones');
    const proporcion = Math.min(1, 1600 / Math.max(ancho, alto));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(ancho * proporcion));
    canvas.height = Math.max(1, Math.round(alto * proporcion));
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('formato');
    ctx.drawImage(fuente, 0, 0, canvas.width, canvas.height);
    let blob: Blob | null = null;
    for (let calidad = 0.88; calidad >= 0.4; calidad -= 0.08) {
      blob = await new Promise<Blob | null>((resolve) =>
        canvas.toBlob(resolve, 'image/jpeg', calidad),
      );
      if (!blob || !['image/jpeg', 'image/webp'].includes(blob.type)) throw new Error('formato');
      if (blob.size <= 400 * 1024) break;
    }
    if (!blob) throw new Error('formato');
    return blob;
  }
}
