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
import { UsuariosPage } from './pages/usuarios/usuarios.page';
import { GestionUsuariosApiService } from './services/gestion-usuarios-api.service';

@Component({ template: '' })
class DestinoTest {}

describe('Característica: acceso a la gestión de usuarios HU008', () => {
  const usuario = signal<{ id: string; role: string } | null>(null);
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
          provide: GestionUsuariosApiService,
          useValue: {
            listar: () => of({ content: [], number: 0, size: 10, totalElements: 0, totalPages: 0 }),
          },
        },
      ],
    });
    TestBed.overrideComponent(UsuariosPage, { set: { template: '' } });
    TestBed.overrideComponent(AppShellComponent, { set: { template: '<router-outlet />' } });
  });

  it('Escenario: el panel de administración abre la gestión de usuarios', async () => {
    usuario.set({ id: 'admin-1', role: 'ADMINISTRADOR' });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/admin');
    expect(TestBed.inject(Router).url).toBe('/admin');
    expect(harness.routeNativeElement?.querySelector('app-usuarios-page')).not.toBeNull();
  });

  it.each(['ESTUDIANTE', 'CATEDRATICO', 'PROFESIONAL_EXTERNO'])(
    'Escenario: el rol %s no accede a la gestión de usuarios',
    async (role) => {
      usuario.set({ id: 'usuario-1', role });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/admin');
      expect(TestBed.inject(Router).url).toBe('/');
      expect(harness.routeNativeElement?.querySelector('app-usuarios-page')).toBeNull();
    },
  );
});
