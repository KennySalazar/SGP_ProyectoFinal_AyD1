import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CrearSolicitudAltaPuenteRequest,
  EstadoSolicitudAltaPuente,
  PaginaResponse,
  SolicitudAltaPuenteResponse,
  SolicitudRevisionDetalleResponse,
  SolicitudRevisionResponse,
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

  obtenerMia(id: string): Observable<SolicitudAltaPuenteResponse> {
    return this.http.get<SolicitudAltaPuenteResponse>(
      `/api/v1/solicitudes-puente/mias/${encodeURIComponent(id)}`,
    );
  }

  listarParaRevision(
    estado: EstadoSolicitudAltaPuente = 'PENDIENTE',
    pagina = 0,
    tamanio = 20,
  ): Observable<PaginaResponse<SolicitudRevisionResponse>> {
    return this.http.get<PaginaResponse<SolicitudRevisionResponse>>('/api/v1/solicitudes-puente', {
      params: { estado, pagina, tamanio },
    });
  }

  obtenerParaRevision(id: string): Observable<SolicitudRevisionDetalleResponse> {
    return this.http.get<SolicitudRevisionDetalleResponse>(
      `/api/v1/solicitudes-puente/${encodeURIComponent(id)}`,
    );
  }

  aprobar(id: string, confirmarCercania: boolean): Observable<SolicitudAltaPuenteResponse> {
    return this.http.post<SolicitudAltaPuenteResponse>(
      `/api/v1/solicitudes-puente/${encodeURIComponent(id)}/aprobar`,
      { confirmarCercania },
    );
  }

  rechazar(id: string, motivo: string): Observable<SolicitudAltaPuenteResponse> {
    return this.http.post<SolicitudAltaPuenteResponse>(
      `/api/v1/solicitudes-puente/${encodeURIComponent(id)}/rechazar`,
      { motivo },
    );
  }
}
