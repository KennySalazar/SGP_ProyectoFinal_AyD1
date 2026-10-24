import type { AsignacionPuenteResponse } from '../../features/puentes/models/puente.models';
import type { FotografiaResponse } from '../../features/inspecciones/models/fotografia.models';
import type { InspeccionResumenResponse } from '../../features/inspecciones/models/inspeccion.models';
import type {
  ResumenSalida,
  SolicitudSalidaLocal,
} from '../../features/salida/models/salida.models';
import Dexie, { Table } from 'dexie';

export interface LocalBridge {
  propietarioId?: string;
  origen?: 'PREPARACION';
  origenId?: string;
  id: string;
  codigo?: string;
  actualizadoEn?: string;
  payload: unknown;
}
export interface LocalInspection {
  origen?: 'PREPARACION';
  origenId?: string;
  propietarioId?: string;
  id: string;
  puenteId: string;
  estado: string;
  actualizadoEn: string;
  payload: unknown;
  sincronizado: boolean;
  actualizacionLocalPendiente?: boolean;
  fotosConfirmadas?: FotografiaResponse[];
  intencionFinal?: {
    accion: 'enviar' | 'publicar';
    estado: 'PENDIENTE' | 'RECHAZADA' | 'CONFIRMADA';
    error?: string;
    resultado?: InspeccionResumenResponse;
  };
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
  propietarioId?: string;
  origen?: 'PREPARACION';
  origenId?: string;
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
  operacion?: 'CREAR_INSPECCION' | 'GUARDAR_BORRADOR' | 'ENVIAR_INSPECCION' | 'PUBLICAR_INSPECCION';
  peticionFijada?: boolean;
  iniciadoEn?: string;
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
  asignaciones_usuario!: Table<
    { propietarioId: string; datos: AsignacionPuenteResponse[] },
    string
  >;
  puentes!: Table<LocalBridge, string>;
  inspecciones!: Table<LocalInspection, string>;
  fotos!: Table<LocalPhoto, string>;
  esquemas_formulario!: Table<LocalFormSchema, string>;
  cola_sync!: Table<SyncQueueItem, string>;
  notificaciones!: Table<LocalNotificacion, string>;
  notificaciones_meta!: Table<LocalNotificacionMeta, string>;
  preparaciones_salida!: Table<ResumenSalida, string>;
  solicitudes_salida!: Table<SolicitudSalidaLocal, string>;

  constructor(nombre = 'sgp_offline') {
    super(nombre);
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
      preparaciones_salida: 'propietarioId',
      solicitudes_salida: 'propietarioId',
    });
    this.version(4).stores({ asignaciones_usuario: 'propietarioId' });
  }
}

export const sgpDb = new SgpDatabase();
