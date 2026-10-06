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
import { puentesRoutes } from './puentes.routes';

describe('Acceso a las solicitudes de alta de puente: HU010', () => {
  const usuario = signal<{ role: string } | null>(null);

  beforeEach(() => {
    usuario.set(null);

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthStore, useValue: { user: usuario } }],
    });
  });

  function comprobarAcceso() {
    return TestBed.runInInjectionContext(() =>
      roleGuard('CATEDRATICO')(new ActivatedRouteSnapshot(), {
        url: '/puentes/solicitudes',
      } as RouterStateSnapshot),
    );
  }

  it('permite el acceso al Catedrático', () => {
    usuario.set({ role: 'CATEDRATICO' });

    expect(comprobarAcceso()).toBe(true);
  });

  it.each(['ADMINISTRADOR', 'ESTUDIANTE', 'PROFESIONAL_EXTERNO'])(
    'redirige al inicio cuando el rol es %s',
    (role) => {
      usuario.set({ role });

      const resultado = comprobarAcceso();

      expect(resultado).toBeInstanceOf(UrlTree);
      expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree)).toBe('/');
    },
  );

  it('declara las rutas de solicitudes protegidas con authGuard y roleGuard', () => {
    const bloque = puentesRoutes.find((ruta) =>
      ruta.children?.some((hija) => hija.path === 'solicitudes'),
    );

    expect(bloque?.canActivate).toHaveLength(2);
    expect(bloque?.children?.map((hija) => hija.path)).toEqual([
      'solicitudes',
      'solicitudes/nueva',
    ]);
  });
});
