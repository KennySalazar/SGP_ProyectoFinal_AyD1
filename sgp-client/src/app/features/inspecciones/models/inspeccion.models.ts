export interface AcompananteRequest {
  id?: string | null;
  nombre: string;
  colegiado?: string | null;
}

export interface AcompananteResponse {
  id: string;
  nombre: string;
  colegiado?: string | null;
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
