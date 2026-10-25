import { HttpClient, HttpErrorResponse, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { defer, Observable, shareReplay, tap, throwError } from 'rxjs';
import { ERROR_GESTIONADO_LOCALMENTE } from '../../../core/interceptors/problem.interceptor';
import { AuthStore } from '../../../core/services/auth.store';
import { uuidV7 } from '../../../offline/services/uuid-v7';

export function esResultadoIncierto(error: HttpErrorResponse): boolean {
  return (
    error.status === 0 ||
    error.status === 401 ||
    error.status >= 500 ||
    (error.status === 409 && error.error?.code === 'operacion_en_proceso')
  );
}

@Injectable({ providedIn: 'root' })
export class OperacionIdempotenteService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthStore);
  private readonly pendientes = new Map<string, Observable<unknown>>();

  ejecutar<T>(
    metodo: 'POST' | 'PUT',
    ruta: string,
    cuerpo: unknown,
    clave?: string,
    encabezados: Record<string, string> = {},
    erroresLocales = false,
  ): Observable<T> {
    const actor = this.auth.user()?.id ?? null;
    const serializado = JSON.stringify(cuerpo ?? null);
    const identidad = JSON.stringify([
      actor,
      metodo,
      ruta,
      serializado,
      clave ?? null,
      encabezados,
      erroresLocales,
    ]);
    const anterior = this.pendientes.get(identidad);
    if (anterior) return anterior as Observable<T>;
    const idempotencia = clave ?? uuidV7();
    const copia: unknown = JSON.parse(serializado);
    const peticion = defer(() => {
      if ((this.auth.user()?.id ?? null) !== actor) {
        return throwError(() => new HttpErrorResponse({ status: 401 }));
      }
      return this.http.request<T>(metodo, ruta, {
        body: copia,
        headers: { 'Idempotency-Key': idempotencia, ...encabezados },
        context: new HttpContext().set(ERROR_GESTIONADO_LOCALMENTE, erroresLocales),
      });
    }).pipe(
      tap({
        next: () => this.pendientes.delete(identidad),
        error: (error: HttpErrorResponse) => {
          if (!esResultadoIncierto(error)) this.pendientes.delete(identidad);
        },
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    this.pendientes.set(identidad, peticion);
    return peticion;
  }
}
