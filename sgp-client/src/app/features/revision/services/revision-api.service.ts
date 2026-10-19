import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import {
  FiltrosBandejaRevision,
  PaginaBorradoresEstudiante,
  PaginaRevisiones,
  ResultadoInicioRevision,
} from '../models/revision.models';

@Injectable({ providedIn: 'root' })
export class RevisionApiService {
  private readonly http = inject(HttpClient);

  readonly pendientes = signal(0);

  listar(filtros: FiltrosBandejaRevision = {}): Observable<PaginaRevisiones> {
    const params: Record<string, string | number> = {
      pagina: filtros.pagina ?? 0,
      tamanio: filtros.tamanio ?? 20,
    };
    if (filtros.estado) params['estado'] = filtros.estado;
    if (filtros.puente?.trim()) params['puente'] = filtros.puente.trim();
    if (filtros.estudiante?.trim()) params['estudiante'] = filtros.estudiante.trim();
    if (filtros.curso?.trim()) params['curso'] = filtros.curso.trim();
    if (filtros.fechaDesde) params['fechaDesde'] = filtros.fechaDesde;
    if (filtros.fechaHasta) params['fechaHasta'] = filtros.fechaHasta;
    return this.http.get<PaginaRevisiones>('/api/v1/revisiones/bandeja', { params });
  }

  cargarContador(): Observable<{ pendientes: number }> {
    return this.http
      .get<{ pendientes: number }>('/api/v1/revisiones/pendientes/contador')
      .pipe(tap((respuesta) => this.pendientes.set(respuesta.pendientes)));
  }

  listarBorradores(pagina = 0, tamanio = 20): Observable<PaginaBorradoresEstudiante> {
    return this.http.get<PaginaBorradoresEstudiante>('/api/v1/revisiones/borradores', {
      params: { pagina, tamanio },
    });
  }

  iniciar(revisionId: string): Observable<ResultadoInicioRevision> {
    return this.http.post<ResultadoInicioRevision>(
      `/api/v1/revisiones/${encodeURIComponent(revisionId)}/iniciar`,
      null,
    );
  }
}
