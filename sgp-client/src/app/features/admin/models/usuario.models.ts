import { RoleName } from '../../../core/models/auth.models';

export type EstadoUsuario = 'ACTIVO' | 'PENDIENTE' | 'INACTIVO';

export interface UsuarioAdmin {
  id: string;
  email: string;
  rol: RoleName;
  estado: EstadoUsuario;
  verificado: boolean;
  activado: boolean;
  activo: boolean;
  numeroColegiado: string | null;
  colegiadoVerificado: boolean | null;
  desactivadoEn: string | null;
  motivoDesactivacion: string | null;
  creadoEn: string;
}

export interface PaginaUsuarios {
  content: UsuarioAdmin[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CambiarRolRequest {
  rol: RoleName;
  /** Obligatorio cuando el nuevo rol es Profesional Externo (RN-USR-04). */
  numeroColegiado?: string;
}
