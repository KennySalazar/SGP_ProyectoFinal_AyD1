export interface AcompananteRequest {
  id?: string | null;
  usuarioId?: string | null;
  nombre: string;
  colegiado?: string | null;
  numeroColegiado?: string | null;
}

export interface AcompananteResponse {
  id: string;
  usuarioId?: string | null;
  nombre: string;
  colegiado?: string | null;
  numeroColegiado?: string | null;
}

export interface IniciarInspeccionRequest {
  id?: string | null;
  puenteId: string;
  fechaInspeccion: string;
  dispositivoId: string;
  nombreAutor?: string | null;
  latitud?: number | null;
  longitud?: number | null;
  precisionGpsM?: number | null;
  confirmarDatosPermanentes: boolean;
  acompanantes?: AcompananteRequest[] | null;
}

export interface DatosPermanentesResponse {
  longitudM?: number | null;
  numeroTramos?: number | null;
  anchoRodaduraM?: number | null;
  anchoAceraDerechaM?: number | null;
  anchoAceraIzquierdaM?: number | null;
  alturaLibreSuperiorM?: number | null;
  alturaLibreSobreCauceM?: number | null;
  tipologiaPuente?: string | null;
  tipoCruce?: string | null;
  numeroViasPorSentido?: number | null;
  materialSuperestructura?: string | null;
  materialSubestructura?: string | null;
  cargaDiseno?: string | null;
  anioConstruccion?: number | null;
  rutaPavimentada?: string | null;
  alineamientoHorizontal?: string | null;
  esviaje?: boolean | null;
  tipoCuerpoAgua?: string | null;
  nombreRio?: string | null;
  encauzamiento?: string | null;
  alumbradoExiste?: boolean | null;
  drenajesAledanosExisten?: boolean | null;
}

export interface IniciarInspeccionResponse {
  id: string;
  puenteId: string;
  codigoPuente: string;
  nombrePuente: string;
  versionFormularioId: string;
  codigoVersionFormulario: string;
  autorId: string;
  rolAutor: string;
  nombreAutor: string;
  estado: string;
  fechaInspeccion: string;
  dispositivoId: string;
  latitudInicio?: number | null;
  longitudInicio?: number | null;
  precisionGpsM?: number | null;
  distanciaAlPuenteM?: number | null;
  advertenciaGps: boolean;
  mensajeAdvertenciaGps?: string | null;
  datosPermanentesConfirmados: boolean;
  datosPermanentes?: DatosPermanentesResponse | null;
  acompanantes: AcompananteResponse[];
  creadoEn: string;
}

export interface ProgresoSeccionResponse {
  seccion: string;
  titulo: string;
  camposRequeridos: number;
  camposCompletados: number;
  porcentaje: number;
  completada: boolean;
}

export interface ProgresoBorradorResponse {
  totalCamposRequeridos: number;
  totalCamposCompletados: number;
  porcentajeTotal: number;
  secciones: ProgresoSeccionResponse[];
}

export interface IndiceCondicionPreliminarResponse {
  indice: number;
  estado: string;
  etiqueta: string;
}

export interface GuardarBorradorRequest {
  version?: number | null;
  datos?: Record<string, unknown> | null;
  identificacionCapturada?: Record<string, unknown> | null;
  fechaInspeccion?: string | null;
  acompanantes?: AcompananteRequest[] | null;
}

export interface InspeccionBorradorResponse {
  id: string;
  puenteId: string;
  codigoPuente: string;
  nombrePuente: string;
  versionFormularioId: string;
  codigoVersionFormulario: string;
  autorId: string;
  rolAutor: string;
  nombreAutor: string;
  estado: string;
  fechaInspeccion: string;
  dispositivoId: string;
  datosPermanentes?: DatosPermanentesResponse | null;
  datos?: Record<string, unknown> | null;
  identificacionCapturada?: Record<string, unknown> | null;
  acompanantes: AcompananteResponse[];
  version: number;
  ultimaActividadEn?: string | null;
  creadoEn: string;
  actualizadoEn?: string | null;
  progreso: ProgresoBorradorResponse;
  icPreliminar?: IndiceCondicionPreliminarResponse | null;
}

export type EstadoInspeccion =
  'BORRADOR' | 'ENVIADA' | 'EN_REVISION' | 'CAMBIOS_SOLICITADOS' | 'PUBLICADA' | 'RECHAZADA';

export interface InspeccionResumenResponse {
  id: string;
  puenteId: string;
  codigoPuente: string;
  nombrePuente: string;
  departamentoNombre?: string | null;
  municipioNombre?: string | null;
  versionFormularioId: string;
  codigoVersionFormulario: string;
  autorId: string;
  rolAutorCreacion: string;
  nombreAutorCapturado: string;
  estado: EstadoInspeccion;
  tipo: string;
  fechaInspeccion: string;
  porcentajeAvance?: number | null;
  ultimaActividadEn?: string | null;
  creadoEn: string;
  actualizadoEn?: string | null;
  /** En una corrección: la inspección publicada que corrige y el motivo (HU-027). */
  supersedeAId?: string | null;
  motivoCorreccion?: string | null;
  /** Corrección publicada que supera a esta inspección; nulo si sigue vigente. */
  superadaPorId?: string | null;
}

export interface InspeccionPublicadaDetalleResponse {
  id: string;
  puenteId: string;
  puenteNombre: string;
  versionFormularioId: string;
  versionFormularioCodigo: string;
  esquemaFormulario: Record<string, unknown> | null;
  autorId: string;
  nombreAutor: string;
  rolAutor: string;
  acompanantes: AcompananteResponse[];
  estado: 'PUBLICADA';
  fechaInspeccion: string;
  dispositivoId: string;
  identificacionCapturada: Record<string, unknown> | null;
  datos: Record<string, unknown> | null;
  datosPermanentes: DatosPermanentesResponse | null;
  creadoEn: string;
  enviadoEn: string | null;
  publicadoEn: string;
  ic: {
    indiceCalculado: number;
    estadoCalculado: 'BUENO' | 'REGULAR' | 'MALO';
    estadoConfirmado: 'BUENO' | 'REGULAR' | 'MALO';
    justificacionAjuste: string | null;
  } | null;
  supersedeAId: string | null;
  motivoCorreccion: string | null;
  superadaPorId: string | null;
  vigencia: 'VIGENTE' | 'SUPERADA';
}
