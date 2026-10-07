import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { InvitacionResponse } from '../../models/invitacion.models';
import { InvitacionesPage } from './invitaciones.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('Característica: invitación de Catedrático HU001', () => {
  let fixture: ComponentFixture<InvitacionesPage>;
  let page: InvitacionesPage;
  let http: HttpTestingController;

  const pendiente: InvitacionResponse = {
    id: 'invitacion-1',
    email: 'catedratico@usac.edu.gt',
    rol: 'CATEDRATICO',
    estado: 'PENDIENTE',
    usuarioId: 'usuario-1',
    invitadoPorId: 'admin-1',
    expiraEn: '2026-10-09T12:00:00-06:00',
    aceptadoEn: null,
    canceladoEn: null,
    creadoEn: '2026-10-06T12:00:00-06:00',
  };
  const aceptada: InvitacionResponse = {
    ...pendiente,
    id: 'invitacion-2',
    email: 'otra@usac.edu.gt',
    estado: 'ACEPTADA',
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
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TranslocoService, useValue: { translate: (key: string) => key } },
      ],
    });
    TestBed.overrideComponent(InvitacionesPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InvitacionesPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    fixture.destroy();
    vi.unstubAllGlobals();
  });

  function responderLista(content: InvitacionResponse[] = [pendiente, aceptada]): void {
    http
      .expectOne((req) => req.method === 'GET' && req.url === '/api/v1/invitaciones')
      .flush({ content, number: 0, size: 10, totalElements: content.length, totalPages: 1 });
    fixture.detectChanges();
  }

  it('Escenario: la lista muestra el estado con texto y solo ofrece reenviar lo no aceptado', () => {
    responderLista();
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('catedratico@usac.edu.gt');
    expect(texto).toContain('invitaciones.estados.PENDIENTE');
    expect(texto).toContain('invitaciones.estados.ACEPTADA');
    expect(texto).toContain('09/10/2026');
    const reenviar = fixture.nativeElement.querySelectorAll(
      'button[aria-label^="invitaciones.resend"]',
    ) as NodeListOf<HTMLButtonElement>;
    expect(reenviar).toHaveLength(1);
    expect(reenviar[0].getAttribute('aria-label')).toContain('catedratico@usac.edu.gt');
  });

  it('Escenario: invitación exitosa envía correo y rol Catedrático y confirma el envío', () => {
    // Dado un Administrador en la pantalla de invitaciones
    responderLista([]);
    // Cuando invita a un correo con rol Catedrático
    page.form.setValue({ email: 'catedratico@usac.edu.gt', rol: 'CATEDRATICO' });
    page.invitar();
    const request = http.expectOne((req) => req.method === 'POST');
    expect(request.request.url).toBe('/api/v1/invitaciones');
    expect(request.request.body).toEqual({ email: 'catedratico@usac.edu.gt', rol: 'CATEDRATICO' });
    request.flush(pendiente);
    // Entonces confirma el envío, limpia el correo y recarga la lista
    responderLista([pendiente]);
    expect(page.invitacionEnviada()).toEqual(pendiente);
    expect(page.form.controls.email.value).toBe('');
    expect(fixture.nativeElement.querySelector('.confirmacion[role="status"]')).not.toBeNull();
  });

  it('Escenario: correo ya registrado se explica junto al formulario', () => {
    // Dado un correo con cuenta activa
    responderLista([]);
    const errores = TestBed.inject(ApiErrorService);
    page.form.setValue({ email: 'catedratico@usac.edu.gt', rol: 'CATEDRATICO' });
    // Cuando el Administrador intenta invitarlo
    page.invitar();
    errores.lastMessage.set('mensaje global');
    http
      .expectOne((req) => req.method === 'POST')
      .flush(
        { status: 409, code: 'email_already_registered', detail: 'El correo ya esta registrado' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();
    // Entonces se muestra el motivo en contexto y no se duplica el aviso global
    expect(page.errorEnvio()).toBe('invitaciones.errores.email_already_registered');
    expect(errores.lastMessage()).toBeNull();
    expect(fixture.nativeElement.querySelector('.error[role="alert"]')).not.toBeNull();
    expect(page.invitacionEnviada()).toBeNull();
  });

  it('Escenario: un correo inválido no se envía', () => {
    responderLista([]);
    page.form.setValue({ email: 'no-es-correo', rol: 'CATEDRATICO' });
    page.invitar();
    http.expectNone((req) => req.method === 'POST');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('invitaciones.invalidEmail');
  });

  it('Escenario: el Administrador reenvía una invitación vencida', () => {
    const vencida: InvitacionResponse = { ...pendiente, estado: 'VENCIDA' };
    responderLista([vencida]);
    page.reenviar(vencida);
    const request = http.expectOne('/api/v1/invitaciones/invitacion-1/reenvio');
    expect(request.request.method).toBe('POST');
    request.flush({ ...pendiente, id: 'invitacion-3' });
    responderLista([{ ...pendiente, id: 'invitacion-3' }]);
    expect(page.reenvioExitoso()?.id).toBe('invitacion-3');
    expect(fixture.nativeElement.textContent).toContain('invitaciones.resent');
  });

  it('Escenario: filtrar la lista por estado', () => {
    responderLista();
    page.filtrar('VENCIDA');
    const request = http.expectOne((req) => req.url === '/api/v1/invitaciones');
    expect(request.request.params.get('estado')).toBe('VENCIDA');
    expect(request.request.params.get('pagina')).toBe('0');
    request.flush({ content: [], number: 0, size: 10, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('invitaciones.empty');
  });
});
