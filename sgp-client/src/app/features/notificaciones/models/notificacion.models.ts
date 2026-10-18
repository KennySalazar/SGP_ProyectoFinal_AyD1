export interface NotificacionResponse {
  id: string;
  tipo: string;
  titulo: string;
  mensaje: string;
  entidad?: string;
  entidadId?: string;
  claveEvento: string;
  leidoEn?: string;
  creadoEn: string;
  leido: boolean;
}

export interface ResumenNotificacionesResponse {
  noLeidas: number;
}

export interface MarcarTodasLeidasResponse {
  marcadas: number;
}

export interface PaginaNotificacionesResponse {
  content: NotificacionResponse[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
