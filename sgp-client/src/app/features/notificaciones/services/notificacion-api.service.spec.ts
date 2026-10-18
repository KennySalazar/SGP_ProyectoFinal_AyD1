import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { NotificacionApiService } from './notificacion-api.service';

describe('NotificacionApiService', () => {
  let service: NotificacionApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(NotificacionApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista los avisos con paginación y filtro de no leídos', () => {
    service.listar(true, 1, 50).subscribe();

    const req = http.expectOne('/api/v1/notificaciones?pagina=1&tamanio=50&soloNoLeidos=true');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 50, number: 1 });
  });

  it('obtiene el resumen de avisos no leídos', () => {
    service.obtenerResumen().subscribe((res) => {
      expect(res.noLeidas).toBe(3);
    });

    const req = http.expectOne('/api/v1/notificaciones/resumen');
    expect(req.request.method).toBe('GET');
    req.flush({ noLeidas: 3 });
  });

  it('marca un aviso como leído por su identificador', () => {
    service.marcarLeida('aviso-1').subscribe();

    const req = http.expectOne('/api/v1/notificaciones/aviso-1/leer');
    expect(req.request.method).toBe('PATCH');
    req.flush({ id: 'aviso-1', leido: true });
  });

  it('marca todos los avisos como leídos', () => {
    service.marcarTodasLeidas().subscribe((res) => {
      expect(res.marcadas).toBe(5);
    });

    const req = http.expectOne('/api/v1/notificaciones/marcar-todas-leidas');
    expect(req.request.method).toBe('POST');
    req.flush({ marcadas: 5 });
  });
});
