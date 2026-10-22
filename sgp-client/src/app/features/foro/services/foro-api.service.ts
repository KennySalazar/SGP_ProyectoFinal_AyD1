import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../puentes/models/puente.models';
import {
  AnclaForo,
  ComentarioForo,
  CrearComentarioRequest,
  CrearHiloRequest,
  FiltroSeccion,
  HiloForo,
  HiloInspeccionForo,
} from '../models/foro.models';

/**
 * Foro por puente (HU-063) y por inspección publicada (HU-064). Leer es público; abrir hilos y
 * comentar exige sesión.
 */
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

  /**
   * Comentarios y respuestas en una sola lista, de los más antiguos a los más nuevos. Con `ancla`
   * solo los de ese campo o elemento; con `filtro`, los de una sección o los generales. El servidor
   * admite un filtro a la vez: si hay ancla, el filtro no se envía.
   */
  listarComentarios(
    hiloId: string,
    pagina: number,
    tamanio: number,
    ancla: AnclaForo | null = null,
    filtro: FiltroSeccion | null = null,
  ): Observable<PaginaResponse<ComentarioForo>> {
    let params = this.paginacion(pagina, tamanio);
    if (!ancla && filtro?.seccion) params = params.set('seccion', filtro.seccion);
    if (!ancla && filtro?.soloGenerales) params = params.set('soloGenerales', true);
    if (ancla) {
      params = params.set('elementoRef', ancla.elementoRef);
      if (ancla.instanciaElementoId)
        params = params.set('instanciaElementoId', ancla.instanciaElementoId);
    }
    return this.http.get<PaginaResponse<ComentarioForo>>(
      `${this.baseUrl}/hilos/${hiloId}/comentarios`,
      { params },
    );
  }

  /** Público. 404 si la inspección no está publicada o todavía no tiene hilo; nunca lo crea. */
  obtenerHiloDeInspeccion(inspeccionId: string): Observable<HiloInspeccionForo> {
    return this.http.get<HiloInspeccionForo>(`${this.baseUrl}/inspecciones/${inspeccionId}/hilo`);
  }

  /** Con sesión. Devuelve el hilo de la inspección y lo crea si aún no existe (idempotente). */
  abrirHiloDeInspeccion(inspeccionId: string): Observable<HiloInspeccionForo> {
    return this.http.post<HiloInspeccionForo>(
      `${this.baseUrl}/inspecciones/${inspeccionId}/hilo`,
      {},
    );
  }

  crearComentario(hiloId: string, request: CrearComentarioRequest): Observable<ComentarioForo> {
    return this.http.post<ComentarioForo>(`${this.baseUrl}/hilos/${hiloId}/comentarios`, request);
  }

  private paginacion(pagina: number, tamanio: number): HttpParams {
    return new HttpParams().set('pagina', pagina).set('tamanio', tamanio);
  }
}
