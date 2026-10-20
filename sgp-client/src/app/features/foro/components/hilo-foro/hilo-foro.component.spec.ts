import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthStore } from '../../../../core/services/auth.store';
import { HiloForoComponent } from './hilo-foro.component';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

const HILO = {
  id: 'hilo-1',
  puenteId: 'puente-1',
  inspeccionId: null,
  categoria: 'CONSULTA',
  titulo: 'Grieta en el cabezal',
  autorId: 'usuario-1',
  autorUsuario: 'ana.estudiante',
  autorRol: 'ESTUDIANTE',
  creadoEn: '2026-10-20T09:00:00-06:00',
  actualizadoEn: '2026-10-20T10:30:00-06:00',
  cerrado: false,
  totalComentarios: 3,
};
const PRINCIPAL = {
  id: 'comentario-1',
  hiloId: 'hilo-1',
  padreId: null,
  autorId: 'usuario-1',
  autorUsuario: 'ana.estudiante',
  autorRol: 'ESTUDIANTE',
  contenido: '<b>¿Alguien la ha medido?</b>',
  creadoEn: '2026-10-20T09:00:00-06:00',
};
const RESPUESTA = {
  ...PRINCIPAL,
  id: 'comentario-2',
  padreId: 'comentario-1',
  autorUsuario: null,
  autorRol: 'CATEDRATICO',
  contenido: 'Sí, mide 3 mm.',
};
const RUTA_HILO = '/api/v1/hilos/hilo-1';
const RUTA_COMENTARIOS = '/api/v1/hilos/hilo-1/comentarios';

function pagina(content: object[], number = 0, last = true) {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    number,
    size: 100,
    first: number === 0,
    last,
    empty: content.length === 0,
  };
}

