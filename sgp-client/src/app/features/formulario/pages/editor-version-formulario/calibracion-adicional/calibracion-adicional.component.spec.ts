import { FormularioApiService } from '../../../services/formulario-api.service';
import { TestBed } from '@angular/core/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CalibracionAdicionalComponent } from './calibracion-adicional.component';
import { CalibracionAdicionalApiService } from '../../../services/calibracion-adicional-api.service';
import { VersionFormulario } from '../../../models/formulario.models';

@Pipe({ name: 'transloco', standalone: true })
class TraduccionPruebaPipe implements PipeTransform {
  transform(clave: string): string {
    return clave;
  }
}

describe('CalibracionAdicionalComponent', () => {
  const version = {
    id: 'v4',
    version: 0,
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
            condicion: {
              indicador: 'nivel',
              algun_valor_en: ['CON_EXPOSICION'],
            },
          },
        ],
      },
    },
  } as unknown as VersionFormulario;
  const sinActa = {
    versionFormularioId: 'v4',
    estado: 'SIN_ACTA',
    mensaje: '',
    acta: null,
  };
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
        {
          provide: FormularioApiService,
          useValue: {
            obtenerPorId: vi.fn().mockReturnValue(of({ ...version, version: 1 })),
          },
        },
        { provide: CalibracionAdicionalApiService, useValue: api },
        {
          provide: TranslocoService,
          useValue: { translate: (clave: string) => clave },
        },
      ],
    });
    TestBed.overrideComponent(CalibracionAdicionalComponent, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionPruebaPipe] },
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
    fixture.componentRef.setInput('version', {
      ...version,
      estado: 'PUBLICADA',
    });
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

  it('comunica la version nueva tras adjuntar y usa If-Match de la version mostrada', () => {
    const emit = vi.spyOn(componente.guardada, 'emit');
    componente.archivo.set(new File(['%PDF'], 'acta.pdf', { type: 'application/pdf' }));
    componente.adjuntar();
    expect(api.adjuntarActa).toHaveBeenCalledWith('v4', expect.any(File), 0);
    expect(emit).toHaveBeenCalledWith(expect.objectContaining({ version: 1 }));
  });
  it('no adjunta un acta a una version publicada ni invocando la accion directamente', () => {
    fixture.componentRef.setInput('version', {
      ...version,
      estado: 'PUBLICADA',
    });
    fixture.detectChanges();
    componente.archivo.set(new File(['%PDF'], 'acta.pdf'));
    componente.adjuntar();
    expect(api.adjuntarActa).not.toHaveBeenCalled();
  });
  function mostrarResponsable(nombre?: string | null, email?: string | null) {
    componente.acta.set({
      ...sinActa,
      estado: 'CON_ACTA',
      acta: {
        archivoId: 'archivo-1',
        nombreOriginal: 'acta.pdf',
        mimeReal: 'application/pdf',
        tamanoBytes: 10,
        checksumSha256: 'checksum',
        subidoPorId: 'autor-del-acta',
        subidoPorNombre: nombre,
        subidoPorEmail: email,
        subidoEn: '2026-10-22T17:00:00Z',
      },
    });
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra nombre y correo del autor y conserva su UUID plegado', () => {
    const vista = mostrarResponsable('Kenny Salazar', 'kenny@ejemplo.com');
    expect(vista.textContent).toContain('Kenny Salazar');
    expect(vista.textContent).toContain('kenny@ejemplo.com');
    const detalle = vista.querySelector('code')?.closest('details');
    expect(detalle?.open).toBe(false);
    expect(detalle?.textContent).toContain('autor-del-acta');
  });

  it('muestra el correo cuando el responsable no tiene nombre', () => {
    const vista = mostrarResponsable(null, 'kenny@ejemplo.com');
    expect(vista.textContent).toContain('kenny@ejemplo.com');
    expect(vista.textContent).not.toContain('formulario.adicional.responsableNoDisponible');
  });

  it('conserva el identificador si faltan los datos del responsable', () => {
    const vista = mostrarResponsable();
    expect(vista.textContent).toContain('formulario.adicional.responsableNoDisponible');
    expect(vista.querySelector('code')?.textContent).toBe('autor-del-acta');
  });
});
