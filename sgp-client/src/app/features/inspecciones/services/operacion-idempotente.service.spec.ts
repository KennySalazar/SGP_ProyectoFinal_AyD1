import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { authInterceptor } from '../../../core/interceptors/auth.interceptor';
import { TokenRefreshService } from '../../../core/services/token-refresh.service';
import { AuthStore } from '../../../core/services/auth.store';
import { OperacionIdempotenteService } from './operacion-idempotente.service';

describe('Operaciones idempotentes de inspeccion', () => {
  let servicio: OperacionIdempotenteService;
  let http: HttpTestingController;
  let actor: string;
  const ruta = '/api/v1/inspecciones/i/borrador';
  const fallo = { error: () => undefined };

  beforeEach(() => {
    actor = 'usuario-1';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        {
          provide: TokenRefreshService,
          useValue: { refresh: () => of({ accessToken: 'renovado' }) },
        },
        {
          provide: AuthStore,
          useValue: { user: () => ({ id: actor }), accessToken: () => 'anterior', clear: vi.fn() },
        },
      ],
    });
    servicio = TestBed.inject(OperacionIdempotenteService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('genera UUID v7 y conserva cuerpo y clave tras perder una respuesta', () => {
    const datos = { version: 1, datos: { nombre: 'original' } };
    servicio.ejecutar('PUT', ruta, datos).subscribe(fallo);
    const primera = http.expectOne(ruta);
    const clave = primera.request.headers.get('Idempotency-Key');
    expect(clave).toMatch(/^[\da-f]{8}-[\da-f]{4}-7[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/);
    datos.datos.nombre = 'cambiado';
    expect(primera.request.body.datos.nombre).toBe('original');
    primera.error(new ProgressEvent('error'));
    servicio.ejecutar('PUT', ruta, { version: 1, datos: { nombre: 'original' } }).subscribe();
    const repetida = http.expectOne(ruta);
    expect(repetida.request.headers.get('Idempotency-Key')).toBe(clave);
    expect(repetida.request.body).toEqual({ version: 1, datos: { nombre: 'original' } });
    repetida.flush({ version: 2 });
  });

  it('mantiene la clave ante 5xx y operacion en proceso', () => {
    servicio.ejecutar('PUT', ruta, {}).subscribe(fallo);
    const primera = http.expectOne(ruta);
    const clave = primera.request.headers.get('Idempotency-Key');
    primera.flush({}, { status: 503, statusText: 'Unavailable' });
    servicio.ejecutar('PUT', ruta, {}).subscribe(fallo);
    const segunda = http.expectOne(ruta);
    expect(segunda.request.headers.get('Idempotency-Key')).toBe(clave);
    segunda.flush(
      { code: 'operacion_en_proceso' },
      { status: 409, statusText: 'Conflict', headers: { 'Retry-After': '1' } },
    );
    servicio.ejecutar('PUT', ruta, {}).subscribe();
    const tercera = http.expectOne(ruta);
    expect(tercera.request.headers.get('Idempotency-Key')).toBe(clave);
    tercera.flush({});
  });

  it('una accion nueva despues de validacion o exito recibe otra clave', () => {
    servicio.ejecutar('POST', ruta, null).subscribe(fallo);
    const primera = http.expectOne(ruta);
    const clave = primera.request.headers.get('Idempotency-Key');
    primera.flush({ detail: 'Faltan datos' }, { status: 422, statusText: 'Invalid' });
    servicio.ejecutar('POST', ruta, null).subscribe();
    const segunda = http.expectOne(ruta);
    const otra = segunda.request.headers.get('Idempotency-Key');
    expect(otra).not.toBe(clave);
    segunda.flush({});
    servicio.ejecutar('POST', ruta, null).subscribe();
    const tercera = http.expectOne(ruta);
    expect(tercera.request.headers.get('Idempotency-Key')).not.toBe(otra);
    tercera.flush({});
  });

  it('comparte una solicitud mientras esta en curso', () => {
    let respuestas = 0;
    servicio.ejecutar('PUT', ruta, {}).subscribe(() => respuestas++);
    servicio.ejecutar('PUT', ruta, {}).subscribe(() => respuestas++);
    http.expectOne(ruta).flush({});
    expect(respuestas).toBe(2);
  });

  it('aisla pendientes por usuario y bloquea una suscripcion de otra sesion', () => {
    const anterior = servicio.ejecutar('PUT', ruta, {});
    anterior.subscribe(fallo);
    const primera = http.expectOne(ruta);
    const clave = primera.request.headers.get('Idempotency-Key');
    primera.error(new ProgressEvent('error'));
    actor = 'usuario-2';
    anterior.subscribe({ error: (error: HttpErrorResponse) => expect(error.status).toBe(401) });
    http.expectNone(ruta);
    servicio.ejecutar('PUT', ruta, {}).subscribe();
    const segunda = http.expectOne(ruta);
    expect(segunda.request.headers.get('Idempotency-Key')).not.toBe(clave);
    segunda.flush({});
    actor = 'usuario-1';
    servicio.ejecutar('PUT', ruta, {}).subscribe();
    const tercera = http.expectOne(ruta);
    expect(tercera.request.headers.get('Idempotency-Key')).toBe(clave);
    tercera.flush({});
  });

  it('conserva claves explicitas sin modificar llamadas de fotografias', () => {
    servicio.ejecutar('POST', ruta, null, 'clave-explicita').subscribe();
    const peticion = http.expectOne(ruta);
    expect(peticion.request.headers.get('Idempotency-Key')).toBe('clave-explicita');
    expect(peticion.request.body).toBeNull();
    peticion.flush({});
  });
  it('renueva una vez el token sin cambiar clave ni cuerpo', () => {
    servicio.ejecutar('PUT', ruta, { version: 2 }).subscribe();
    const primera = http.expectOne(ruta);
    const clave = primera.request.headers.get('Idempotency-Key');
    primera.flush({}, { status: 401, statusText: 'Unauthorized' });
    const repetida = http.expectOne(ruta);
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer renovado');
    expect(repetida.request.headers.get('Idempotency-Key')).toBe(clave);
    expect(repetida.request.body).toEqual({ version: 2 });
    repetida.flush({ version: 3 });
  });
});
