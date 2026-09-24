import { inject, Injectable } from '@angular/core';
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
      catchError(() => { this.auth.clear(); return of(void 0); })
    );
  }
}