describe('HiloForoComponent', () => {
  let fixture: ComponentFixture<HiloForoComponent>;
  let componente: HiloForoComponent;
  let http: HttpTestingController;

  async function crear(autenticado: boolean): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [HiloForoComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthStore, useValue: { authenticated: signal(autenticado) } },
        { provide: TranslocoService, useValue: { translate: (clave: string) => clave } },
      ],
    })
      .overrideComponent(HiloForoComponent, {
        remove: { imports: [TranslocoPipe] },
        add: { imports: [TraduccionTestPipe] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(HiloForoComponent);
    componente = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('hiloId', 'hilo-1');
    fixture.detectChanges();
  }

  /** Atiende la carga del hilo y de su primera página de comentarios. */
  function cargar(hilo: object, comentarios: object[], ultima = true): void {
    http.expectOne(RUTA_HILO).flush(hilo);
    http
      .expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'GET')
      .flush(pagina(comentarios, 0, ultima));
    fixture.detectChanges();
  }

  const elemento = () => fixture.nativeElement as HTMLElement;

  afterEach(() => http.verify());

  it('un visitante lee el hilo con las respuestas agrupadas bajo su comentario', async () => {
    await crear(false);
    cargar(HILO, [PRINCIPAL, RESPUESTA]);

    expect(elemento().textContent).toContain('Grieta en el cabezal');
    expect(elemento().querySelectorAll('article.comentario')).toHaveLength(2);
    const respuestas = elemento().querySelector('.respuestas');
    expect(respuestas?.textContent).toContain('Sí, mide 3 mm.');
    // Quien escribe se identifica por su nombre de usuario, sin rol ni nombre completo; sin
    // nombre de usuario se muestra el rol, nunca el correo.
    expect(elemento().querySelector('article.comentario')?.textContent).toContain(
      '@ana.estudiante',
    );
    expect(respuestas?.textContent).toContain('foro.roles.CATEDRATICO');
  });

  it('el contenido se muestra como texto y no como HTML', async () => {
    await crear(false);
    cargar(HILO, [PRINCIPAL]);

    const contenido = elemento().querySelector('.contenido');
    expect(contenido?.textContent).toBe('<b>¿Alguien la ha medido?</b>');
    expect(contenido?.querySelector('b')).toBeNull();
  });

  it('un visitante no ve formularios y se le invita a iniciar sesión', async () => {
    await crear(false);
    cargar(HILO, [PRINCIPAL]);

    expect(elemento().querySelector('textarea')).toBeNull();
    expect(elemento().querySelector('button.boton-responder')).toBeNull();
    expect(elemento().querySelector('a[href="/login"]')).not.toBeNull();
  });

  it('quien tiene sesión comenta y la conversación se recarga', async () => {
    await crear(true);
    cargar(HILO, [PRINCIPAL]);
    expect(elemento().querySelector('textarea')).not.toBeNull();

    componente.texto.set('  Mido mañana.  ');
    componente.publicar();
    const request = http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'POST');
    expect(request.request.body).toEqual({ contenido: 'Mido mañana.', padreId: null });
    request.flush({ ...PRINCIPAL, id: 'comentario-3' });

    expect(componente.texto()).toBe('');
    expect(componente.publicado()).toBe(true);
    cargar(HILO, [PRINCIPAL, { ...PRINCIPAL, id: 'comentario-3', contenido: 'Mido mañana.' }]);
    expect(elemento().querySelectorAll('article.comentario')).toHaveLength(2);
    expect(elemento().textContent).toContain('foro.hilo.publicado');
  });

  it('un comentario vacío no se envía', async () => {
    await crear(true);
    cargar(HILO, []);

    componente.texto.set('   ');
    componente.publicar();

    http.expectNone((r) => r.method === 'POST');
    expect(elemento().textContent).toContain('foro.hilo.sinComentarios');
  });

  it('responde a un comentario principal enviando su identificador como padre', async () => {
    await crear(true);
    cargar(HILO, [PRINCIPAL]);

    componente.abrirRespuesta('comentario-1');
    fixture.detectChanges();
    expect(elemento().querySelector('.respuesta-formulario')).not.toBeNull();
    componente.textoRespuesta.set('Gracias');
    componente.responder();

    const request = http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'POST');
    expect(request.request.body).toEqual({ contenido: 'Gracias', padreId: 'comentario-1' });
    request.flush({ ...RESPUESTA, contenido: 'Gracias' });

    expect(componente.respondiendoA()).toBeNull();
    cargar(HILO, [PRINCIPAL, { ...RESPUESTA, contenido: 'Gracias' }]);
    expect(elemento().querySelector('.respuestas')?.textContent).toContain('Gracias');
  });

  it('al abrir la respuesta el foco pasa a su campo y al cancelar vuelve al botón', async () => {
    await crear(true);
    cargar(HILO, [PRINCIPAL]);

    componente.abrirRespuesta('comentario-1');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(document.activeElement).toBe(elemento().querySelector('.respuesta-formulario textarea'));

    componente.cancelarRespuesta();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(document.activeElement).toBe(elemento().querySelector('button.boton-responder'));
  });

  it('las respuestas no tienen botón para responder: solo hay un nivel', async () => {
    await crear(true);
    cargar(HILO, [PRINCIPAL, RESPUESTA]);

    expect(elemento().querySelectorAll('button.boton-responder')).toHaveLength(1);
    expect(elemento().querySelector('.respuesta button.boton-responder')).toBeNull();
  });

  it('un hilo cerrado se lee pero no admite comentarios', async () => {
    await crear(true);
    cargar({ ...HILO, cerrado: true }, [PRINCIPAL]);

    expect(elemento().textContent).toContain('foro.hilo.avisoCerrado');
    expect(elemento().querySelector('textarea')).toBeNull();
    expect(elemento().querySelector('button.boton-responder')).toBeNull();
  });

  it('si el hilo se cerró mientras escribía, lo indica y quita el formulario', async () => {
    await crear(true);
    cargar(HILO, [PRINCIPAL]);

    componente.texto.set('Tarde');
    componente.publicar();
    http
      .expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'POST')
      .flush(
        { detail: 'El hilo está cerrado y ya no admite comentarios.', code: 'hilo_cerrado' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(componente.errorPublicar()).toBe('El hilo está cerrado y ya no admite comentarios.');
    expect(componente.hilo()?.cerrado).toBe(true);
    expect(elemento().querySelector('textarea')).toBeNull();
  });

  it('muestra el motivo que da el servidor cuando el comentario es inválido', async () => {
    await crear(true);
    cargar(HILO, [PRINCIPAL]);

    componente.texto.set('x');
    componente.publicar();
    http
      .expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'POST')
      .flush(
        {
          detail: 'El comentario contiene datos inválidos.',
          errores: [
            { campo: 'contenido', mensaje: 'El comentario no puede exceder 2000 caracteres.' },
          ],
        },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();

    expect(elemento().querySelector('[role="alert"]')?.textContent).toContain(
      'El comentario no puede exceder 2000 caracteres.',
    );
  });

  it('carga más comentarios cuando el hilo tiene otra página', async () => {
    await crear(false);
    cargar(HILO, [PRINCIPAL], false);
    expect(elemento().querySelector('button.cargar-mas')).not.toBeNull();

    componente.cargarMas();
    const request = http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'GET');
    expect(request.request.params.get('pagina')).toBe('1');
    expect(request.request.params.get('tamanio')).toBe('100');
    request.flush(pagina([RESPUESTA], 1, true));
    fixture.detectChanges();

    expect(componente.comentarios()).toHaveLength(2);
    expect(elemento().querySelector('button.cargar-mas')).toBeNull();
  });

  it('muestra un error con opción de reintentar si el hilo no carga', async () => {
    await crear(false);
    http.expectOne(RUTA_HILO).flush(null, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(elemento().querySelector('[role="alert"]')?.textContent).toContain('foro.hilo.error');

    componente.cargar();
    http.expectOne(RUTA_HILO).flush(HILO);
    // Al fallar el hilo se cancela la petición de comentarios que iba en paralelo.
    const pendientes = http.match((r) => r.url === RUTA_COMENTARIOS).filter((r) => !r.cancelled);
    expect(pendientes).toHaveLength(1);
    pendientes[0].flush(pagina([PRINCIPAL]));
    fixture.detectChanges();
    expect(componente.error()).toBe(false);
    expect(elemento().textContent).toContain('Grieta en el cabezal');
  });

  it('avisa al volver a la lista', async () => {
    await crear(false);
    cargar(HILO, []);
    let volvio = false;
    componente.volver.subscribe(() => (volvio = true));

    (elemento().querySelector('button') as HTMLButtonElement).click();

    expect(volvio).toBe(true);
  });
});
