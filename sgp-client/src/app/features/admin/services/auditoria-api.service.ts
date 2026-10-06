import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ConsultaAuditoria, DetalleAuditoria, PaginaAuditoria } from '../models/auditoria.models';

@Injectable({ providedIn: 'root' })
export class AuditoriaApiService {
  private readonly http = inject(HttpClient);

  consultar(consulta: ConsultaAuditoria = {}): Observable<PaginaAuditoria> {
    const pagina = consulta.pagina ?? 0;
    const tamanio = consulta.tamanio ?? 20;
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
    if (consulta.usuarioEmail) params['usuarioEmail'] = consulta.usuarioEmail;
    if (consulta.desde) params['desde'] = consulta.desde;
    if (consulta.hasta) params['hasta'] = consulta.hasta;
    return this.http.get<PaginaAuditoria>('/api/v1/auditoria', { params });
  }

  detalle(id: string): Observable<DetalleAuditoria> {
    return this.http.get<DetalleAuditoria>(`/api/v1/auditoria/${encodeURIComponent(id)}`);
  }
}
