import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthCardComponent } from '../../../../shared/components/auth-card/auth-card.component';
import { ContrasenaVisibleComponent } from '../../../../shared/components/contrasena-visible/contrasena-visible.component';
import { RegisterPage } from './register.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('Registro de estudiantes', () => {
  let fixture: ComponentFixture<RegisterPage>;
  let page: RegisterPage;
  let http: HttpTestingController;
  let router: Router;

  function crear(): void {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TranslocoService, useValue: { translate: (clave: string) => clave } },
      ],
    });
    for (const componente of [RegisterPage, AuthCardComponent, ContrasenaVisibleComponent]) {
      TestBed.overrideComponent(componente, {
        remove: { imports: [TranslocoPipe] },
        add: { imports: [TraduccionTestPipe] },
      });
    }
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(RegisterPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  }

  const completo = {
    email: 'ana@ejemplo.com',
    nombreCompleto: 'Ana Reyes',
    nombreUsuario: 'ana.reyes',
    password: 'Puentes2026',
    confirmacion: 'Puentes2026',
  };

  afterEach(() => {
    http.verify();
    fixture.destroy();
  });

  it('pide nombre completo y nombre de usuario además del correo, la contraseña y la confirmación', () => {
    crear();
    const campos = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('input[formControlName]'),
    ).map((campo) => campo.getAttribute('formControlName'));

    expect(campos).toEqual([
      'email',
      'nombreCompleto',
      'nombreUsuario',
      'password',
      'confirmacion',
    ]);
  });

  it('la contraseña y su confirmación se pueden mostrar u ocultar con el ojo', () => {
    crear();
    const html = fixture.nativeElement as HTMLElement;
    const campo = html.querySelector<HTMLInputElement>('input[formControlName="password"]')!;
    const ojo = campo
      .closest('app-contrasena-visible')!
      .querySelector<HTMLButtonElement>('button.ojo')!;

    expect(campo.type).toBe('password');
    ojo.click();
    expect(campo.type).toBe('text');
    ojo.click();
    expect(campo.type).toBe('password');

    const campoConfirmacion = html.querySelector<HTMLInputElement>(
      'input[formControlName="confirmacion"]',
    )!;
    const ojoConfirmacion = campoConfirmacion
      .closest('app-contrasena-visible')!
      .querySelector<HTMLButtonElement>('button.ojo')!;

    expect(campoConfirmacion.type).toBe('password');
    ojoConfirmacion.click();
    expect(campoConfirmacion.type).toBe('text');
    ojoConfirmacion.click();
    expect(campoConfirmacion.type).toBe('password');
  });

  it('envía el alta con el perfil y avanza a verificar el correo', () => {
    crear();
    page.form.setValue({ ...completo, nombreCompleto: '  Ana Reyes ' });

    page.submit();

    const request = http.expectOne('/api/v1/auth/register');
    expect(request.request.body).toEqual({
      email: 'ana@ejemplo.com',
      nombreCompleto: 'Ana Reyes',
      nombreUsuario: 'ana.reyes',
      password: 'Puentes2026',
    });
    request.flush({ challengeId: 'reto-1', expiresAt: '2026-10-20T12:00:00Z', message: 'ok' });
    expect(router.navigate).toHaveBeenCalledWith(['/registro/verificar'], {
      queryParams: { email: 'ana@ejemplo.com', challengeId: 'reto-1' },
    });
  });

  it('no envía el alta si las contraseñas no coinciden', () => {
    crear();
    page.form.setValue({ ...completo, confirmacion: 'OtraClaveDistinta123' });

    page.submit();

    http.expectNone('/api/v1/auth/register');
    expect(page.form.hasError('noCoinciden')).toBe(true);
    expect(page.form.invalid).toBe(true);

    page.form.controls.confirmacion.markAsTouched();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('auth.register.mismatch');
  });

  it('el formulario es inválido si falta la confirmación de la contraseña', () => {
    crear();
    page.form.setValue({ ...completo, confirmacion: '' });

    page.submit();

    http.expectNone('/api/v1/auth/register');
    expect(page.form.controls.confirmacion.invalid).toBe(true);
    expect(page.form.invalid).toBe(true);
  });

  it.each([
    ['nombreCompleto', ''],
    ['nombreUsuario', ''],
    ['nombreUsuario', 'ab'],
    ['nombreUsuario', 'ana reyes'],
    ['nombreUsuario', '.ana'],
    ['nombreUsuario', 'ana.'],
  ])('no envía el alta con un perfil inválido (%s = "%s")', (campo, valor) => {
    crear();
    page.form.setValue({ ...completo, [campo]: valor });

    page.submit();

    http.expectNone('/api/v1/auth/register');
    expect(page.form.invalid).toBe(true);
  });
});
