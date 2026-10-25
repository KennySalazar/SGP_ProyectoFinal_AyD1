import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ComparacionIcResponse, ResultadoIcHistorialResponse } from '../models/recalculo-ic.models';

@Injectable({ providedIn: 'root' })
export class RecalculoIcApiService {
  private readonly http = inject(HttpClient);

  historial(id: string): Observable<ComparacionIcResponse> {
    return this.http.get<ComparacionIcResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/resultados-ic`,
    );
  }

  recalcular(id: string): Observable<ResultadoIcHistorialResponse> {
    return this.http.post<ResultadoIcHistorialResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/recalculos-ic`,
      null,
    );
  }
}
