export interface ActividadPlanMantenimiento {
  id: string;
  posicion: number;
  descripcion: string;
}

export interface PlanMantenimiento {
  id: string;
  puenteId: string;
  codigoPuente: string;
  nombrePuente: string;
  puenteActivo: boolean;
  periodicidadMeses: number;
  fechaInicio: string;
  proximoCiclo: number | null;
  fechaProximoCiclo: string | null;
  responsableUsuarioId: string | null;
  aprobadoPorId: string | null;
  aprobadoEn: string | null;
  actividades: ActividadPlanMantenimiento[];
  version: number;
}

export interface ActualizarPlanMantenimiento {
  periodicidadMeses: number;
  actividades: string[];
  version: number;
}
