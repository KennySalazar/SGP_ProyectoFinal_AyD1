import Dexie, { Table } from 'dexie';

export interface LocalBridge { id: string; codigo?: string; actualizadoEn?: string; payload: unknown; }
export interface LocalInspection { id: string; puenteId: string; estado: string; actualizadoEn: string; payload: unknown; sincronizado: boolean; }
export interface LocalPhoto { id: string; inspeccionId: string; blob: Blob; mime: string; sincronizado: boolean; }
export interface LocalFormSchema { id: string; version: string; schema: unknown; activo: boolean; }
export interface SyncQueueItem { id: string; tipo: 'INSPECCION' | 'FOTO'; entityId: string; idempotencyKey: string; intentos: number; creadoEn: string; ultimoError?: string; }

export class SgpDatabase extends Dexie {
  puentes!: Table<LocalBridge, string>;
  inspecciones!: Table<LocalInspection, string>;
  fotos!: Table<LocalPhoto, string>;
  esquemas_formulario!: Table<LocalFormSchema, string>;
  cola_sync!: Table<SyncQueueItem, string>;

  constructor() {
    super('sgp_offline');
    this.version(1).stores({
      puentes: 'id,codigo,actualizadoEn',
      inspecciones: 'id,puenteId,estado,actualizadoEn,sincronizado',
      fotos: 'id,inspeccionId,sincronizado',
      esquemas_formulario: 'id,version,activo',
      cola_sync: 'id,tipo,entityId,creadoEn,intentos'
    });
  }
}

export const sgpDb = new SgpDatabase();
