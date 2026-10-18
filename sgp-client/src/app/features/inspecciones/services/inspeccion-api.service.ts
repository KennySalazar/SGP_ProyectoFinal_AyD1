import { FotografiaResponse, MetadatosFotografia } from '../models/fotografia.models';
import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import {
  DatosPermanentesResponse,
  GuardarBorradorRequest,
  IniciarInspeccionRequest,
  IniciarInspeccionResponse,
  InspeccionBorradorResponse,
  InspeccionResumenResponse,
  InspeccionPublicadaDetalleResponse,
  EstadoInspeccion,
} from '../models/inspeccion.models';
import { PaginaResponse } from '../../puentes/models/puente.models';

@Injectable({ providedIn: 'root' })
export class InspeccionApiService {
  private readonly http = inject(HttpClient);

  iniciarInspeccion(
    request: IniciarInspeccionRequest,
    idempotencyKey?: string,
  ): Observable<IniciarInspeccionResponse> {
    const headers: Record<string, string> = {};
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    return this.http.post<IniciarInspeccionResponse>('/api/v1/inspecciones', request, {
      headers,
    });
  }

  obtenerDatosPermanentes(puenteId: string): Observable<DatosPermanentesResponse | null> {
    return this.http
      .get<DatosPermanentesResponse>(`/api/v1/inspecciones/puentes/${puenteId}/datos-permanentes`, {
        observe: 'response',
      })
      .pipe(map((res) => (res.status === 204 ? null : res.body)));
  }

  adjuntarFotografia(
    inspeccionId: string,
    blob: Blob,
    metadatos: MetadatosFotografia,
    clave: string,
  ): Observable<FotografiaResponse> {
    const datos = new FormData();
    datos.append('metadatos', new Blob([JSON.stringify(metadatos)], { type: 'application/json' }));
    datos.append('archivo', blob, `${metadatos.id}.jpg`);
    return this.http.post<FotografiaResponse>(
      `/api/v1/inspecciones/${inspeccionId}/fotografias`,
      datos,
      { headers: { 'Idempotency-Key': clave } },
    );
  }
  listarFotografias(inspeccionId: string): Observable<FotografiaResponse[]> {
    return this.http.get<FotografiaResponse[]>(`/api/v1/inspecciones/${inspeccionId}/fotografias`);
  }
  retirarFotografia(inspeccionId: string, fotoId: string): Observable<void> {
    return this.http.delete<void>(
      `/api/v1/inspecciones/${encodeURIComponent(inspeccionId)}/fotografias/${encodeURIComponent(fotoId)}`,
    );
  }
  contenidoFotografia(inspeccionId: string, fotoId: string, miniatura = false): Observable<Blob> {
    return this.http.get(
      `/api/v1/inspecciones/${inspeccionId}/fotografias/${fotoId}/${miniatura ? 'miniatura' : 'contenido'}`,
      { responseType: 'blob' },
    );
  }

  obtenerPublicada(id: string): Observable<InspeccionPublicadaDetalleResponse> {
    return this.http.get<InspeccionPublicadaDetalleResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/publicada`,
    );
  }

  obtenerBorrador(id: string): Observable<InspeccionBorradorResponse> {
    return this.http.get<InspeccionBorradorResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/borrador`,
    );
  }

  guardarBorrador(
    id: string,
    request: GuardarBorradorRequest,
  ): Observable<InspeccionBorradorResponse> {
    return this.http.put<InspeccionBorradorResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/borrador`,
      request,
    );
  }

  listarMisInspecciones(
    pagina = 0,
    tamanio = 20,
    estado?: EstadoInspeccion,
  ): Observable<PaginaResponse<InspeccionResumenResponse>> {
    const params: Record<string, string | number> = { pagina, tamanio };
    if (estado) params['estado'] = estado;
    return this.http.get<PaginaResponse<InspeccionResumenResponse>>('/api/v1/inspecciones', {
      params,
    });
  }

  /** El Estudiante envía su inspección completa a revisión (HU-026). */
  enviar(id: string): Observable<InspeccionResumenResponse> {
    return this.http.post<InspeccionResumenResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/enviar`,
      null,
    );
  }

  /** El Catedrático o Profesional externo verificado publica sin revisión previa (HU-026). */
  publicar(id: string): Observable<InspeccionResumenResponse> {
    return this.http.post<InspeccionResumenResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/publicar`,
      null,
    );
  }

  /** Crea una corrección en borrador de una inspección publicada (HU-027). */
  crearCorreccion(id: string, motivo: string): Observable<InspeccionResumenResponse> {
    return this.http.post<InspeccionResumenResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/correcciones`,
      { motivo },
    );
  }

  eliminarBorrador(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/inspecciones/${encodeURIComponent(id)}`);
  }
}
