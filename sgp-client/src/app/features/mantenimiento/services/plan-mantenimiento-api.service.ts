import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ActualizarPlanMantenimiento,
  PlanMantenimiento,
} from '../models/plan-mantenimiento.models';

@Injectable({ providedIn: 'root' })
export class PlanMantenimientoApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/planes-mantenimiento';

  listar(): Observable<PlanMantenimiento[]> {
    return this.http.get<PlanMantenimiento[]>(this.baseUrl);
  }

  obtenerPorPuente(puenteId: string): Observable<PlanMantenimiento> {
    return this.http.get<PlanMantenimiento>(
      `${this.baseUrl}/puente/${encodeURIComponent(puenteId)}`,
    );
  }

  actualizar(
    puenteId: string,
    request: ActualizarPlanMantenimiento,
  ): Observable<PlanMantenimiento> {
    return this.http.put<PlanMantenimiento>(
      `${this.baseUrl}/puente/${encodeURIComponent(puenteId)}`,
      request,
    );
  }
}
