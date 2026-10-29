import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { problemInterceptor } from './problem.interceptor';
import { AuthStore } from '../services/auth.store';
import { TokenRefreshService } from '../services/token-refresh.service';
import { ApiErrorService } from '../services/api-error.service';

describe('AuthInterceptor & ProblemInterceptor: renovacion transparente de sesion', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authStore: {
    accessToken: ReturnType<typeof vi.fn>;
    authenticated: ReturnType<typeof vi.fn>;
    clear: ReturnType<typeof vi.fn>;
  };
  let tokenRefreshService: {
    refresh: ReturnType<typeof vi.fn>;
  };
  let apiErrorService: {
    normalize: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(() => {
    authStore = {
      accessToken: vi.fn().mockReturnValue('token-inicial'),
      authenticated: vi.fn().mockReturnValue(true),
      clear: vi.fn(),
    };
    tokenRefreshService = {
      refresh: vi.fn().mockReturnValue(of({ accessToken: 'token-renovado' })),
    };
    apiErrorService = {
      normalize: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([problemInterceptor, authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthStore, useValue: authStore },
        { provide: TokenRefreshService, useValue: tokenRefreshService },
        { provide: ApiErrorService, useValue: apiErrorService },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('adjunta Authorization Bearer cuando existe accessToken', () => {
    http.get('/api/v1/puentes').subscribe();
    const req = httpMock.expectOne('/api/v1/puentes');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-inicial');
    expect(req.request.withCredentials).toBe(true);
    req.flush([]);
  });

  it('no adjunta Authorization Bearer cuando accessToken es null', () => {
    authStore.accessToken.mockReturnValue(null);
    http.get('/api/v1/puentes').subscribe();
    const req = httpMock.expectOne('/api/v1/puentes');
    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.withCredentials).toBe(true);
    req.flush([]);
  });

  it('no intenta renovar token en rutas publicas de autenticacion al recibir 401', () => {
    let errorCapturado: HttpErrorResponse | undefined;
    http.post('/api/v1/auth/login', { email: 'test@example.com' }).subscribe({
      error: (err: HttpErrorResponse) => {
        errorCapturado = err;
      },
    });

    const req = httpMock.expectOne('/api/v1/auth/login');
    req.flush(
      { detail: 'Credenciales invalidas', title: 'Error de autenticacion' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(tokenRefreshService.refresh).not.toHaveBeenCalled();
    expect(errorCapturado?.status).toBe(401);
    expect(apiErrorService.normalize).toHaveBeenCalledOnce();
  });

  it('renueva el token transparentemente ante 401 y reintenta sin mostrar alerta global', () => {
    let respuestaRecibida: unknown;
    http.get('/api/v1/puentes').subscribe({
      next: (res) => {
        respuestaRecibida = res;
      },
    });

    // Primera solicitud falla con 401
    const reqInicial = httpMock.expectOne('/api/v1/puentes');
    expect(reqInicial.request.headers.get('Authorization')).toBe('Bearer token-inicial');
    reqInicial.flush(
      { detail: 'Se requiere una sesion valida', title: 'Autenticacion requerida' },
      { status: 401, statusText: 'Unauthorized' },
    );

    // Debe invocar refresh()
    expect(tokenRefreshService.refresh).toHaveBeenCalledOnce();

    // Segunda solicitud (reintento) con el token renovado
    const reqReintento = httpMock.expectOne('/api/v1/puentes');
    expect(reqReintento.request.headers.get('Authorization')).toBe('Bearer token-renovado');
    reqReintento.flush([{ id: 'p1', nombre: 'Puente Central' }]);

    expect(respuestaRecibida).toEqual([{ id: 'p1', nombre: 'Puente Central' }]);
    // Al haberse recuperado transparentemente, el aviso global NO debe haberse disparado
    expect(apiErrorService.normalize).not.toHaveBeenCalled();
  });

  it('intenta renovacion si token en memoria era null pero usuario estaba autenticado', () => {
    authStore.accessToken.mockReturnValue(null);
    authStore.authenticated.mockReturnValue(true);

    let respuestaRecibida: unknown;
    http.get('/api/v1/puentes').subscribe({
      next: (res) => {
        respuestaRecibida = res;
      },
    });

    const reqInicial = httpMock.expectOne('/api/v1/puentes');
    expect(reqInicial.request.headers.has('Authorization')).toBe(false);
    reqInicial.flush(
      { detail: 'Se requiere una sesion valida', title: 'Autenticacion requerida' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(tokenRefreshService.refresh).toHaveBeenCalledOnce();

    const reqReintento = httpMock.expectOne('/api/v1/puentes');
    expect(reqReintento.request.headers.get('Authorization')).toBe('Bearer token-renovado');
    reqReintento.flush([{ id: 'p1' }]);

    expect(respuestaRecibida).toEqual([{ id: 'p1' }]);
    expect(apiErrorService.normalize).not.toHaveBeenCalled();
  });

  it('limpia sesion, redirige a login y publica error cuando falla la renovacion del token', () => {
    tokenRefreshService.refresh.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: { detail: 'Sesion expirada', title: 'Autenticacion requerida' },
          }),
      ),
    );

    let errorFinal: unknown;
    http.get('/api/v1/puentes').subscribe({
      error: (err: unknown) => {
        errorFinal = err;
      },
    });

    const reqInicial = httpMock.expectOne('/api/v1/puentes');
    reqInicial.flush(
      { detail: 'Se requiere una sesion valida', title: 'Autenticacion requerida' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(tokenRefreshService.refresh).toHaveBeenCalledOnce();
    expect(authStore.clear).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
    expect(errorFinal).toBeDefined();
    expect(apiErrorService.normalize).toHaveBeenCalledOnce();
  });
});
