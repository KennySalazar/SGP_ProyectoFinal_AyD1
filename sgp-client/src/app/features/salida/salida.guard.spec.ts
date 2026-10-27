import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../../core/services/auth.store';
import { salidaGuard } from './salida.guard';

describe('Acceso preparar salida', () => {
  for (const rol of ['ESTUDIANTE', 'CATEDRATICO', 'PROFESIONAL_EXTERNO', 'ADMINISTRADOR']) {
    it('aplica acceso para ' + rol, () => {
      TestBed.configureTestingModule({
        providers: [
          {
            provide: AuthStore,
            useValue: {
              user: () => ({ role: rol, active: true, activated: true, verified: true }),
              colegiadoPendiente: () => false,
            },
          },
          { provide: Router, useValue: { createUrlTree: vi.fn().mockReturnValue('inicio') } },
        ],
      });
      const resultado = TestBed.runInInjectionContext(() =>
        salidaGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
      );
      expect(resultado).toBe(rol === 'ADMINISTRADOR' ? 'inicio' : true);
      TestBed.resetTestingModule();
    });
  }
});
