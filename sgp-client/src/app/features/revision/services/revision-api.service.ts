import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import {
  FiltrosBandejaRevision,
  PaginaObservacionesRecibidas,
  RevisionDetalleResponse,
  CrearObservacionRequest,
  ObservacionResponse,
  ListadoObservacionesResponse,
  ContadorObservacionesResponse,
  ObservacionGeneralResponse,
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
  recibidas(pagina = 0): Observable<PaginaObservacionesRecibidas> {
    return this.http.get<PaginaObservacionesRecibidas>('/api/v1/observaciones/recibidas', {
      params: { pagina, tamanio: 20 },
    });
  }
  detalle(revisionId: string): Observable<RevisionDetalleResponse> {
    return this.http.get<RevisionDetalleResponse>(this.ruta(revisionId));
  }
  observaciones(revisionId: string): Observable<ListadoObservacionesResponse> {
    return this.http.get<ListadoObservacionesResponse>(this.ruta(revisionId) + '/observaciones');
  }
  observacion(revisionId: string, id: string): Observable<ObservacionResponse> {
    return this.http.get<ObservacionResponse>(
      this.ruta(revisionId) + '/observaciones/' + encodeURIComponent(id),
    );
  }
  crearObservacion(
    revisionId: string,
    request: CrearObservacionRequest,
  ): Observable<ObservacionResponse> {
    return this.http.post<ObservacionResponse>(this.ruta(revisionId) + '/observaciones', request);
  }
  editarObservacion(
    revisionId: string,
    id: string,
    texto: string,
  ): Observable<ObservacionResponse> {
    return this.http.put<ObservacionResponse>(
      this.ruta(revisionId) + '/observaciones/' + encodeURIComponent(id),
      { texto },
    );
  }
  eliminarObservacion(revisionId: string, id: string): Observable<void> {
    return this.http.delete<void>(
      this.ruta(revisionId) + '/observaciones/' + encodeURIComponent(id),
    );
  }
  contadorObservaciones(revisionId: string): Observable<ContadorObservacionesResponse> {
    return this.http.get<ContadorObservacionesResponse>(
      this.ruta(revisionId) + '/observaciones/contador',
    );
  }
  observacionGeneral(revisionId: string): Observable<ObservacionGeneralResponse> {
    return this.http.get<ObservacionGeneralResponse>(
      this.ruta(revisionId) + '/observaciones/general',
    );
  }
  guardarGeneral(revisionId: string, texto: string | null): Observable<ObservacionGeneralResponse> {
    return this.http.put<ObservacionGeneralResponse>(
      this.ruta(revisionId) + '/observaciones/general',
      { texto },
    );
  }
  private ruta(revisionId: string): string {
    return '/api/v1/revisiones/' + encodeURIComponent(revisionId);
  }
}
