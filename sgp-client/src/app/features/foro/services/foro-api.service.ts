import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../puentes/models/puente.models';
import {
  ComentarioForo,
  CrearComentarioRequest,
  CrearHiloRequest,
  HiloForo,
} from '../models/foro.models';

/** Foro por puente (HU-063). Leer es público; abrir hilos y comentar exige sesión. */
@Injectable({ providedIn: 'root' })
export class ForoApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1';

  listarHilos(
    puenteId: string,
    pagina: number,
    tamanio: number,
  ): Observable<PaginaResponse<HiloForo>> {
    return this.http.get<PaginaResponse<HiloForo>>(`${this.baseUrl}/puentes/${puenteId}/hilos`, {
      params: this.paginacion(pagina, tamanio),
    });
  }

  crearHilo(puenteId: string, request: CrearHiloRequest): Observable<HiloForo> {
    return this.http.post<HiloForo>(`${this.baseUrl}/puentes/${puenteId}/hilos`, request);
  }

  obtenerHilo(hiloId: string): Observable<HiloForo> {
    return this.http.get<HiloForo>(`${this.baseUrl}/hilos/${hiloId}`);
  }

  /** Comentarios y respuestas en una sola lista, de los más antiguos a los más nuevos. */
  listarComentarios(
    hiloId: string,
    pagina: number,
    tamanio: number,
  ): Observable<PaginaResponse<ComentarioForo>> {
    return this.http.get<PaginaResponse<ComentarioForo>>(
      `${this.baseUrl}/hilos/${hiloId}/comentarios`,
      { params: this.paginacion(pagina, tamanio) },
    );
  }

  crearComentario(hiloId: string, request: CrearComentarioRequest): Observable<ComentarioForo> {
    return this.http.post<ComentarioForo>(`${this.baseUrl}/hilos/${hiloId}/comentarios`, request);
  }

  private paginacion(pagina: number, tamanio: number): HttpParams {
    return new HttpParams().set('pagina', pagina).set('tamanio', tamanio);
  }
}
