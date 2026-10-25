import { SincronizacionService } from '../../offline/services/sincronizacion.service';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../../app.routes';
import { ApiErrorService } from '../../core/services/api-error.service';
import { AuthStore } from '../../core/services/auth.store';
import { AppShellComponent } from '../../layouts/app-shell/app-shell.component';
import { ConnectivityService } from '../../offline/services/connectivity.service';
import { DetallePublicadoPage } from './pages/detalle-publicado/detalle-publicado.page';
import { InspeccionApiService } from './services/inspeccion-api.service';

@Component({ template: '' })
class DestinoTest {}

describe('Acceso al detalle publicado HU-030', () => {
  const usuario = signal<{ id: string; role: string } | null>(null);
  const obtenerPublicada = vi.fn();
  beforeEach(() => {
    usuario.set(null);
    obtenerPublicada.mockReset().mockReturnValue(of(null));
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
        { provide: InspeccionApiService, useValue: { obtenerPublicada } },
        { provide: ApiErrorService, useValue: {} },
      ],
    });
    TestBed.overrideComponent(AppShellComponent, { set: { template: '<router-outlet />' } });
    TestBed.overrideComponent(DetallePublicadoPage, { set: { template: '', imports: [] } });
  });

  it.each(['ESTUDIANTE', 'CATEDRATICO', 'PROFESIONAL_EXTERNO', 'ADMINISTRADOR'])(
    'permite consultar la ruta publicada al rol %s',
    async (role) => {
      usuario.set({ id: 'lector', role });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/inspecciones/registro/publicada');
      expect(TestBed.inject(Router).url).toBe('/inspecciones/registro/publicada');
      expect(obtenerPublicada).toHaveBeenCalledWith('registro');
      expect(
        harness.routeNativeElement?.querySelector('app-detalle-publicado-page'),
      ).not.toBeNull();
    },
  );

  it('redirige al login a un visitante sin consultar la API', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/inspecciones/registro/publicada');
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(obtenerPublicada).not.toHaveBeenCalled();
  });

  it.each(['ADMINISTRADOR'])(
    'conserva las restricciones existentes de listado y edición para %s',
    async (role) => {
      usuario.set({ id: 'lector', role });
      const harness = await RouterTestingHarness.create();
      for (const ruta of [
        '/inspecciones',
        '/inspecciones/registro',
        '/inspecciones/registro/borrador',
      ]) {
        await harness.navigateByUrl(ruta);
        expect(TestBed.inject(Router).url).toBe('/');
      }
      expect(obtenerPublicada).not.toHaveBeenCalled();
    },
  );
});
