import { FormularioApiService } from '../../../formulario/services/formulario-api.service';
import { InspeccionesLocalesService } from '../../../../offline/services/inspecciones-locales.service';
import { ConnectivityService } from '../../../../offline/services/connectivity.service';
import { IniciarInspeccionRequest } from '../../models/inspeccion.models';
import { TranslocoPipe } from '@jsverse/transloco';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Pipe, PipeTransform, signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { describe, beforeEach, afterEach, it, expect, vi } from 'vitest';
import { AuthStore } from '../../../../core/services/auth.store';
import { UserSession } from '../../../../core/models/auth.models';
import { PuenteResponse } from '../../../puentes/models/puente.models';
import {
  DatosPermanentesResponse,
  IniciarInspeccionResponse,
} from '../../models/inspeccion.models';
import { InspeccionApiService } from '../../services/inspeccion-api.service';
import { IniciarInspeccionDialogComponent } from './iniciar-inspeccion-dialog.component';

@Pipe({ name: 'transloco', standalone: true })
class MockTranslocoPipe implements PipeTransform {
  transform(key: string, params?: Record<string, unknown>): string {
    if (params) {
      return `${key}:${JSON.stringify(params)}`;
    }
    return key;
  }
}

describe('IniciarInspeccionDialogComponent', () => {
  let fixture: ComponentFixture<IniciarInspeccionDialogComponent>;
  let component: IniciarInspeccionDialogComponent;

  const authStoreMock = {
    authenticated: signal(true),
    user: signal<UserSession | null>({
      id: 'usr-1',
      email: 'catedratico@cunoc.edu.gt',
      role: 'CATEDRATICO',
      active: true,
      activated: true,
      verified: true,
      twoFactorEnabled: false,
    }),
  };

  const puentePrueba: PuenteResponse = {
    id: '11111111-1111-1111-1111-111111111111',
    codigo: 'P-01-01-0001',
    nombre: 'Puente Los Esclavos',
    departamento: { id: 'd1', codigoIne: '01', nombre: 'Guatemala' },
    municipio: { id: 'm1', departamentoId: 'd1', codigoIne: '0101', nombre: 'Guatemala' },
    ruta: 'CA-1',
    kilometraje: 65.2,
    latitud: 14.6407,
    longitud: -90.5132,
    activo: true,
    estadoActual: 'Sin evaluar',
    indiceCondicionActual: null,
    fechaUltimaInspeccion: null,
    creadoEn: '2026-01-01T00:00:00Z',
    utm: {
      zona: 15,
      hemisferio: 'N',
      epsg: 32615,
      este: 549300,
      norte: 1615800,
    },
  };

  const mockApi = {
    iniciarInspeccion: vi.fn(),
    obtenerDatosPermanentes: vi.fn(),
  };

  const locales = {
    encolarInicio: vi.fn(async (_actor: string, solicitud: IniciarInspeccionRequest) => ({
      id: 'operacion',
      idempotencyKey: solicitud.id,
    })),
    confirmarInicio: vi.fn().mockResolvedValue(undefined),
    marcarOperacionParaIntervencion: vi.fn().mockResolvedValue(undefined),
    marcarOperacionPendiente: vi.fn().mockResolvedValue(undefined),
    guardarEsquemaLocal: vi.fn().mockResolvedValue(undefined),
    obtenerEsquemaActivoLocal: vi.fn().mockResolvedValue({ id: 'version' }),
    iniciarInspeccionLocal: vi.fn(),
  };

  beforeEach(() => {
    mockApi.iniciarInspeccion.mockReset();
    mockApi.obtenerDatosPermanentes.mockReset();
    mockApi.obtenerDatosPermanentes.mockReturnValue(of(null));

    TestBed.configureTestingModule({
      imports: [IniciarInspeccionDialogComponent],
      providers: [
        {
          provide: FormularioApiService,
          useValue: { obtenerActiva: () => of({ id: 'version', version: 1, esquema: {} }) },
        },
        { provide: InspeccionesLocalesService, useValue: locales },
        { provide: ConnectivityService, useValue: { online: signal(true) } },
        { provide: AuthStore, useValue: authStoreMock },
        { provide: InspeccionApiService, useValue: mockApi },
      ],
    });

    TestBed.overrideComponent(IniciarInspeccionDialogComponent, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [MockTranslocoPipe] },
    });

    fixture = TestBed.createComponent(IniciarInspeccionDialogComponent);
    component = fixture.componentInstance;
    vi.spyOn(component, 'cargarReporte').mockResolvedValue(undefined);
    component.puente = puentePrueba;
  });

  afterEach(() => {
    fixture.destroy();
    vi.clearAllMocks();
  });

  it('inicializa el formulario con la fecha actual y el correo del usuario', async () => {
    component.visible = true;
    component.ngOnChanges({
      visible: {
        currentValue: true,
        previousValue: false,
        firstChange: true,
        isFirstChange: () => true,
      },
    });
    fixture.detectChanges();

    expect(component.form.get('fechaInspeccion')?.value).toBe(component.hoy);
    expect(component.form.get('nombreAutor')?.value).toBe('catedratico@cunoc.edu.gt');
    expect(component.form.get('confirmarDatosPermanentes')?.value).toBe(false);
  });

  it('consulta datos técnicos permanentes si existen para el puente', async () => {
    const datosPermanentes: DatosPermanentesResponse = {
      longitudM: 80.5,
      numeroTramos: 3,
      tipologiaPuente: 'Viga T',
      materialSuperestructura: 'Concreto reforzado',
      anioConstruccion: 1990,
    };
    mockApi.obtenerDatosPermanentes.mockReturnValue(of(datosPermanentes));

    component.visible = true;
    component.ngOnChanges({
      visible: {
        currentValue: true,
        previousValue: false,
        firstChange: true,
        isFirstChange: () => true,
      },
    });
    fixture.detectChanges();

    expect(mockApi.obtenerDatosPermanentes).toHaveBeenCalledWith(puentePrueba.id);
    expect(component.datosPermanentes()).toEqual(datosPermanentes);
  });

  it('permite agregar y eliminar acompañantes', async () => {
    component.form.patchValue({
      nombreAcompanante: 'Ing. Carlos López',
      colegiadoAcompanante: '12345',
    });
    component.agregarAcompanante();

    expect(component.acompanantes()).toHaveLength(1);
    expect(component.acompanantes()[0]).toEqual({
      nombre: 'Ing. Carlos López',
      colegiado: '12345',
    });
    expect(component.form.get('nombreAcompanante')?.value).toBe('');

    component.eliminarAcompanante(0);
    expect(component.acompanantes()).toHaveLength(0);
  });

  it('rechaza fecha futura antes de enviar al backend', async () => {
    component.visible = true;
    component.ngOnChanges({
      visible: {
        currentValue: true,
        previousValue: false,
        firstChange: true,
        isFirstChange: () => true,
      },
    });
    fixture.detectChanges();

    component.form.patchValue({ fechaInspeccion: '2099-01-01' });
    component.iniciarInspeccion();
    await Promise.resolve();
    await Promise.resolve();

    expect(component.error()).toContain('posterior a la fecha actual');
    expect(mockApi.iniciarInspeccion).not.toHaveBeenCalled();
  });

  it('inicia inspección exitosa y emite inspeccionIniciada con resultado', async () => {
    const responseExitosa: IniciarInspeccionResponse = {
      id: '22222222-2222-2222-2222-222222222222',
      puenteId: puentePrueba.id,
      codigoPuente: puentePrueba.codigo,
      nombrePuente: puentePrueba.nombre,
      versionFormularioId: '33333333-3333-3333-3333-333333333333',
      codigoVersionFormulario: 'sieca-v1',
      autorId: 'usr-1',
      rolAutor: 'CATEDRATICO',
      nombreAutor: 'catedratico@cunoc.edu.gt',
      estado: 'BORRADOR',
      fechaInspeccion: component.hoy,
      dispositivoId: 'disp-test',
      advertenciaGps: true,
      mensajeAdvertenciaGps: 'El dispositivo se encuentra a más de 500 metros del puente',
      distanciaAlPuenteM: 1200,
      datosPermanentesConfirmados: false,
      acompanantes: [],
      creadoEn: '2026-10-15T12:00:00Z',
    };

    mockApi.iniciarInspeccion.mockReturnValue(of(responseExitosa));

    const spyEmision = vi.spyOn(component.inspeccionIniciada, 'emit');

    component.visible = true;
    component.ngOnChanges({
      visible: {
        currentValue: true,
        previousValue: false,
        firstChange: true,
        isFirstChange: () => true,
      },
    });
    fixture.detectChanges();

    component.iniciarInspeccion();
    await Promise.resolve();
    await Promise.resolve();

    expect(mockApi.iniciarInspeccion).toHaveBeenCalled();
    expect(component.resultado()).toEqual(responseExitosa);
    expect(spyEmision).toHaveBeenCalledWith(responseExitosa);
    expect(component.resultado()?.advertenciaGps).toBe(true);
  });

  it('muestra mensaje de error si el backend responde con error', async () => {
    mockApi.iniciarInspeccion.mockReturnValue(
      throwError(() => ({
        error: { message: 'El puente se encuentra inactivo.' },
      })),
    );

    component.visible = true;
    component.ngOnChanges({
      visible: {
        currentValue: true,
        previousValue: false,
        firstChange: true,
        isFirstChange: () => true,
      },
    });
    fixture.detectChanges();

    component.iniciarInspeccion();
    await Promise.resolve();
    await Promise.resolve();

    expect(component.error()).toBe('El puente se encuentra inactivo.');
    expect(component.guardando()).toBe(false);
  });

  it('conserva la solicitud y la clave en la cola local si se pierde la respuesta de inicio', async () => {
    mockApi.iniciarInspeccion.mockReturnValue(throwError(() => ({ status: 0 })));
    locales.iniciarInspeccionLocal.mockImplementation(
      async (solicitud: IniciarInspeccionRequest) => ({
        ...solicitud,
        autorId: 'usr-1',
        estado: 'BORRADOR',
      }),
    );
    component.form.patchValue({ fechaInspeccion: component.hoy });
    component.iniciarInspeccion();
    for (let paso = 0; paso < 6; paso++) await Promise.resolve();
    const [solicitud, clave] = mockApi.iniciarInspeccion.mock.calls[0];
    expect(clave).toBe(solicitud.id);
    expect(clave[14]).toBe('7');
    expect(locales.iniciarInspeccionLocal).toHaveBeenCalledWith(
      solicitud,
      'usr-1',
      'CATEDRATICO',
      'catedratico@cunoc.edu.gt',
      puentePrueba.nombre,
      puentePrueba.codigo,
      'Guatemala',
      puentePrueba.municipio!.nombre,
      'version',
      null,
      true,
    );
    expect(component.resultado()?.id).toBe(solicitud.id);
    expect(component.guardando()).toBe(false);
    expect(mockApi.iniciarInspeccion).toHaveBeenCalledOnce();
  });

  it('continúa las fotografías guardadas sin volver a crear la inspección', async () => {
    const contexto = {
      id: 'inspeccion-local',
      autorId: 'usr-1',
      puenteId: puentePrueba.id,
    } as IniciarInspeccionResponse;
    component.pendienteAnterior.set(contexto);
    component.continuarPendiente();
    expect(component.resultado()).toEqual(contexto);
    expect(mockApi.iniciarInspeccion).not.toHaveBeenCalled();
  });
  it('permite corregir el formulario y usa otro UUID despues de un error determinista', async () => {
    mockApi.iniciarInspeccion.mockReturnValue(
      throwError(() => ({ status: 422, error: { detail: 'Revise la fecha' } })),
    );
    component.form.patchValue({ fechaInspeccion: component.hoy });
    component.iniciarInspeccion();
    await Promise.resolve();
    await Promise.resolve();
    const primera = mockApi.iniciarInspeccion.mock.calls[0];
    expect(component.form.enabled).toBe(true);
    component.form.patchValue({ nombreAutor: 'Corregido' });
    component.iniciarInspeccion();
    await Promise.resolve();
    await Promise.resolve();
    const segunda = mockApi.iniciarInspeccion.mock.calls[1];
    expect(segunda[0].nombreAutor).toBe('Corregido');
    expect(segunda[0].id).not.toBe(primera[0].id);
    expect(segunda[1]).not.toBe(primera[1]);
  });
});
