import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { UsuarioAdmin } from '../../models/usuario.models';
import { UsuariosPage } from './usuarios.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('Característica: gestión de usuarios HU008', () => {
  let fixture: ComponentFixture<UsuariosPage>;
  let page: UsuariosPage;
  let http: HttpTestingController;

  const administrador: UsuarioAdmin = {
    id: 'admin-1',
    email: 'admin@ejemplo.com',
    rol: 'ADMINISTRADOR',
    estado: 'ACTIVO',
    verificado: true,
    activado: true,
    activo: true,
    numeroColegiado: null,
    colegiadoVerificado: null,
    desactivadoEn: null,
    motivoDesactivacion: null,
    creadoEn: '2026-10-01T10:00:00-06:00',
  };
  const catedratico: UsuarioAdmin = {
    ...administrador,
    id: 'usuario-1',
    email: 'catedratico@usac.edu.gt',
    rol: 'CATEDRATICO',
  };
  const inactivo: UsuarioAdmin = {
    ...catedratico,
    id: 'usuario-2',
    email: 'inactivo@usac.edu.gt',
    estado: 'INACTIVO',
    activo: false,
    desactivadoEn: '2026-10-05T10:00:00-06:00',
    motivoDesactivacion: 'Dejó el curso',
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
        { provide: AuthStore, useValue: { user: signal({ id: 'admin-1' }) } },
      ],
    });
    TestBed.overrideComponent(UsuariosPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(UsuariosPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    fixture.destroy();
    vi.unstubAllGlobals();
  });

  function responderLista(content: UsuarioAdmin[]) {
    const request = http.expectOne((req) => req.method === 'GET' && req.url === '/api/v1/usuarios');
    request.flush({ content, number: 0, size: 10, totalElements: content.length, totalPages: 1 });
    fixture.detectChanges();
    return request.request;
  }

  function botones(prefijo: string): HTMLButtonElement[] {
    return Array.from(
      fixture.nativeElement.querySelectorAll(`button[aria-label^="${prefijo}"]`),
    ) as HTMLButtonElement[];
  }

  it('Escenario: listar usuarios paginados con su estado en texto', () => {
    const request = responderLista([administrador, catedratico, inactivo]);
    expect(request.params.get('pagina')).toBe('0');
    expect(request.params.has('rol')).toBe(false);
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('catedratico@usac.edu.gt');
    expect(texto).toContain('usuarios.estados.ACTIVO');
    expect(texto).toContain('usuarios.estados.INACTIVO');
    expect(texto).toContain('Dejó el curso');
    expect(texto).toContain('usuarios.you');
  });

  it('Escenario: filtrar por rol y estado', () => {
    responderLista([]);
    page.filtroRol.setValue('ESTUDIANTE');
    let request = responderLista([]);
    expect(request.params.get('rol')).toBe('ESTUDIANTE');
    page.filtrarEstado('PENDIENTE');
    request = responderLista([]);
    expect(request.params.get('rol')).toBe('ESTUDIANTE');
    expect(request.params.get('estado')).toBe('PENDIENTE');
    expect(fixture.nativeElement.textContent).toContain('usuarios.empty');
  });

  it('Escenario: el administrador no ve acciones sobre su propia cuenta', () => {
    responderLista([administrador, catedratico]);
    expect(botones('usuarios.deactivate').map((b) => b.getAttribute('aria-label'))).toEqual([
      'usuarios.deactivate: catedratico@usac.edu.gt',
    ]);
    expect(botones('usuarios.changeRole')).toHaveLength(1);
  });

  it('Escenario: desactivar un usuario con motivo tras confirmar', () => {
    responderLista([catedratico]);
    page.abrirDesactivacion(catedratico);
    http.expectNone((req) => req.method === 'POST');
    page.motivo.setValue('  Dejó el curso  ');
    page.desactivar();
    const request = http.expectOne('/api/v1/usuarios/usuario-1/desactivacion');
    expect(request.request.body).toEqual({ motivo: 'Dejó el curso' });
    request.flush({ ...catedratico, estado: 'INACTIVO', activo: false });
    responderLista([{ ...catedratico, estado: 'INACTIVO', activo: false }]);
    expect(page.porDesactivar()).toBeNull();
    expect(page.confirmacion()).toEqual({
      clave: 'usuarios.desactivado',
      email: 'catedratico@usac.edu.gt',
    });
    expect(botones('usuarios.reactivate')).toHaveLength(1);
  });

  it('Escenario: desactivar sin motivo envía motivo nulo', () => {
    responderLista([catedratico]);
    page.abrirDesactivacion(catedratico);
    page.desactivar();
    const request = http.expectOne('/api/v1/usuarios/usuario-1/desactivacion');
    expect(request.request.body).toEqual({ motivo: null });
    request.flush(catedratico);
    responderLista([]);
  });

  it('Escenario: un rechazo del servidor se explica dentro del diálogo', () => {
    responderLista([catedratico]);
    const errores = TestBed.inject(ApiErrorService);
    page.abrirDesactivacion(catedratico);
    page.desactivar();
    errores.lastMessage.set('mensaje global');
    http.expectOne('/api/v1/usuarios/usuario-1/desactivacion').flush(
      {
        status: 409,
        code: 'usuario_ya_inactivo',
        detail: 'La cuenta ya se encuentra desactivada.',
      },
      { status: 409, statusText: 'Conflict' },
    );
    expect(page.errorDesactivacion()).toBe('La cuenta ya se encuentra desactivada.');
    expect(page.porDesactivar()).toEqual(catedratico);
    expect(errores.lastMessage()).toBeNull();
  });

  it('Escenario: reactivar un usuario inactivo', () => {
    responderLista([inactivo]);
    page.reactivar(inactivo);
    http.expectOne('/api/v1/usuarios/usuario-2/reactivacion').flush({ ...inactivo, activo: true });
    responderLista([{ ...inactivo, estado: 'ACTIVO', activo: true }]);
    expect(page.confirmacion()?.clave).toBe('usuarios.reactivado');
  });

  it('Escenario: cambiar a Profesional Externo exige el número de colegiado', () => {
    // Dado el diálogo de cambio de rol, el rol actual no se ofrece
    responderLista([catedratico]);
    page.abrirCambioRol(catedratico);
    expect(page.rolesDisponibles().map((opcion) => opcion.valor)).not.toContain('CATEDRATICO');
    // Cuando elige Profesional Externo sin colegiado, no se envía
    page.cambioRol.controls.rol.setValue('PROFESIONAL_EXTERNO');
    expect(page.nuevoRolEsProfesional()).toBe(true);
    page.cambiarRol();
    http.expectNone((req) => req.method === 'PUT');
    // Y con el colegiado sí se confirma el cambio
    page.cambioRol.controls.numeroColegiado.setValue(' 12345 ');
    page.cambiarRol();
    const request = http.expectOne('/api/v1/usuarios/usuario-1/rol');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ rol: 'PROFESIONAL_EXTERNO', numeroColegiado: '12345' });
    request.flush({ ...catedratico, rol: 'PROFESIONAL_EXTERNO' });
    responderLista([]);
    expect(page.porCambiarRol()).toBeNull();
    expect(page.confirmacion()?.clave).toBe('usuarios.rolCambiado');
  });

  it('Escenario: cambiar a otro rol no envía colegiado', () => {
    responderLista([catedratico]);
    page.abrirCambioRol(catedratico);
    page.cambioRol.controls.rol.setValue('PROFESIONAL_EXTERNO');
    page.cambioRol.controls.numeroColegiado.setValue('12345');
    page.cambioRol.controls.rol.setValue('ESTUDIANTE');
    page.cambiarRol();
    const request = http.expectOne('/api/v1/usuarios/usuario-1/rol');
    expect(request.request.body).toEqual({ rol: 'ESTUDIANTE' });
    request.flush(catedratico);
    responderLista([]);
  });

  it('Escenario: un colegiado duplicado se informa en el diálogo de cambio de rol', () => {
    responderLista([catedratico]);
    page.abrirCambioRol(catedratico);
    page.cambioRol.setValue({ rol: 'PROFESIONAL_EXTERNO', numeroColegiado: '777' });
    page.cambiarRol();
    http
      .expectOne('/api/v1/usuarios/usuario-1/rol')
      .flush(
        { status: 409, code: 'colegiado_duplicado', detail: 'El colegiado ya está asociado.' },
        { status: 409, statusText: 'Conflict' },
      );
    expect(page.errorCambioRol()).toBe('El colegiado ya está asociado.');
    expect(page.porCambiarRol()).toEqual(catedratico);
  });
});
