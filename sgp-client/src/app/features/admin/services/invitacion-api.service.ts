import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CrearInvitacionRequest,
  EstadoInvitacion,
  InvitacionResponse,
  PaginaInvitaciones,
} from '../models/invitacion.models';

@Injectable({ providedIn: 'root' })
export class InvitacionApiService {
  private readonly http = inject(HttpClient);

  invitar(request: CrearInvitacionRequest): Observable<InvitacionResponse> {
    return this.http.post<InvitacionResponse>('/api/v1/invitaciones', request);
  }

  listar(
    estado: EstadoInvitacion | null,
    pagina = 0,
    tamanio = 10,
  ): Observable<PaginaInvitaciones> {
    if (
      !Number.isInteger(pagina) ||
      pagina < 0 ||
      !Number.isInteger(tamanio) ||
      tamanio < 1 ||
      tamanio > 100
    ) {
      throw new RangeError(
        'La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.',
      );
    }
    const params: Record<string, string | number> = { pagina, tamanio };
    if (estado) params['estado'] = estado;
    return this.http.get<PaginaInvitaciones>('/api/v1/invitaciones', { params });
  }

  reenviar(id: string): Observable<InvitacionResponse> {
    return this.http.post<InvitacionResponse>(
      `/api/v1/invitaciones/${encodeURIComponent(id)}/reenvio`,
      null,
    );
  }
}
