import {
  FotografiaResponse,
  MetadatosFotografia,
  ValidacionEvidenciaResponse,
} from '../models/fotografia.models';
import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import {
  DatosPermanentesResponse,
  IniciarInspeccionRequest,
  IniciarInspeccionResponse,
} from '../models/inspeccion.models';

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
  consultarEvidenciaFotografica(inspeccionId: string): Observable<ValidacionEvidenciaResponse> {
    return this.http.get<ValidacionEvidenciaResponse>(
      `/api/v1/inspecciones/${inspeccionId}/evidencia-fotografica`,
    );
  }
  retirarFotografia(inspeccionId: string, fotoId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/inspecciones/${inspeccionId}/fotografias/${fotoId}`);
  }
  contenidoFotografia(inspeccionId: string, fotoId: string, miniatura = false): Observable<Blob> {
    return this.http.get(
      `/api/v1/inspecciones/${inspeccionId}/fotografias/${fotoId}/${miniatura ? 'miniatura' : 'contenido'}`,
      { responseType: 'blob' },
    );
  }
}
