import Dexie, { Table } from 'dexie';

export interface LocalBridge {
  id: string;
  codigo?: string;
  actualizadoEn?: string;
  payload: unknown;
}
export interface LocalInspection {
  propietarioId?: string;
  id: string;
  puenteId: string;
  estado: string;
  actualizadoEn: string;
  payload: unknown;
  sincronizado: boolean;
  actualizacionLocalPendiente?: boolean;
}
export interface LocalPhoto {
  propietarioId?: string;
  idempotencyKey?: string;
  metadatos?: {
    id: string;
    categoria: string;
    latitud: number;
    longitud: number;
    capturadoEn: string;
    elementoRef?: string;
    instanciaElementoId?: string;
  };
  id: string;
  inspeccionId: string;
  blob: Blob;
  mime: string;
  sincronizado: boolean;
}
export interface LocalFormSchema {
  id: string;
  version: string;
  schema: unknown;
  activo: boolean;
}
export interface SyncQueueItem {
  id: string;
  tipo: 'INSPECCION' | 'FOTO';
  entityId: string;
  idempotencyKey: string;
  propietarioId?: string;
  operacion?: 'CREAR_INSPECCION' | 'GUARDAR_BORRADOR';
  payload?: unknown;
  intentos: number;
  creadoEn: string;
  ultimoError?: string;
  codigoError?: string;
  estado?: 'PENDIENTE' | 'ENVIANDO' | 'INTERVENCION';
}

export interface LocalNotificacion {
  id: string;
  tipo: string;
  titulo: string;
  mensaje: string;
  entidad?: string;
  entidadId?: string;
  claveEvento: string;
  leidoEn?: string;
  creadoEn: string;
}

export interface LocalNotificacionMeta {
  key: string;
  valor: number | string;
}

export class SgpDatabase extends Dexie {
  puentes!: Table<LocalBridge, string>;
  inspecciones!: Table<LocalInspection, string>;
  fotos!: Table<LocalPhoto, string>;
  esquemas_formulario!: Table<LocalFormSchema, string>;
  cola_sync!: Table<SyncQueueItem, string>;
  notificaciones!: Table<LocalNotificacion, string>;
  notificaciones_meta!: Table<LocalNotificacionMeta, string>;

  constructor() {
    super('sgp_offline');
    this.version(1).stores({
      puentes: 'id,codigo,actualizadoEn',
      inspecciones: 'id,puenteId,estado,actualizadoEn,sincronizado',
      fotos: 'id,inspeccionId,sincronizado',
      esquemas_formulario: 'id,version,activo',
      cola_sync: 'id,tipo,entityId,creadoEn,intentos',
    });
    this.version(2).stores({
      notificaciones: 'id,leidoEn,creadoEn',
      notificaciones_meta: 'key',
    });
    this.version(3).stores({
      cola_sync: 'id,tipo,entityId,creadoEn,intentos,propietarioId,estado',
    });
  }
}

export const sgpDb = new SgpDatabase();
