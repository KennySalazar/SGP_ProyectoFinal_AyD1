import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { ProfesionalResponse } from '../../models/profesional.models';
import { ProfesionalesPage } from './profesionales.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('Característica: verificación de colegiado HU002', () => {
  let fixture: ComponentFixture<ProfesionalesPage>;
  let page: ProfesionalesPage;
  let http: HttpTestingController;

  const pendiente: ProfesionalResponse = {
    usuarioId: 'usuario-1',
    email: 'ingeniero@ejemplo.com',
    numeroColegiado: '12345',
    colegiadoVerificado: false,
    colegiadoVerificadoEn: null,
    colegiadoVerificadoPorId: null,
    cuentaActivada: true,
    creadoEn: '2026-10-07T10:00:00-06:00',
  };
  const verificado: ProfesionalResponse = {
    ...pendiente,
    colegiadoVerificado: true,
    colegiadoVerificadoEn: '2026-10-07T12:00:00-06:00',
    colegiadoVerificadoPorId: 'admin-1',
  };

  beforeEach(() => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({
        matches: false,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
      })),
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TranslocoService, useValue: { translate: (key: string) => key } },
      ],
    });
    TestBed.overrideComponent(ProfesionalesPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProfesionalesPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    fixture.destroy();
    vi.unstubAllGlobals();
  });

  function responderLista(content: ProfesionalResponse[], verificadoEsperado = 'false'): void {
    const request = http.expectOne((req) => req.url === '/api/v1/profesionales');
    expect(request.request.params.get('verificado')).toBe(verificadoEsperado);
    request.flush({ content, number: 0, size: 10, totalElements: content.length, totalPages: 1 });
    fixture.detectChanges();
  }

  it('Escenario: la pantalla abre con los pendientes y su estado con texto', () => {
    responderLista([pendiente]);
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('ingeniero@ejemplo.com');
    expect(texto).toContain('12345');
    expect(texto).toContain('profesionales.estado.PENDIENTE');
    expect(texto).toContain('profesionales.accountActive');
    expect(
      fixture.nativeElement.querySelector('button[aria-label^="profesionales.verify"]'),
    ).not.toBeNull();
  });

  it('Escenario: el Administrador verifica el colegiado tras confirmar', () => {
    // Dado un profesional con colegiado sin verificar
    responderLista([pendiente]);
    // Cuando abre la confirmación, aún no se envía nada
    page.confirmarVerificacion(pendiente);
    fixture.detectChanges();
    http.expectNone((req) => req.method === 'POST');
    expect(page.porVerificar()).toEqual(pendiente);
    // Y al confirmar se marca como verificado
    page.verificar();
    const request = http.expectOne('/api/v1/profesionales/usuario-1/verificacion-colegiado');
    expect(request.request.method).toBe('POST');
    request.flush(verificado);
    // Entonces se cierra el diálogo, se confirma el cambio y se recarga la lista
    responderLista([]);
    expect(page.porVerificar()).toBeNull();
    expect(page.verificado()).toEqual(verificado);
    expect(fixture.nativeElement.textContent).toContain('profesionales.verified');
  });

  it('Escenario: cancelar la confirmación no verifica', () => {
    responderLista([pendiente]);
    page.confirmarVerificacion(pendiente);
    page.cancelarVerificacion();
    expect(page.porVerificar()).toBeNull();
    http.expectNone((req) => req.method === 'POST');
  });

  it('Escenario: si otro administrador ya la verificó se informa en el diálogo', () => {
    responderLista([pendiente]);
    const errores = TestBed.inject(ApiErrorService);
    page.confirmarVerificacion(pendiente);
    page.verificar();
    errores.lastMessage.set('mensaje global');
    http
      .expectOne('/api/v1/profesionales/usuario-1/verificacion-colegiado')
      .flush(
        { status: 409, code: 'colegiado_ya_verificado', detail: 'Ya fue verificado.' },
        { status: 409, statusText: 'Conflict' },
      );
    expect(page.errorVerificacion()).toBe('Ya fue verificado.');
    expect(errores.lastMessage()).toBeNull();
    responderLista([]);
  });

  it('Escenario: los verificados muestran la fecha y no ofrecen verificar', () => {
    responderLista([pendiente]);
    page.filtrar('VERIFICADOS');
    responderLista([verificado], 'true');
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('profesionales.estado.VERIFICADO');
    expect(texto).toContain('07/10/2026');
    expect(
      fixture.nativeElement.querySelector('button[aria-label^="profesionales.verify"]'),
    ).toBeNull();
  });

  it('Escenario: el filtro Todos no envía verificado', () => {
    responderLista([]);
    page.filtrar('TODOS');
    const request = http.expectOne((req) => req.url === '/api/v1/profesionales');
    expect(request.request.params.has('verificado')).toBe(false);
    request.flush({ content: [], number: 0, size: 10, totalElements: 0, totalPages: 0 });
  });
});
