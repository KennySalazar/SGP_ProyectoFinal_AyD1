import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CrearSolicitudAltaPuenteRequest,
  PaginaResponse,
  SolicitudAltaPuenteResponse,
} from '../models/puente.models';

@Injectable({ providedIn: 'root' })
export class SolicitudAltaPuenteApiService {
  private readonly http = inject(HttpClient);

  crear(request: CrearSolicitudAltaPuenteRequest): Observable<SolicitudAltaPuenteResponse> {
    return this.http.post<SolicitudAltaPuenteResponse>('/api/v1/solicitudes-puente', request);
  }

  listarMias(pagina = 0, tamanio = 20): Observable<PaginaResponse<SolicitudAltaPuenteResponse>> {
    return this.http.get<PaginaResponse<SolicitudAltaPuenteResponse>>(
      '/api/v1/solicitudes-puente/mias',
      { params: { pagina, tamanio } },
    );
  }
}
