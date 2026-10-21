import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { UserSession } from '../../../../core/models/auth.models';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { PerfilPage } from './perfil.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

const SESION: UserSession = {
  id: 'usuario-1',
  email: 'ana@ejemplo.com',
  nombreCompleto: null,
  nombreUsuario: null,
  role: 'ESTUDIANTE',
  verified: true,
  activated: true,
  active: true,
  twoFactorEnabled: false,
};

describe('Perfil de la cuenta', () => {
  let fixture: ComponentFixture<PerfilPage>;
  let page: PerfilPage;
  let http: HttpTestingController;
  const usuario = signal<UserSession | null>(null);
  const setUser = vi.fn((sesion: UserSession | null) => usuario.set(sesion));

  function crear(sesion: UserSession): void {
    usuario.set(sesion);
    setUser.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthStore, useValue: { user: usuario, setUser } },
        { provide: TranslocoService, useValue: { translate: (clave: string) => clave } },
      ],
    }).overrideComponent(PerfilPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PerfilPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  }

  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => {
    http.verify();
    fixture.destroy();
  });

  it('muestra el correo y el rol y parte vacío en una cuenta sin perfil', () => {
    crear(SESION);

    expect(texto()).toContain('ana@ejemplo.com');
    expect(texto()).toContain('invitaciones.roles.ESTUDIANTE');
    expect(page.form.getRawValue()).toEqual({ nombreCompleto: '', nombreUsuario: '' });
    expect(page.form.invalid).toBe(true);
  });

  it('parte de los datos actuales cuando la cuenta ya tiene perfil', () => {
    crear({ ...SESION, nombreCompleto: 'Ana Reyes', nombreUsuario: 'ana.reyes' });

    expect(page.form.getRawValue()).toEqual({
      nombreCompleto: 'Ana Reyes',
      nombreUsuario: 'ana.reyes',
    });
  });

  it('guarda el perfil, actualiza la sesión y lo confirma', () => {
    crear(SESION);
    page.form.setValue({ nombreCompleto: '  Ana Reyes  ', nombreUsuario: 'ana.reyes' });

    page.guardar();

    const request = http.expectOne('/api/v1/auth/me/perfil');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      nombreCompleto: 'Ana Reyes',
      nombreUsuario: 'ana.reyes',
    });
    const actualizada = { ...SESION, nombreCompleto: 'Ana Reyes', nombreUsuario: 'ana.reyes' };
    request.flush(actualizada);
    fixture.detectChanges();

    expect(setUser).toHaveBeenCalledWith(actualizada);
    expect(page.guardado()).toBe(true);
    expect(texto()).toContain('perfil.guardado');
  });

  it.each([
    ['', 'ana'],
    ['Ana', ''],
    ['Ana', 'ab'],
    ['Ana', 'ana reyes'],
    ['Ana', 'ana.'],
  ])('no envía un perfil incompleto o mal formado (%s / %s)', (nombre, usuarioNuevo) => {
    crear(SESION);
    page.form.setValue({ nombreCompleto: nombre, nombreUsuario: usuarioNuevo });

    page.guardar();

    http.expectNone('/api/v1/auth/me/perfil');
    expect(setUser).not.toHaveBeenCalled();
  });

  it('un nombre de usuario en uso se explica junto al campo y sin aviso global', () => {
    crear(SESION);
    const errores = TestBed.inject(ApiErrorService);
    errores.lastMessage.set('mensaje global');
    page.form.setValue({ nombreCompleto: 'Ana Reyes', nombreUsuario: 'ocupado' });

    page.guardar();
    http.expectOne('/api/v1/auth/me/perfil').flush(
      {
        status: 409,
        code: 'nombre_usuario_en_uso',
        detail: 'Ese nombre de usuario ya está en uso. Elige otro.',
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(page.errorUsuario()).toBe('Ese nombre de usuario ya está en uso. Elige otro.');
    expect(texto()).toContain('Ese nombre de usuario ya está en uso. Elige otro.');
    expect(errores.lastMessage()).toBeNull();
    expect(page.guardado()).toBe(false);
    expect(setUser).not.toHaveBeenCalled();
  });

  it('muestra junto a cada campo lo que rechaza el servidor', () => {
    crear(SESION);
    page.form.setValue({ nombreCompleto: 'Ana Reyes', nombreUsuario: 'admin' });

    page.guardar();
    http.expectOne('/api/v1/auth/me/perfil').flush(
      {
        status: 422,
        code: 'perfil_invalido',
        detail: 'El nombre completo o el nombre de usuario no son válidos.',
        errores: [
          { campo: 'nombreUsuario', mensaje: 'Ese nombre de usuario está reservado. Elige otro.' },
        ],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    expect(page.errorUsuario()).toBe('Ese nombre de usuario está reservado. Elige otro.');
    expect(page.errorNombre()).toBeNull();
    expect(page.errorGeneral()).toBeNull();
  });
});
