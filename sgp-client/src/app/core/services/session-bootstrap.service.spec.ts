import '@angular/compiler';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injector, runInInjectionContext } from '@angular/core';
import { firstValueFrom, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { UserSession } from '../models/auth.models';
import { AuthStore } from './auth.store';
import { SessionBootstrapService } from './session-bootstrap.service';
import { TokenRefreshService } from './token-refresh.service';

const OFFLINE_USER_KEY = 'sgp.offline.user';

describe('SessionBootstrapService offline session', () => {
  const user: UserSession = {
    id: 'user-offline',
    email: 'inspector@example.test',
    role: 'ESTUDIANTE',
    verified: true,
    activated: true,
    active: true,
    twoFactorEnabled: false,
  };

  beforeEach(() => {
    const values = new Map<string, string>();
    vi.stubGlobal('sessionStorage', {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, String(value)),
      removeItem: (key: string) => values.delete(key),
      clear: () => values.clear(),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function createServices(status: number) {
    const refresh = vi.fn(() =>
      throwError(() => new HttpErrorResponse({ status, statusText: 'Refresh failed' })),
    );
    const injector = Injector.create({
      providers: [
        { provide: HttpClient, useValue: {} },
        AuthStore,
        SessionBootstrapService,
        { provide: TokenRefreshService, useValue: { refresh } },
      ],
    });
    const auth = injector.get(AuthStore);
    const bootstrap = runInInjectionContext(injector, () => inject(SessionBootstrapService));
    return { auth, bootstrap, injector };
  }

  it('preserves the cached user if refresh fails because the server is unreachable', async () => {
    sessionStorage.setItem(OFFLINE_USER_KEY, JSON.stringify(user));
    const { auth, bootstrap, injector } = createServices(504);
    await firstValueFrom(bootstrap.initialize());

    expect(auth.user()).toEqual(user);
    expect(auth.authenticated()).toBe(true);
    expect(auth.accessToken()).toBeNull();
    expect(JSON.parse(sessionStorage.getItem(OFFLINE_USER_KEY) ?? 'null')).toEqual(user);
    injector.destroy();
  });

  it('clears the cached user when refresh is rejected as unauthorized', async () => {
    sessionStorage.setItem(OFFLINE_USER_KEY, JSON.stringify(user));
    const { auth, bootstrap, injector } = createServices(401);
    await firstValueFrom(bootstrap.initialize());

    expect(auth.user()).toBeNull();
    expect(auth.authenticated()).toBe(false);
    expect(sessionStorage.getItem(OFFLINE_USER_KEY)).toBeNull();
    injector.destroy();
  });
});
