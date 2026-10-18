import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CalibracionAdicionalComponent } from './calibracion-adicional.component';
import { CalibracionAdicionalApiService } from '../../../services/calibracion-adicional-api.service';
import { VersionFormulario } from '../../../models/formulario.models';

describe('CalibracionAdicionalComponent', () => {
  const version = {
    id: 'v4',
    estado: 'BORRADOR',
    reglasEvaluacionIc: {
      reglas_evaluacion_ic: {
        reglas_anulacion: [
          {
            id: 'RN-IC-05a',
            descripcion: 'Socavacion',
            pendiente: 'Confirmar alcance',
            estado_resultante: 'MALO',
            elementos: ['/estribo'],
            condicion: { indicador: 'nivel', algun_valor_en: ['CON_EXPOSICION'] },
          },
        ],
      },
    },
  } as unknown as VersionFormulario;
  const sinActa = { versionFormularioId: 'v4', estado: 'SIN_ACTA', mensaje: '', acta: null };
  const api = {
    consultarActa: vi.fn(),
    guardarReglas: vi.fn(),
    adjuntarActa: vi.fn(),
    descargarActa: vi.fn(),
  };
  let fixture: ReturnType<typeof TestBed.createComponent<CalibracionAdicionalComponent>>;
  let componente: CalibracionAdicionalComponent;
  beforeEach(async () => {
    vi.resetAllMocks();
    api.consultarActa.mockReturnValue(of(sinActa));
    api.guardarReglas.mockReturnValue(of(version));
    api.adjuntarActa.mockReturnValue(of(sinActa));
    TestBed.configureTestingModule({
      imports: [CalibracionAdicionalComponent],
      providers: [
        { provide: CalibracionAdicionalApiService, useValue: api },
        { provide: TranslocoService, useValue: { translate: (clave: string) => clave } },
      ],
    });
    TestBed.overrideComponent(CalibracionAdicionalComponent, {
      set: { template: '', imports: [] },
    });
    await TestBed.compileComponents();
    fixture = TestBed.createComponent(CalibracionAdicionalComponent);
    componente = fixture.componentInstance;
    fixture.componentRef.setInput('version', structuredClone(version));
    fixture.detectChanges();
  });
  it('consulta el estado del acta sin asumir que existe', () => {
    expect(api.consultarActa).toHaveBeenCalledWith('v4');
    expect(componente.acta()?.estado).toBe('SIN_ACTA');
  });
  it('exige confirmar la decision y conserva los datos originales', () => {
    componente.form.controls['r0c0'].setValue('Socavacion revisada');
    componente.guardar();
    expect(api.guardarReglas).not.toHaveBeenCalled();
    componente.confirmar(0, { target: { checked: true } } as unknown as Event);
    componente.guardar();
    const enviadas = api.guardarReglas.mock.calls[0][1];
    expect(enviadas[0].pendiente).toBeNull();
    expect(enviadas[0].descripcion).toBe('Socavacion revisada');
    expect(
      (version.reglasEvaluacionIc['reglas_evaluacion_ic'] as Record<string, unknown>)[
        'reglas_anulacion'
      ],
    ).toEqual(
      expect.arrayContaining([expect.objectContaining({ pendiente: 'Confirmar alcance' })]),
    );
  });
  it('conserva cambios y muestra el detalle de un rechazo del servidor', () => {
    api.guardarReglas.mockReturnValue(
      throwError(() => ({ error: { detail: 'Referencia invalida' } })),
    );
    componente.confirmar(0, { target: { checked: true } } as unknown as Event);
    componente.guardar();
    expect(componente.error()).toBe('Referencia invalida');
    expect(componente.cambio()).toBe(true);
  });
  it('no guarda reglas de una version publicada', () => {
    fixture.componentRef.setInput('version', { ...version, estado: 'PUBLICADA' });
    fixture.detectChanges();
    componente.confirmar(0, { target: { checked: true } } as unknown as Event);
    componente.guardar();
    expect(api.guardarReglas).not.toHaveBeenCalled();
  });
  it('descartar restaura la configuracion sin llamar al backend', () => {
    componente.form.controls['r0c0'].setValue('Cambio');
    componente.descartar();
    expect(componente.form.controls['r0c0'].value).toBe('Socavacion');
    expect(componente.cambio()).toBe(false);
  });
  it('rechaza archivos vacios antes de subir y permite PDF para validacion del servidor', () => {
    componente.seleccionar({
      target: { files: [new File([], 'acta.pdf')], value: '' },
    } as unknown as Event);
    expect(componente.archivo()).toBeNull();
    componente.seleccionar({
      target: { files: [new File(['contenido'], 'acta.pdf')], value: '' },
    } as unknown as Event);
    componente.adjuntar();
    expect(api.adjuntarActa).toHaveBeenCalled();
  });
  it('permite guardar reglas configuradas sin editar un campo artificialmente', () => {
    const configurada = structuredClone(version);
    const reglas = configurada.reglasEvaluacionIc['reglas_evaluacion_ic'] as {
      reglas_anulacion: Record<string, unknown>[];
    };
    reglas.reglas_anulacion[0]['pendiente'] = null;
    fixture.componentRef.setInput('version', configurada);
    fixture.detectChanges();
    expect(componente.cambio()).toBe(false);
    expect(componente.puedeGuardar()).toBe(true);
    componente.guardar();
    expect(api.guardarReglas).toHaveBeenCalledTimes(1);
  });
});
