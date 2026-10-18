import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Pipe, PipeTransform, signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { describe, beforeEach, it, expect, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { FormularioDinamicoComponent } from '../../../formulario/components/formulario-dinamico/formulario-dinamico.component';
import { FormularioApiService } from '../../../formulario/services/formulario-api.service';
import { GuardarBorradorRequest, InspeccionBorradorResponse } from '../../models/inspeccion.models';
import { InspeccionApiService } from '../../services/inspeccion-api.service';
import { ReporteFotograficoComponent } from '../../components/reporte-fotografico/reporte-fotografico.component';
import { FotografiasLocalesService } from '../../../../offline/services/fotografias-locales.service';
import { ProcesadorFotografiasService } from '../../services/procesador-fotografias.service';
import { By } from '@angular/platform-browser';
import { CompletarInspeccionPage } from './completar-inspeccion.page';

@Pipe({ name: 'transloco', standalone: true })
class MockTranslocoPipe implements PipeTransform {
  transform(key: string, params?: Record<string, unknown>): string {
    if (params) {
      return `${key}:${JSON.stringify(params)}`;
    }
    return key;
  }
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

  const mockActivatedRoute = {
    snapshot: {
      paramMap: convertToParamMap({ id: 'inspeccion-123' }),
    },
  };

  beforeEach(() => {
    mockInspeccionApi.obtenerBorrador.mockReset();
    mockInspeccionApi.guardarBorrador.mockReset();
    mockInspeccionApi.enviar.mockReset();
    mockInspeccionApi.publicar.mockReset();
    mockApiErrors.clear.mockReset();
    mockInspeccionApi.listarFotografias.mockReturnValue(of([]));
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

    TestBed.configureTestingModule({
      imports: [CompletarInspeccionPage],
      providers: [
        { provide: InspeccionApiService, useValue: mockInspeccionApi },
        { provide: FormularioApiService, useValue: mockFormularioApi },
        { provide: ActivatedRoute, useValue: mockActivatedRoute },
        { provide: AuthStore, useValue: { colegiadoPendiente, user: signal({ id: 'usr-1' }) } },
        { provide: ApiErrorService, useValue: mockApiErrors },
        {
          provide: FotografiasLocalesService,
          useValue: {
            guardarContexto: vi.fn().mockResolvedValue(undefined),
            pendientes: vi.fn().mockResolvedValue([]),
          },
        },
        { provide: ProcesadorFotografiasService, useValue: {} },
        { provide: TranslocoService, useValue: { translate: (key: string) => key } },
      ],
    });

    TestBed.overrideComponent(CompletarInspeccionPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [MockTranslocoPipe] },
    });
    // El formulario dinámico tiene sus propias pruebas; aquí solo importa la pantalla.
    TestBed.overrideComponent(FormularioDinamicoComponent, { set: { template: '' } });
    TestBed.overrideComponent(ReporteFotograficoComponent, { set: { template: '' } });

    fixture = TestBed.createComponent(CompletarInspeccionPage);
    component = fixture.componentInstance;
  });

  it('carga la información del borrador y restaura datos previamente guardados', () => {
    fixture.detectChanges();

    expect(mockInspeccionApi.obtenerBorrador).toHaveBeenCalledWith('inspeccion-123');
    expect(component.borrador()).toEqual(mockBorrador);
    expect(component.cargando()).toBe(false);

    // Progreso de 8 secciones presente
    const progreso = component.borrador()?.progreso;
    expect(progreso?.secciones).toHaveLength(8);
    expect(progreso?.totalCamposRequeridos).toBe(79);
    expect(progreso?.totalCamposCompletados).toBe(15);

    // IC preliminar presente
    expect(component.borrador()?.icPreliminar?.etiqueta).toBe('Preliminar');
    expect(component.borrador()?.icPreliminar?.indice).toBe(82.5);
  });

  it('integra el reporte del borrador reanudado y bloquea la transición durante cargas', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    const reporte = fixture.debugElement.query(By.directive(ReporteFotograficoComponent))
      .componentInstance as ReporteFotograficoComponent;
    expect(reporte.inspeccion().id).toBe('inspeccion-123');
    expect(mockInspeccionApi.listarFotografias).toHaveBeenCalledWith('inspeccion-123');
    reporte.ocupado.set(true);
    fixture.detectChanges();
    component.abrirConfirmacion();
    expect(component.confirmandoAccion()).toBe(false);
    reporte.ocupado.set(false);
    fixture.detectChanges();
    component.abrirConfirmacion();
    fixture.detectChanges();
    expect(reporte.soloLectura()).toBe(true);
  });

  it('guarda parcialmente el borrador y actualiza versión y última actividad', () => {
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

    expect(mockInspeccionApi.guardarBorrador).toHaveBeenCalledWith(
      'inspeccion-123',
      expect.objectContaining<Partial<GuardarBorradorRequest>>({
        version: 1,
        fechaInspeccion: '2026-10-15',
      }),
    );

    expect(component.borrador()?.version).toBe(2);
    expect(component.borrador()?.progreso.totalCamposCompletados).toBe(18);
    expect(component.mensajeExito()).toContain('guardado exitosamente');
    expect(component.errorGuardado()).toBeNull();
  });

  it('guarda automáticamente los cambios después de la pausa de escritura', () => {
    vi.useFakeTimers();
    fixture.detectChanges();
    // Sin formulario en pantalla se guardan los datos recibidos en memoria.
    component.esquema.set(null);
    fixture.detectChanges();
    mockInspeccionApi.guardarBorrador.mockReturnValue(of(mockBorrador));

    component.onValorChange(mockBorrador.datos!);
    component.onValorChange({ identificacion: { nombre_puente: 'Puente actualizado' } });
    vi.advanceTimersByTime(1199);
    expect(mockInspeccionApi.guardarBorrador).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(mockInspeccionApi.guardarBorrador).toHaveBeenCalledWith(
      'inspeccion-123',
      expect.objectContaining({
        version: 1,
        datos: { identificacion: { nombre_puente: 'Puente actualizado' } },
      }),
    );
    expect(component.estadoAutoguardado()).toBe('guardado');
    vi.useRealTimers();
  });

  it('maneja error 404 al cargar si el borrador no existe o no tiene permisos', () => {
    mockInspeccionApi.obtenerBorrador.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );

    fixture.detectChanges();

    expect(component.cargando()).toBe(false);
    expect(component.errorCarga()).toContain('no tiene permisos para acceder');
  });

  it('maneja error 403 si el usuario no es el autor del borrador', () => {
    mockInspeccionApi.obtenerBorrador.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );

    fixture.detectChanges();

    expect(component.cargando()).toBe(false);
    expect(component.errorCarga()).toContain('Solo el autor');
  });

  it('maneja conflicto 409 de concurrencia al guardar y preserva datos locales', () => {
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

    expect(component.guardando()).toBe(false);
    expect(component.errorGuardado()).toContain('Conflicto de concurrencia');
    // Mantiene los datos en memoria intactos
    expect(component.borrador()?.version).toBe(1);
  });

  it('maneja error 422 de fecha futura', () => {
    fixture.detectChanges();

    component.fechaInspeccion.set('2099-01-01');
    component.guardarBorrador();

    expect(component.errorGuardado()).toContain('posterior a la fecha actual');
    expect(mockInspeccionApi.guardarBorrador).not.toHaveBeenCalled();
  });

  describe('HU-026: enviar o publicar la inspección', () => {
    function boton(): HTMLButtonElement | null {
      return fixture.nativeElement.querySelector('.btn-accion-final');
    }

    it('el Catedrático publica tras confirmar y ve el resultado', () => {
      mockInspeccionApi.publicar.mockReturnValue(
        of({ id: 'inspeccion-123', puenteId: 'puente-456', estado: 'PUBLICADA' }),
      );
      fixture.detectChanges();

      expect(component.accionFinal()).toBe('publicar');
      expect(boton()?.textContent).toContain('inspecciones.transicion.publicar');

      component.abrirConfirmacion();
      expect(component.confirmandoAccion()).toBe(true);
      expect(mockInspeccionApi.publicar).not.toHaveBeenCalled();

      component.confirmarAccion();
      fixture.detectChanges();

      expect(mockInspeccionApi.publicar).toHaveBeenCalledWith('inspeccion-123');
      expect(mockInspeccionApi.enviar).not.toHaveBeenCalled();
      expect(component.confirmandoAccion()).toBe(false);
      expect(fixture.nativeElement.textContent).toContain('inspecciones.transicion.publicada');
    });

    it('el Estudiante envía a revisión', () => {
      mockInspeccionApi.obtenerBorrador.mockReturnValue(
        of({ ...mockBorrador, rolAutor: 'ESTUDIANTE' }),
      );
      mockInspeccionApi.enviar.mockReturnValue(
        of({ id: 'inspeccion-123', puenteId: 'puente-456', estado: 'ENVIADA' }),
      );
      fixture.detectChanges();

      expect(component.accionFinal()).toBe('enviar');
      component.abrirConfirmacion();
      component.confirmarAccion();
      fixture.detectChanges();

      expect(mockInspeccionApi.enviar).toHaveBeenCalledWith('inspeccion-123');
      expect(fixture.nativeElement.textContent).toContain('inspecciones.transicion.enviada');
    });

    it('un 422 detalla por separado los campos y las fotografías que faltan', () => {
      mockInspeccionApi.publicar.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 422,
              error: {
                code: 'inspeccion_incompleta',
                detail: 'Faltan 1 campos obligatorios y 2 categorias de fotografia.',
                errores: [
                  { campo: 'subestructura', mensaje: 'El campo subestructura es obligatorio' },
                  { campo: 'fotografias.ACCESO', mensaje: 'Falta foto' },
                  { campo: 'fotografias.CAUCE', mensaje: 'Falta foto' },
                ],
              },
            }),
        ),
      );
      fixture.detectChanges();

      component.abrirConfirmacion();
      component.confirmarAccion();
      fixture.detectChanges();

      expect(component.errorAccion()).toEqual({
        mensaje: 'Faltan 1 campos obligatorios y 2 categorias de fotografia.',
        campos: [{ campo: 'subestructura', mensaje: 'El campo subestructura es obligatorio' }],
        fotografias: ['ACCESO', 'CAUCE'],
      });
      expect(component.confirmandoAccion()).toBe(true);
      expect(component.resultadoAccion()).toBeNull();
      expect(mockApiErrors.clear).toHaveBeenCalled();
    });

    it('no permite la acción mientras hay cambios sin guardar', () => {
      fixture.detectChanges();
      component.estadoAutoguardado.set('pendiente');
      fixture.detectChanges();

      expect(boton()?.disabled).toBe(true);
      component.abrirConfirmacion();
      expect(component.confirmandoAccion()).toBe(false);
    });

    it('el Profesional externo con colegiado pendiente no puede publicar', () => {
      mockInspeccionApi.obtenerBorrador.mockReturnValue(
        of({ ...mockBorrador, rolAutor: 'PROFESIONAL_EXTERNO' }),
      );
      colegiadoPendiente.set(true);
      fixture.detectChanges();

      expect(component.colegiadoPendiente()).toBe(true);
      expect(boton()?.disabled).toBe(true);
      expect(fixture.nativeElement.textContent).toContain(
        'inspecciones.transicion.colegiadoPendiente',
      );
      component.abrirConfirmacion();
      expect(component.confirmandoAccion()).toBe(false);
    });

    it('una inspección que ya no está en borrador no ofrece la acción', () => {
      mockInspeccionApi.obtenerBorrador.mockReturnValue(of({ ...mockBorrador, estado: 'ENVIADA' }));
      fixture.detectChanges();

      expect(component.accionFinal()).toBeNull();
      expect(boton()).toBeNull();
    });
  });
});
