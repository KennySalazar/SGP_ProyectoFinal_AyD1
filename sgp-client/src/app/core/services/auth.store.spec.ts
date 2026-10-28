import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { describe, expect, it, vi } from 'vitest';
import { AuthStore } from './auth.store';
import { UserSession } from '../models/auth.models';

describe('HU-060: cambio de sesión entre pestañas', () => {
  it('invalida observadores de la cola y no persiste el token al cambiar la cuenta', () => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient()] });
    const auth = TestBed.inject(AuthStore);
    auth.setUser({
      id: 'a',
      role: 'ESTUDIANTE',
      email: 'a@example.test',
      active: true,
      activated: true,
      verified: true,
      twoFactorEnabled: false,
    } as UserSession);
    auth.setAccessToken('token-solo-en-memoria');
    const cambio = vi.fn();
    const subscription = auth.cambiosSesion$.subscribe(cambio);
    window.dispatchEvent(
      new StorageEvent('storage', {
        key: 'sgp.offline.user',
        newValue: JSON.stringify({ id: 'b' }),
      }),
    );
    expect(auth.user()).toBeNull();
    expect(auth.accessToken()).toBeNull();
    expect(cambio).toHaveBeenCalledWith(null);
    expect(localStorage.getItem('sgp.offline.user')).not.toContain('token-solo-en-memoria');
    subscription.unsubscribe();
    localStorage.clear();
    sessionStorage.clear();
  });
});
