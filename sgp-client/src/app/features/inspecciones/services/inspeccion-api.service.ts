import { FotografiaResponse, MetadatosFotografia } from '../models/fotografia.models';
import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable, of, switchMap } from 'rxjs';
import {
  ConfirmacionEstadoIcResponse,
  ConfirmarEstadoIcRequest,
  DatosPermanentesResponse,
  GuardarBorradorRequest,
  IniciarInspeccionRequest,
  IniciarInspeccionResponse,
  InspeccionBorradorResponse,
  InspeccionResumenResponse,
  InspeccionPublicadaDetalleResponse,
  InspeccionPublicadaResumenResponse,
  EstadoInspeccion,
} from '../models/inspeccion.models';
import { PuenteResponse, PaginaResponse } from '../../puentes/models/puente.models';
@Injectable({ providedIn: 'root' })
export class InspeccionApiService {
  private readonly http = inject(HttpClient);
  iniciarInspeccion(
    request: IniciarInspeccionRequest,
    idempotencyKey?: string,
  ): Observable<IniciarInspeccionResponse> {
    const headers: Record<string, string> = {};
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    return this.http.post<IniciarInspeccionResponse>('/api/v1/inspecciones', request, {
      headers,
    });
  }
  obtenerDatosPermanentes(puenteId: string): Observable<DatosPermanentesResponse | null> {
    return this.http
      .get<DatosPermanentesResponse>(`/api/v1/inspecciones/puentes/${puenteId}/datos-permanentes`, {
        observe: 'response',
      })
      .pipe(map((res) => (res.status === 204 ? null : res.body)));
  }
  obtenerBorrador(
    id: string,
    recuperarIdentificacion = false,
  ): Observable<InspeccionBorradorResponse> {
    return this.http
      .get<InspeccionBorradorResponse>(`/api/v1/inspecciones/${encodeURIComponent(id)}/borrador`)
      .pipe(
        switchMap((borrador) => {
          if (!recuperarIdentificacion || borrador.estado !== 'BORRADOR') return of(borrador);
          const capturada = this.registro(borrador.identificacionCapturada);
          const guardada = this.registro(borrador.datos?.['identificacion']);
          const identificacion = { ...capturada };
          for (const [clave, valor] of Object.entries(guardada)) {
            if (this.tieneValor(valor) || !this.tieneValor(identificacion[clave]))
              identificacion[clave] = valor;
          }
          if (
            ['ruta', 'departamento', 'municipio'].every((clave) =>
              this.tieneValor(identificacion[clave]),
            )
          ) {
            return of(borrador);
          }
          return this.http
            .get<PuenteResponse>(`/api/v1/puentes/${encodeURIComponent(borrador.puenteId)}`)
            .pipe(
              map((puente) => {
                const origen: Record<string, unknown> = {
                  ruta: puente.ruta,
                  departamento: puente.departamento.nombre,
                  municipio: puente.municipio.nombre,
                  kilometraje: puente.kilometraje,
                };
                for (const [clave, valor] of Object.entries(origen)) {
                  if (!this.tieneValor(identificacion[clave])) identificacion[clave] = valor;
                }
                if (
                  ['ruta', 'departamento', 'municipio'].some(
                    (clave) => !this.tieneValor(identificacion[clave]),
                  )
                ) {
                  throw new Error(
                    'El puente no tiene todos los datos de identificacion requeridos.',
                  );
                }
                return {
                  ...borrador,
                  identificacionCapturada: { ...capturada, ...identificacion },
                  datos: { ...borrador.datos, identificacion },
                };
              }),
            );
        }),
      );
  }
  private registro(valor: unknown): Record<string, unknown> {
    return valor !== null && typeof valor === 'object' && !Array.isArray(valor)
      ? (valor as Record<string, unknown>)
      : {};
  }
  private tieneValor(valor: unknown): boolean {
    return (
      valor !== null &&
      valor !== undefined &&
      (typeof valor !== 'string' || valor.trim().length > 0)
    );
  }
  guardarBorrador(
    id: string,
    request: GuardarBorradorRequest,
  ): Observable<InspeccionBorradorResponse> {
    return this.http.put<InspeccionBorradorResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/borrador`,
      request,
    );
  }
  listarMisInspecciones(
    pagina = 0,
    tamanio = 20,
    estado?: EstadoInspeccion,
  ): Observable<PaginaResponse<InspeccionResumenResponse>> {
    const params: Record<string, string | number> = { pagina, tamanio };
    if (estado) params['estado'] = estado;
    return this.http.get<PaginaResponse<InspeccionResumenResponse>>('/api/v1/inspecciones', {
      params,
    });
  }
  enviar(id: string, idempotencyKey?: string): Observable<InspeccionResumenResponse> {
    return this.http.post<InspeccionResumenResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/enviar`,
      null,
      idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : {},
    );
  }
  publicar(id: string): Observable<InspeccionResumenResponse> {
    return this.http.post<InspeccionResumenResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/publicar`,
      null,
    );
  }
  crearCorreccion(id: string, motivo: string): Observable<InspeccionResumenResponse> {
    return this.http.post<InspeccionResumenResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/correcciones`,
      { motivo },
    );
  }
  eliminarBorrador(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/inspecciones/${encodeURIComponent(id)}`);
  }
  obtenerConfirmacionEstado(id: string): Observable<ConfirmacionEstadoIcResponse> {
    return this.http.get<ConfirmacionEstadoIcResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/confirmacion-estado`,
    );
  }
  confirmarEstado(
    id: string,
    request: ConfirmarEstadoIcRequest,
  ): Observable<ConfirmacionEstadoIcResponse> {
    return this.http.put<ConfirmacionEstadoIcResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/confirmacion-estado`,
      request,
    );
  }
  adjuntarFotografia(
    inspeccionId: string,
    blob: Blob,
    metadatos: MetadatosFotografia,
    clave: string,
  ): Observable<FotografiaResponse> {
    const datos = new FormData();
    datos.append('metadatos', new Blob([JSON.stringify(metadatos)], { type: 'application/json' }));
    datos.append('archivo', blob, `${metadatos.id}.jpg`);
    return this.http.post<FotografiaResponse>(
      `/api/v1/inspecciones/${inspeccionId}/fotografias`,
      datos,
      { headers: { 'Idempotency-Key': clave } },
    );
  }
  listarFotografias(inspeccionId: string): Observable<FotografiaResponse[]> {
    return this.http.get<FotografiaResponse[]>(`/api/v1/inspecciones/${inspeccionId}/fotografias`);
  }
  retirarFotografia(inspeccionId: string, fotoId: string): Observable<void> {
    return this.http.delete<void>(
      `/api/v1/inspecciones/${encodeURIComponent(inspeccionId)}/fotografias/${encodeURIComponent(fotoId)}`,
    );
  }
  contenidoFotografia(inspeccionId: string, fotoId: string, miniatura = false): Observable<Blob> {
    return this.http.get(
      `/api/v1/inspecciones/${inspeccionId}/fotografias/${fotoId}/${miniatura ? 'miniatura' : 'contenido'}`,
      { responseType: 'blob' },
    );
  }

  listarPublicadasPorPuente(
    puenteId: string,
    pagina = 0,
    tamanio = 20,
  ): Observable<PaginaResponse<InspeccionPublicadaResumenResponse>> {
    return this.http.get<PaginaResponse<InspeccionPublicadaResumenResponse>>(
      `/api/v1/inspecciones/puentes/${encodeURIComponent(puenteId)}/publicadas`,
      { params: { pagina, tamanio } },
    );
  }

  obtenerPublicada(id: string): Observable<InspeccionPublicadaDetalleResponse> {
    return this.http.get<InspeccionPublicadaDetalleResponse>(
      `/api/v1/inspecciones/${encodeURIComponent(id)}/publicada`,
    );
  }
}
