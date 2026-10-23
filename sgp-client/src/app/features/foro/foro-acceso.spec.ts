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
import { SincronizacionService } from '../../offline/services/sincronizacion.service';
import { ModeracionForoPage } from './pages/moderacion/moderacion.page';
import { ForoApiService } from './services/foro-api.service';

@Component({ template: '' })
class DestinoTest {}

describe('Característica: acceso a la moderación del foro HU065', () => {
  const usuario = signal<{ role: string } | null>(null);
  beforeEach(() => {
    usuario.set(null);
    TestBed.configureTestingModule({
      providers: [
        { provide: SincronizacionService, useValue: { iniciar: () => undefined } },
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
          provide: ForoApiService,
          useValue: {
            listarInspeccionesSinRevision: () =>
              of({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 }),
          },
        },
      ],
    });
    TestBed.overrideComponent(ModeracionForoPage, { set: { template: '' } });
    TestBed.overrideComponent(AppShellComponent, { set: { template: '<router-outlet />' } });
  });

  it.each(['CATEDRATICO', 'ADMINISTRADOR'])(
    'Escenario: el rol %s abre la moderación posterior',
    async (role) => {
      usuario.set({ role });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/foro/moderacion');
      expect(TestBed.inject(Router).url).toBe('/foro/moderacion');
      expect(harness.routeNativeElement?.querySelector('app-moderacion-foro')).not.toBeNull();
    },
  );

  it.each(['ESTUDIANTE', 'PROFESIONAL_EXTERNO'])(
    'Escenario: el rol %s no modera el foro',
    async (role) => {
      // El backend responde además 403 a estos roles.
      usuario.set({ role });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/foro/moderacion');
      expect(TestBed.inject(Router).url).toBe('/');
    },
  );
});
