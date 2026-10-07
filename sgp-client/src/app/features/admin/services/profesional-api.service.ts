import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaProfesionales, ProfesionalResponse } from '../models/profesional.models';

@Injectable({ providedIn: 'root' })
export class ProfesionalApiService {
  private readonly http = inject(HttpClient);

  listar(verificado: boolean | null, pagina = 0, tamanio = 10): Observable<PaginaProfesionales> {
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
    const params: Record<string, string | number | boolean> = { pagina, tamanio };
    if (verificado !== null) params['verificado'] = verificado;
    return this.http.get<PaginaProfesionales>('/api/v1/profesionales', { params });
  }

  verificarColegiado(usuarioId: string): Observable<ProfesionalResponse> {
    return this.http.post<ProfesionalResponse>(
      `/api/v1/profesionales/${encodeURIComponent(usuarioId)}/verificacion-colegiado`,
      null,
    );
  }
}
