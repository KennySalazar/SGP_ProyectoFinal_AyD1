import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AsignacionPuenteResponse,
  CrearPuenteRequest,
  ConsultaCatalogoPuentes,
  DepartamentoResponse,
  EstudianteAsignableResponse,
  MunicipioResponse,
  PaginaResponse,
  PuenteCatalogoResponse,
  PuenteAsignableResponse,
  PuenteResponse,
} from '../models/puente.models';

@Injectable({ providedIn: 'root' })
export class PuenteApiService {
  private readonly http = inject(HttpClient);

  listarCatalogo(
    consulta: ConsultaCatalogoPuentes = {},
  ): Observable<PaginaResponse<PuenteCatalogoResponse>> {
    const pagina = consulta.pagina ?? 0;
    const tamanio = consulta.tamanio ?? 20;
    if (
      !Number.isInteger(pagina) ||
      pagina < 0 ||
      !Number.isInteger(tamanio) ||
      tamanio < 1 ||
      tamanio > 100
    ) {
      throw new RangeError(
        'La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.',
      );
    }
    const params: Record<string, string | number> = { pagina, tamanio };
    if (consulta.departamentoId) params['departamentoId'] = consulta.departamentoId;
    if (consulta.estado) params['estado'] = consulta.estado;
    return this.http.get<PaginaResponse<PuenteCatalogoResponse>>('/api/v1/puentes', { params });
  }

  listarDepartamentos(pagina = 0, tamanio = 100): Observable<PaginaResponse<DepartamentoResponse>> {
    return this.http.get<PaginaResponse<DepartamentoResponse>>('/api/v1/catalogos/departamentos', {
      params: { pagina, tamanio },
    });
  }

  listarMunicipios(
    departamentoId: string,
    pagina = 0,
    tamanio = 100,
  ): Observable<PaginaResponse<MunicipioResponse>> {
    return this.http.get<PaginaResponse<MunicipioResponse>>(
      `/api/v1/catalogos/departamentos/${encodeURIComponent(departamentoId)}/municipios`,
      { params: { pagina, tamanio } },
    );
  }

  registrar(request: CrearPuenteRequest): Observable<PuenteResponse> {
    return this.http.post<PuenteResponse>('/api/v1/puentes', request);
  }

  listarEstudiantesAsignables(): Observable<EstudianteAsignableResponse[]> {
    return this.http.get<EstudianteAsignableResponse[]>('/api/v1/asignaciones-puentes/estudiantes');
  }

  listarPuentesAsignables(): Observable<PuenteAsignableResponse[]> {
    return this.http.get<PuenteAsignableResponse[]>('/api/v1/asignaciones-puentes/puentes');
  }

  listarAsignaciones(): Observable<AsignacionPuenteResponse[]> {
    return this.http.get<AsignacionPuenteResponse[]>('/api/v1/asignaciones-puentes');
  }

  asignarPuente(request: {
    cursoEstudianteId: string;
    puenteId: string;
  }): Observable<{ message: string }> {
    return this.http.post<{ message: string }>('/api/v1/asignaciones-puentes', request);
  }

  revocarAsignacion(asignacionId: string, motivo: string): Observable<{ message: string }> {
    return this.http.patch<{ message: string }>(
      `/api/v1/asignaciones-puentes/${encodeURIComponent(asignacionId)}/revocar`,
      { motivo },
    );
  }

  listarMisPuentesAsignados(): Observable<AsignacionPuenteResponse[]> {
    return this.http.get<AsignacionPuenteResponse[]>('/api/v1/asignaciones-puentes/mis-puentes');
  }
}
