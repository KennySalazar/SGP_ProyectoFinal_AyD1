import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { roleGuard } from '../../core/guards/role.guard';
import { AuthStore } from '../../core/services/auth.store';

describe('Acceso al registro de puentes: HU009', () => {
  const usuario = signal<{ role: string } | null>(null);

  beforeEach(() => {
    usuario.set(null);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthStore,
          useValue: { user: usuario },
        },
      ],
    });
  });

  function comprobarAcceso() {
    return TestBed.runInInjectionContext(() =>
      roleGuard('ADMINISTRADOR')(new ActivatedRouteSnapshot(), {
        url: '/puentes/nuevo',
      } as RouterStateSnapshot),
    );
  }

  it('permite el acceso al Administrador', () => {
    usuario.set({ role: 'ADMINISTRADOR' });

    expect(comprobarAcceso()).toBe(true);
  });

  it.each(['ESTUDIANTE', 'CATEDRATICO'])('redirige al inicio cuando el rol es %s', (role) => {
    usuario.set({ role });

    const resultado = comprobarAcceso();

    expect(resultado).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree)).toBe('/');
  });

  it('redirige al inicio cuando no hay usuario', () => {
    const resultado = comprobarAcceso();

    expect(resultado).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree)).toBe('/');
  });
});
