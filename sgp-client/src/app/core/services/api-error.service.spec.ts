import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from './api-error.service';

describe('Mensajes de evaluacion pendiente del IC', () => {
  const translate = vi.fn(
    (key: string, params?: { recorrido?: string }) => `${key}: ${params?.recorrido ?? ''}`,
  );
  let service: ApiErrorService;

  beforeEach(() => {
    translate.mockClear();
    TestBed.configureTestingModule({
      providers: [{ provide: TranslocoService, useValue: { translate } }],
    });
    service = TestBed.inject(ApiErrorService);
  });

  afterEach(() => TestBed.resetTestingModule());

  it('presenta el indicador pendiente como recorrido legible en la alerta general', () => {
    const mensaje = service.normalize(
      new HttpErrorResponse({
        status: 422,
        error: {
          code: 'datos_ic_incompletos',
          detail:
            'Falta el indicador /subestructura/estribo_entrada/cortina_superior/grietas_una_direccion',
        },
      }),
    );
    expect(translate).toHaveBeenCalledWith('inspecciones.confirmacionIc.indicadorPendiente', {
      recorrido: 'Subestructura > Estribo entrada > Cortina superior > Grietas una direccion',
    });
    expect(mensaje).not.toContain('/subestructura');
    expect(service.lastMessage()).toBe(mensaje);
  });

  it('conserva el detalle de errores ajenos a datos pendientes del IC', () => {
    expect(
      service.normalize(
        new HttpErrorResponse({
          status: 422,
          error: {
            code: 'configuracion_ic_incompleta',
            detail: 'Faltan los pesos del formulario',
          },
        }),
      ),
    ).toBe('Faltan los pesos del formulario');
    expect(translate).not.toHaveBeenCalled();
  });
});
