import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ERROR_GESTIONADO_LOCALMENTE } from '../../../core/interceptors/problem.interceptor';
import {
  MarcarTodasLeidasResponse,
  NotificacionResponse,
  PaginaNotificacionesResponse,
  ResumenNotificacionesResponse,
} from '../models/notificacion.models';

@Injectable({ providedIn: 'root' })
export class NotificacionApiService {
  private readonly http = inject(HttpClient);

  listar(soloNoLeidos = false, pagina = 0, tamanio = 20): Observable<PaginaNotificacionesResponse> {
    let params = new HttpParams()
      .set('pagina', pagina.toString())
      .set('tamanio', Math.min(100, Math.max(1, tamanio)).toString());
    if (soloNoLeidos) {
      params = params.set('soloNoLeidos', 'true');
    }
    return this.http.get<PaginaNotificacionesResponse>('/api/v1/notificaciones', { params });
  }

  obtenerResumen(): Observable<ResumenNotificacionesResponse> {
    return this.http.get<ResumenNotificacionesResponse>('/api/v1/notificaciones/resumen', {
      context: new HttpContext().set(ERROR_GESTIONADO_LOCALMENTE, true),
    });
  }

  obtener(id: string): Observable<NotificacionResponse> {
    return this.http.get<NotificacionResponse>(`/api/v1/notificaciones/${encodeURIComponent(id)}`);
  }

  marcarLeida(id: string): Observable<NotificacionResponse> {
    return this.http.patch<NotificacionResponse>(
      `/api/v1/notificaciones/${encodeURIComponent(id)}/leer`,
      {},
    );
  }

  marcarTodasLeidas(): Observable<MarcarTodasLeidasResponse> {
    return this.http.post<MarcarTodasLeidasResponse>(
      '/api/v1/notificaciones/marcar-todas-leidas',
      {},
    );
  }
}
