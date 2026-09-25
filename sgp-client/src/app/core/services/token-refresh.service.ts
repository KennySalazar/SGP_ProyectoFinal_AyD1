import { HttpBackend, HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, shareReplay, tap, finalize } from 'rxjs';
import { AccessTokenResponse } from '../models/auth.models';
import { AuthStore } from './auth.store';

@Injectable({ providedIn: 'root' })
export class TokenRefreshService {
  private readonly rawHttp = new HttpClient(inject(HttpBackend));
  private readonly auth = inject(AuthStore);
  private inflight: Observable<AccessTokenResponse> | null = null;

  refresh(): Observable<AccessTokenResponse> {
    if (this.inflight) return this.inflight;
    this.inflight = this.rawHttp
      .post<AccessTokenResponse>('/api/v1/auth/refresh', {}, { withCredentials: true })
      .pipe(
        tap((response) => this.auth.acceptToken(response)),
        finalize(() => {
          this.inflight = null;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    return this.inflight;
  }
}
