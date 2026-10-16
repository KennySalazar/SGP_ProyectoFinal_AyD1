import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import {
  DatosPermanentesResponse,
  GuardarBorradorRequest,
  IniciarInspeccionRequest,
  IniciarInspeccionResponse,
  InspeccionBorradorResponse,
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
}
