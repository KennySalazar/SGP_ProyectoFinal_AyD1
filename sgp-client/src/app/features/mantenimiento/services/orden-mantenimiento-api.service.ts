import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  EjecucionOrden,
  EstadoOrden,
  EvidenciaMantenimiento,
  HistorialEstadoOrden,
  OrdenMantenimiento,
  PaginaOrdenesMantenimiento,
  RegistrarEjecucionRequest,
  ResponsableDisponible,
} from '../models/orden-mantenimiento.models';

@Injectable({ providedIn: 'root' })
export class OrdenMantenimientoApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/ordenes-mantenimiento';

  listar(
    pagina: number,
    tamanio: number,
    estado?: EstadoOrden | null,
  ): Observable<PaginaOrdenesMantenimiento> {
    let params = new HttpParams().set('pagina', pagina).set('tamanio', tamanio);
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<PaginaOrdenesMantenimiento>(this.baseUrl, { params });
  }

  obtener(id: string): Observable<OrdenMantenimiento> {
    return this.http.get<OrdenMantenimiento>(this.url(id));
  }

  historial(id: string): Observable<HistorialEstadoOrden[]> {
    return this.http.get<HistorialEstadoOrden[]>(`${this.url(id)}/historial`);
  }

  programar(id: string, fechaProgramada: string, version: number): Observable<OrdenMantenimiento> {
    return this.http.post<OrdenMantenimiento>(`${this.url(id)}/programar`, {
      fechaProgramada,
      version,
    });
  }

  descartar(id: string, motivo: string, version: number): Observable<OrdenMantenimiento> {
    return this.http.post<OrdenMantenimiento>(`${this.url(id)}/descartar`, { motivo, version });
  }

  cancelar(id: string, motivo: string, version: number): Observable<OrdenMantenimiento> {
    return this.http.post<OrdenMantenimiento>(`${this.url(id)}/cancelar`, { motivo, version });
  }

  iniciar(id: string, version: number): Observable<OrdenMantenimiento> {
    return this.http.post<OrdenMantenimiento>(`${this.url(id)}/iniciar`, { version });
  }

  /** Cierra la orden como ejecutada (HU-048, RN-MTO-05). */
  cerrar(id: string, version: number): Observable<OrdenMantenimiento> {
    return this.http.post<OrdenMantenimiento>(`${this.url(id)}/cerrar`, { version });
  }

  obtenerEjecucion(id: string): Observable<EjecucionOrden> {
    return this.http.get<EjecucionOrden>(`${this.url(id)}/ejecucion`);
  }

  registrarEjecucion(id: string, request: RegistrarEjecucionRequest): Observable<EjecucionOrden> {
    return this.http.put<EjecucionOrden>(`${this.url(id)}/ejecucion`, request);
  }

  responsables(): Observable<ResponsableDisponible[]> {
    return this.http.get<ResponsableDisponible[]>(`${this.baseUrl}/responsables`);
  }

  evidencias(id: string): Observable<EvidenciaMantenimiento[]> {
    return this.http.get<EvidenciaMantenimiento[]>(`${this.url(id)}/evidencias`);
  }

  adjuntarEvidencia(
    id: string,
    imagen: Blob,
    elementoRef?: string | null,
  ): Observable<EvidenciaMantenimiento> {
    const datos = new FormData();
    datos.append('archivo', imagen, 'evidencia.jpg');
    if (elementoRef?.trim()) {
      datos.append('elementoRef', elementoRef.trim());
    }
    return this.http.post<EvidenciaMantenimiento>(`${this.url(id)}/evidencias`, datos);
  }

  retirarEvidencia(id: string, evidenciaId: string, motivo: string): Observable<void> {
    return this.http.post<void>(
      `${this.url(id)}/evidencias/${encodeURIComponent(evidenciaId)}/retirar`,
      { motivo },
    );
  }

  /** La imagen se pide con la sesión del usuario: el extremo exige autenticación. */
  imagenEvidencia(id: string, evidenciaId: string, miniatura: boolean): Observable<Blob> {
    const recurso = miniatura ? 'miniatura' : 'contenido';
    return this.http.get(
      `${this.url(id)}/evidencias/${encodeURIComponent(evidenciaId)}/${recurso}`,
      { responseType: 'blob' },
    );
  }

  private url(id: string): string {
    return `${this.baseUrl}/${encodeURIComponent(id)}`;
  }
}
