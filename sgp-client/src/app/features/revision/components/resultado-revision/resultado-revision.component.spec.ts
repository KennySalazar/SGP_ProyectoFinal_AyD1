import { AprobacionCalibracionComponent } from '../../../formulario/components/aprobacion-calibracion/aprobacion-calibracion.component';
import { AprobacionCalibracionApiService } from '../../../formulario/services/aprobacion-calibracion-api.service';
import { of as estadoRespaldo } from 'rxjs';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../../../../core/services/auth.store';
import { traduccionesDePrueba } from '../../../formulario/motor/testing/traducciones';
import { RevisionApiService } from '../../services/revision-api.service';
import { ResultadoRevisionComponent } from './resultado-revision.component';
const ciclo = {
  revisionId: 'r',
  inspeccionId: 'i',
  autorId: 'autor',
  revisorId: 'revisor',
  numeroCiclo: 1,
  estadoRevision: 'EN_CURSO',
  estadoInspeccion: 'EN_REVISION',
  veredicto: null,
  calificacion: null,
  observacionGeneral: null,
  enviadaEn: '2026-10-19T12:00:00Z',
  resueltaEn: null,
};
const lista = {
  revisionId: 'r',
  observaciones: [],
  total: 0,
  pendientes: 0,
  general: { revisionId: 'r', texto: null },
};
describe('Veredicto privado HU-038', () => {
  const user = signal<{ id: string; role: string } | null>({
    id: 'revisor',
    role: 'CATEDRATICO',
  });
  const api = {
    historial: vi.fn(),
    observaciones: vi.fn(),
    estadoRevision: vi.fn(),
    emitirVeredicto: vi.fn(),
    atenderCambios: vi.fn(),
    resolverObservacion: vi.fn(),
  };
  let fixture: ReturnType<typeof TestBed.createComponent<ResultadoRevisionComponent>>;
  beforeEach(() => {
    user.set({ id: 'revisor', role: 'CATEDRATICO' });
    vi.resetAllMocks();
    api.historial.mockReturnValue(of([ciclo]));
    api.observaciones.mockReturnValue(of(lista));
    api.estadoRevision.mockReturnValue(
      of({
        calculo: {
          estadoCalculado: 'REGULAR',
          hashEntrada: 'a'.repeat(64),
          indiceCalculado: 50,
        },
      }),
    );
    api.emitirVeredicto.mockReturnValue(
      of({
        ...ciclo,
        estadoRevision: 'RESUELTA',
        estadoInspeccion: 'PUBLICADA',
        veredicto: 'APROBADA',
        calificacion: 90,
      }),
    );
    TestBed.overrideComponent(AprobacionCalibracionComponent, {
      set: { template: '', imports: [] },
    });
    TestBed.configureTestingModule({
      imports: [ResultadoRevisionComponent, traduccionesDePrueba()],
      providers: [
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
        provideRouter([]),
        { provide: AuthStore, useValue: { user } },
        { provide: RevisionApiService, useValue: api },
      ],
    });
    fixture = TestBed.createComponent(ResultadoRevisionComponent);
    fixture.componentRef.setInput('inspeccionId', 'i');
    fixture.componentRef.setInput('revisionId', 'r');
    fixture.detectChanges();
  });
  afterEach(() => fixture.destroy());
  const abrir = () => {
    fixture.componentInstance.abrir();
    fixture.detectChanges();
    return fixture.componentInstance;
  };
  it('identifica claramente la accion del catedratico sin enviar un veredicto al abrir', () => {
    const html = fixture.nativeElement as HTMLElement;
    const boton = Array.from(html.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Abrir calificacion y veredicto'),
    );
    expect(boton).toBeDefined();
    boton!.click();
    fixture.detectChanges();
    expect(api.historial).toHaveBeenCalledWith('i');
    expect(api.emitirVeredicto).not.toHaveBeenCalled();
    expect(html.textContent).toContain('Revisar y confirmar decision');
  });
  it('conserva el acceso de consulta del estudiante sin ofrecerle emitir veredicto', () => {
    user.set({ id: 'autor', role: 'ESTUDIANTE' });
    fixture.detectChanges();
    const html = fixture.nativeElement as HTMLElement;
    expect(html.textContent).toContain('Consultar resultado e historial');
    expect(html.textContent).not.toContain('Abrir calificacion y veredicto');
    abrir();
    expect(html.querySelector('form')).toBeNull();
    expect(api.emitirVeredicto).not.toHaveBeenCalled();
  });
  it('cierra el panel sin enviar veredicto y permite volver a abrirlo', () => {
    const c = abrir();
    const html = fixture.nativeElement as HTMLElement;
    const boton = Array.from(html.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Cerrar panel'),
    );
    expect(boton).toBeDefined();
    boton!.click();
    fixture.detectChanges();
    expect(c.abierto()).toBe(false);
    expect(html.querySelector('form')).toBeNull();
    expect(html.textContent).toContain('Abrir calificacion y veredicto');
    expect(api.emitirVeredicto).not.toHaveBeenCalled();
    abrir();
    expect(c.abierto()).toBe(true);
    expect(html.querySelector('form')).not.toBeNull();
  });
  it('no permite cerrar mientras hay una operacion pendiente', () => {
    const c = abrir();
    c.ocupado.set(true);
    c.cerrar();
    expect(c.abierto()).toBe(true);
    expect(api.emitirVeredicto).not.toHaveBeenCalled();
  });
  it('consulta datos privados solo al abrir el seguimiento', () => {
    expect(api.historial).not.toHaveBeenCalled();
    abrir();
    expect(api.historial).toHaveBeenCalledWith('i');
  });
  it('valida nota y exige justificación al ajustar el estado', () => {
    const c = abrir();
    c.form.controls.calificacion.setValue(0);
    expect(c.valido()).toBe(true);
    for (const valor of [-1, 100.01, 1.001]) {
      c.form.controls.calificacion.setValue(valor);
      expect(c.valido()).toBe(false);
    }
    c.form.controls.calificacion.setValue(100);
    c.form.controls.estado.setValue('BUENO');
    expect(c.valido()).toBe(false);
    c.form.controls.justificacion.setValue('Justificación');
    expect(c.valido()).toBe(true);
  });
  it('descarta el cálculo anterior mientras actualiza un conflicto de publicación', () => {
    const c = abrir();
    c.form.controls.calificacion.setValue(90);
    expect(c.valido()).toBe(true);
    const actualizacion = new Subject<unknown>();
    api.estadoRevision.mockReturnValue(actualizacion);
    c.cargar();
    expect(c.calculo()).toBeNull();
    expect(c.valido()).toBe(false);
    actualizacion.next({
      calculo: {
        estadoCalculado: 'BUENO',
        hashEntrada: 'b'.repeat(64),
        indiceCalculado: 80,
      },
    });
    expect(c.valido()).toBe(true);
    expect(c.calculo()?.calculo.hashEntrada).toBe('b'.repeat(64));
  });
  it('no aprueba observaciones pendientes y exige general para otros veredictos', () => {
    api.observaciones.mockReturnValue(of({ ...lista, pendientes: 1 }));
    const c = abrir();
    c.form.controls.calificacion.setValue(75);
    expect(c.valido()).toBe(false);
    c.form.controls.veredicto.setValue('RECHAZADA');
    expect(c.valido()).toBe(false);
    c.form.controls.observacionGeneral.setValue('Rechazo justificado');
    expect(c.valido()).toBe(true);
  });
  it('confirmación explícita conserva hash y nota y solo se puede enviar una vez', () => {
    const c = abrir();
    c.form.controls.calificacion.setValue(90);
    c.emitir();
    expect(api.emitirVeredicto).not.toHaveBeenCalled();
    c.preparar();
    c.emitir();
    expect(api.emitirVeredicto).toHaveBeenCalledWith(
      'r',
      expect.objectContaining({
        calificacion: 90,
        veredicto: 'APROBADA',
        confirmacionEstado: {
          estadoConfirmado: 'REGULAR',
          hashEntrada: 'a'.repeat(64),
          justificacion: null,
        },
      }),
    );
    c.preparar();
    c.emitir();
    expect(api.emitirVeredicto).toHaveBeenCalledTimes(1);
  });
  it('el estudiante consulta su nota sin controles de veredicto', () => {
    user.set({ id: 'autor', role: 'ESTUDIANTE' });
    fixture.detectChanges();
    api.historial.mockReturnValue(
      of([
        {
          ...ciclo,
          estadoRevision: 'RESUELTA',
          calificacion: 87.65,
          veredicto: 'CAMBIOS_SOLICITADOS',
        },
      ]),
    );
    const c = abrir();
    expect(c.puedeEmitir()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('87.65');
    expect(api.estadoRevision).not.toHaveBeenCalled();
  });
  it('cambiar de sesión cancela peticiones y elimina la calificación visible', () => {
    api.historial.mockReturnValue(
      of([
        {
          ...ciclo,
          estadoRevision: 'RESUELTA',
          veredicto: 'APROBADA',
          calificacion: 87.65,
        },
      ]),
    );
    abrir();
    expect(fixture.nativeElement.textContent).toContain('87.65');
    user.set({ id: 'otro', role: 'CATEDRATICO' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('87.65');
    expect(fixture.componentInstance.historial()).toEqual([]);
    const tardia = new Subject<unknown>();
    api.historial.mockReturnValue(tardia);
    abrir();
    user.set(null);
    fixture.detectChanges();
    tardia.next([{ ...ciclo, calificacion: 95 }]);
    expect(fixture.componentInstance.historial()).toEqual([]);
  });
  it('no conserva calificaciones si el servidor deniega el acceso', () => {
    api.historial.mockReturnValue(
      throwError(() => ({ status: 403, error: { detail: 'No autorizado' } })),
    );
    const c = abrir();
    expect(c.ciclo()).toBeNull();
    expect(c.historial()).toEqual([]);
  });
  it('atender cambios habilita la corrección del estudiante sin crear otro editor', () => {
    user.set({ id: 'autor', role: 'ESTUDIANTE' });
    fixture.detectChanges();
    const cambios = {
      ...ciclo,
      estadoRevision: 'RESUELTA',
      estadoInspeccion: 'CAMBIOS_SOLICITADOS',
      veredicto: 'CAMBIOS_SOLICITADOS',
      calificacion: 0,
    };
    api.historial.mockReturnValue(of([cambios]));
    api.atenderCambios.mockReturnValue(of({ ...cambios, estadoInspeccion: 'BORRADOR' }));
    const c = abrir();
    expect(c.puedeResolver()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('0 / 100');
    c.atenderCambios();
    fixture.detectChanges();
    expect(api.atenderCambios).toHaveBeenCalledWith('r');
    expect(c.puedeResolver()).toBe(true);
    expect(fixture.nativeElement.querySelector('a[href="/inspecciones/i"]')).not.toBeNull();
  });
  it('resuelve con respuesta opcional y actualiza los pendientes sin alterar el ciclo', () => {
    user.set({ id: 'autor', role: 'ESTUDIANTE' });
    fixture.detectChanges();
    api.historial.mockReturnValue(
      of([
        {
          ...ciclo,
          estadoRevision: 'RESUELTA',
          estadoInspeccion: 'BORRADOR',
          veredicto: 'CAMBIOS_SOLICITADOS',
        },
      ]),
    );
    api.observaciones
      .mockReturnValueOnce(of({ ...lista, pendientes: 1 }))
      .mockReturnValue(of(lista));
    api.resolverObservacion.mockReturnValue(of({ id: 'obs', estado: 'RESUELTA' }));
    const c = abrir();
    c.respuesta('obs').setValue(' Corregido ');
    c.resolverObservacion('obs');
    expect(api.resolverObservacion).toHaveBeenCalledWith('r', 'obs', 'Corregido');
    expect(c.observaciones()?.pendientes).toBe(0);
    expect(api.emitirVeredicto).not.toHaveBeenCalled();
  });
  it('los ciclos anteriores y los estados terminales permanecen de solo lectura', () => {
    user.set({ id: 'autor', role: 'ESTUDIANTE' });
    fixture.detectChanges();
    api.historial.mockReturnValue(
      of([
        { ...ciclo, revisionId: 'r2', numeroCiclo: 2 },
        {
          ...ciclo,
          estadoRevision: 'RESUELTA',
          estadoInspeccion: 'BORRADOR',
          veredicto: 'CAMBIOS_SOLICITADOS',
        },
      ]),
    );
    const c = abrir();
    expect(c.puedeResolver()).toBe(false);
    c.atenderCambios();
    expect(api.atenderCambios).not.toHaveBeenCalled();
    for (const estadoInspeccion of ['PUBLICADA', 'RECHAZADA']) {
      api.historial.mockReturnValue(
        of([
          {
            ...ciclo,
            estadoRevision: 'RESUELTA',
            estadoInspeccion,
            veredicto: estadoInspeccion === 'PUBLICADA' ? 'APROBADA' : 'RECHAZADA',
          },
        ]),
      );
      c.cargar();
      expect(c.puedeResolver()).toBe(false);
      c.resolverObservacion('obs');
    }
    expect(api.resolverObservacion).not.toHaveBeenCalled();
  });
});
