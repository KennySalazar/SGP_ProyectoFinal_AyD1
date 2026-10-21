import { inject, Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, concatMap, map, Observable, of } from 'rxjs';
import { AuthStore } from './auth.store';
import { TokenRefreshService } from './token-refresh.service';

@Injectable({ providedIn: 'root' })
export class SessionBootstrapService {
  private readonly refresh = inject(TokenRefreshService);
  private readonly auth = inject(AuthStore);

  initialize(): Observable<void> {
    return this.refresh.refresh().pipe(
      concatMap(() => this.auth.loadMe()),
      map(() => void 0),
      catchError((error: unknown) => {
        const sinConexion =
          error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
        if (!sinConexion) this.auth.clear();
        return of(void 0);
      }),
    );
  }
}
