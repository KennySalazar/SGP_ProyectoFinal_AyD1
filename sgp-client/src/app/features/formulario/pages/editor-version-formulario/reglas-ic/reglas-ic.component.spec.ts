import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoService, TranslocoPipe } from '@jsverse/transloco';
import { Subject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../../core/services/api-error.service';
import { VersionFormulario } from '../../../models/formulario.models';
import { FormularioApiService } from '../../../services/formulario-api.service';
import { ReglasIcComponent } from './reglas-ic.component';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

function version(): VersionFormulario {
  return {
    id: 'v1',
    codigo: 'v1',
    estado: 'BORRADOR',
    activa: false,
    esquema: { type: 'object' },
    vinculosAlmacenamiento: { original: true },
    reglasEvaluacionIc: {
      elementos: [],
      reglas_evaluacion_ic: {
        version_algoritmo: 'ic-prueba',
        escala_severidad: { NO_PRESENTA: 0, LEVE: 1, MODERADO: 2, SEVERO: 4 },
        agregacion: {
          indicadores_de_un_elemento: 'MAXIMO',
          instancias_repetibles: 'PROMEDIO',
          elementos_ausentes_portantes: 4,
          elementos_ausentes_no_portantes: 3,
        },
        rangos_estado: { BUENO: 75, REGULAR: 50, MALO: 0 },
        reglas_anulacion: [{ id: 'RN-IC-05' }],
      },
    },
    versionAlgoritmoIc: 'ic-prueba',
    creadoPorId: 'u1',
    creadoEn: '',
    actualizadoEn: '',
    version: 0,
    pesos: [],
    mapeos: [],
  };
}

describe('Editor visual de reglas IC', () => {
  let fixture: ComponentFixture<ReglasIcComponent>;
  let component: ReglasIcComponent;
  let actualizar: ReturnType<typeof vi.fn>;
  let normalize: ReturnType<typeof vi.fn>;
  beforeEach(async () => {
    actualizar = vi.fn().mockReturnValue(of(version()));
    normalize = vi.fn();
    TestBed.configureTestingModule({
      imports: [ReglasIcComponent],
      providers: [
        {
          provide: TranslocoService,
          useValue: { translate: (key: string) => key },
        },
        {
          provide: FormularioApiService,
          useValue: { actualizarBorrador: actualizar },
        },
        { provide: ApiErrorService, useValue: { normalize } },
      ],
    });
    TestBed.overrideComponent(ReglasIcComponent, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    await TestBed.compileComponents();
    fixture = TestBed.createComponent(ReglasIcComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('version', version());
    fixture.detectChanges();
  });
  it('carga las reglas guardadas y comunica cambios pendientes al editar', () => {
    expect(component.formulario.valid).toBe(true);
    const emit = vi.spyOn(component.estado, 'emit');
    component.formulario.controls[
      JSON.stringify(['agregacion', 'elementos_ausentes_no_portantes'])
    ].setValue(2);
    expect(emit).toHaveBeenLastCalledWith({
      pendiente: true,
      guardando: false,
    });
    expect(component.errores()).toEqual([]);
  });
  it('envia el esquema vigente y conserva escala y condiciones criticas', () => {
    component.formulario.controls[
      JSON.stringify(['agregacion', 'elementos_ausentes_no_portantes'])
    ].setValue(2);
    component.guardar();
    expect(actualizar).toHaveBeenCalledWith(
      'v1',
      expect.objectContaining({
        esquema: { type: 'object' },
        reglasEvaluacionIc: expect.objectContaining({
          reglas_evaluacion_ic: expect.objectContaining({
            rangos_estado: { BUENO: 75, REGULAR: 50, MALO: 0 },
            escala_severidad: {
              NO_PRESENTA: 0,
              LEVE: 1,
              MODERADO: 2,
              SEVERO: 4,
            },
            reglas_anulacion: [{ id: 'RN-IC-05' }],
          }),
        }),
      }),
      0,
    );
  });
  it('rechaza rangos invalidos sin enviar la solicitud', () => {
    component.formulario.controls[JSON.stringify(['rangos_estado', 'REGULAR'])].setValue(90);
    component.guardar();
    expect(actualizar).not.toHaveBeenCalled();
    expect(component.errores()).toContain('rangosInvalidos');
  });
  it('una version publicada deshabilita campos y no permite guardar', () => {
    fixture.componentRef.setInput('version', {
      ...version(),
      estado: 'PUBLICADA',
    });
    fixture.detectChanges();
    expect(component.formulario.disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('button[type="submit"]')).toBeNull();
    component.guardar();
    expect(actualizar).not.toHaveBeenCalled();
  });
  it('conserva los cambios ante un error del servidor e impide solicitudes duplicadas', () => {
    const pendiente = new Subject<VersionFormulario>();
    actualizar.mockReturnValue(pendiente);
    const clave = JSON.stringify(['agregacion', 'elementos_ausentes_no_portantes']);
    component.formulario.controls[clave].setValue(2);
    component.guardar();
    component.guardar();
    expect(actualizar).toHaveBeenCalledTimes(1);
    const error = { status: 409, error: { detail: 'Version inmutable' } };
    pendiente.error(error);
    expect(component.errores()).toEqual(['Version inmutable']);
    expect(component.formulario.controls[clave].value).toBe(2);
    expect(component.guardando()).toBe(false);
  });
  it('un bloqueo externo evita guardar', () => {
    fixture.componentRef.setInput('bloqueado', true);
    fixture.detectChanges();
    component.guardar();
    expect(actualizar).not.toHaveBeenCalled();
    expect(component.formulario.disabled).toBe(true);
  });
  it('descarta cambios y recupera los valores guardados sin llamar a la API', () => {
    const clave = JSON.stringify(['agregacion', 'elementos_ausentes_no_portantes']);
    component.formulario.controls[clave].setValue(2);
    const emit = vi.spyOn(component.estado, 'emit');
    component.restablecer();
    expect(component.formulario.controls[clave].value).toBe(3);
    expect(emit).toHaveBeenLastCalledWith({
      pendiente: false,
      guardando: false,
    });
    expect(actualizar).not.toHaveBeenCalled();
  });
  it('una actualizacion de pesos no borra la edicion pendiente de reglas', () => {
    const clave = JSON.stringify(['agregacion', 'elementos_ausentes_no_portantes']);
    component.formulario.controls[clave].setValue(2);
    fixture.componentRef.setInput('version', {
      ...version(),
      pesos: [{ id: 'p1', elementoRef: '/vigas', peso: 2, creadoEn: '' }],
    });
    fixture.detectChanges();
    expect(component.formulario.controls[clave].value).toBe(2);
  });
  it('normaliza los rangos del borrador sin modificar el original', () => {
    const borrador = version();
    const reglas = borrador.reglasEvaluacionIc['reglas_evaluacion_ic'] as Record<string, unknown>;
    reglas['rangos_estado'] = { BUENO: 80, REGULAR: 60, MALO: 0 };
    fixture.componentRef.setInput('version', borrador);
    fixture.detectChanges();
    expect(component.formulario.controls[JSON.stringify(['rangos_estado', 'BUENO'])].value).toBe(
      75,
    );
    expect(component.formulario.controls[JSON.stringify(['rangos_estado', 'REGULAR'])].value).toBe(
      50,
    );
    expect(reglas['rangos_estado']).toEqual({
      BUENO: 80,
      REGULAR: 60,
      MALO: 0,
    });
    component.guardar();
    expect(actualizar).toHaveBeenCalled();
  });
  it('conserva los rangos historicos de una version publicada', () => {
    const publicada = version();
    publicada.estado = 'PUBLICADA';
    const reglas = publicada.reglasEvaluacionIc['reglas_evaluacion_ic'] as Record<string, unknown>;
    reglas['rangos_estado'] = { BUENO: 80, REGULAR: 60, MALO: 0 };
    fixture.componentRef.setInput('version', publicada);
    fixture.detectChanges();
    expect(component.formulario.controls[JSON.stringify(['rangos_estado', 'BUENO'])].value).toBe(
      80,
    );
    expect(component.formulario.disabled).toBe(true);
    expect(component.errores()).toEqual([]);
    component.guardar();
    expect(actualizar).not.toHaveBeenCalled();
  });
});
