import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { routes } from '../../app.routes';
import { AuthStore } from '../../core/services/auth.store';
import { AppShellComponent } from '../../layouts/app-shell/app-shell.component';
import { ConnectivityService } from '../../offline/services/connectivity.service';
import { BitacoraPage } from './pages/bitacora/bitacora.page';
import { AuditoriaApiService } from './services/auditoria-api.service';

@Component({ template: '' })
class DestinoTest {}

describe('Característica: acceso a la bitácora HU007', () => {
  const usuario = signal<{ role: string } | null>(null);
  beforeEach(() => {
    usuario.set(null);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'login', component: DestinoTest },
          { path: '', pathMatch: 'full', component: DestinoTest },
          ...routes,
        ]),
        {
          provide: AuthStore,
          useValue: { user: usuario, authenticated: () => usuario() !== null },
        },
        { provide: ConnectivityService, useValue: { online: signal(true) } },
        { provide: TranslocoService, useValue: { translate: (key: string) => key } },
        {
          provide: AuditoriaApiService,
          useValue: {
            consultar: () =>
              of({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 }),
          },
        },
      ],
    });
    TestBed.overrideComponent(BitacoraPage, { set: { template: '' } });
    TestBed.overrideComponent(AppShellComponent, { set: { template: '<router-outlet />' } });
  });

  it('Escenario: un Administrador abre la ruta de bitácora', async () => {
    // Dado un Administrador autenticado
    usuario.set({ role: 'ADMINISTRADOR' });
    // Cuando abre la bitácora
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/admin/bitacora');
    // Entonces se muestra la pantalla de consulta
    expect(TestBed.inject(Router).url).toBe('/admin/bitacora');
    expect(harness.routeNativeElement?.querySelector('app-bitacora-page')).not.toBeNull();
  });

  it.each(['ESTUDIANTE', 'CATEDRATICO', 'PROFESIONAL_EXTERNO'])(
    'Escenario: el rol %s no accede a la pantalla',
    async (role) => {
      // Dado un rol distinto de Administrador
      usuario.set({ role });
      // Cuando intenta abrir la ruta
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/admin/bitacora');
      // Entonces no se monta la bitácora; el backend verifica además el 403
      expect(TestBed.inject(Router).url).toBe('/');
      expect(harness.routeNativeElement?.querySelector('app-bitacora-page')).toBeNull();
    },
  );

  it('Escenario: un visitante debe iniciar sesión para consultar', async () => {
    // Dado un visitante sin sesión
    // Cuando abre la bitácora, entonces se dirige al login
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/admin/bitacora');
    expect(TestBed.inject(Router).url).toBe('/login');
  });
});
