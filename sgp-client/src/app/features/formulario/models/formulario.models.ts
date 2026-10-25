import { AprobacionCalibracion } from './aprobacion-calibracion.models';
export type EstadoVersionFormulario = 'BORRADOR' | 'PUBLICADA';
export type TipoMapeoCampo = 'EQUIVALENTE' | 'RENOMBRADO' | 'NUEVO' | 'ELIMINADO';
export interface VersionFormularioResumen {
  id: string;
  codigo: string;
  estado: EstadoVersionFormulario;
  activa: boolean;
  versionAlgoritmoIc: string;
  versionAnteriorId?: string | null;
  versionAnteriorCodigo?: string | null;
  creadoEn: string;
  publicadoEn?: string | null;
}
export interface ConfiguracionIcElemento {
  id: string;
  elementoRef: string;
  peso: number;
  descripcion?: string | null;
  creadoEn: string;
}
export interface MapeoCampo {
  id: string;
  versionOrigenId: string;
  versionDestinoId: string;
  elementoRefOrigen?: string | null;
  elementoRefDestino?: string | null;
  tipo: TipoMapeoCampo;
  observacion?: string | null;
  creadoPorId?: string | null;
  creadoEn: string;
}
export interface VersionFormulario {
  id: string;
  codigo: string;
  estado: EstadoVersionFormulario;
  activa: boolean;
  esquema: Record<string, unknown>;
  vinculosAlmacenamiento: Record<string, unknown>;
  reglasEvaluacionIc: Record<string, unknown>;
  rotuloCalibracion?: string | null;
  aprobacionCalibracion?: AprobacionCalibracion | null;
  hashContenido?: string | null;
  versionAlgoritmoIc: string;
  versionAnteriorId?: string | null;
  versionAnteriorCodigo?: string | null;
  creadoPorId: string;
  publicadoPorId?: string | null;
  publicadoEn?: string | null;
  calibradoPorId?: string | null;
  calibradoEn?: string | null;
  creadoEn: string;
  actualizadoEn: string;
  version: number;
  pesos: ConfiguracionIcElemento[];
  mapeos: MapeoCampo[];
}
export interface FormularioActivo {
  id: string;
  codigo: string;
  version: number;
  esquema: Record<string, unknown>;
  hashContenido: string;
  publicadoEn: string;
}
export interface AnalisisMapeo {
  versionOrigenId?: string | null;
  versionOrigenCodigo?: string | null;
  versionDestinoId: string;
  versionDestinoCodigo: string;
  camposOrigen: string[];
  camposDestino: string[];
  mapeosDeclarados: MapeoCampo[];
  camposOrigenSinMapeo: string[];
  camposDestinoSinMapeo?: string[];
  mapeoCompleto: boolean;
}
export interface CrearVersionRequest {
  codigo: string;
  versionBaseId?: string | null;
  cargarPlantillaInicial?: boolean | null;
  esquema?: Record<string, unknown> | null;
  vinculosAlmacenamiento?: Record<string, unknown> | null;
  reglasEvaluacionIc?: Record<string, unknown> | null;
}
export interface ActualizarBorradorRequest {
  codigo?: string | null;
  esquema: Record<string, unknown>;
  vinculosAlmacenamiento?: Record<string, unknown> | null;
  reglasEvaluacionIc?: Record<string, unknown> | null;
}
export interface ConfiguracionIcElementoRequest {
  elementoRef: string;
  peso: number;
  descripcion?: string | null;
}
export interface DeclaracionMapeoRequest {
  elementoRefOrigen?: string | null;
  elementoRefDestino?: string | null;
  tipo: TipoMapeoCampo;
  observacion?: string | null;
}
export interface PublicarVersionRequest {
  mapeos?: DeclaracionMapeoRequest[] | null;
}
export type TipoDatoVisual =
  'string' | 'multiline' | 'integer' | 'number' | 'boolean' | 'select' | 'date';
export interface PreguntaVisual {
  clave: string;
  titulo: string;
  descripcion?: string;
  tipo: TipoDatoVisual;
  obligatorio: boolean;
  soloLectura?: boolean;
  opciones?: string[];
  longitudMinima?: number;
  longitudMaxima?: number;
  valorMinimo?: number;
  valorMaximo?: number;
  vinculo?: string;
}
export interface SeccionVisual {
  clave: string;
  titulo: string;
  descripcion?: string;
  esRepetible?: boolean;
  preguntas: PreguntaVisual[];
}
export interface VinculoVisual {
  id: string;
  campoRef: string;
  columnaDestino: string;
  tipo?: string;
  descripcion?: string;
}
export interface ConfigurarEscalaIcRequest {
  valores: {
    NO_PRESENTA: number;
    LEVE: number;
    MODERADO: number;
    SEVERO: number;
  };
}
