import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { routes } from '../../app.routes';
import { ApiErrorService } from '../../core/services/api-error.service';
import { AuthStore } from '../../core/services/auth.store';
import { AppShellComponent } from '../../layouts/app-shell/app-shell.component';
import { ConnectivityService } from '../../offline/services/connectivity.service';
import { CatalogoPuentesPage } from './pages/catalogo-puentes/catalogo-puentes.page';
import { RegistrarPuentePage } from './pages/registrar-puente/registrar-puente.page';
import { PuenteApiService } from './services/puente-api.service';

@Component({ selector: 'app-destino-test', template: '' })
class DestinoTest {}

describe('Rutas públicas y protegidas de puentes', () => {
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
        { provide: ApiErrorService, useValue: {} },
        {
          provide: PuenteApiService,
          useValue: {
            listarCatalogo: () => of({ content: [], totalElements: 0 }),
            listarDepartamentos: () => of({ content: [] }),
          },
        },
      ],
    });
    TestBed.overrideComponent(CatalogoPuentesPage, { set: { template: '' } });
    TestBed.overrideComponent(RegistrarPuentePage, { set: { template: '' } });
    TestBed.overrideComponent(AppShellComponent, { set: { template: '<router-outlet />' } });
  });

  it('permite abrir el catálogo sin sesión', async () => {
    const harness = await RouterTestingHarness.create();
    const page = await harness.navigateByUrl('/puentes', CatalogoPuentesPage);
    expect(page.pagina()?.totalElements).toBe(0);
    expect(TestBed.inject(Router).url).toBe('/puentes');
  });

  it('redirige al login cuando un visitante intenta registrar', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/puentes/nuevo');
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('conserva el acceso al registro para administradores', async () => {
    usuario.set({ role: 'ADMINISTRADOR' });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/puentes/nuevo');
    expect(TestBed.inject(Router).url).toBe('/puentes/nuevo');
    expect(harness.routeNativeElement?.querySelector('app-registrar-puente-page')).not.toBeNull();
  });

  it.each(['ESTUDIANTE', 'CATEDRATICO', 'PROFESIONAL_EXTERNO'])(
    'permite catálogo pero no registro al rol %s',
    async (role) => {
      usuario.set({ role });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/puentes', CatalogoPuentesPage);
      await harness.navigateByUrl('/puentes/nuevo');
      expect(TestBed.inject(Router).url).toBe('/');
    },
  );
});
