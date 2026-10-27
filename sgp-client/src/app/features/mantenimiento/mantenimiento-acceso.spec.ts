import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { routes } from '../../app.routes';
import { AuthStore } from '../../core/services/auth.store';

describe('Acceso al catalogo de mantenimiento HU055', () => {
  let role: string | undefined;
  afterEach(() => TestBed.resetTestingModule());
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthStore,
          useValue: { user: () => (role ? { role } : null) },
        },
      ],
    });
  });
  it.each(['ADMINISTRADOR', 'CATEDRATICO', 'ESTUDIANTE', 'PROFESIONAL_EXTERNO', undefined])(
    'protege el catalogo para %s sin abrir las demas pantallas administrativas',
    (actual) => {
      role = actual;
      const hijos = routes.find((r) => r.children)?.children ?? [];
      const formulario = hijos.find((r) => r.path === 'mantenimiento');
      const admin = hijos.find((r) => r.path === 'admin');
      expect(formulario).toBeDefined();
      const ejecutar = (guard: CanActivateFn) =>
        TestBed.runInInjectionContext(() =>
          guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
        );
      const resultado = ejecutar(formulario!.canActivate![0] as CanActivateFn);
      if (actual === 'ADMINISTRADOR' || actual === 'CATEDRATICO') expect(resultado).toBe(true);
      else expect(resultado).toBeInstanceOf(UrlTree);
      const accesoAdmin = ejecutar(admin!.canActivate![0] as CanActivateFn);
      if (actual === 'ADMINISTRADOR') expect(accesoAdmin).toBe(true);
      else expect(accesoAdmin).toBeInstanceOf(UrlTree);
    },
  );
});
