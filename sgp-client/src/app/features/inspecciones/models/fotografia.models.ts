export const CATEGORIAS_FOTO = [
  'ACCESO',
  'SUPERESTRUCTURA',
  'SUBESTRUCTURA',
  'CAUCE',
  'OTRA',
] as const;
export type CategoriaFoto = (typeof CATEGORIAS_FOTO)[number];
export interface MetadatosFotografia {
  id: string;
  categoria: CategoriaFoto;
  latitud: number;
  longitud: number;
  capturadoEn: string;
  elementoRef?: string;
  instanciaElementoId?: string;
}
export interface FotografiaResponse extends MetadatosFotografia {
  inspeccionId: string;
  mimeReal: string;
  tamanoBytes: number;
  anchoPx: number;
  altoPx: number;
}
export interface ImagenPreparada {
  blob: Blob;
  latitud: number;
  longitud: number;
  capturadoEn: string;
  orientacion: number;
}
