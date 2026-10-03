import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CrearPuenteRequest,
  DepartamentoResponse,
  MunicipioResponse,
  PaginaResponse,
  PuenteResponse,
} from '../models/puente.models';

@Injectable({ providedIn: 'root' })
export class PuenteApiService {
  private readonly http = inject(HttpClient);

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
}
