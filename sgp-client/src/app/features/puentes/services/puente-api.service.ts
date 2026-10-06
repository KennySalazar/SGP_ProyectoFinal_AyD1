import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ActualizarPuenteRequest,
  CrearPuenteRequest,
  ConsultaCatalogoPuentes,
  DepartamentoResponse,
  MunicipioResponse,
  PaginaResponse,
  PuenteCatalogoResponse,
  PuenteResponse,
  UbicacionTerritorialResponse,
} from '../models/puente.models';

@Injectable({ providedIn: 'root' })
export class PuenteApiService {
  private readonly http = inject(HttpClient);

  resolverUbicacion(latitud: number, longitud: number): Observable<UbicacionTerritorialResponse> {
    return this.http.get<UbicacionTerritorialResponse>('/api/v1/catalogos/ubicacion', {
      params: { latitud, longitud },
    });
  }

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
    const params: Record<string, string | number | boolean> = { pagina, tamanio };
    if (consulta.departamentoId) params['departamentoId'] = consulta.departamentoId;
    if (consulta.estado) params['estado'] = consulta.estado;
    if (consulta.activo !== undefined) params['activo'] = consulta.activo;
    if (consulta.todos) params['todos'] = true;
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

  darDeBaja(id: string, motivo: string): Observable<PuenteResponse> {
    return this.http.post<PuenteResponse>(`/api/v1/puentes/${encodeURIComponent(id)}/baja`, {
      motivo,
    });
  }

  reactivar(id: string): Observable<PuenteResponse> {
    return this.http.post<PuenteResponse>(
      `/api/v1/puentes/${encodeURIComponent(id)}/reactivar`,
      {},
    );
  }

  obtenerPorId(id: string): Observable<PuenteResponse> {
    return this.http.get<PuenteResponse>(`/api/v1/puentes/${encodeURIComponent(id)}`);
  }

  actualizar(id: string, request: ActualizarPuenteRequest): Observable<PuenteResponse> {
    return this.http.put<PuenteResponse>(`/api/v1/puentes/${encodeURIComponent(id)}`, request);
  }
}
