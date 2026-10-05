import { ProblemDetails } from '../../../core/models/problem-details';

export interface PaginaResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface DepartamentoResponse {
  id: string;
  codigoIne: string;
  nombre: string;
}

export interface MunicipioResponse {
  id: string;
  departamentoId: string;
  codigoIne: string;
  nombre: string;
}

export interface CrearPuenteRequest {
  nombre: string;
  departamentoId: string;
  municipioId: string;
  ruta: string;
  kilometraje: number | null;
  latitud: number;
  longitud: number;
  confirmarCercania: boolean;
}

export interface UtmResponse {
  zona: number;
  hemisferio: string;
  epsg: number;
  este: number;
  norte: number;
}

export interface PuenteResponse {
  id: string;
  codigo: string;
  nombre: string;
  departamento: DepartamentoResponse;
  municipio: MunicipioResponse;
  ruta: string;
  kilometraje: number | null;
  latitud: number;
  longitud: number;
  utm: UtmResponse;
  activo: boolean;
  estadoActual: string;
  indiceCondicionActual: number | null;
  fechaUltimaInspeccion: string | null;
  creadoEn: string;
}

export type EstadoPuente = 'Bueno' | 'Regular' | 'Malo' | 'Sin evaluar';

export interface PuenteCatalogoResponse {
  id: string;
  codigo: string;
  nombre: string;
  departamento: DepartamentoResponse;
  municipio: MunicipioResponse;
  latitud: number | null;
  longitud: number | null;
  activo: boolean;
  estadoActual: EstadoPuente;
}

export interface ConsultaCatalogoPuentes {
  departamentoId?: string;
  estado?: EstadoPuente;
  pagina?: number;
  tamanio?: number;
}

export interface PuenteCercanoResponse {
  id: string;
  codigo: string;
  nombre: string;
  activo: boolean;
  distanciaMetros: number;
}

export interface PuenteProblemDetails extends ProblemDetails {
  code?: string;
  requiereConfirmacion?: boolean;
  puentesCercanos?: PuenteCercanoResponse[];
  totalPuentesCercanos?: number;
  totalPaginas?: number;
  pagina?: number;
  tamanoPagina?: number;
}
