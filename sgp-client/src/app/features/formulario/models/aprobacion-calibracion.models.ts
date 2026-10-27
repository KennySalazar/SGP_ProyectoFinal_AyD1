export interface AprobacionCalibracion {
  id: string;
  versionFormularioId: string;
  aprobadoPorId: string;
  aprobadoPorNombre: string;
  aprobadoEn: string;
  observaciones: string | null;
  rotuloCalibracion: string;
}

export interface EstadoCalibracion {
  versionFormularioId: string;
  estado: 'SIN_APROBAR' | 'APROBADA';
  rotuloCalibracion: string;
  aprobadoPorId: string | null;
  aprobadoPorNombre: string | null;
  aprobadoEn: string | null;
}
