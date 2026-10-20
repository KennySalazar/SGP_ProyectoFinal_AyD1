import { PrioridadMantenimiento, TipoMantenimiento } from './regla-mantenimiento.models';

export type EstadoOrden =
  'PROPUESTA' | 'PROGRAMADA' | 'EN_EJECUCION' | 'EJECUTADA' | 'DESCARTADA' | 'CANCELADA';

export interface OrdenMantenimiento {
  id: string;
  puenteId: string;
  inspeccionOrigenId: string | null;
  reglaOrigenId: string | null;
  resultadoIcOrigenId: string | null;
  elementoRef: string | null;
  instanciaElementoId: string | null;
  tipo: TipoMantenimiento;
  estado: EstadoOrden;
  descripcion: string;
  prioridadSugerida: PrioridadMantenimiento;
  prioridadConfirmada: PrioridadMantenimiento;
  justificacionPrioridad: string | null;
  fundamentoGeneracion: unknown;
  creadoPorId: string | null;
  aceptadoPorId: string | null;
  aceptadoEn: string | null;
  fechaProgramada: string | null;
  motivoCierreSinEjecucion: string | null;
  creadoEn: string;
  actualizadoEn: string;
  version: number;
}

export interface PaginaOrdenesMantenimiento {
  content: OrdenMantenimiento[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  empty?: boolean;
}

export interface HistorialEstadoOrden {
  secuencia: number;
  estadoAnterior: EstadoOrden | null;
  estadoNuevo: EstadoOrden;
  realizadoPorId: string | null;
  motivo: string | null;
  creadoEn: string;
}

/** Registro de ejecución de una orden (HU-048). */
export interface EjecucionOrden {
  ordenId: string;
  iniciadoEn: string;
  fechaEjecucion: string | null;
  responsableUsuarioId: string | null;
  responsableUsuarioEmail: string | null;
  responsableNombre: string | null;
  actualizadoEn: string;
}

export interface RegistrarEjecucionRequest {
  fechaEjecucion: string | null;
  responsableUsuarioId: string | null;
  responsableNombre: string | null;
}

export interface ResponsableDisponible {
  id: string;
  email: string;
  rol: string;
}

export interface EvidenciaMantenimiento {
  id: string;
  ordenId: string;
  elementoRef: string | null;
  mimeReal: string;
  tamanoBytes: number;
  anchoPx: number | null;
  altoPx: number | null;
  subidoPorId: string;
  subidoEn: string;
}
