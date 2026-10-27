import { EstadoObservacion } from '../../formulario/models/observacion.models';
import { CategoriaFoto } from '../../inspecciones/models/fotografia.models';
import {
  EstadoInspeccion,
  IndiceCondicionPreliminarResponse,
  InspeccionResumenResponse,
} from '../../inspecciones/models/inspeccion.models';
import { PaginaResponse } from '../../puentes/models/puente.models';

export interface RevisionBandejaResponse {
  revisionId: string;
  inspeccionId: string;
  puenteId: string;
  puenteCodigo: string;
  puenteNombre: string;
  estudianteId: string;
  estudianteEmail: string;
  estudianteNombre: string;
  cursoId: string;
  cursoNombre: string;
  periodo: string;
  seccion: string;
  numeroCiclo: number;
  enviadaEn: string;
  iniciadaEn?: string | null;
  diasEnEspera: number;
  esperaProlongada: boolean;
  estadoInspeccion: EstadoInspeccion;
  estadoRevision: 'PENDIENTE' | 'EN_CURSO';
}

export interface FiltrosBandejaRevision {
  estado?: 'ENVIADA' | 'EN_REVISION';
  puente?: string;
  estudiante?: string;
  curso?: string;
  fechaDesde?: string;
  fechaHasta?: string;
  pagina?: number;
  tamanio?: number;
}

export interface BorradorEstudianteResponse {
  inspeccionId: string;
  puenteId: string;
  puenteCodigo: string;
  puenteNombre: string;
  estudianteId: string;
  estudianteEmail: string;
  estudianteNombre: string;
  cursoId: string;
  cursoNombre: string;
  periodo: string;
  seccion: string;
  ultimaActividadEn: string;
}

export type PaginaRevisiones = PaginaResponse<RevisionBandejaResponse>;
export type PaginaBorradoresEstudiante = PaginaResponse<BorradorEstudianteResponse>;
export type ResultadoInicioRevision = InspeccionResumenResponse;

/** Contratos de HU-037; el detalle contiene exclusivamente el envío histórico. */
export interface RevisionDetalleResponse {
  revisionId: string;
  inspeccionId: string;
  estadoRevision: 'PENDIENTE' | 'EN_CURSO' | 'RESUELTA';
  revisorId: string;
  numeroCiclo: number;
  enviadaEn: string;
  autorId: string;
  puenteId: string;
  fechaInspeccion: string;
  versionFormularioId: string;
  versionFormularioCodigo: string;
  esquemaFormulario: Record<string, unknown>;
  datos: Record<string, unknown>;
  identificacionCapturada: Record<string, unknown> | null;
  icPreliminar: IndiceCondicionPreliminarResponse | null;
  fotografiasEndpoint: string;
  fotografiasCongeladas: boolean;
  observacionesEndpoint: string;
}

export type DestinoObservacionRequest =
  | {
      tipo: 'ELEMENTO';
      elementoRef: string;
      instanciaElementoId: string | null;
      categoriaFotografica: null;
    }
  | {
      tipo: 'CATEGORIA_FOTOGRAFICA';
      elementoRef: null;
      instanciaElementoId: null;
      categoriaFotografica: CategoriaFoto;
    };

export interface CrearObservacionRequest {
  destino: DestinoObservacionRequest;
  texto: string;
}
export interface ObservacionResponse {
  id: string;
  revisionId: string;
  destino: DestinoObservacionRequest | null;
  texto: string;
  estado: EstadoObservacion;
  creadoPorId: string;
  creadoEn: string;
  resueltoPorId?: string | null;
  resueltoEn?: string | null;
  respuestaAutor?: string | null;
}
export interface ObservacionGeneralResponse {
  revisionId: string;
  texto: string | null;
}
export interface ContadorObservacionesResponse {
  revisionId: string;
  total: number;
  pendientes: number;
}
export interface ListadoObservacionesResponse extends ContadorObservacionesResponse {
  observaciones: ObservacionResponse[];
  general: ObservacionGeneralResponse;
}

/** Cada enlace abre el snapshot del ciclo indicado, nunca el borrador actual. */
export interface CicloObservacionesResponse {
  revisionId: string;
  inspeccionId: string;
  numeroCiclo: number;
  estadoRevision: 'PENDIENTE' | 'EN_CURSO' | 'RESUELTA';
  enviadaEn: string;
}
export interface PaginaObservacionesRecibidas {
  content: CicloObservacionesResponse[];
  number: number;
  size: number;
  totalElements: number;
  empty: boolean;
}

export type VeredictoRevision = 'APROBADA' | 'CAMBIOS_SOLICITADOS' | 'RECHAZADA';
/** Información privada: nunca se reutiliza en avisos ni listados públicos. */
export interface RevisionResultadoResponse {
  revisionId: string;
  inspeccionId: string;
  autorId: string;
  revisorId: string;
  numeroCiclo: number;
  estadoRevision: 'PENDIENTE' | 'EN_CURSO' | 'RESUELTA';
  estadoInspeccion: EstadoInspeccion;
  veredicto: VeredictoRevision | null;
  observacionGeneral: string | null;
  calificacion: number | null;
  enviadaEn: string;
  resueltaEn: string | null;
}
export interface EmitirVeredictoRequest {
  veredicto: VeredictoRevision;
  calificacion: number;
  observacionGeneral: string | null;
  confirmacionEstado:
    import('../../inspecciones/models/inspeccion.models').ConfirmarEstadoIcRequest | null;
}
