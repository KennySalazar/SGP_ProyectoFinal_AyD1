import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../../../core/services/auth.store';
import { ConnectivityService } from '../../../offline/services/connectivity.service';
import { NotificacionOfflineService } from '../../../offline/services/notificacion-offline.service';
import { NotificacionResponse } from '../models/notificacion.models';
import { NotificacionApiService } from './notificacion-api.service';
import { NotificacionesStore } from './notificaciones.store';

const VERSION = 'c92f9cbe-637e-4634-9097-0600ab18d056';
const aviso = (tipo = 'CALIBRACION_PENDIENTE'): NotificacionResponse => ({
  id: 'aviso',
  tipo,
  titulo: 'Calibracion pendiente',
  mensaje: 'Revisar version',
  entidad: tipo === 'REGLAS_PENDIENTES_VALIDAR' ? 'catalogo_reglas' : 'version_formulario',
  entidadId: VERSION,
  claveEvento: `evento:${VERSION}`,
  creadoEn: '2026-10-24T12:00:00Z',
  leido: false,
});

describe('Avisos academicos HU056', () => {
  let store: NotificacionesStore;
  const online = signal(true);
  const api = { marcarLeida: vi.fn(), listar: vi.fn() };
  const offline = {
    marcarLeidaLocal: vi.fn(),
    guardarAvisos: vi.fn(),
    obtenerAvisos: vi.fn(),
    obtenerConteo: vi.fn(),
  };
  let router: Router;

  beforeEach(() => {
    vi.resetAllMocks();
    online.set(true);
    offline.marcarLeidaLocal.mockResolvedValue(undefined);
    offline.guardarAvisos.mockResolvedValue(undefined);
    api.marcarLeida.mockReturnValue(of({ ...aviso(), leido: true }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthStore,
          useValue: { authenticated: () => false, user: () => ({ role: 'CATEDRATICO' }) },
        },
        { provide: ConnectivityService, useValue: { online } },
        { provide: NotificacionOfflineService, useValue: offline },
        { provide: NotificacionApiService, useValue: api },
      ],
    });
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    store = TestBed.inject(NotificacionesStore);
    store.avisos.set([aviso()]);
    store.unreadCount.set(1);
  });

  afterEach(() => TestBed.resetTestingModule());

  it.each(['CALIBRACION_PENDIENTE', 'CALIBRACION_APROBADA'])(
    'abre la version exacta y confirma lectura de %s',
    async (tipo) => {
      await store.marcarLeida(aviso(tipo));
      expect(api.marcarLeida).toHaveBeenCalledWith('aviso');
      expect(router.navigate).toHaveBeenCalledWith(['/admin/formularios', VERSION]);
      expect(store.avisos()[0].leido).toBe(true);
      expect(store.unreadCount()).toBe(0);
      expect(offline.marcarLeidaLocal).toHaveBeenCalledWith('aviso');
    },
  );

  it('abre el catalogo con la version del evento', async () => {
    await store.marcarLeida(aviso('REGLAS_PENDIENTES_VALIDAR'));
    expect(router.navigate).toHaveBeenCalledWith(['/mantenimiento', 'reglas'], {
      queryParams: { versionId: VERSION },
    });
  });

  it('no repite la lectura ni descuenta el contador al reabrir', async () => {
    await store.marcarLeida(aviso());
    await store.marcarLeida(aviso());
    expect(api.marcarLeida).toHaveBeenCalledTimes(1);
    expect(store.unreadCount()).toBe(0);
  });

  it('conserva el pendiente si falla el servidor y permite reintentar', async () => {
    api.marcarLeida.mockReturnValueOnce(throwError(() => new Error('sin respuesta')));
    await store.marcarLeida(aviso());
    expect(store.errorLectura()).toBe(true);
    expect(store.unreadCount()).toBe(1);
    expect(store.avisos()[0].leido).toBe(false);
    expect(router.navigate).not.toHaveBeenCalled();
    expect(offline.marcarLeidaLocal).not.toHaveBeenCalled();
    await store.marcarLeida(aviso());
    expect(store.errorLectura()).toBe(false);
    expect(router.navigate).toHaveBeenCalledTimes(1);
  });

  it('espera confirmacion y evita doble clic durante la lectura', async () => {
    const respuesta = new Subject<NotificacionResponse>();
    api.marcarLeida.mockReturnValue(respuesta);
    const apertura = store.marcarLeida(aviso());
    await store.marcarLeida(aviso());
    expect(api.marcarLeida).toHaveBeenCalledTimes(1);
    expect(store.unreadCount()).toBe(1);
    expect(router.navigate).not.toHaveBeenCalled();
    respuesta.next({ ...aviso(), leido: true });
    respuesta.complete();
    await apertura;
    expect(store.abriendoAviso()).toBe(false);
    expect(store.unreadCount()).toBe(0);
  });

  it('conserva los avisos academicos pendientes sin conexion hasta confirmar con el servidor', async () => {
    online.set(false);
    await store.marcarLeida(aviso());
    expect(store.errorLectura()).toBe(true);
    expect(store.unreadCount()).toBe(1);
    expect(api.marcarLeida).not.toHaveBeenCalled();
    expect(offline.marcarLeidaLocal).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('ignora destinos externos y construye una ruta interna', async () => {
    await store.marcarLeida({ ...aviso(), rutaDestino: 'https://externo.example' });
    expect(router.navigate).toHaveBeenCalledWith(['/admin/formularios', VERSION]);
  });

  it('conserva el destino al guardar y recuperar avisos locales', async () => {
    const item = { ...aviso(), rutaDestino: `/admin/formularios/${VERSION}` };
    api.listar.mockReturnValue(of({ content: [item], totalElements: 1, totalPages: 1 }));
    await store.cargarAvisos();
    expect(offline.guardarAvisos.mock.calls[0][0][0].rutaDestino).toBe(item.rutaDestino);
    online.set(false);
    offline.obtenerConteo.mockResolvedValue(1);
    offline.obtenerAvisos.mockResolvedValue([item]);
    await store.cargarAvisos();
    expect(store.avisos()[0].rutaDestino).toBe(item.rutaDestino);
  });
});
