import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RoleName, UserSession } from '../../../../core/models/auth.models';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { ReglaMantenimiento, ResumenReglasVersion } from '../../models/regla-mantenimiento.models';
import { CatalogoReglasPage } from './catalogo-reglas.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

const BASE = '/api/v1/reglas-mantenimiento';
const PROVISIONAL: ReglaMantenimiento = {
  id: 'regla-1',
  codigo: 'RM-001',
  numeroRevision: 1,
  versionFormularioId: 'v1',
  versionFormularioCodigo: 'SIECA-v1',
  elementoRef: '/superestructura/elemento_portante_acero',
  condicion: { indicador: 'pintura', operador: 'IGUAL', umbral: 'MALO' },
  tipoMantenimiento: 'PREVENTIVO',
  intervencionSugerida: 'Pintura general',
  reglaPrioridad: { prioridad_base: 'MEDIA' },
  activa: true,
  provisional: true,
  estadoValidacion: 'Provisional · sin validar',
  creadoPorId: 'admin',
  validadoPorId: null,
  validadoEn: null,
  validadoPorNombre: null,
  rotuloValidacion: 'Provisional · sin validar',
  creadoEn: '2026-10-23T12:00:00Z',
};
const VALIDADA: ReglaMantenimiento = {
  ...PROVISIONAL,
  provisional: false,
  estadoValidacion: 'Validada',
  validadoPorId: 'docente-1',
  validadoPorNombre: 'Kenny Salazar',
  validadoEn: '2026-10-24T02:30:00Z',
  rotuloValidacion: 'Validada por Kenny Salazar el 23/10/2026',
};
const RESUMEN: ResumenReglasVersion = {
  versionFormularioId: 'v1',
  versionFormularioCodigo: 'SIECA-v1',
  tieneReglasActivas: true,
  totalReglas: 1,
  totalActivas: 1,
  totalProvisionales: 1,
  totalValidadas: 0,
  advertenciaSinReglasActivas: null,
};

