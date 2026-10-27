import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject, tap } from 'rxjs';
import { AccessTokenResponse, UserSession } from '../models/auth.models';

const OFFLINE_USER_KEY = 'sgp.offline.user';

function isUserSession(value: unknown): value is UserSession {
  if (typeof value !== 'object' || value === null) return false;
  const user = value as Partial<UserSession>;
  return (
    typeof user.id === 'string' &&
    typeof user.email === 'string' &&
    ['ADMINISTRADOR', 'CATEDRATICO', 'PROFESIONAL_EXTERNO', 'ESTUDIANTE'].includes(
      user.role as string,
    ) &&
    typeof user.verified === 'boolean' &&
    typeof user.activated === 'boolean' &&
    typeof user.active === 'boolean' &&
    typeof user.twoFactorEnabled === 'boolean'
  );
}

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly http = inject(HttpClient);
  private readonly tokenState = signal<string | null>(null);
  private readonly userState = signal<UserSession | null>(this.readOfflineUser());

  readonly cambiosSesion$ = new Subject<string | null>();
  readonly accessToken = this.tokenState.asReadonly();
  readonly user = this.userState.asReadonly();
  readonly authenticated = computed(() => this.userState() !== null);
  /** Profesional Externo cuyo colegiado aún no verifica el Administrador: no puede inspeccionar. */
  readonly colegiadoPendiente = computed(() => {
    const user = this.userState();
    return user?.role === 'PROFESIONAL_EXTERNO' && user.colegiadoVerificado !== true;
  });

  constructor() {
    window.addEventListener('storage', (evento) => {
      if (evento.key !== OFFLINE_USER_KEY) return;
      let id: string | undefined;
      try {
        id = evento.newValue ? (JSON.parse(evento.newValue) as Partial<UserSession>).id : undefined;
      } catch {
        id = undefined;
      }
      if (id !== this.userState()?.id) {
        this.tokenState.set(null);
        this.userState.set(null);
        this.cambiosSesion$.next(null);
        sessionStorage.removeItem(OFFLINE_USER_KEY);
      }
    });
  }

  setAccessToken(token: string | null): void {
    this.tokenState.set(token);
  }
  setUser(user: UserSession | null): void {
    const anterior = this.userState()?.id;
    this.userState.set(user);
    if (anterior !== user?.id) this.cambiosSesion$.next(user?.id ?? null);
    try {
      if (user) {
        sessionStorage.setItem(OFFLINE_USER_KEY, JSON.stringify(user));
        localStorage.setItem(OFFLINE_USER_KEY, JSON.stringify(user));
      } else {
        sessionStorage.removeItem(OFFLINE_USER_KEY);
        localStorage.removeItem(OFFLINE_USER_KEY);
      }
    } catch {
      // La autenticación en memoria sigue funcionando si el almacenamiento no está disponible.
    }
  }
  clear(): void {
    this.tokenState.set(null);
    this.setUser(null);
  }

  loadMe(): Observable<UserSession> {
    return this.http.get<UserSession>('/api/v1/auth/me').pipe(tap((user) => this.setUser(user)));
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/v1/auth/logout', {}, { withCredentials: true });
  }

  acceptToken(response: AccessTokenResponse): void {
    this.setAccessToken(response.accessToken);
  }

  private readOfflineUser(): UserSession | null {
    try {
      const stored =
        sessionStorage.getItem(OFFLINE_USER_KEY) ?? localStorage.getItem(OFFLINE_USER_KEY);
      if (!stored) return null;
      const user: unknown = JSON.parse(stored);
      return isUserSession(user) ? user : null;
    } catch {
      return null;
    }
  }
}
