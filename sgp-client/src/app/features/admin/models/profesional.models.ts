export interface ProfesionalResponse {
  usuarioId: string;
  email: string;
  numeroColegiado: string;
  colegiadoVerificado: boolean;
  colegiadoVerificadoEn: string | null;
  colegiadoVerificadoPorId: string | null;
  cuentaActivada: boolean;
  creadoEn: string;
}

export interface PaginaProfesionales {
  content: ProfesionalResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
