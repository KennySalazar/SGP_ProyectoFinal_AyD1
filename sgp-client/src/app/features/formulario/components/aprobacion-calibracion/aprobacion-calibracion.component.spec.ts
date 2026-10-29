import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../../../../core/services/auth.store';
import { ConnectivityService } from '../../../../offline/services/connectivity.service';
import { traduccionesDePrueba } from '../../motor/testing/traducciones';
import { AprobacionCalibracionApiService } from '../../services/aprobacion-calibracion-api.service';
import { AprobacionCalibracion } from '../../models/aprobacion-calibracion.models';
import { AprobacionCalibracionComponent } from './aprobacion-calibracion.component';

describe('AprobacionCalibracionComponent', () => {
  const aprobacion: AprobacionCalibracion = {
    id: 'a1',
    versionFormularioId: 'v1',
    aprobadoPorId: 'c1',
    aprobadoPorNombre: 'Mario',
    aprobadoEn: '2026-10-23T02:30:00Z',
    observaciones: 'Respaldo academico',
    rotuloCalibracion: 'Aprobada por Mario el 22/10/2026',
  };
  const provisional = {
    versionFormularioId: 'v1',
    estado: 'SIN_APROBAR',
    rotuloCalibracion: 'Calibracion provisional · sin aprobar',
    aprobadoPorId: null,
    aprobadoPorNombre: null,
    aprobadoEn: null,
  };
  const aprobada = {
    ...provisional,
    estado: 'APROBADA',
    rotuloCalibracion: aprobacion.rotuloCalibracion,
    aprobadoPorId: 'c1',
    aprobadoPorNombre: 'Mario',
    aprobadoEn: aprobacion.aprobadoEn,
  };
  const user = signal({ role: 'CATEDRATICO' });
  const online = signal(true);
  const cambios = new Subject<string>();
  const api = {
    estado: vi.fn(),
    consultar: vi.fn(),
    aprobar: vi.fn(),
    actualizada: cambios.asObservable(),
  };
  let fixture: ComponentFixture<AprobacionCalibracionComponent>;
  let componente: AprobacionCalibracionComponent;
  const html = () => fixture.nativeElement as HTMLElement;
  beforeEach(async () => {
    vi.resetAllMocks();
    user.set({ role: 'CATEDRATICO' });
    online.set(true);
    api.estado.mockReturnValue(of(provisional));
    api.consultar.mockReturnValue(of(null));
    api.aprobar.mockReturnValue(of(aprobacion));
    await TestBed.configureTestingModule({
      imports: [AprobacionCalibracionComponent, traduccionesDePrueba()],
      providers: [
        { provide: AuthStore, useValue: { user } },
        { provide: ConnectivityService, useValue: { online } },
        { provide: AprobacionCalibracionApiService, useValue: api },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AprobacionCalibracionComponent);
    componente = fixture.componentInstance;
    fixture.componentRef.setInput('versionId', 'v1');
    fixture.componentRef.setInput('gestion', true);
    fixture.detectChanges();
  });
  afterEach(() => fixture.destroy());

  it('un Catedratico puede aprobar publicada sin comprobar acta PDF', () => {
    expect(componente.puedeAprobar()).toBe(true);
    expect(html().textContent).toContain('Aprobar calibración');
    componente.abrir();
    componente.confirmar();
    expect(api.aprobar).toHaveBeenCalledWith('v1', '');
    expect(componente.estado()?.estado).toBe('APROBADA');
    expect(componente.puedeAprobar()).toBe(false);
    fixture.detectChanges();
    expect(html().textContent).toContain(aprobacion.rotuloCalibracion);
    expect(html().textContent).toContain('Respaldo academico');
  });

  it.each(['ADMINISTRADOR', 'ESTUDIANTE', 'PROFESIONAL_EXTERNO'])(
    'el rol %s no puede aprobar ni invocando la accion',
    (rol) => {
      user.set({ role: rol });
      fixture.detectChanges();
      componente.abrir();
      componente.confirmar();
      expect(componente.dialogo()).toBe(false);
      expect(api.aprobar).not.toHaveBeenCalled();
      expect(html().textContent).not.toContain('Aprobar calibración');
    },
  );

  it('el borrador no consulta estado publicado ni permite aprobar', () => {
    api.estado.mockClear();
    api.consultar.mockClear();
    fixture.componentRef.setInput('estadoVersion', 'BORRADOR');
    fixture.detectChanges();
    expect(api.estado).not.toHaveBeenCalled();
    expect(api.consultar).not.toHaveBeenCalled();
    expect(componente.puedeAprobar()).toBe(false);
    expect(html().textContent).toContain('Los borradores no heredan aprobaciones');
  });

  it('una version nueva no conserva el respaldo de la anterior', () => {
    componente.abrir();
    componente.confirmar();
    fixture.componentRef.setInput('versionId', 'v2');
    fixture.componentRef.setInput('estadoVersion', 'BORRADOR');
    fixture.detectChanges();
    expect(componente.aprobacion()).toBeNull();
    expect(componente.estado()).toBeNull();
    expect(html().textContent).not.toContain(aprobacion.rotuloCalibracion);
  });

  it('cancelar no envia una aprobacion y descarta las observaciones del dialogo', () => {
    componente.abrir();
    componente.observaciones.setValue('Comentario');
    componente.cancelar();
    componente.confirmar();
    expect(api.aprobar).not.toHaveBeenCalled();
    expect(componente.observaciones.value).toBe('');
  });

  it('no permite observaciones mayores a 2000 caracteres', () => {
    componente.abrir();
    componente.observaciones.setValue('x'.repeat(2001));
    componente.confirmar();
    expect(api.aprobar).not.toHaveBeenCalled();
  });

  it('un doble clic envia una sola solicitud y bloquea el cierre mientras guarda', () => {
    const pendiente = new Subject<AprobacionCalibracion>();
    api.aprobar.mockReturnValue(pendiente);
    componente.abrir();
    componente.confirmar();
    componente.confirmar();
    componente.cancelar();
    expect(api.aprobar).toHaveBeenCalledTimes(1);
    expect(componente.dialogo()).toBe(true);
    pendiente.next(aprobacion);
    pendiente.complete();
    expect(componente.dialogo()).toBe(false);
  });

  it('ante aprobacion simultanea consulta y muestra al responsable ganador', () => {
    const emit = vi.spyOn(componente.aprobada, 'emit');
    api.aprobar.mockReturnValue(
      throwError(() => ({ status: 409, error: { code: 'calibracion_ya_aprobada' } })),
    );
    api.estado.mockReturnValue(of(aprobada));
    api.consultar.mockReturnValue(of(aprobacion));
    componente.abrir();
    componente.observaciones.setValue('Mi observacion');
    componente.confirmar();
    expect(componente.error()).toContain('Otro Catedrático ya aprobó');
    expect(componente.aprobacion()).toEqual(aprobacion);
    expect(componente.observaciones.value).toBe('Mi observacion');
    expect(componente.exito()).toBe(false);
    expect(componente.puedeAprobar()).toBe(false);
    expect(emit).toHaveBeenCalledWith(aprobacion);
  });

  it('un error de validacion conserva las observaciones y permite corregir', () => {
    api.aprobar.mockReturnValue(throwError(() => ({ status: 400 })));
    componente.abrir();
    componente.observaciones.setValue('Conforme');
    componente.confirmar();
    expect(componente.observaciones.value).toBe('Conforme');
    expect(componente.dialogo()).toBe(true);
    expect(componente.puedeAprobar()).toBe(true);
    expect(componente.exito()).toBe(false);
  });

  it('un rechazo 403 bloquea nuevos intentos', () => {
    api.aprobar.mockReturnValue(throwError(() => ({ status: 403 })));
    componente.abrir();
    componente.confirmar();
    expect(componente.error()).toContain('Solo un Catedrático autorizado');
    expect(componente.puedeAprobar()).toBe(false);
  });

  it('un conflicto por borrador no se anuncia como exito', () => {
    api.aprobar.mockReturnValue(
      throwError(() => ({
        status: 409,
        error: { code: 'calibracion_version_no_publicada' },
      })),
    );
    componente.abrir();
    componente.confirmar();
    expect(componente.error()).toContain('versión publicada');
    expect(componente.exito()).toBe(false);
    expect(componente.puedeAprobar()).toBe(false);
  });

  it('un envio incierto vuelve a consultar sin repetir POST', () => {
    api.aprobar.mockReturnValue(throwError(() => ({ status: 0 })));
    componente.abrir();
    componente.confirmar();
    expect(api.aprobar).toHaveBeenCalledTimes(1);
    expect(api.estado).toHaveBeenCalledTimes(2);
    expect(componente.error()).toContain('No fue posible confirmar');
    expect(componente.exito()).toBe(false);
  });

  it('muestra aviso sin inventar respaldo si falla la consulta', () => {
    api.estado.mockReturnValue(throwError(() => ({ status: 500 })));
    fixture.componentRef.setInput('versionId', 'v2');
    fixture.detectChanges();
    expect(componente.estado()).toBeNull();
    expect(componente.error()).toContain('No fue posible consultar');
    expect(componente.puedeAprobar()).toBe(false);
  });

  it('sin conexion no aprueba y al reconectar verifica otra vez', () => {
    online.set(false);
    fixture.detectChanges();
    componente.abrir();
    componente.confirmar();
    expect(api.aprobar).not.toHaveBeenCalled();
    expect(html().textContent).toContain('Sin conexión');
    online.set(true);
    fixture.detectChanges();
    expect(componente.puedeAprobar()).toBe(true);
  });

  it('la vista del IC consulta resumen sin observaciones ni acciones de gestion', () => {
    api.consultar.mockClear();
    fixture.componentRef.setInput('gestion', false);
    fixture.detectChanges();
    expect(api.consultar).not.toHaveBeenCalled();
    expect(componente.puedeAprobar()).toBe(false);
    cambios.next('v1');
    expect(api.estado).toHaveBeenCalledTimes(3);
  });

  it('muestra historial al Administrador y usa fecha de Guatemala', () => {
    api.estado.mockReturnValue(of(aprobada));
    api.consultar.mockReturnValue(of(aprobacion));
    user.set({ role: 'ADMINISTRADOR' });
    fixture.detectChanges();
    expect(html().textContent).toContain('Respaldo academico');
    expect(componente.fecha(aprobacion.aprobadoEn)).toContain('22/10/2026');
    expect(componente.puedeAprobar()).toBe(false);
  });
});
