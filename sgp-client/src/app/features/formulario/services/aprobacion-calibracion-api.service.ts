import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Subject, tap } from 'rxjs';
import { AprobacionCalibracion, EstadoCalibracion } from '../models/aprobacion-calibracion.models';

@Injectable({ providedIn: 'root' })
export class AprobacionCalibracionApiService {
  private readonly http = inject(HttpClient);
  private readonly cambios = new Subject<string>();
  readonly actualizada = this.cambios.asObservable();

  estado(id: string) {
    return this.http.get<EstadoCalibracion>(`${this.ruta(id)}/estado-calibracion`);
  }

  consultar(id: string) {
    return this.http.get<AprobacionCalibracion | null>(`${this.ruta(id)}/aprobacion-calibracion`);
  }

  aprobar(id: string, observaciones: string) {
    return this.http
      .post<AprobacionCalibracion>(`${this.ruta(id)}/aprobacion-calibracion`, {
        observaciones: observaciones.trim() || null,
      })
      .pipe(tap(() => this.cambios.next(id)));
  }

  private ruta(id: string) {
    return `/api/v1/formularios/${encodeURIComponent(id)}`;
  }
}
