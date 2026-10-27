import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  EstimacionSalida,
  PaginaSalida,
  PreparacionSalida,
  SalidaRequest,
} from '../models/salida.models';

@Injectable({ providedIn: 'root' })
export class SalidaApiService {
  private readonly http = inject(HttpClient);
  buscar(busqueda: string, departamentoId: string, pagina: number) {
    let params = new HttpParams()
      .set('busqueda', busqueda)
      .set('pagina', pagina)
      .set('tamanio', 20);
    if (departamentoId) params = params.set('departamentoId', departamentoId);
    return this.http.get<PaginaSalida>('/api/v1/preparaciones-salida/puentes', { params });
  }
  estimar(cuerpo: SalidaRequest) {
    return this.http.post<EstimacionSalida>('/api/v1/preparaciones-salida/estimacion', cuerpo);
  }
  preparar(cuerpo: SalidaRequest, clave: string) {
    return this.http.post<PreparacionSalida>('/api/v1/preparaciones-salida', cuerpo, {
      headers: { 'Idempotency-Key': clave },
    });
  }
}
