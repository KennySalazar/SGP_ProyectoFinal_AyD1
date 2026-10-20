import { TestBed } from '@angular/core/testing';
import { computed, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, Subject, throwError } from 'rxjs';
import { AuthStore } from '../../../core/services/auth.store';
import { UserSession } from '../../../core/models/auth.models';
import { ConnectivityService } from '../../../offline/services/connectivity.service';
import { StorageService } from '../../../offline/services/storage.service';
import { FormularioApiService } from '../../formulario/services/formulario-api.service';
import { PreparacionSalida, ResumenSalida } from '../models/salida.models';
import { PreparacionSalidaService } from './preparacion-salida.service';
import { SalidaLocalService } from './salida-local.service';
import { SalidaApiService } from './salida-api.service';

const usuario: UserSession = {
  id: 'u1',
  email: 'test@test.com',
  role: 'CATEDRATICO',
  active: true,
  activated: true,
  verified: true,
  twoFactorEnabled: false,
};
const resumen: ResumenSalida = {
  propietarioId: 'u1',
  id: 's1',
  dispositivoId: 'd1',
  preparadoEn: '',
  versionFormularioId: 'v3',
  codigoVersionFormulario: 'v3',
  numeroPuentes: 1,
  asignaciones: [],
};
const paquete = {
  id: 's1',
  usuarioId: 'u1',
  paquete: { formulario: { id: 'v3' } },
} as PreparacionSalida;

