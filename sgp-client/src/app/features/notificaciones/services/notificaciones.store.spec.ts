import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthStore } from '../../../core/services/auth.store';
import { ConnectivityService } from '../../../offline/services/connectivity.service';
import { NotificacionOfflineService } from '../../../offline/services/notificacion-offline.service';
import { NotificacionApiService } from './notificacion-api.service';
import { NotificacionesStore } from './notificaciones.store';
describe('Destinos privados de los avisos de revisión', () => {
  const user = signal({ id: 'autor', role: 'ESTUDIANTE' });
  let store: NotificacionesStore;
  beforeEach(() => {
    user.set({ id: 'autor', role: 'ESTUDIANTE' });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthStore, useValue: { user, authenticated: () => false } },
        { provide: ConnectivityService, useValue: { online: signal(true) } },
        { provide: NotificacionOfflineService, useValue: {} },
        { provide: NotificacionApiService, useValue: {} },
      ],
    });
    store = TestBed.inject(NotificacionesStore);
  });
  afterEach(() => store.ngOnDestroy());
  it('abre el ciclo privado del estudiante sin enviarlo a la bandeja del revisor', () => {
    expect(store.obtenerRutaEntidad('revision', 'ciclo')).toEqual(['/observaciones', 'ciclo']);
  });
  it('conserva la bandeja de los catedráticos y los destinos de otras historias', () => {
    user.set({ id: 'revisor', role: 'CATEDRATICO' });
    expect(store.obtenerRutaEntidad('REVISION', 'ciclo')).toEqual(['/revisiones']);
    expect(store.obtenerRutaEntidad('INSPECCION', 'i')).toEqual(['/inspecciones', 'i']);
    expect(store.obtenerRutaEntidad('ORDEN', 'o')).toEqual(['/mantenimiento', 'ordenes', 'o']);
  });
  it('abre el detalle de una orden desde los avisos de mantenimiento (HU-047, HU-069)', () => {
    user.set({ id: 'catedratico', role: 'CATEDRATICO' });
    expect(store.obtenerRutaEntidad('orden_mantenimiento', 'o')).toEqual([
      '/mantenimiento',
      'ordenes',
      'o',
    ]);
    expect(store.obtenerRutaEntidad('puente', 'p')).toEqual(['/puentes', 'p']);
  });
  it('una mención lleva al comentario y una alerta del foro a su hilo (HU-066)', () => {
    expect(store.obtenerRutaEntidad('foro_comentario', 'c')).toEqual(['/foro', 'comentarios', 'c']);
    expect(store.obtenerRutaEntidad('foro_hilo', 'h')).toEqual(['/foro', 'hilos', 'h']);
  });
});
