import '@angular/compiler';
import { Injector, runInInjectionContext } from '@angular/core';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../../core/services/auth.store';
import { TokenRefreshService } from '../../core/services/token-refresh.service';
import { InspeccionApiService } from '../../features/inspecciones/services/inspeccion-api.service';
import { sgpDb } from '../db/sgp-db';
import { ConnectivityService } from './connectivity.service';
import { InspeccionesLocalesService } from './inspecciones-locales.service';
import { FotografiasLocalesService } from './fotografias-locales.service';
import { SincronizacionService } from './sincronizacion.service';

describe('SincronizacionService autenticación previa', () => {
  afterEach(() => vi.restoreAllMocks());

  function createService(refresh: ReturnType<typeof vi.fn>, loadMe: ReturnType<typeof vi.fn>) {
    const injector = Injector.create({
      providers: [
        SincronizacionService,
        { provide: AuthStore, useValue: { user: () => ({ id: 'user-1' }), loadMe } },
        { provide: TokenRefreshService, useValue: { refresh } },
        { provide: ConnectivityService, useValue: { online: () => true } },
        { provide: InspeccionApiService, useValue: {} },
        { provide: InspeccionesLocalesService, useValue: {} },
        { provide: FotografiasLocalesService, useValue: { confirmar: vi.fn() } },
      ],
    });
    return {
      service: runInInjectionContext(injector, () => injector.get(SincronizacionService)),
      injector,
    };
  }

  it('no toca la cola si no puede restaurar credenciales', async () => {
    const refresh = vi.fn(() => throwError(() => new Error('refresh rechazado')));
    const loadMe = vi.fn(() => of({ id: 'user-1' }));
    const leerCola = vi.spyOn(sgpDb.cola_sync, 'toArray');
    const actualizarCola = vi.spyOn(sgpDb.cola_sync, 'update');
    const { service, injector } = createService(refresh, loadMe);

    await service.sincronizarAhora();

    expect(refresh).toHaveBeenCalledOnce();
    expect(loadMe).not.toHaveBeenCalled();
    expect(leerCola).not.toHaveBeenCalled();
    expect(actualizarCola).not.toHaveBeenCalled();
    injector.destroy();
  });

  it('restaura y valida la sesión antes de consultar la cola', async () => {
    const refresh = vi.fn(() => of({ accessToken: 'valid-token' }));
    const loadMe = vi.fn(() => of({ id: 'user-1' }));
    const leerCola = vi.spyOn(sgpDb.cola_sync, 'toArray').mockResolvedValue([]);
    const { service, injector } = createService(refresh, loadMe);

    await service.sincronizarAhora();

    expect(refresh).toHaveBeenCalledOnce();
    expect(loadMe).toHaveBeenCalledOnce();
    expect(leerCola).toHaveBeenCalledOnce();
    injector.destroy();
  });
});
