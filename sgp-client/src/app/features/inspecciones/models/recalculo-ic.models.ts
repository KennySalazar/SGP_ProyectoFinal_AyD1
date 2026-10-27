import { EstadoCondicionIc } from './inspeccion.models';

export interface ResultadoIcHistorialResponse {
  id: string;
  inspeccionId: string;
  tipo: 'PUBLICACION' | 'RECALCULO';
  versionConfiguracionId: string;
  versionConfiguracionCodigo: string;
  indiceCalculado: number;
  estadoPorRango: EstadoCondicionIc;
  estadoCalculado: EstadoCondicionIc;
  estadoConfirmado: EstadoCondicionIc;
  justificacionAjuste: string | null;
  condicionesAnulacion: unknown;
  desglose: unknown;
  hashEntrada: string;
  versionAlgoritmo: string;
  solicitadoPorId: string;
  solicitadoPorNombre: string;
  calculadoEn: string;
  rotulo: string;
}

export interface ComparacionIcResponse {
  inspeccionId: string;
  original: ResultadoIcHistorialResponse;
  recalculos: ResultadoIcHistorialResponse[];
}
