import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CatedraticoResponse,
  ActualizarCursoRequest,
  CrearCursoRequest,
  CursoResponse,
  PaginaResponse,
} from '../models/curso.models';

@Injectable({ providedIn: 'root' })
export class CursoApiService {
  private readonly http = inject(HttpClient);

  listar(): Observable<PaginaResponse<CursoResponse>> {
    return this.http.get<PaginaResponse<CursoResponse>>('/api/v1/cursos', {
      params: { pagina: 0, tamanio: 100 },
    });
  }

  listarCatedraticos(): Observable<CatedraticoResponse[]> {
    return this.http.get<CatedraticoResponse[]>('/api/v1/cursos/catedraticos');
  }

  crear(request: CrearCursoRequest): Observable<CursoResponse> {
    return this.http.post<CursoResponse>('/api/v1/cursos', request);
  }

  actualizar(id: string, request: ActualizarCursoRequest): Observable<CursoResponse> {
    return this.http.put<CursoResponse>(`/api/v1/cursos/${encodeURIComponent(id)}`, request);
  }

  finalizar(id: string): Observable<CursoResponse> {
    return this.http.patch<CursoResponse>(`/api/v1/cursos/${encodeURIComponent(id)}/finalizar`, {});
  }
}
