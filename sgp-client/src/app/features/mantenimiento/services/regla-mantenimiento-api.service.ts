import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CrearReglaRequest,
  EditarReglaRequest,
  FiltrosReglasMantenimiento,
  PaginaReglasMantenimiento,
  ReglaMantenimiento,
  ResumenReglasVersion,
} from '../models/regla-mantenimiento.models';

@Injectable({ providedIn: 'root' })
export class ReglaMantenimientoApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/reglas-mantenimiento';

  listar(filtros: FiltrosReglasMantenimiento): Observable<PaginaReglasMantenimiento> {
    let params = new HttpParams();

    if (filtros.versionId) {
      params = params.set('versionId', filtros.versionId);
    }
    if (filtros.tipo) {
      params = params.set('tipo', filtros.tipo);
    }
    if (filtros.elemento && filtros.elemento.trim()) {
      params = params.set('elemento', filtros.elemento.trim());
    }
    if (filtros.activa !== undefined && filtros.activa !== null) {
      params = params.set('activa', filtros.activa.toString());
    }
    if (filtros.pagina !== undefined && filtros.pagina !== null) {
      params = params.set('page', filtros.pagina.toString());
    }
    if (filtros.tamanio !== undefined && filtros.tamanio !== null) {
      params = params.set('size', filtros.tamanio.toString());
    }

    return this.http.get<PaginaReglasMantenimiento>(this.baseUrl, { params });
  }

  obtenerPorId(id: string): Observable<ReglaMantenimiento> {
    return this.http.get<ReglaMantenimiento>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }

  obtenerResumen(versionId?: string): Observable<ResumenReglasVersion> {
    let params = new HttpParams();
    if (versionId) {
      params = params.set('versionId', versionId);
    }
    return this.http.get<ResumenReglasVersion>(`${this.baseUrl}/resumen`, { params });
  }

  crear(request: CrearReglaRequest): Observable<ReglaMantenimiento> {
    return this.http.post<ReglaMantenimiento>(this.baseUrl, request);
  }

  editar(id: string, request: EditarReglaRequest): Observable<ReglaMantenimiento> {
    return this.http.put<ReglaMantenimiento>(`${this.baseUrl}/${encodeURIComponent(id)}`, request);
  }

  cambiarEstado(id: string, activa: boolean): Observable<ReglaMantenimiento> {
    return this.http.patch<ReglaMantenimiento>(`${this.baseUrl}/${encodeURIComponent(id)}/estado`, {
      activa,
    });
  }

  validar(id: string): Observable<ReglaMantenimiento> {
    return this.http.post<ReglaMantenimiento>(
      `${this.baseUrl}/${encodeURIComponent(id)}/validar`,
      null,
    );
  }

  sembrar(versionId?: string): Observable<{ mensaje: string; reglasSembradas: number }> {
    let params = new HttpParams();
    if (versionId) {
      params = params.set('versionId', versionId);
    }
    return this.http.post<{ mensaje: string; reglasSembradas: number }>(
      `${this.baseUrl}/sembrar`,
      null,
      { params },
    );
  }
}
