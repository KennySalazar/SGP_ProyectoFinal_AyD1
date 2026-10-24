import { SincronizacionService } from '../../../../offline/services/sincronizacion.service';
import { EMPTY, Subject } from 'rxjs';
import { LocalInspection } from '../../../../offline/db/sgp-db';
import { AprobacionCalibracionComponent } from '../../../formulario/components/aprobacion-calibracion/aprobacion-calibracion.component';
import { AprobacionCalibracionApiService } from '../../../formulario/services/aprobacion-calibracion-api.service';
import { of as estadoRespaldo } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Pipe, PipeTransform, signal, Component, input } from '@angular/core';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { describe, beforeEach, afterEach, it, expect, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { FormularioDinamicoComponent } from '../../../formulario/components/formulario-dinamico/formulario-dinamico.component';
import { FormularioApiService } from '../../../formulario/services/formulario-api.service';
import { GuardarBorradorRequest, InspeccionBorradorResponse } from '../../models/inspeccion.models';
import { InspeccionApiService } from '../../services/inspeccion-api.service';
import { DocumentosInspeccionComponent } from '../../components/documentos-inspeccion/documentos-inspeccion.component';
import { ReporteFotograficoComponent } from '../../components/reporte-fotografico/reporte-fotografico.component';
import { FotografiasLocalesService } from '../../../../offline/services/fotografias-locales.service';
import { ProcesadorFotografiasService } from '../../services/procesador-fotografias.service';
import { InspeccionesLocalesService } from '../../../../offline/services/inspecciones-locales.service';
import { ConnectivityService } from '../../../../offline/services/connectivity.service';
import { By } from '@angular/platform-browser';
import { ConfirmacionEstadoIcComponent } from '../../components/confirmacion-estado-ic/confirmacion-estado-ic.component';
import { CompletarInspeccionPage } from './completar-inspeccion.page';
import { ResultadoRevisionComponent } from '../../../revision/components/resultado-revision/resultado-revision.component';
@Pipe({ name: 'transloco', standalone: true })
class MockTranslocoPipe implements PipeTransform {
  transform(key: string, params?: Record<string, unknown>): string {
    if (params) {
      return `${key}:${JSON.stringify(params)}`;
    }
    return key;
  }
}

/** Los documentos PDF tienen su propia prueba; aquí no deben pedir nada al servidor. */
@Component({ selector: 'app-documentos-inspeccion', standalone: true, template: '' })
class DocumentosInspeccionStubComponent {
  readonly inspeccion = input<unknown>();
  readonly soloLectura = input(false);
}

describe('CompletarInspeccionPage', () => {
  let fixture: ComponentFixture<CompletarInspeccionPage>;
  let component: CompletarInspeccionPage;
  const mockBorrador: InspeccionBorradorResponse = {
    id: 'inspeccion-123',
    puenteId: 'puente-456',
    codigoPuente: 'P-01-01',
    nombrePuente: 'Puente Chiquito',
    versionFormularioId: 'form-v1',
    codigoVersionFormulario: 'sieca-v1',
    autorId: 'usr-1',
    rolAutor: 'CATEDRATICO',
    nombreAutor: 'Profesor Ingeniero',
    estado: 'BORRADOR',
    fechaInspeccion: '2026-10-15',
    dispositivoId: 'disp-01',
    datos: {
      identificacion: {
        nombre_puente: 'Puente Chiquito',
        codigo: 'P-01-01',
      },
    },
    acompanantes: [],
    version: 1,
    ultimaActividadEn: '2026-10-15T12:00:00Z',
    creadoEn: '2026-10-15T10:00:00Z',
    progreso: {
      totalCamposRequeridos: 79,
      totalCamposCompletados: 15,
      porcentajeTotal: 18.99,
      secciones: [
        {
          seccion: 'identificacion',
          titulo: '1. Identificación del puente',
          camposRequeridos: 7,
          camposCompletados: 5,
          porcentaje: 71.43,
          completada: false,
        },
        {
          seccion: 'datos_generales',
          titulo: '2. Datos generales',
          camposRequeridos: 11,
          camposCompletados: 10,
          porcentaje: 90.91,
          completada: false,
        },
        {
          seccion: 'tramos',
          titulo: '3. Tramos',
          camposRequeridos: 4,
          camposCompletados: 0,
          porcentaje: 0,
          completada: false,
        },
        {
          seccion: 'subestructura',
          titulo: '4. Sub-estructura',
          camposRequeridos: 27,
          camposCompletados: 0,
          porcentaje: 0,
          completada: false,
        },
        {
          seccion: 'superestructura',
          titulo: '5. Super-estructura',
          camposRequeridos: 7,
          camposCompletados: 0,
          porcentaje: 0,
          completada: false,
        },
        {
          seccion: 'elementos_no_estructurales',
          titulo: '6. Elementos no estructurales',
          camposRequeridos: 9,
          camposCompletados: 0,
          porcentaje: 0,
          completada: false,
        },
        {
          seccion: 'caracteristicas_cauce',
          titulo: '7. Características del cauce',
          camposRequeridos: 1,
          camposCompletados: 0,
          porcentaje: 0,
          completada: false,
        },
        {
          seccion: 'otros',
          titulo: '8. Otros',
          camposRequeridos: 4,
          camposCompletados: 0,
          porcentaje: 0,
          completada: false,
        },
      ],
    },
    icPreliminar: {
      indice: 82.5,
      estado: 'Bueno',
      etiqueta: 'Preliminar',
    },
  };
  const mockInspeccionApi = {
    obtenerBorrador: vi.fn(),
    guardarBorrador: vi.fn(),
    enviar: vi.fn(),
    publicar: vi.fn(),
    listarFotografias: vi.fn(),
  };
  const colegiadoPendiente = signal(false);
  const mockApiErrors = { lastMessage: signal<string | null>(null), clear: vi.fn() };
  const mockFormularioApi = {
    obtenerEsquemaPublicado: vi.fn(),
    obtenerActiva: vi.fn(),
  };
  let observacionLocal: Subject<LocalInspection>;
  const sync = { sincronizarAhora: vi.fn().mockResolvedValue(undefined) };
  const mockActivatedRoute = {
    snapshot: {
      paramMap: convertToParamMap({ id: 'inspeccion-123' }),
    },
  };
  beforeEach(() => {
    observacionLocal = new Subject<LocalInspection>();
    sync.sincronizarAhora.mockClear();
    vi.spyOn(FormularioDinamicoComponent.prototype, 'revisar').mockReturnValue(true);
    mockInspeccionApi.obtenerBorrador.mockReset();
    mockInspeccionApi.guardarBorrador.mockReset();
    mockInspeccionApi.enviar.mockReset();
    mockInspeccionApi.publicar.mockReset();
    mockApiErrors.clear.mockReset();
    mockInspeccionApi.listarFotografias.mockReturnValue(
      of(
        ['ACCESO', 'SUPERESTRUCTURA', 'SUBESTRUCTURA', 'CAUCE'].map((categoria) => ({
          id: categoria,
          categoria,
        })),
      ),
    );
    colegiadoPendiente.set(false);
    mockFormularioApi.obtenerEsquemaPublicado.mockReset();
    mockFormularioApi.obtenerActiva.mockReset();
    mockInspeccionApi.obtenerBorrador.mockReturnValue(of(mockBorrador));
    mockFormularioApi.obtenerEsquemaPublicado.mockReturnValue(
      of({
        id: 'form-v1',
        codigo: 'sieca-v1',
        version: 1,
        esquema: {
          $schema: 'https://json-schema.org/draft/2020-12/schema',
          type: 'object',
          secciones: [],
        },
        hashContenido: 'abc12345',
        publicadoEn: '2026-01-01T00:00:00Z',
      }),
    );
    TestBed.overrideComponent(AprobacionCalibracionComponent, {
      set: { template: '', imports: [] },
    });
    TestBed.configureTestingModule({
      imports: [CompletarInspeccionPage],
      providers: [
        { provide: SincronizacionService, useValue: sync },
        {
          provide: InspeccionesLocalesService,
          useValue: {
            observarInspeccion: () => observacionLocal,
            encolarAccionFinal: vi.fn().mockResolvedValue(undefined),
            guardarBorradorEnCache: vi.fn().mockResolvedValue(undefined),
            guardarBorradorLocal: vi
              .fn()
              .mockResolvedValue({ id: 'op1', idempotencyKey: 'clave-test' }),
            guardarEsquemaLocal: vi.fn().mockResolvedValue(undefined),
            marcarOperacionPendiente: vi.fn().mockResolvedValue(undefined),
            marcarOperacionParaIntervencion: vi.fn().mockResolvedValue(undefined),
            confirmarGuardado: vi.fn().mockResolvedValue(undefined),
            cachearBorradorCanonico: vi.fn().mockResolvedValue(undefined),
            tieneConflictoConcurrencia: vi.fn().mockResolvedValue(false),
            obtenerInspeccionLocal: vi.fn().mockResolvedValue(undefined),
            obtenerEsquemaActivoLocal: vi.fn().mockResolvedValue(undefined),
            obtenerEsquemaLocal: vi.fn().mockResolvedValue(undefined),
            actualizarBorradorCanonicoSiSinPendientes: vi.fn().mockResolvedValue(true),
          },
        },
        {
          provide: AprobacionCalibracionApiService,
          useValue: {
            actualizada: estadoRespaldo(),
            estado: () =>
              estadoRespaldo({
                estado: 'SIN_APROBAR',
                rotuloCalibracion: 'Calibracion provisional · sin aprobar',
              }),
            consultar: () => estadoRespaldo(null),
          },
        },
        { provide: InspeccionApiService, useValue: mockInspeccionApi },
        { provide: FormularioApiService, useValue: mockFormularioApi },
        { provide: ActivatedRoute, useValue: mockActivatedRoute },
        {
          provide: AuthStore,
          useValue: { colegiadoPendiente, user: signal({ id: 'usr-1' }) },
        },
        { provide: ApiErrorService, useValue: mockApiErrors },
        { provide: ConnectivityService, useValue: { online: signal(true) } },
        {
          provide: FotografiasLocalesService,
          useValue: {
            observarInventario: () => EMPTY,
            inventario: vi.fn().mockResolvedValue([]),
            guardarInventario: vi.fn().mockResolvedValue(undefined),
            guardarContexto: vi.fn().mockResolvedValue(undefined),
            pendientes: vi.fn().mockResolvedValue([]),
          },
        },
        { provide: ProcesadorFotografiasService, useValue: {} },
        { provide: TranslocoService, useValue: { translate: (key: string) => key } },
      ],
    });
    TestBed.overrideComponent(CompletarInspeccionPage, {
      remove: { imports: [TranslocoPipe, DocumentosInspeccionComponent] },
      add: { imports: [MockTranslocoPipe, DocumentosInspeccionStubComponent] },
    });
    TestBed.overrideComponent(ResultadoRevisionComponent, { set: { template: '' } });
    TestBed.overrideComponent(FormularioDinamicoComponent, { set: { template: '' } });
    TestBed.overrideComponent(ReporteFotograficoComponent, { set: { template: '' } });
    TestBed.overrideComponent(ConfirmacionEstadoIcComponent, { set: { template: '' } });

    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(CompletarInspeccionPage);
    component = fixture.componentInstance;
  });
  afterEach(() => {
    vi.useRealTimers();
    fixture.destroy();
    vi.restoreAllMocks();
  });

  async function cargar() {
    fixture.detectChanges();
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(component.cargando()).toBe(false);
    });
    fixture.detectChanges();
  }

  it('carga la información del borrador y restaura datos previamente guardados', async () => {
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(mockInspeccionApi.obtenerBorrador).toHaveBeenCalledWith('inspeccion-123', true);
    expect(component.borrador()).toEqual(mockBorrador);
    expect(component.cargando()).toBe(false);
    const progreso = component.borrador()?.progreso;
    expect(progreso?.secciones).toHaveLength(8);
    expect(progreso?.totalCamposRequeridos).toBe(79);
    expect(progreso?.totalCamposCompletados).toBe(15);
    expect(component.borrador()?.icPreliminar?.etiqueta).toBe('Preliminar');
    expect(component.borrador()?.icPreliminar?.indice).toBe(82.5);
  });

  it('renderiza borradores nuevos o reanudados que todavía no incluyen progreso', async () => {
    mockInspeccionApi.obtenerBorrador.mockReturnValue(
      of({ ...mockBorrador, progreso: undefined } as unknown as InspeccionBorradorResponse),
    );

    expect(() => fixture.detectChanges()).not.toThrow();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(component.borrador()?.progreso).toEqual({
      totalCamposRequeridos: 0,
      totalCamposCompletados: 0,
      porcentajeTotal: 0,
      secciones: [],
    });
  });

  it('integra el reporte del borrador reanudado y bloquea la transición durante cargas', async () => {
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    await fixture.whenStable();
    const reporte = fixture.debugElement.query(By.directive(ReporteFotograficoComponent))
      .componentInstance as ReporteFotograficoComponent;
    expect(reporte.inspeccion()!.id).toBe('inspeccion-123');
    expect(mockInspeccionApi.listarFotografias).toHaveBeenCalledWith('inspeccion-123');
    reporte.ocupado.set(true);
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    component.abrirConfirmacion();
    expect(component.confirmandoAccion()).toBe(false);
    reporte.ocupado.set(false);
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    component.abrirConfirmacion();
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(reporte.soloLectura()).toBe(true);
  });

  it('guarda parcialmente el borrador y actualiza versión y última actividad', async () => {
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    const borradorActualizado: InspeccionBorradorResponse = {
      ...mockBorrador,
      version: 2,
      ultimaActividadEn: '2026-10-15T12:30:00Z',
      progreso: {
        ...mockBorrador.progreso,
        totalCamposCompletados: 18,
        porcentajeTotal: 22.78,
      },
    };
    mockInspeccionApi.guardarBorrador.mockReturnValue(of(borradorActualizado));
    component.guardarBorrador();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(mockInspeccionApi.guardarBorrador).toHaveBeenCalledWith(
      'inspeccion-123',
      expect.objectContaining<Partial<GuardarBorradorRequest>>({
        version: 1,
        fechaInspeccion: '2026-10-15',
      }),
      'clave-test',
    );
    expect(component.borrador()?.version).toBe(2);
    expect(component.borrador()?.progreso.totalCamposCompletados).toBe(18);
    expect(component.mensajeExito()).toBe('Borrador guardado en el servidor.');
    expect(component.errorGuardado()).toBeNull();
  });
  it('guarda automáticamente los cambios después de la pausa de escritura', async () => {
    vi.useFakeTimers();
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.btn-guardar')).toBeNull();
    component.esquema.set(null);
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    mockInspeccionApi.guardarBorrador.mockReturnValue(of(mockBorrador));
    component.onValorChange(mockBorrador.datos!);
    component.onValorChange({ identificacion: { nombre_puente: 'Puente actualizado' } });
    vi.advanceTimersByTime(1199);
    expect(mockInspeccionApi.guardarBorrador).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(mockInspeccionApi.guardarBorrador).toHaveBeenCalledWith(
      'inspeccion-123',
      expect.objectContaining({
        version: 1,
        datos: {
          identificacion: {
            nombre_puente: 'Puente actualizado',
            fecha_inspeccion: '2026-10-15',
          },
        },
      }),
      'clave-test',
    );
    expect(component.estadoAutoguardado()).toBe('guardado');
    vi.useRealTimers();
  });
  it('maneja error 404 al cargar si el borrador no existe o no tiene permisos', async () => {
    mockInspeccionApi.obtenerBorrador.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(component.cargando()).toBe(false);
    expect(component.errorCarga()).toContain('no tiene permisos para acceder');
  });
  it('maneja error 403 si el usuario no es el autor del borrador', async () => {
    mockInspeccionApi.obtenerBorrador.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(component.cargando()).toBe(false);
    expect(component.errorCarga()).toContain('Solo el autor');
  });
  it('maneja conflicto 409 de concurrencia al guardar y preserva datos locales', async () => {
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    mockInspeccionApi.guardarBorrador.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              code: 'concurrencia_conflicto',
              message: 'Conflicto de concurrencia',
            },
          }),
      ),
    );
    component.guardarBorrador();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(component.guardando()).toBe(false);
    expect(component.errorGuardado()).toContain('Conflicto de concurrencia');
    expect(component.borrador()?.version).toBe(1);
  });
  it('carga y cachea el borrador del servidor ante un conflicto conocido sin autoguardar datos obsoletos', async () => {
    const borradorServidor: InspeccionBorradorResponse = {
      ...mockBorrador,
      version: 2,
      datos: { identificacion: { nombre_puente: 'Versión del servidor' } },
    };
    mockInspeccionApi.obtenerBorrador.mockReturnValue(of(borradorServidor));
    const locales = TestBed.inject(InspeccionesLocalesService);
    vi.spyOn(locales, 'tieneConflictoConcurrencia').mockResolvedValue(true);
    const guardarCache = vi.spyOn(locales, 'cachearBorradorCanonico').mockResolvedValue();
    const leerLocal = vi.spyOn(locales, 'obtenerInspeccionLocal');

    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    await vi.waitFor(() => expect(component.borrador()?.version).toBe(2));

    expect(mockInspeccionApi.obtenerBorrador).toHaveBeenCalledWith('inspeccion-123', true);
    expect(leerLocal).not.toHaveBeenCalled();
    expect(guardarCache).toHaveBeenCalledWith(borradorServidor, 'usr-1');
    expect(component.datosIniciales()?.datos).toEqual(borradorServidor.datos);
    expect(mockInspeccionApi.guardarBorrador).not.toHaveBeenCalled();
  });
  it('refresca en servidor al reabrir una copia sincronizada, sin usarla para ediciones pendientes', async () => {
    const borradorServidor: InspeccionBorradorResponse = {
      ...mockBorrador,
      version: 2,
      datos: { identificacion: { nombre_puente: 'Versión actual' } },
    };
    const borradorLocal = {
      ...mockBorrador,
      datos: { identificacion: { nombre_puente: 'Versión en caché' } },
    };
    mockInspeccionApi.obtenerBorrador.mockReturnValue(of(borradorServidor));
    const locales = TestBed.inject(InspeccionesLocalesService);
    vi.spyOn(locales, 'tieneConflictoConcurrencia').mockResolvedValue(false);
    vi.spyOn(locales, 'obtenerInspeccionLocal').mockResolvedValue({
      id: mockBorrador.id,
      puenteId: mockBorrador.puenteId,
      propietarioId: 'usr-1',
      estado: 'BORRADOR',
      actualizadoEn: mockBorrador.creadoEn,
      sincronizado: true,
      payload: { borrador: borradorLocal },
    });
    const refrescar = vi
      .spyOn(locales, 'actualizarBorradorCanonicoSiSinPendientes')
      .mockResolvedValue(true);

    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    await vi.waitFor(() => expect(component.borrador()?.version).toBe(2));

    expect(mockInspeccionApi.obtenerBorrador).toHaveBeenCalledWith('inspeccion-123', true);
    expect(refrescar).toHaveBeenCalledWith(borradorServidor, 'usr-1');
    expect(component.borrador()?.datos).toEqual(borradorServidor.datos);
  });
  it('maneja error 422 de fecha futura', async () => {
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    component.fechaInspeccion.set('2099-01-01');
    component.guardarBorrador();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(component.errorGuardado()).toContain('posterior a la fecha actual');
    expect(mockInspeccionApi.guardarBorrador).not.toHaveBeenCalled();
  });
  describe('HU-026: enviar o publicar la inspección', () => {
    function boton(): HTMLButtonElement | null {
      return fixture.nativeElement.querySelector('.btn-accion-final');
    }
    it('el Catedrático confirma una publicación pendiente sin adelantar el estado remoto', async () => {
      await cargar();
      component.abrirConfirmacion();
      expect(component.confirmandoAccion()).toBe(true);
      await component.confirmarAccion();
      fixture.detectChanges();
      expect(TestBed.inject(InspeccionesLocalesService).encolarAccionFinal).toHaveBeenCalledWith(
        'inspeccion-123',
        'publicar',
        expect.objectContaining({ version: 1 }),
      );
      expect(mockInspeccionApi.publicar).not.toHaveBeenCalled();
      expect(component.intencionFinal()?.estado).toBe('PENDIENTE');
      expect(component.borrador()?.estado).toBe('BORRADOR');
      expect(component.soloLectura()).toBe(true);
      expect(fixture.nativeElement.textContent).toContain('offline.hu061.pending');
    });
    it('el Estudiante encola el envío a revisión', async () => {
      mockInspeccionApi.obtenerBorrador.mockReturnValue(
        of({ ...mockBorrador, rolAutor: 'ESTUDIANTE' }),
      );
      await cargar();
      component.abrirConfirmacion();
      await component.confirmarAccion();
      expect(TestBed.inject(InspeccionesLocalesService).encolarAccionFinal).toHaveBeenCalledWith(
        'inspeccion-123',
        'enviar',
        expect.anything(),
      );
      expect(component.resultadoAccion()).toBeNull();
      expect(mockInspeccionApi.enviar).not.toHaveBeenCalled();
    });
    it('no encola cuando la revisión del formulario detecta campos pendientes', async () => {
      await cargar();
      const formulario = fixture.debugElement.query(
        By.directive(FormularioDinamicoComponent),
      ).componentInstance;
      vi.spyOn(formulario, 'revisar').mockReturnValue(false);
      component.abrirConfirmacion();
      expect(component.confirmandoAccion()).toBe(false);
      expect(component.errorAccion()?.mensaje).toBe('offline.hu061.incomplete');
      expect(TestBed.inject(InspeccionesLocalesService).encolarAccionFinal).not.toHaveBeenCalled();
    });
    it('incluye los cambios locales en la confirmación sin exigir conexión', async () => {
      await cargar();
      component.estadoAutoguardado.set('pendiente');
      vi.spyOn(TestBed.inject(ConnectivityService), 'online').mockReturnValue(false);
      component.abrirConfirmacion();
      await component.confirmarAccion();
      expect(component.intencionFinal()?.estado).toBe('PENDIENTE');
      expect(TestBed.inject(InspeccionesLocalesService).encolarAccionFinal).toHaveBeenCalled();
    });
    it('el Profesional externo con colegiado pendiente no puede publicar', async () => {
      mockInspeccionApi.obtenerBorrador.mockReturnValue(
        of({ ...mockBorrador, rolAutor: 'PROFESIONAL_EXTERNO' }),
      );
      colegiadoPendiente.set(true);
      fixture.detectChanges();
      await Promise.resolve();
      await Promise.resolve();
      fixture.detectChanges();
      expect(component.colegiadoPendiente()).toBe(true);
      expect(boton()?.disabled).toBe(true);
      expect(fixture.nativeElement.textContent).toContain(
        'inspecciones.transicion.colegiadoPendiente',
      );
      component.abrirConfirmacion();
      expect(component.confirmandoAccion()).toBe(false);
    });
    it('una inspección que ya no está en borrador no ofrece la acción', async () => {
      mockInspeccionApi.obtenerBorrador.mockReturnValue(of({ ...mockBorrador, estado: 'ENVIADA' }));
      fixture.detectChanges();
      await Promise.resolve();
      await Promise.resolve();
      fixture.detectChanges();
      expect(component.accionFinal()).toBeNull();
      expect(boton()).toBeNull();
    });
  });
  it('bloquea publicacion mientras se confirma el IC', async () => {
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    component.operandoIc.set(true);
    component.abrirConfirmacion();
    component.confirmarAccion();
    expect(component.confirmandoAccion()).toBe(false);
    expect(mockInspeccionApi.publicar).not.toHaveBeenCalled();
  });
  it('vuelve a comprobar cambios pendientes despues de abrir el dialogo', async () => {
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    component.abrirConfirmacion();
    component.estadoAutoguardado.set('pendiente');
    component.confirmarAccion();
    expect(mockInspeccionApi.publicar).not.toHaveBeenCalled();
  });
  it('muestra el rechazo persistido por el sincronizador y permite revisar el IC', async () => {
    await cargar();
    observacionLocal.next({
      propietarioId: 'usr-1',
      intencionFinal: {
        accion: 'publicar',
        estado: 'RECHAZADA',
        error: 'Revisa el calculo actualizado',
      },
    } as LocalInspection);
    fixture.detectChanges();
    expect(component.intencionFinal()?.error).toBe('Revisa el calculo actualizado');
    expect(component.soloLectura()).toBe(false);
    expect(component.resultadoAccion()).toBeNull();
  });
  it('no guarda el borrador despues de la confirmación remota de publicación', async () => {
    await cargar();
    observacionLocal.next({
      propietarioId: 'usr-1',
      estado: 'PUBLICADA',
      intencionFinal: {
        accion: 'publicar',
        estado: 'CONFIRMADA',
        resultado: { id: 'inspeccion-123', puenteId: 'puente-456', estado: 'PUBLICADA' },
      },
    } as LocalInspection);
    component.guardarBorrador();
    expect(component.borrador()?.estado).toBe('PUBLICADA');
    expect(mockInspeccionApi.guardarBorrador).not.toHaveBeenCalled();
  });
  it('conserva la fecha principal al cargar una identificacion con fecha distinta', async () => {
    mockInspeccionApi.obtenerBorrador.mockReturnValue(
      of({
        ...mockBorrador,
        datos: { identificacion: { fecha_inspeccion: '2026-10-18', ruta: 'P01' } },
      }),
    );
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(component.valoresIniciales()?.['identificacion']).toEqual(
      expect.objectContaining({
        fecha_inspeccion: '2026-10-15',
        ruta: 'P01',
      }),
    );
  });
  it('guarda la misma fecha en datos, identificacion y encabezado', async () => {
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    component.esquema.set(null);
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    mockInspeccionApi.guardarBorrador.mockReturnValue(of(mockBorrador));
    component.onValorChange({
      identificacion: { fecha_inspeccion: '2026-10-14', ruta: 'P01' },
    });
    component.guardarBorrador();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(mockInspeccionApi.guardarBorrador).toHaveBeenCalledWith(
      'inspeccion-123',
      expect.objectContaining({
        fechaInspeccion: '2026-10-14',
        identificacionCapturada: expect.objectContaining({
          fecha_inspeccion: '2026-10-14',
          ruta: 'P01',
        }),
        datos: expect.objectContaining({
          identificacion: expect.objectContaining({ fecha_inspeccion: '2026-10-14' }),
        }),
      }),
      'clave-test',
    );
  });
  it('integra la confirmacion del IC y el reporte fotografico para el autor', async () => {
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    await fixture.whenStable();
    const confirmacion = fixture.debugElement.query(By.directive(ConfirmacionEstadoIcComponent))
      .componentInstance as ConfirmacionEstadoIcComponent;
    const reporte = fixture.debugElement.query(By.directive(ReporteFotograficoComponent))
      .componentInstance as ReporteFotograficoComponent;
    expect(confirmacion.inspeccionId()).toBe(mockBorrador.id);
    expect(reporte.inspeccion()!.id).toBe(mockBorrador.id);
    confirmacion.ocupado.emit(true);
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(component.operandoIc()).toBe(true);
    const boton = fixture.nativeElement.querySelector('.btn-accion-final') as HTMLButtonElement;
    expect(boton.disabled).toBe(true);
    component.abrirConfirmacion();
    expect(component.confirmandoAccion()).toBe(false);
    confirmacion.ocupado.emit(false);
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(boton.disabled).toBe(false);
  });

  it('vuelve a comprobar cargas fotograficas antes de ejecutar la publicacion', async () => {
    await cargar();
    component.abrirConfirmacion();
    expect(component.confirmandoAccion()).toBe(true);
    const reporte = fixture.debugElement.query(By.directive(ReporteFotograficoComponent))
      .componentInstance as ReporteFotograficoComponent;
    reporte.ocupado.set(true);
    component.confirmarAccion();
    expect(mockInspeccionApi.publicar).not.toHaveBeenCalled();
    expect(mockInspeccionApi.enviar).not.toHaveBeenCalled();
  });
  it('conserva el editor si no se puede persistir la acción final', async () => {
    await cargar();
    vi.mocked(TestBed.inject(InspeccionesLocalesService).encolarAccionFinal).mockRejectedValue(
      new Error('Faltan datos'),
    );
    component.abrirConfirmacion();
    await component.confirmarAccion();
    expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
    expect(component.borrador()?.estado).toBe('BORRADOR');
    expect(component.errorAccion()?.mensaje).toBe('Faltan datos');
  });
  it('reintenta el guardado incierto con el cuerpo original aunque haya nuevas ediciones', async () => {
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    mockInspeccionApi.guardarBorrador.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 0 })),
    );
    component.guardarBorrador();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    const primera = structuredClone(mockInspeccionApi.guardarBorrador.mock.calls.at(-1)![1]);
    component.fechaInspeccion.set('2026-10-14');
    component.guardarBorrador();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    expect(mockInspeccionApi.guardarBorrador.mock.calls.at(-1)![1]).toEqual(primera);
    expect(component.fechaInspeccion()).toBe('2026-10-14');
  });
});
