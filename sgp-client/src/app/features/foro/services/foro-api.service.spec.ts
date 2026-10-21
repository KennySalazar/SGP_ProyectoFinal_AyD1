import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ForoApiService } from './foro-api.service';

describe('ForoApiService', () => {
  let service: ForoApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ForoApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista los hilos de un puente paginados', () => {
    service.listarHilos('puente-1', 2, 10).subscribe();

    const request = http.expectOne((r) => r.url === '/api/v1/puentes/puente-1/hilos');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('pagina')).toBe('2');
    expect(request.request.params.get('tamanio')).toBe('10');
    request.flush({ content: [] });
  });

  it('abre un hilo con título, categoría y primer comentario', () => {
    const cuerpo = { titulo: 'Tema', categoria: 'ALERTA' as const, comentario: 'Texto' };
    service.crearHilo('puente-1', cuerpo).subscribe();

    const request = http.expectOne('/api/v1/puentes/puente-1/hilos');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(cuerpo);
    request.flush({});
  });

  it('consulta un hilo', () => {
    service.obtenerHilo('hilo-1').subscribe();

    const request = http.expectOne('/api/v1/hilos/hilo-1');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('lista los comentarios de un hilo paginados', () => {
    service.listarComentarios('hilo-1', 1, 100).subscribe();

    const request = http.expectOne((r) => r.url === '/api/v1/hilos/hilo-1/comentarios');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('pagina')).toBe('1');
    expect(request.request.params.get('tamanio')).toBe('100');
    request.flush({ content: [] });
  });

  it('comenta o responde enviando el comentario principal como padre', () => {
    service.crearComentario('hilo-1', { contenido: 'Hola', padreId: null }).subscribe();
    service
      .crearComentario('hilo-1', { contenido: 'Respuesta', padreId: 'comentario-1' })
      .subscribe();

    const solicitudes = http.match('/api/v1/hilos/hilo-1/comentarios');
    expect(solicitudes.map((r) => r.request.method)).toEqual(['POST', 'POST']);
    expect(solicitudes.map((r) => r.request.body)).toEqual([
      { contenido: 'Hola', padreId: null },
      { contenido: 'Respuesta', padreId: 'comentario-1' },
    ]);
    solicitudes.forEach((r) => r.flush({}));
  });
  it('filtra los comentarios por el campo o elemento anclado', () => {
    service
      .listarComentarios('hilo-1', 0, 100, {
        elementoRef: '/subestructura/pilas',
        instanciaElementoId: 'pila-2',
      })
      .subscribe();

    const request = http.expectOne((r) => r.url === '/api/v1/hilos/hilo-1/comentarios');
    expect(request.request.params.get('elementoRef')).toBe('/subestructura/pilas');
    expect(request.request.params.get('instanciaElementoId')).toBe('pila-2');
    request.flush({ content: [] });
  });

  it('no envía la instancia cuando el elemento no es repetible', () => {
    service
      .listarComentarios('hilo-1', 0, 100, {
        elementoRef: '/subestructura/estribo',
        instanciaElementoId: null,
      })
      .subscribe();

    const request = http.expectOne((r) => r.url === '/api/v1/hilos/hilo-1/comentarios');
    expect(request.request.params.get('elementoRef')).toBe('/subestructura/estribo');
    expect(request.request.params.has('instanciaElementoId')).toBe(false);
    request.flush({ content: [] });
  });

  it('sin ancla pide todos los comentarios del hilo', () => {
    service.listarComentarios('hilo-1', 0, 100).subscribe();

    const request = http.expectOne((r) => r.url === '/api/v1/hilos/hilo-1/comentarios');
    expect(request.request.params.has('elementoRef')).toBe(false);
    request.flush({ content: [] });
  });

  it('comenta un elemento enviando su ancla', () => {
    const cuerpo = {
      contenido: 'Se ve expuesta',
      padreId: null,
      elementoRef: '/subestructura/pilas',
      instanciaElementoId: 'pila-2',
    };
    service.crearComentario('hilo-1', cuerpo).subscribe();

    const request = http.expectOne('/api/v1/hilos/hilo-1/comentarios');
    expect(request.request.body).toEqual(cuerpo);
    request.flush({});
  });

  it('consulta el hilo de una inspección sin crearlo', () => {
    service.obtenerHiloDeInspeccion('insp-1').subscribe();

    const request = http.expectOne('/api/v1/inspecciones/insp-1/hilo');
    expect(request.request.method).toBe('GET');
    request.flush({ hilo: {}, anclas: [] });
  });

  it('abre el hilo de una inspección con POST', () => {
    service.abrirHiloDeInspeccion('insp-1').subscribe();

    const request = http.expectOne('/api/v1/inspecciones/insp-1/hilo');
    expect(request.request.method).toBe('POST');
    request.flush({ hilo: {}, anclas: [] });
  });
});
