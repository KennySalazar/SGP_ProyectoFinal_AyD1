import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { AccessTokenResponse, UserSession } from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly http = inject(HttpClient);
  private readonly tokenState = signal<string | null>(null);
  private readonly userState = signal<UserSession | null>(null);

  readonly accessToken = this.tokenState.asReadonly();
  readonly user = this.userState.asReadonly();
  readonly authenticated = computed(() => this.tokenState() !== null && this.userState() !== null);

  setAccessToken(token: string | null): void {
    this.tokenState.set(token);
  }
  setUser(user: UserSession | null): void {
    this.userState.set(user);
  }
  clear(): void {
    this.tokenState.set(null);
    this.userState.set(null);
  }

  loadMe(): Observable<UserSession> {
    return this.http.get<UserSession>('/api/v1/auth/me').pipe(tap((user) => this.setUser(user)));
  }

  acceptToken(response: AccessTokenResponse): void {
    this.setAccessToken(response.accessToken);
  }
}
