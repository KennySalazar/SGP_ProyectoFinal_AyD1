export interface PaginaResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
}

export interface CatedraticoResponse {
  id: string;
  email: string;
}

export interface CursoResponse {
  id: string;
  nombre: string;
  periodo: string;
  catedratico: CatedraticoResponse;
  fechaInicio: string;
  fechaFin: string;
  estado: 'VIGENTE' | 'FINALIZADO';
}

export interface CrearCursoRequest {
  nombre: string;
  periodo: string;
  catedraticoId: string;
  fechaInicio: string;
  fechaFin: string;
}

export interface ActualizarCursoRequest {
  nombre: string;
  periodo: string;
  catedraticoId: string;
  fechaInicio: string;
  fechaFin: string;
}
