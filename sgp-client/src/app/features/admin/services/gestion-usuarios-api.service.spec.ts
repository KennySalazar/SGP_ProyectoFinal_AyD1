import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { GestionUsuariosApiService } from './gestion-usuarios-api.service';

describe('Característica: API de gestión de usuarios HU008', () => {
  let api: GestionUsuariosApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(GestionUsuariosApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('Escenario: listar combina rol, estado y paginación', () => {
    api.listar({ rol: 'ESTUDIANTE', estado: 'INACTIVO' }, 2, 10).subscribe();
    const request = http.expectOne((req) => req.url === '/api/v1/usuarios');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('rol')).toBe('ESTUDIANTE');
    expect(request.request.params.get('estado')).toBe('INACTIVO');
    expect(request.request.params.get('pagina')).toBe('2');
    request.flush({ content: [] });
  });

  it('Escenario: listar sin filtros no los envía', () => {
    api.listar({ rol: null, estado: null }).subscribe();
    const request = http.expectOne((req) => req.url === '/api/v1/usuarios');
    expect(request.request.params.has('rol')).toBe(false);
    expect(request.request.params.has('estado')).toBe(false);
    request.flush({ content: [] });
  });

  it.each([-1, 0, 101])('Escenario: rechazar tamaño de página inválido %s', (tamanio) => {
    expect(() => api.listar({}, 0, tamanio)).toThrow(RangeError);
    http.expectNone((req) => req.url === '/api/v1/usuarios');
  });

  it('Escenario: desactivar, reactivar y cambiar rol usan los endpoints del usuario', () => {
    api.desactivar('usuario-1', 'Motivo').subscribe();
    let request = http.expectOne('/api/v1/usuarios/usuario-1/desactivacion');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ motivo: 'Motivo' });
    request.flush({});

    api.reactivar('usuario-1').subscribe();
    request = http.expectOne('/api/v1/usuarios/usuario-1/reactivacion');
    expect(request.request.method).toBe('POST');
    request.flush({});

    api
      .cambiarRol('usuario-1', { rol: 'PROFESIONAL_EXTERNO', numeroColegiado: '12345' })
      .subscribe();
    request = http.expectOne('/api/v1/usuarios/usuario-1/rol');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ rol: 'PROFESIONAL_EXTERNO', numeroColegiado: '12345' });
    request.flush({});
  });
});
