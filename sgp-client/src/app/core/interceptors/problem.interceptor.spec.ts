import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpContext } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient } from '@angular/common/http';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ERROR_GESTIONADO_LOCALMENTE, problemInterceptor } from './problem.interceptor';
import { ApiErrorService } from '../services/api-error.service';

describe('HU-060: errores de diagnóstico no se publican en el aviso global', () => {
  const normalize = vi.fn();
  beforeEach(() => {
    normalize.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([problemInterceptor])),
        provideHttpClientTesting(),
        { provide: ApiErrorService, useValue: { normalize } },
      ],
    });
  });
  it('conserva el error para su gestor local sin exponer el detalle sensible', () => {
    const local = vi.fn();
    TestBed.inject(HttpClient)
      .get('/diagnostico', { context: new HttpContext().set(ERROR_GESTIONADO_LOCALMENTE, true) })
      .subscribe({ error: local });
    TestBed.inject(HttpTestingController)
      .expectOne('/diagnostico')
      .flush({ detail: 'password=secreto' }, { status: 422, statusText: 'rechazo' });
    expect(local).toHaveBeenCalledOnce();
    expect(normalize).not.toHaveBeenCalled();
    TestBed.inject(HttpTestingController).verify();
  });
  it('mantiene el aviso global de las solicitudes habituales', () => {
    TestBed.inject(HttpClient)
      .get('/habitual')
      .subscribe({ error: () => {} });
    TestBed.inject(HttpTestingController)
      .expectOne('/habitual')
      .flush({}, { status: 500, statusText: 'error' });
    expect(normalize).toHaveBeenCalledOnce();
    TestBed.inject(HttpTestingController).verify();
  });
});
