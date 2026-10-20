import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../../app.routes';
import { AuthStore } from '../../core/services/auth.store';
import { ApiErrorService } from '../../core/services/api-error.service';
import { AppShellComponent } from '../../layouts/app-shell/app-shell.component';
import { ConnectivityService } from '../../offline/services/connectivity.service';
import { ObservacionesRecibidasPage } from './pages/observaciones-recibidas/observaciones-recibidas.page';
import { RevisionApiService } from './services/revision-api.service';

@Component({ template: '' })
class InicioTest {}

describe('Ruta de observaciones del autor', () => {
  const usuario = signal<{ id: string; role: string } | null>(null);
  const recibidas = vi.fn();
  beforeEach(() => {
    usuario.set(null);
    recibidas
      .mockReset()
      .mockReturnValue(of({ content: [], empty: true, number: 0, size: 20, totalElements: 0 }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'login', component: InicioTest },
          { path: '', pathMatch: 'full', component: InicioTest },
          ...routes,
        ]),
        {
          provide: AuthStore,
          useValue: { user: usuario, authenticated: () => usuario() !== null },
        },
        { provide: ConnectivityService, useValue: { online: signal(true) } },
        { provide: RevisionApiService, useValue: { recibidas } },
        { provide: ApiErrorService, useValue: {} },
      ],
    });
    TestBed.overrideComponent(AppShellComponent, { set: { template: '<router-outlet />' } });
    TestBed.overrideComponent(ObservacionesRecibidasPage, { set: { template: '', imports: [] } });
  });
  it('estudiante accede a la consulta sin habilitar bandeja ni edición', async () => {
    usuario.set({ id: 'autor', role: 'ESTUDIANTE' });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/observaciones');
    expect(TestBed.inject(Router).url).toBe('/observaciones');
    expect(recibidas).toHaveBeenCalledWith(0);
    for (const ruta of ['/revisiones', '/inspecciones/i/borrador']) {
      await harness.navigateByUrl(ruta);
      expect(TestBed.inject(Router).url).toBe('/');
    }
  });
  it.each(['CATEDRATICO', 'PROFESIONAL_EXTERNO', 'ADMINISTRADOR'])(
    'deniega entrada de autor al rol %s',
    async (role) => {
      usuario.set({ id: 'otro', role });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/observaciones');
      expect(TestBed.inject(Router).url).toBe('/');
      expect(recibidas).not.toHaveBeenCalled();
    },
  );
  it('requiere autenticación sin consultar datos', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/observaciones');
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(recibidas).not.toHaveBeenCalled();
  });
});
