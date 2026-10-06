export type AccionAuditoria = 'CREAR' | 'MODIFICAR' | 'CAMBIAR_ESTADO' | 'MODERAR';

export interface RegistroAuditoria {
  id: string;
  usuarioId: string | null;
  usuarioEmail: string | null;
  accion: AccionAuditoria;
  entidad: string;
  entidadId: string | null;
  procesoAutomatico: string | null;
  creadoEn: string;
}

export interface CambioAuditoria {
  campo: string;
  anterior: string | null;
  posterior: string | null;
}

export interface DetalleAuditoria extends RegistroAuditoria {
  cambios: CambioAuditoria[];
}

export interface PaginaAuditoria {
  content: RegistroAuditoria[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ConsultaAuditoria {
  usuarioEmail?: string;
  desde?: string;
  hasta?: string;
  pagina?: number;
  tamanio?: number;
}
