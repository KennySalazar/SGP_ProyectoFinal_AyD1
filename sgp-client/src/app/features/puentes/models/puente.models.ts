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

export interface PuenteFormValores {
  nombre: string;
  departamentoId: string;
  municipioId: string;
  ruta: string;
  kilometraje: number | null;
  latitud: number;
  longitud: number;
}

export type PuenteFormInicial = Pick<
  PuenteFormValores,
  'nombre' | 'ruta' | 'kilometraje' | 'latitud' | 'longitud'
>;

export interface CrearPuenteRequest extends PuenteFormValores {
  confirmarCercania: boolean;
}

export interface CrearSolicitudAltaPuenteRequest extends PuenteFormValores {
  justificacion: string | null;
  confirmarCercania: boolean;
}

export type EstadoSolicitudAltaPuente = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA' | 'CANCELADA';

export interface SolicitudAltaPuenteResponse {
  id: string;
  nombre: string;
  departamento: DepartamentoResponse;
  municipio: MunicipioResponse;
  ruta: string;
  kilometraje: number | null;
  latitud: number;
  longitud: number;
  justificacion: string | null;
  estado: EstadoSolicitudAltaPuente;
  motivoDecision: string | null;
  revisadoEn: string | null;
  puenteCreadoId: string | null;
  puenteCreadoCodigo: string | null;
  creadoEn: string;
}

export interface SolicitudRevisionResponse {
  solicitud: SolicitudAltaPuenteResponse;
  solicitanteEmail: string | null;
  revisadoPorEmail: string | null;
}

export interface SolicitudRevisionDetalleResponse extends SolicitudRevisionResponse {
  puentesCercanos: PuenteCercanoResponse[];
  totalPuentesCercanos: number;
}

export interface ActualizarPuenteRequest extends PuenteFormValores {
  confirmarCercania: boolean;
  codigo?: string;
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
  activo?: boolean;
  todos?: boolean;
  pagina?: number;
  tamanio?: number;
}

export interface PuenteCercanoResponse {
  id: string;
  codigo: string;
  nombre: string;
  activo: boolean;
  distanciaMetros: number;
  latitud: number;
  longitud: number;
}

export interface EstudianteAsignableResponse {
  cursoEstudianteId: string;
  estudianteId: string;
  estudianteEmail: string;
  cursoId: string;
  cursoNombre: string;
  periodo: string;
}

export interface PuenteAsignableResponse {
  id: string;
  codigo: string;
  nombre: string;
}

export interface AsignacionPuenteResponse {
  id: string;
  cursoEstudianteId: string;
  estudianteEmail: string;
  cursoNombre: string;
  periodo: string;
  puenteId: string;
  puenteCodigo: string;
  puenteNombre: string;
  asignadoEn: string;
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

export interface CandidatoTerritorialResponse {
  departamento: DepartamentoResponse;
  municipio: MunicipioResponse;
}

export interface UbicacionTerritorialResponse {
  latitud: number;
  longitud: number;
  zonaUtm: string;
  requiereSeleccion: boolean;
  candidatos: CandidatoTerritorialResponse[];
}
