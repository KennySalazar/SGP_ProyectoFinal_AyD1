import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, describe, expect, it } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthCardComponent } from '../../../../shared/components/auth-card/auth-card.component';
import { ContrasenaVisibleComponent } from '../../../../shared/components/contrasena-visible/contrasena-visible.component';
import { ActivarCuentaPage } from './activar-cuenta.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('Característica: activación de cuenta invitada HU001', () => {
  let fixture: ComponentFixture<ActivarCuentaPage>;
  let page: ActivarCuentaPage;
  let http: HttpTestingController;

  function crear(token: string | null): void {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TranslocoService, useValue: { translate: (key: string) => key } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } },
        },
      ],
    });
    for (const componente of [ActivarCuentaPage, AuthCardComponent, ContrasenaVisibleComponent]) {
      TestBed.overrideComponent(componente, {
        remove: { imports: [TranslocoPipe] },
        add: { imports: [TraduccionTestPipe] },
      });
    }
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ActivarCuentaPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  }

  function validarInvitacion(): void {
    const request = http.expectOne('/api/v1/invitaciones/validacion');
    expect(request.request.body).toEqual({ token: 'token-1' });
    request.flush({
      email: 'catedratico@usac.edu.gt',
      rol: 'CATEDRATICO',
      expiraEn: '2026-10-09T12:00:00-06:00',
    });
    fixture.detectChanges();
  }

  afterEach(() => {
    http.verify();
    fixture.destroy();
  });

  it('Escenario: el invitado completa su activación con una contraseña válida', () => {
    // Dado un enlace de invitación válido y no vencido
    crear('token-1');
    validarInvitacion();
    expect(page.estado()).toBe('lista');
    expect(fixture.nativeElement.textContent).toContain('catedratico@usac.edu.gt');
    expect(fixture.nativeElement.textContent).toContain('invitaciones.roles.CATEDRATICO');
    // Cuando define su nombre, su nombre de usuario y su contraseña
    page.form.setValue({
      password: 'Puentes2026',
      confirmacion: 'Puentes2026',
      nombreCompleto: '  Ana Catedrática  ',
      nombreUsuario: 'ana.catedratica',
    });
    page.activar();
    const request = http.expectOne('/api/v1/invitaciones/aceptacion');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      token: 'token-1',
      password: 'Puentes2026',
      nombreCompleto: 'Ana Catedrática',
      nombreUsuario: 'ana.catedratica',
    });
    request.flush({ message: 'La cuenta fue activada.' });
    fixture.detectChanges();
    // Entonces la cuenta queda activa y se ofrece iniciar sesión
    expect(page.estado()).toBe('activada');
    const login = fixture.nativeElement.querySelector('a[href="/login"]') as HTMLAnchorElement;
    expect(login).not.toBeNull();
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it.each([
    ['Puentes', 'Puentes'],
    ['soloLetrasLargas', 'soloLetrasLargas'],
    ['Puentes2026', 'Puentes2027'],
  ])(
    'Escenario: una contraseña que incumple RN-USR-06 o no coincide no se envía (%s)',
    (password, confirmacion) => {
      crear('token-1');
      validarInvitacion();
      page.form.setValue({
        password,
        confirmacion,
        nombreCompleto: 'Ana Catedrática',
        nombreUsuario: 'ana.catedratica',
      });
      page.activar();
      http.expectNone('/api/v1/invitaciones/aceptacion');
      expect(page.estado()).toBe('lista');
    },
  );

  it('Escenario: la contraseña y su confirmación se muestran u ocultan por separado', () => {
    crear('token-1');
    validarInvitacion();
    const html = fixture.nativeElement as HTMLElement;
    const password = html.querySelector<HTMLInputElement>('input[formControlName="password"]')!;
    const confirmacion = html.querySelector<HTMLInputElement>(
      'input[formControlName="confirmacion"]',
    )!;
    const ojo = (campo: HTMLInputElement) =>
      campo.closest('app-contrasena-visible')!.querySelector<HTMLButtonElement>('button.ojo')!;

    expect(html.querySelectorAll('button.ojo')).toHaveLength(2);
    ojo(password).click();

    expect(password.type).toBe('text');
    expect(confirmacion.type).toBe('password');
  });

  it('Escenario: enlace de invitación vencido indica que solo el Administrador puede reenviarla', () => {
    // Dado un enlace vencido
    crear('token-1');
    const errores = TestBed.inject(ApiErrorService);
    errores.lastMessage.set('mensaje global');
    // Cuando el invitado intenta usarlo
    http
      .expectOne('/api/v1/invitaciones/validacion')
      .flush(
        { status: 410, code: 'invitacion_vencida', detail: 'El enlace vencio' },
        { status: 410, statusText: 'Gone' },
      );
    fixture.detectChanges();
    // Entonces se rechaza sin formulario y se explica el reenvío por el Administrador
    expect(page.estado()).toBe('vencida');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('auth.activation.expired');
    expect(errores.lastMessage()).toBeNull();
  });

  it('Escenario: un enlace usado o reemplazado se muestra como inválido', () => {
    crear('token-1');
    http
      .expectOne('/api/v1/invitaciones/validacion')
      .flush(
        { status: 400, code: 'invitacion_invalida' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();
    expect(page.estado()).toBe('invalida');
    expect(fixture.nativeElement.textContent).toContain('auth.activation.invalid');
  });

  it('Escenario: el enlace vence mientras el invitado llenaba el formulario', () => {
    crear('token-1');
    validarInvitacion();
    page.form.setValue({
      password: 'Puentes2026',
      confirmacion: 'Puentes2026',
      nombreCompleto: 'Ana Catedrática',
      nombreUsuario: 'ana.catedratica',
    });
    page.activar();
    http
      .expectOne('/api/v1/invitaciones/aceptacion')
      .flush({ status: 410, code: 'invitacion_vencida' }, { status: 410, statusText: 'Gone' });
    fixture.detectChanges();
    expect(page.estado()).toBe('vencida');
  });

  it.each([
    ['', 'Ana'],
    ['Ana Catedrática', ''],
    ['Ana Catedrática', 'ab'],
    ['Ana Catedrática', 'ana reyes'],
    ['Ana Catedrática', '.ana'],
    ['Ana Catedrática', 'ana.'],
  ])('Escenario: un perfil incompleto o mal formado no se envía (%s / %s)', (nombre, usuario) => {
    crear('token-1');
    validarInvitacion();
    page.form.setValue({
      password: 'Puentes2026',
      confirmacion: 'Puentes2026',
      nombreCompleto: nombre,
      nombreUsuario: usuario,
    });
    page.activar();
    http.expectNone('/api/v1/invitaciones/aceptacion');
    expect(page.estado()).toBe('lista');
  });

  it('Escenario: un nombre de usuario en uso se corrige sin perder la invitación', () => {
    crear('token-1');
    validarInvitacion();
    const errores = TestBed.inject(ApiErrorService);
    page.form.setValue({
      password: 'Puentes2026',
      confirmacion: 'Puentes2026',
      nombreCompleto: 'Ana Catedrática',
      nombreUsuario: 'ocupado',
    });
    page.activar();
    http.expectOne('/api/v1/invitaciones/aceptacion').flush(
      {
        status: 409,
        code: 'nombre_usuario_en_uso',
        detail: 'Ese nombre de usuario ya está en uso. Elige otro.',
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    // El formulario sigue disponible, con el motivo junto al campo y sin aviso global.
    expect(page.estado()).toBe('lista');
    expect(page.errorPerfil()).toBe('Ese nombre de usuario ya está en uso. Elige otro.');
    expect(fixture.nativeElement.querySelector('form')).not.toBeNull();
    expect(errores.lastMessage()).toBeNull();

    page.form.controls.nombreUsuario.setValue('ana.libre');
    page.activar();
    http.expectOne('/api/v1/invitaciones/aceptacion').flush({ message: 'La cuenta fue activada.' });
    fixture.detectChanges();
    expect(page.estado()).toBe('activada');
  });

  it('Escenario: sin token no se consulta el servidor', () => {
    crear(null);
    http.expectNone('/api/v1/invitaciones/validacion');
    expect(page.estado()).toBe('invalida');
  });
});