describe('Catalogo de reglas HU055', () => {
  let fixture: ComponentFixture<CatalogoReglasPage>;
  let page: CatalogoReglasPage;
  let http: HttpTestingController;
  const user = signal<UserSession | null>(null);
  const normalize = vi.fn(
    (err: HttpErrorResponse) => err.error?.detail || 'No fue posible validar',
  );

  function rol(role: RoleName): void {
    user.set({
      id: 'usuario',
      email: 'usuario@cunoc.edu.gt',
      role,
      verified: true,
      activated: true,
      active: true,
      twoFactorEnabled: true,
    });
  }

  const parametros = new BehaviorSubject(convertToParamMap({}));

  beforeEach(async () => {
    parametros.next(convertToParamMap({}));
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe = vi.fn();
        unobserve = vi.fn();
        disconnect = vi.fn();
      },
    );
    normalize.mockClear();
    rol('CATEDRATICO');
    await TestBed.configureTestingModule({
      imports: [CatalogoReglasPage],
      providers: [
        { provide: ActivatedRoute, useValue: { queryParamMap: parametros } },
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: AuthStore, useValue: { user } },
        { provide: ApiErrorService, useValue: { normalize } },
      ],
    })
      .overrideComponent(CatalogoReglasPage, {
        remove: { imports: [TranslocoPipe] },
        add: { imports: [TraduccionTestPipe] },
      })
      .compileComponents();
    fixture = TestBed.createComponent(CatalogoReglasPage);
    page = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/v1/formularios').flush([
      { id: 'v1', codigo: 'SIECA-v1', activa: true, estado: 'PUBLICADA' },
      { id: 'v2', codigo: 'SIECA-v2', activa: false, estado: 'PUBLICADA' },
    ]);
    refrescar();
    fixture.detectChanges();
  });

  afterEach(() => {
    try {
      http.verify();
    } finally {
      TestBed.resetTestingModule();
      vi.unstubAllGlobals();
    }
  });

  function refrescar(reglas: ReglaMantenimiento[] = [PROVISIONAL], resumen = RESUMEN): void {
    http
      .expectOne((r) => r.url === BASE)
      .flush({ content: reglas, totalElements: reglas.length, totalPages: 1, size: 10, number: 0 });
    http.expectOne((r) => r.url === BASE + '/resumen').flush(resumen);
  }

  it('abre la version del aviso aunque no sea la activa y atiende otro aviso en la misma pagina', () => {
    parametros.next(convertToParamMap({ versionId: 'v2' }));
    const reglas = http.expectOne((r) => r.url === BASE);
    expect(reglas.request.params.get('versionId')).toBe('v2');
    reglas.flush({ content: [], totalElements: 0 });
    const resumen = http.expectOne((r) => r.url === BASE + '/resumen');
    expect(resumen.request.params.get('versionId')).toBe('v2');
    resumen.flush(RESUMEN);
    expect(page.versionSeleccionadaId()).toBe('v2');
    parametros.next(convertToParamMap({ versionId: 'v1' }));
    refrescar();
    expect(page.versionSeleccionadaId()).toBe('v1');
  });

  it('no sustituye una version inexistente por la activa', () => {
    parametros.next(convertToParamMap({ versionId: 'inexistente' }));
    expect(page.versionSeleccionadaId()).toBeNull();
    expect(page.reglas()).toEqual([]);
    expect(page.errorValidacion()).toContain('no esta disponible');
    http.expectNone((r) => r.url === BASE);
    page.cambiarVersion('v2');
    refrescar();
    expect(page.errorValidacion()).toBeNull();
  });

  function botonValidar(): HTMLButtonElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(
      '[aria-label="mantenimiento.validar"]',
    );
  }

  it.each<RoleName>(['ADMINISTRADOR', 'CATEDRATICO', 'ESTUDIANTE', 'PROFESIONAL_EXTERNO'])(
    'solo Catedratico puede abrir y confirmar validacion: %s',
    (role) => {
      rol(role);
      fixture.detectChanges();
      expect(botonValidar() !== null).toBe(role === 'CATEDRATICO');
      page.abrirValidar(PROVISIONAL);
      if (role === 'CATEDRATICO') {
        expect(page.reglaPorValidar()?.id).toBe(PROVISIONAL.id);
      } else {
        expect(page.reglaPorValidar()).toBeNull();
        page.reglaPorValidar.set(PROVISIONAL);
        page.confirmarValidar();
        http.expectNone(BASE + '/regla-1/validar');
      }
    },
  );

  it('una sesion ausente no valida', () => {
    user.set(null);
    page.abrirValidar(PROVISIONAL);
    expect(page.reglaPorValidar()).toBeNull();
    page.reglaPorValidar.set(PROVISIONAL);
    page.confirmarValidar();
    http.expectNone(BASE + '/regla-1/validar');
  });

  it('no ofrece validar reglas inactivas ni ya validadas', () => {
    page.reglas.set([{ ...PROVISIONAL, activa: false }, VALIDADA]);
    fixture.detectChanges();
    expect(botonValidar()).toBeNull();
    for (const regla of page.reglas()) {
      page.abrirValidar(regla);
      expect(page.reglaPorValidar()).toBeNull();
      page.reglaPorValidar.set(regla);
      page.confirmarValidar();
      page.reglaPorValidar.set(null);
    }
    http.expectNone(BASE + '/regla-1/validar');
  });

  it('Catedratico no ve ni ejecuta crear editar cambiar estado o sembrar', () => {
    const dom = fixture.nativeElement as HTMLElement;
    expect(dom.querySelector('[aria-label="mantenimiento.editar"]')).toBeNull();
    expect(dom.querySelector('[aria-label="Desactivar"]')).toBeNull();
    page.abrirCrear();
    page.guardarCrear();
    page.abrirEditar(PROVISIONAL);
    page.guardarEditar();
    page.abrirCambiarEstado(PROVISIONAL);
    page.confirmarCambiarEstado();
    page.sembrarCatalogo();
    expect(page.dialogCrearVisible()).toBe(false);
    expect(page.dialogEditarVisible()).toBe(false);
    expect(page.reglaPorCambiarEstado()).toBeNull();
    http.expectNone((r) => r.method !== 'GET');
  });

  it('Administrador conserva editar y desactivar pero no validar', () => {
    rol('ADMINISTRADOR');
    fixture.detectChanges();
    const dom = fixture.nativeElement as HTMLElement;
    expect(dom.querySelector('[aria-label="mantenimiento.editar"]')).not.toBeNull();
    expect(dom.querySelector('[aria-label="Desactivar"]')).not.toBeNull();
    expect(botonValidar()).toBeNull();
    page.abrirCrear();
    expect(page.dialogCrearVisible()).toBe(true);
    page.abrirEditar(PROVISIONAL);
    expect(page.dialogEditarVisible()).toBe(true);
    page.abrirCambiarEstado(PROVISIONAL);
    expect(page.reglaPorCambiarEstado()).toEqual(PROVISIONAL);
  });

  it('confirma una vez, actualiza detalle y consulta los contadores nuevamente', () => {
    page.abrirDetalle(PROVISIONAL);
    page.abrirValidar(PROVISIONAL);
    page.confirmarValidar();
    page.confirmarValidar();
    page.cerrarValidar();
    expect(page.validando()).toBe(true);
    expect(page.reglaPorValidar()?.id).toBe('regla-1');
    const req = http.expectOne(BASE + '/regla-1/validar');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    req.flush(VALIDADA);
    expect(page.validando()).toBe(false);
    expect(page.reglaPorValidar()).toBeNull();
    expect(page.reglaDetalle()).toEqual(VALIDADA);
    expect(page.reglas()).toEqual([VALIDADA]);
    refrescar([VALIDADA], { ...RESUMEN, totalProvisionales: 0, totalValidadas: 1 });
    fixture.detectChanges();
    expect(page.resumen()?.totalProvisionales).toBe(0);
    expect(page.resumen()?.totalValidadas).toBe(1);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(VALIDADA.rotuloValidacion);
    expect(botonValidar()).toBeNull();
  });

  it('deshabilita el boton de la fila mientras se valida', () => {
    page.abrirValidar(PROVISIONAL);
    page.confirmarValidar();
    fixture.detectChanges();
    expect(botonValidar()?.disabled).toBe(true);
    http.expectOne(BASE + '/regla-1/validar').flush(VALIDADA);
    refrescar([VALIDADA]);
  });

  it('al volver a cargar conserva el nombre y la fecha que responde el backend', () => {
    page.cargarReglas();
    http.expectOne((r) => r.url === BASE).flush({ content: [VALIDADA], totalElements: 1 });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(VALIDADA.rotuloValidacion);
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain(VALIDADA.validadoEn!);
    expect(botonValidar()).toBeNull();
  });

  it('un reintento responde con el primer validador sin reemplazarlo por el usuario actual', () => {
    page.abrirValidar(PROVISIONAL);
    page.confirmarValidar();
    http.expectOne(BASE + '/regla-1/validar').flush(VALIDADA);
    refrescar([VALIDADA]);
    expect(page.mensajeExito()).toBe(VALIDADA.rotuloValidacion);
    expect(page.reglas()[0].validadoPorId).toBe('docente-1');
  });

  it('409 cierra confirmacion y actualiza la revision vigente sin exito falso', () => {
    page.abrirDetalle(PROVISIONAL);
    page.abrirValidar(PROVISIONAL);
    page.confirmarValidar();
    http
      .expectOne(BASE + '/regla-1/validar')
      .flush(
        { code: 'regla_no_vigente', detail: 'Solo se puede validar la revision activa.' },
        { status: 409, statusText: 'Conflict' },
      );
    expect(page.reglaPorValidar()).toBeNull();
    expect(page.reglaDetalle()).toBeNull();
    expect(page.mensajeExito()).toBeNull();
    expect(page.validando()).toBe(false);
    refrescar([{ ...PROVISIONAL, activa: false }]);
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent,
    ).toContain('Solo se puede validar la revision activa.');
    expect(botonValidar()).toBeNull();
  });

  it('un fallo permite reintentar sin marcar la regla validada', () => {
    page.abrirValidar(PROVISIONAL);
    page.confirmarValidar();
    http
      .expectOne(BASE + '/regla-1/validar')
      .flush({ detail: 'Servicio no disponible' }, { status: 503, statusText: 'Unavailable' });
    expect(page.errorValidacion()).toBe('Servicio no disponible');
    expect(page.reglas()[0].provisional).toBe(true);
    expect(page.mensajeExito()).toBeNull();
    expect(page.validando()).toBe(false);
    page.confirmarValidar();
    http.expectOne(BASE + '/regla-1/validar').flush(VALIDADA);
    refrescar([VALIDADA]);
    expect(page.errorValidacion()).toBeNull();
  });

  it('una nueva revision del Administrador vuelve a mostrar provisional sin heredar nombre', () => {
    rol('ADMINISTRADOR');
    page.abrirEditar(VALIDADA);
    page.guardarEditar();
    const nueva = { ...PROVISIONAL, id: 'regla-2', numeroRevision: 2 };
    http.expectOne(BASE + '/regla-1').flush(nueva);
    refrescar([nueva]);
    rol('CATEDRATICO');
    fixture.detectChanges();
    expect(page.reglas()[0].validadoPorNombre).toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Provisional · sin validar',
    );
    expect(botonValidar()).not.toBeNull();
  });

  it('no aplica respuestas anteriores despues de cambiar la version', () => {
    page.cargarReglas();
    page.cargarResumen();
    const reglasViejas = http.expectOne((r) => r.url === BASE);
    const resumenViejo = http.expectOne((r) => r.url === BASE + '/resumen');
    page.cambiarVersion('v2');
    const nuevas = http.expectOne((r) => r.url === BASE);
    expect(nuevas.request.params.get('versionId')).toBe('v2');
    nuevas.flush({ content: [], totalElements: 0 });
    http
      .expectOne((r) => r.url === BASE + '/resumen')
      .flush({ ...RESUMEN, versionFormularioId: 'v2', totalReglas: 0 });
    reglasViejas.flush({ content: [VALIDADA], totalElements: 1 });
    resumenViejo.flush(RESUMEN);
    expect(page.reglas()).toEqual([]);
    expect(page.resumen()?.versionFormularioId).toBe('v2');
    expect(page.cargando()).toBe(false);
  });
});
