import { RoleName } from '../../../core/models/auth.models';

export type EstadoInvitacion = 'PENDIENTE' | 'VENCIDA' | 'ACEPTADA' | 'CANCELADA';

export interface CrearInvitacionRequest {
  email: string;
  rol: RoleName;
  /** Obligatorio solo para el rol Profesional Externo (RN-USR-04). */
  numeroColegiado?: string;
}

export interface InvitacionResponse {
  id: string;
  email: string;
  rol: RoleName;
  estado: EstadoInvitacion;
  usuarioId: string | null;
  invitadoPorId: string;
  numeroColegiado: string | null;
  expiraEn: string;
  aceptadoEn: string | null;
  canceladoEn: string | null;
  creadoEn: string;
}

export interface PaginaInvitaciones {
  content: InvitacionResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
