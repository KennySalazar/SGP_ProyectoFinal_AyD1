import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { EstadoCalibracion } from '../../formulario/models/aprobacion-calibracion.models';

@Injectable({ providedIn: 'root' })
export class EstadoCalibracionPuenteApiService {
  private readonly http = inject(HttpClient);

  consultar(puenteId: string): Observable<EstadoCalibracion | null> {
    return this.http.get<EstadoCalibracion | null>(
      `/api/v1/puentes/${encodeURIComponent(puenteId)}/estado-calibracion`,
    );
  }
}