describe('Preparacion y sesion HU041', () => {
  let servicio: PreparacionSalidaService;
  const actor = signal<UserSession | null>(usuario);
  const online = signal(true);
  let local: {
    ultima: ReturnType<typeof vi.fn>;
    solicitud: ReturnType<typeof vi.fn>;
    guardar: ReturnType<typeof vi.fn>;
    quitar: ReturnType<typeof vi.fn>;
    descartarSolicitud: ReturnType<typeof vi.fn>;
  };
  let api: { preparar: ReturnType<typeof vi.fn> };
  let storage: {
    requestPersistentStorage: ReturnType<typeof vi.fn>;
    usage: ReturnType<typeof vi.fn>;
  };
  let formulario: { obtenerActiva: ReturnType<typeof vi.fn> };
  beforeEach(() => {
    actor.set(usuario);
    online.set(true);
    local = {
      ultima: vi.fn().mockResolvedValue(undefined),
      solicitud: vi
        .fn()
        .mockResolvedValue({ clave: 'k1', cuerpo: { dispositivoId: 'd1', puentesIds: ['p1'] } }),
      guardar: vi.fn().mockResolvedValue(resumen),
      quitar: vi.fn().mockResolvedValue(undefined),
      descartarSolicitud: vi.fn().mockResolvedValue(undefined),
    };
    api = { preparar: vi.fn().mockReturnValue(of(paquete)) };
    storage = {
      requestPersistentStorage: vi.fn().mockResolvedValue(true),
      usage: vi.fn().mockResolvedValue({ quota: 100000000, usage: 10 }),
    };
    formulario = { obtenerActiva: vi.fn().mockReturnValue(of({ id: 'v3' })) };
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthStore,
          useValue: {
            user: actor,
            colegiadoPendiente: computed(
              () => actor()?.role === 'PROFESIONAL_EXTERNO' && !actor()?.colegiadoVerificado,
            ),
          },
        },
        { provide: ConnectivityService, useValue: { online } },
        { provide: SalidaLocalService, useValue: local },
        { provide: SalidaApiService, useValue: api },
        { provide: StorageService, useValue: storage },
        { provide: FormularioApiService, useValue: formulario },
      ],
    });
    servicio = TestBed.inject(PreparacionSalidaService);
  });
  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('confirma exito despues del guardado local y solicita persistencia', async () => {
    expect(await servicio.preparar(['p1'])).toBe(true);
    expect(api.preparar).toHaveBeenCalledWith({ dispositivoId: 'd1', puentesIds: ['p1'] }, 'k1');
    expect(local.guardar).toHaveBeenCalledWith('u1', paquete, expect.any(Function));
    expect(servicio.ultima()?.id).toBe('s1');
    expect(storage.requestPersistentStorage).toHaveBeenCalledOnce();
    expect(servicio.paso()).toBe(4);
  });
  it('mantiene el dispositivo y normaliza la seleccion para reintentos', () => {
    const primera = servicio.cuerpo(['b', 'a', 'a']);
    expect(primera.puentesIds).toEqual(['a', 'b']);
    expect(servicio.cuerpo(['a', 'b'])).toEqual(primera);
  });

  it('sin conexion no inicia peticiones ni reemplaza datos', async () => {
    online.set(false);
    expect(await servicio.preparar(['p1'])).toBe(false);
    expect(api.preparar).not.toHaveBeenCalled();
    expect(local.guardar).not.toHaveBeenCalled();
  });
  it('administrador y profesional sin verificar no preparan', async () => {
    actor.set({ ...usuario, role: 'ADMINISTRADOR' });
    expect(await servicio.preparar(['p1'])).toBe(false);
    actor.set({ ...usuario, role: 'PROFESIONAL_EXTERNO', colegiadoVerificado: false });
    expect(await servicio.preparar(['p1'])).toBe(false);
    expect(api.preparar).not.toHaveBeenCalled();
  });
  it('un corte conserva la solicitud para reintentar con la misma clave', async () => {
    api.preparar.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
    expect(await servicio.preparar(['p1'])).toBe(false);
    expect(local.descartarSolicitud).not.toHaveBeenCalled();
    expect(local.guardar).not.toHaveBeenCalled();
    expect(await servicio.preparar(['p1'])).toBe(true);
    expect(api.preparar.mock.calls.map((c) => c[1])).toEqual(['k1', 'k1']);
  });
  it('4xx determinista permite corregir y comenzar otra solicitud', async () => {
    api.preparar.mockReturnValueOnce(
      throwError(
        () => new HttpErrorResponse({ status: 422, error: { detail: 'Seleccion invalida' } }),
      ),
    );
    expect(await servicio.preparar(['p1'])).toBe(false);
    expect(local.descartarSolicitud).toHaveBeenCalledWith('u1', 'k1');
    expect(servicio.error()).toBe('Seleccion invalida');
  });
  it('409 en proceso conserva la clave', async () => {
    api.preparar.mockReturnValueOnce(
      throwError(
        () => new HttpErrorResponse({ status: 409, error: { code: 'operacion_en_proceso' } }),
      ),
    );
    await servicio.preparar(['p1']);
    expect(local.descartarSolicitud).not.toHaveBeenCalled();
  });
  it('no muestra exito si falla la transaccion por espacio', async () => {
    local.guardar.mockRejectedValueOnce(new DOMException('Quota', 'QuotaExceededError'));
    expect(await servicio.preparar(['p1'])).toBe(false);
    expect(servicio.error()).toContain('espacio');
    expect(storage.requestPersistentStorage).not.toHaveBeenCalled();
  });
  it('denegar persistencia no invalida una salida guardada', async () => {
    storage.requestPersistentStorage.mockResolvedValue(false);
    expect(await servicio.preparar(['p1'])).toBe(true);
    expect(servicio.persistente()).toBe(false);
  });
  it('fallo de Storage API posterior al guardado se informa sin perder el exito', async () => {
    storage.requestPersistentStorage.mockRejectedValue(new Error('No soportado'));
    expect(await servicio.preparar(['p1'])).toBe(true);
    expect(servicio.aviso()).toContain('esta guardada');
  });
  it('respuesta de cuenta anterior no se guarda tras cambiar sesion', async () => {
    const pendiente = new Subject<PreparacionSalida>();
    api.preparar.mockReturnValue(pendiente);
    const operacion = servicio.preparar(['p1']);
    await Promise.resolve();
    await Promise.resolve();
    actor.set({ ...usuario, id: 'u2' });
    pendiente.next(paquete);
    pendiente.complete();
    expect(await operacion).toBe(false);
    expect(local.guardar).not.toHaveBeenCalled();
    expect(servicio.ultima()).toBeNull();
  });
  it('avisa si cambia el formulario activo y oculta el resumen al cambiar usuario', async () => {
    local.ultima.mockResolvedValue(resumen);
    formulario.obtenerActiva.mockReturnValue(of({ id: 'v4' }));
    TestBed.tick();
    await vi.waitFor(() => expect(servicio.ultima()?.id).toBe('s1'));
    await servicio.comprobarVersion();
    expect(servicio.nuevaVersion()).toBe(true);
    actor.set({ ...usuario, id: 'u2' });
    expect(servicio.ultima()).toBeNull();
    expect(servicio.nuevaVersion()).toBe(false);
  });
  it('no ejecuta dos preparaciones simultaneas desde la pantalla', async () => {
    const pendiente = new Subject<PreparacionSalida>();
    api.preparar.mockReturnValue(pendiente);
    const primera = servicio.preparar(['p1']);
    expect(await servicio.preparar(['p1'])).toBe(false);
    await Promise.resolve();
    await Promise.resolve();
    pendiente.next(paquete);
    pendiente.complete();
    expect(await primera).toBe(true);
    expect(api.preparar).toHaveBeenCalledOnce();
  });
});
