export type TipoMantenimiento = 'RUTINARIO' | 'PREVENTIVO' | 'CORRECTIVO' | 'EMERGENCIA';

export type PrioridadMantenimiento = 'BAJA' | 'MEDIA' | 'ALTA' | 'CRITICA';

export interface CondicionRegla {
  indicador?: string;
  operador?: string;
  umbral?: unknown;
  [key: string]: unknown;
}

export interface ReglaPrioridad {
  prioridad_base?: PrioridadMantenimiento;
  factores?: unknown[];
  [key: string]: unknown;
}

export interface ReglaMantenimiento {
  id: string;
  codigo: string;
  numeroRevision: number;
  versionFormularioId: string;
  versionFormularioCodigo: string;
  elementoRef: string;
  condicion: CondicionRegla;
  tipoMantenimiento: TipoMantenimiento;
  intervencionSugerida: string;
  reglaPrioridad: ReglaPrioridad;
  activa: boolean;
  provisional: boolean;
  estadoValidacion: string;
  creadoPorId: string;
  validadoPorNombre: string | null;
  rotuloValidacion: string;
  validadoPorId: string | null;
  validadoEn: string | null;
  creadoEn: string;
}

export interface ResumenReglasVersion {
  versionFormularioId: string;
  versionFormularioCodigo: string;
  tieneReglasActivas: boolean;
  totalReglas: number;
  totalActivas: number;
  totalProvisionales: number;
  totalValidadas: number;
  advertenciaSinReglasActivas: string | null;
}

export interface PaginaReglasMantenimiento {
  content: ReglaMantenimiento[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface FiltrosReglasMantenimiento {
  versionId?: string;
  tipo?: TipoMantenimiento | null;
  elemento?: string;
  activa?: boolean | null;
  pagina?: number;
  tamanio?: number;
}

export interface CrearReglaRequest {
  codigo: string;
  versionFormularioId: string;
  elementoRef: string;
  condicion: CondicionRegla;
  tipoMantenimiento: TipoMantenimiento;
  intervencionSugerida: string;
  reglaPrioridad: ReglaPrioridad;
  activa?: boolean;
}

export interface EditarReglaRequest {
  elementoRef: string;
  condicion: CondicionRegla;
  tipoMantenimiento: TipoMantenimiento;
  intervencionSugerida: string;
  reglaPrioridad: ReglaPrioridad;
  activa?: boolean;
}

export interface CambiarEstadoReglaRequest {
  activa: boolean;
}
