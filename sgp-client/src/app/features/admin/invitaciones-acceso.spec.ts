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
import { ActivarCuentaPage } from '../usuario/pages/activar-cuenta/activar-cuenta.page';
import { InvitacionesPage } from './pages/invitaciones/invitaciones.page';
import { ProfesionalesPage } from './pages/profesionales/profesionales.page';
import { InvitacionApiService } from './services/invitacion-api.service';
import { ProfesionalApiService } from './services/profesional-api.service';

@Component({ template: '' })
class DestinoTest {}

describe('Característica: acceso a invitaciones HU001', () => {
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
          provide: InvitacionApiService,
          useValue: {
            listar: () => of({ content: [], number: 0, size: 10, totalElements: 0, totalPages: 0 }),
          },
        },
        {
          provide: ProfesionalApiService,
          useValue: {
            listar: () => of({ content: [], number: 0, size: 10, totalElements: 0, totalPages: 0 }),
          },
        },
      ],
    });
    TestBed.overrideComponent(InvitacionesPage, { set: { template: '' } });
    TestBed.overrideComponent(ProfesionalesPage, { set: { template: '' } });
    TestBed.overrideComponent(ActivarCuentaPage, { set: { template: '' } });
    TestBed.overrideComponent(AppShellComponent, { set: { template: '<router-outlet />' } });
  });

  it('Escenario: un Administrador abre la gestión de invitaciones', async () => {
    usuario.set({ role: 'ADMINISTRADOR' });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/admin/invitaciones');
    expect(TestBed.inject(Router).url).toBe('/admin/invitaciones');
    expect(harness.routeNativeElement?.querySelector('app-invitaciones-page')).not.toBeNull();
  });

  it.each(['ESTUDIANTE', 'CATEDRATICO', 'PROFESIONAL_EXTERNO'])(
    'Escenario: el rol %s no accede a la pantalla de invitaciones',
    async (role) => {
      // La guardia replica la regla; el backend responde además 403 (HU001).
      usuario.set({ role });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/admin/invitaciones');
      expect(TestBed.inject(Router).url).toBe('/');
      expect(harness.routeNativeElement?.querySelector('app-invitaciones-page')).toBeNull();
    },
  );

  it('Escenario: un visitante debe iniciar sesión para invitar', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/admin/invitaciones');
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('Escenario: el invitado abre el enlace de activación sin sesión', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/activar-cuenta?token=abc');
    expect(TestBed.inject(Router).url).toBe('/activar-cuenta?token=abc');
    expect(harness.routeDebugElement?.componentInstance).toBeInstanceOf(ActivarCuentaPage);
  });

  it('Escenario HU002: un Administrador abre la verificación de colegiados', async () => {
    usuario.set({ role: 'ADMINISTRADOR' });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/admin/profesionales');
    expect(TestBed.inject(Router).url).toBe('/admin/profesionales');
    expect(harness.routeNativeElement?.querySelector('app-profesionales-page')).not.toBeNull();
  });

  it.each(['ESTUDIANTE', 'CATEDRATICO', 'PROFESIONAL_EXTERNO'])(
    'Escenario HU002: el rol %s no accede a la verificación de colegiados',
    async (role) => {
      usuario.set({ role });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/admin/profesionales');
      expect(TestBed.inject(Router).url).toBe('/');
      expect(harness.routeNativeElement?.querySelector('app-profesionales-page')).toBeNull();
    },
  );
});
