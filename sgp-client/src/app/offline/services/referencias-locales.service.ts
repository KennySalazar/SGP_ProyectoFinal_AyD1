import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  Observable,
  catchError,
  concat,
  defer,
  filter,
  from,
  of,
  switchMap,
  throwError,
} from 'rxjs';
import { AuthStore } from '../../core/services/auth.store';
import { AsignacionPuenteResponse } from '../../features/puentes/models/puente.models';
import { sgpDb } from '../db/sgp-db';
import { ConnectivityService } from './connectivity.service';

/** SWR privado: cada cuenta conserva su propia copia, fuera de la caché compartida del worker. */
@Injectable({ providedIn: 'root' })
export class ReferenciasLocalesService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthStore);
  private readonly conexion = inject(ConnectivityService);

  misPuentesAsignados(): Observable<AsignacionPuenteResponse[]> {
    return defer(() => {
      const propietarioId = this.auth.user()?.id;
      if (!propietarioId) return of([]);
      return from(sgpDb.asignaciones_usuario.get(propietarioId)).pipe(
        switchMap((cache) => {
          const anterior = cache ? of(cache.datos) : of<AsignacionPuenteResponse[]>();
          const red = this.conexion.online()
            ? this.http
                .get<AsignacionPuenteResponse[]>('/api/v1/asignaciones-puentes/mis-puentes')
                .pipe(
                  switchMap((datos) =>
                    from(sgpDb.asignaciones_usuario.put({ propietarioId, datos })).pipe(
                      switchMap(() => of(datos)),
                    ),
                  ),
                  catchError((error) =>
                    cache ? of<AsignacionPuenteResponse[]>() : throwError(() => error),
                  ),
                )
            : of<AsignacionPuenteResponse[]>();
          return concat(anterior, red).pipe(filter(() => this.auth.user()?.id === propietarioId));
        }),
      );
    });
  }
}
