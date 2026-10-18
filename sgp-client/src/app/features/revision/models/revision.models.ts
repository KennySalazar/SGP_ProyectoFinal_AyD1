import {
  EstadoInspeccion,
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
