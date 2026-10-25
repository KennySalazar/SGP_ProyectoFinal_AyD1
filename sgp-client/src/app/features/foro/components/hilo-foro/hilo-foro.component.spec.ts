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
  transform(key: string, params?: Record<string, unknown>): string {
    return params ? `${key} ${JSON.stringify(params)}` : key;
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
  elementoRef: null,
  instanciaElementoId: null,
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

  async function crear(
    autenticado: boolean,
    usuario: { id: string; role: string } | null = autenticado
      ? { id: 'lector-1', role: 'ESTUDIANTE' }
      : null,
  ): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [HiloForoComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: AuthStore,
          useValue: { authenticated: signal(autenticado), user: signal(usuario) },
        },
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
    expect(request.request.body).toEqual({
      contenido: 'Mido mañana.',
      padreId: null,
      elementoRef: null,
      instanciaElementoId: null,
    });
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
  describe('en el hilo de una inspección (HU-064)', () => {
    const PILA = { elementoRef: '/subestructura/pilas', instanciaElementoId: 'pila-2' };
    const ESTRIBO = { elementoRef: '/subestructura/estribo', instanciaElementoId: null };
    const ANCLADO = { ...PRINCIPAL, id: 'comentario-9', ...PILA };
    const etiqueta = (a: { elementoRef: string }) =>
      a.elementoRef === PILA.elementoRef ? 'Pilas · Pila 2' : 'Estribo';

    /** Elige el elemento después de la primera carga, como hace quien pulsa un marcador. */
    function elegir(ancla: object | null, comentarios: object[]): void {
      fixture.componentRef.setInput('etiquetaDeAncla', etiqueta);
      fixture.componentRef.setInput('ancla', ancla);
      fixture.detectChanges();
      cargar(HILO, comentarios);
    }

    it('con un elemento elegido pide y muestra solo sus comentarios', async () => {
      await crear(true);
      cargar(HILO, [PRINCIPAL, ANCLADO]);

      fixture.componentRef.setInput('etiquetaDeAncla', etiqueta);
      fixture.componentRef.setInput('ancla', PILA);
      fixture.detectChanges();
      http.expectOne(RUTA_HILO).flush(HILO);
      const request = http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'GET');
      expect(request.request.params.get('elementoRef')).toBe(PILA.elementoRef);
      expect(request.request.params.get('instanciaElementoId')).toBe('pila-2');
      request.flush(pagina([ANCLADO]));
      fixture.detectChanges();

      expect(elemento().querySelectorAll('article.comentario')).toHaveLength(1);
      expect(elemento().querySelector('.filtro')?.textContent).toContain(
        'foro.hilo.filtrando {"elemento":"Pilas · Pila 2"}',
      );
      // Ya se sabe de qué elemento son: no se repite en cada comentario.
      expect(elemento().querySelector('.sobre')).toBeNull();
    });

    it('sin elemento elegido cada comentario anclado dice sobre qué elemento es', async () => {
      await crear(false);
      fixture.componentRef.setInput('etiquetaDeAncla', etiqueta);
      cargar(HILO, [PRINCIPAL, ANCLADO]);

      const sobre = elemento().querySelectorAll('.sobre');
      expect(sobre).toHaveLength(1);
      expect(sobre[0].textContent).toContain('foro.hilo.sobre {"elemento":"Pilas · Pila 2"}');
      expect(elemento().querySelector('.filtro')).toBeNull();
    });

    it('los comentarios nuevos quedan anclados al elemento elegido', async () => {
      await crear(true);
      cargar(HILO, []);
      elegir(PILA, []);
      expect(elemento().querySelector('.destino')?.textContent).toContain(
        'foro.hilo.comentarSobre {"elemento":"Pilas · Pila 2"}',
      );
      let publicados = 0;
      componente.comentarioPublicado.subscribe(() => publicados++);

      componente.texto.set('Se ve el acero expuesto');
      componente.publicar();
      const request = http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'POST');
      expect(request.request.body).toEqual({
        contenido: 'Se ve el acero expuesto',
        padreId: null,
        elementoRef: PILA.elementoRef,
        instanciaElementoId: 'pila-2',
      });
      request.flush(ANCLADO);
      cargar(HILO, [ANCLADO]);

      expect(publicados).toBe(1);
    });

    it('un elemento sin instancia no envía instancia', async () => {
      await crear(true);
      cargar(HILO, []);
      elegir(ESTRIBO, []);

      componente.texto.set('Texto');
      componente.publicar();
      const request = http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'POST');

      expect(request.request.body.elementoRef).toBe(ESTRIBO.elementoRef);
      expect(request.request.body.instanciaElementoId).toBeNull();
      request.flush(ANCLADO);
      cargar(HILO, []);
    });

    it('una respuesta no lleva ancla y avisa que cambiaron los totales', async () => {
      await crear(true);
      cargar(HILO, [ANCLADO]);
      let publicados = 0;
      componente.comentarioPublicado.subscribe(() => publicados++);

      componente.abrirRespuesta('comentario-9');
      componente.textoRespuesta.set('Coincido');
      componente.responder();
      const request = http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'POST');
      expect(request.request.body).toEqual({ contenido: 'Coincido', padreId: 'comentario-9' });
      request.flush({ ...RESPUESTA, ...PILA });
      cargar(HILO, [ANCLADO]);

      expect(publicados).toBe(1);
    });

    it('sin comentarios del elemento lo dice con un mensaje propio', async () => {
      await crear(false);
      cargar(HILO, [PRINCIPAL]);
      elegir(ESTRIBO, []);

      expect(elemento().textContent).toContain('foro.hilo.sinComentariosElemento');
    });

    it('permite dejar de filtrar por el elemento', async () => {
      await crear(true);
      cargar(HILO, [ANCLADO]);
      elegir(PILA, [ANCLADO]);
      let quitada = 0;
      componente.anclaQuitada.subscribe(() => quitada++);

      const verTodos = Array.from(
        elemento().querySelectorAll<HTMLButtonElement>('.filtro button'),
      ).find((b) => b.textContent?.includes('foro.hilo.verTodos'));
      verTodos!.click();

      expect(quitada).toBe(1);
    });

    it('desde el filtro lleva la vista al elemento en el formulario', async () => {
      await crear(true);
      cargar(HILO, [ANCLADO]);
      elegir(PILA, [ANCLADO]);
      const destinos: unknown[] = [];
      componente.irAElemento.subscribe((a) => destinos.push(a));

      const ver = Array.from(elemento().querySelectorAll<HTMLButtonElement>('.filtro button')).find(
        (b) => b.textContent?.includes('foro.hilo.verEnFormulario'),
      );
      ver!.click();

      expect(destinos).toEqual([PILA]);
    });

    it('la referencia «Sobre: …» de un comentario es un enlace que lleva a ese elemento', async () => {
      await crear(false);
      fixture.componentRef.setInput('etiquetaDeAncla', etiqueta);
      cargar(HILO, [PRINCIPAL, ANCLADO]);
      const destinos: unknown[] = [];
      componente.irAElemento.subscribe((a) => destinos.push(a));

      const referencias = elemento().querySelectorAll<HTMLButtonElement>('button.sobre');
      expect(referencias).toHaveLength(1);
      // Su nombre accesible empieza por el texto visible y dice qué hace.
      expect(referencias[0].getAttribute('aria-label')).toContain(
        'foro.hilo.sobre {"elemento":"Pilas · Pila 2"}',
      );
      expect(referencias[0].getAttribute('aria-label')).toContain('foro.hilo.verEnFormulario');
      referencias[0].click();

      expect(destinos).toEqual([{ elementoRef: PILA.elementoRef, instanciaElementoId: 'pila-2' }]);
    });

    it('cancela el comentario sobre el elemento: descarta lo escrito y vuelve a todo el hilo', async () => {
      await crear(true);
      cargar(HILO, []);
      elegir(PILA, []);
      componente.texto.set('Texto a medio escribir');
      componente.errorPublicar.set('Algún error anterior');
      let quitada = 0;
      componente.anclaQuitada.subscribe(() => quitada++);
      fixture.detectChanges();

      const cancelar = Array.from(
        elemento().querySelectorAll<HTMLButtonElement>('form .acciones button'),
      ).find((b) => b.textContent?.includes('common.cancel'));
      cancelar!.click();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(quitada).toBe(1);
      expect(componente.texto()).toBe('');
      expect(componente.errorPublicar()).toBeNull();
      http.expectNone((r) => r.method === 'POST');
      // El foco no se pierde: queda en el cuadro de comentario.
      expect(document.activeElement).toBe(
        elemento().querySelector('form:not(.respuesta-formulario) textarea'),
      );
    });

    it('sin elemento elegido no hay nada que cancelar', async () => {
      await crear(true);
      cargar(HILO, [PRINCIPAL]);

      const textos = Array.from(elemento().querySelectorAll('form .acciones button')).map(
        (b) => b.textContent ?? '',
      );
      expect(textos.some((t) => t.includes('common.cancel'))).toBe(false);
    });

    it('no se puede cancelar mientras el comentario se está publicando', async () => {
      await crear(true);
      cargar(HILO, []);
      elegir(PILA, []);
      componente.texto.set('Texto');
      componente.publicar();
      let quitada = 0;
      componente.anclaQuitada.subscribe(() => quitada++);

      componente.cancelarComentarioAnclado();

      expect(quitada).toBe(0);
      expect(componente.texto()).toBe('Texto');
      http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'POST');
    });

    it('muestra el motivo que da el servidor cuando el elemento no existe', async () => {
      await crear(true);
      cargar(HILO, []);
      elegir(ESTRIBO, []);

      componente.texto.set('Texto');
      componente.publicar();
      http
        .expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'POST')
        .flush(
          {
            detail: 'El comentario contiene datos inválidos.',
            errores: [
              {
                campo: 'elementoRef',
                mensaje: 'El elemento no existe en el formulario con que se levantó la inspección.',
              },
            ],
          },
          { status: 422, statusText: 'Unprocessable Entity' },
        );
      fixture.detectChanges();

      expect(componente.errorPublicar()).toBe(
        'El elemento no existe en el formulario con que se levantó la inspección.',
      );
    });

    it('puede ocultar el botón de volver a la lista de hilos del puente', async () => {
      await crear(false);
      fixture.componentRef.setInput('mostrarVolver', false);
      cargar(HILO, []);

      expect(elemento().querySelector(':scope > button')).toBeNull();
    });

    it('enfoca el formulario de comentario nuevo y no el de una respuesta', async () => {
      await crear(true);
      cargar(HILO, [PRINCIPAL]);
      componente.abrirRespuesta('comentario-1');
      fixture.detectChanges();

      componente.enfocarFormulario();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(document.activeElement).toBe(
        elemento().querySelector('form:not(.respuesta-formulario) textarea'),
      );
    });
  });
  describe('un hilo largo', () => {
    /** `n` comentarios principales, del más antiguo al más nuevo. */
    function comentariosDe(n: number): object[] {
      return Array.from({ length: n }, (_, k) => ({
        ...PRINCIPAL,
        id: `c-${k + 1}`,
        contenido: `Comentario número ${k + 1}`,
        creadoEn: `2026-10-20T09:${String(k).padStart(2, '0')}:00-06:00`,
      }));
    }
    const articulos = () =>
      Array.from(elemento().querySelectorAll<HTMLElement>('article.comentario:not(.respuesta)'));
    const botonMas = () => elemento().querySelector<HTMLButtonElement>('button.cargar-mas');
    const contador = () => elemento().querySelector('.contador-vista')?.textContent ?? '';

    it('muestra los primeros diez y ofrece mostrar más, con cuántos se ven', async () => {
      await crear(false);
      cargar(HILO, comentariosDe(13));

      expect(articulos()).toHaveLength(10);
      expect(botonMas()).not.toBeNull();
      expect(contador()).toContain('foro.hilo.mostrando {"mostrados":10,"total":13}');
    });

    it('mostrar más enseña los demás sin pedir nada al servidor y mueve el foco al primero nuevo', async () => {
      await crear(false);
      cargar(HILO, comentariosDe(13));

      botonMas()!.click();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(articulos()).toHaveLength(13);
      expect(botonMas()).toBeNull();
      expect(document.activeElement).toBe(articulos()[10]);
      http.expectNone((r) => r.url === RUTA_COMENTARIOS);
    });

    it('cuenta las respuestas de los comentarios que se ven', async () => {
      await crear(false);
      const comentarios = [
        ...comentariosDe(12),
        { ...RESPUESTA, id: 'r-1', padreId: 'c-1' },
        { ...RESPUESTA, id: 'r-2', padreId: 'c-12' },
      ];
      cargar(HILO, comentarios);

      // diez principales y la respuesta de uno de ellos; la del comentario 12 aún no se ve
      expect(contador()).toContain('"mostrados":11,"total":14');
    });

    it('con diez o menos no hay botón ni contador', async () => {
      await crear(false);
      cargar(HILO, comentariosDe(10));

      expect(articulos()).toHaveLength(10);
      expect(botonMas()).toBeNull();
      expect(elemento().querySelector('.contador-vista')).toBeNull();
    });

    it('si el servidor tiene más páginas no inventa el total y las pide al agotar lo cargado', async () => {
      await crear(false);
      cargar(HILO, comentariosDe(10), false);
      expect(contador()).toContain('foro.hilo.mostrandoSinTotal {"mostrados":10');
      expect(botonMas()).not.toBeNull();

      botonMas()!.click();
      const request = http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'GET');
      expect(request.request.params.get('pagina')).toBe('1');
      request.flush(
        pagina(
          comentariosDe(3).map((c, k) => ({ ...c, id: `d-${k}` })),
          1,
          true,
        ),
      );
      fixture.detectChanges();

      expect(articulos()).toHaveLength(13);
    });

    it('un comentario largo se recorta y se expande con «Ver más»', async () => {
      await crear(false);
      const largo = { ...PRINCIPAL, id: 'largo', contenido: 'x'.repeat(400) };
      cargar(HILO, [largo, { ...PRINCIPAL, id: 'corto', contenido: 'Corto' }]);

      const contenidos = elemento().querySelectorAll<HTMLElement>('.contenido');
      expect(contenidos[0].classList.contains('recortado')).toBe(true);
      expect(contenidos[1].classList.contains('recortado')).toBe(false);
      const verMas = elemento().querySelectorAll<HTMLButtonElement>('button.ver-mas');
      expect(verMas).toHaveLength(1);
      expect(verMas[0].getAttribute('aria-expanded')).toBe('false');

      verMas[0].click();
      fixture.detectChanges();

      expect(contenidos[0].classList.contains('recortado')).toBe(false);
      expect(elemento().querySelector('button.ver-mas')?.getAttribute('aria-expanded')).toBe(
        'true',
      );
    });

    it('también se recorta un comentario de pocas letras pero de muchas líneas', async () => {
      await crear(false);
      cargar(HILO, [{ ...PRINCIPAL, contenido: 'a\nb\nc\nd\ne' }]);

      expect(elemento().querySelector('.contenido')?.classList.contains('recortado')).toBe(true);
    });

    it('«Responder» va en la cabecera del comentario y desaparece mientras se responde', async () => {
      await crear(true);
      cargar(HILO, [PRINCIPAL]);

      const boton = elemento().querySelector('.comentario-cabecera button.boton-responder');
      expect(boton).not.toBeNull();

      componente.abrirRespuesta('comentario-1');
      fixture.detectChanges();

      expect(elemento().querySelector('button.boton-responder')).toBeNull();
      expect(elemento().querySelector('.respuesta-formulario')).not.toBeNull();
    });
  });

  describe('el cuadro para comentar', () => {
    const orden = () => {
      const cuadro = elemento().querySelector('form:not(.respuesta-formulario)')!;
      const lista = elemento().querySelector('section.comentarios')!;
      // 4 = la lista sigue al cuadro; 2 = el cuadro sigue a la lista
      return cuadro.compareDocumentPosition(lista);
    };

    it('por omisión va después de la lista, como en el foro de un puente', async () => {
      await crear(true);
      cargar(HILO, [PRINCIPAL]);

      expect(orden() & Node.DOCUMENT_POSITION_PRECEDING).toBeTruthy();
    });

    it('con compositorArriba va antes de la lista', async () => {
      await crear(true);
      fixture.componentRef.setInput('compositorArriba', true);
      cargar(HILO, [PRINCIPAL]);

      expect(orden() & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('la invitación a iniciar sesión también sube con el cuadro', async () => {
      await crear(false);
      fixture.componentRef.setInput('compositorArriba', true);
      cargar(HILO, [PRINCIPAL]);

      const aviso = elemento().querySelector('.aviso a[href="/login"]')!;
      const lista = elemento().querySelector('section.comentarios')!;
      expect(aviso.compareDocumentPosition(lista) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('al publicar, si el comentario queda escondido por la paginación se ofrece verlo', async () => {
      await crear(true);
      fixture.componentRef.setInput('compositorArriba', true);
      const doce = Array.from({ length: 12 }, (_, k) => ({ ...PRINCIPAL, id: `c-${k + 1}` }));
      cargar(HILO, doce);

      componente.texto.set('Mi comentario');
      componente.publicar();
      const request = http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'POST');
      request.flush({ ...PRINCIPAL, id: 'nuevo', contenido: 'Mi comentario' });
      cargar(HILO, [...doce, { ...PRINCIPAL, id: 'nuevo', contenido: 'Mi comentario' }]);

      const ver = elemento().querySelector<HTMLButtonElement>('.confirmacion button.ver-mas');
      expect(ver?.textContent).toContain('foro.hilo.verMiComentario');
      ver!.click();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(elemento().querySelectorAll('article.comentario:not(.respuesta)')).toHaveLength(13);
      expect(document.activeElement?.getAttribute('data-comentario-id')).toBe('nuevo');
      expect(elemento().querySelector('.confirmacion button.ver-mas')).toBeNull();
    });

    it('si el comentario nuevo ya se ve no ofrece nada que buscar', async () => {
      await crear(true);
      cargar(HILO, []);

      componente.texto.set('Mi comentario');
      componente.publicar();
      http
        .expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'POST')
        .flush({ ...PRINCIPAL, id: 'nuevo', contenido: 'Mi comentario' });
      cargar(HILO, [{ ...PRINCIPAL, id: 'nuevo', contenido: 'Mi comentario' }]);

      expect(elemento().querySelector('.confirmacion button.ver-mas')).toBeNull();
    });
  });

  describe('el selector de sección', () => {
    const FILTROS = {
      todos: 9,
      generales: 2,
      secciones: [
        { clave: 'identificacion', titulo: '1. Identificación del puente', total: 2 },
        { clave: 'subestructura', titulo: '4. Sub-estructura', total: 5 },
      ],
    };
    const selector = () => elemento().querySelector<HTMLSelectElement>('.filtro-seccion select');
    const elegir = (valor: string) => {
      selector()!.value = valor;
      selector()!.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    };

    it('ofrece todos, los generales y cada sección con su total', async () => {
      await crear(false);
      fixture.componentRef.setInput('filtros', FILTROS);
      cargar(HILO, [PRINCIPAL]);

      const opciones = Array.from(selector()!.options).map((o) =>
        o.textContent?.replace(/\s+/g, ' ').trim(),
      );
      expect(opciones).toEqual([
        'foro.hilo.filtroTodos {"total":9}',
        'foro.hilo.filtroGenerales {"total":2}',
        '1. Identificación del puente (2)',
        '4. Sub-estructura (5)',
      ]);
    });

    it('sin opciones suficientes no se muestra', async () => {
      await crear(false);
      fixture.componentRef.setInput('filtros', { todos: 3, generales: 3, secciones: [] });
      cargar(HILO, [PRINCIPAL]);

      expect(selector()).toBeNull();
    });

    it('sin que se le dé la información no se muestra, como en el foro de un puente', async () => {
      await crear(false);
      cargar(HILO, [PRINCIPAL]);

      expect(selector()).toBeNull();
    });

    it('al elegir una sección pide solo esa sección y vuelve a mostrar diez', async () => {
      await crear(false);
      fixture.componentRef.setInput('filtros', FILTROS);
      cargar(HILO, [PRINCIPAL]);

      elegir('seccion:subestructura');
      http.expectOne(RUTA_HILO).flush(HILO);
      const request = http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'GET');
      expect(request.request.params.get('seccion')).toBe('subestructura');
      expect(request.request.params.has('soloGenerales')).toBe(false);
      request.flush(pagina([PRINCIPAL]));
    });

    it('al elegir los generales pide solo los no anclados', async () => {
      await crear(false);
      fixture.componentRef.setInput('filtros', FILTROS);
      cargar(HILO, [PRINCIPAL]);

      elegir('generales');
      http.expectOne(RUTA_HILO).flush(HILO);
      const request = http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'GET');
      expect(request.request.params.get('soloGenerales')).toBe('true');
      expect(request.request.params.has('seccion')).toBe(false);
      request.flush(pagina([]));
    });

    it('al volver a todos no manda ningún filtro', async () => {
      await crear(false);
      fixture.componentRef.setInput('filtros', FILTROS);
      cargar(HILO, [PRINCIPAL]);
      elegir('generales');
      http.expectOne(RUTA_HILO).flush(HILO);
      http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'GET').flush(pagina([]));

      elegir('todos');
      http.expectOne(RUTA_HILO).flush(HILO);
      const request = http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'GET');
      expect(request.request.params.has('seccion')).toBe(false);
      expect(request.request.params.has('soloGenerales')).toBe(false);
      request.flush(pagina([PRINCIPAL]));
    });

    it('con un elemento elegido el selector se esconde y el servidor recibe solo el elemento', async () => {
      await crear(false);
      fixture.componentRef.setInput('filtros', FILTROS);
      cargar(HILO, [PRINCIPAL]);
      elegir('seccion:subestructura');
      http.expectOne(RUTA_HILO).flush(HILO);
      http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'GET').flush(pagina([]));

      fixture.componentRef.setInput('ancla', { elementoRef: '/otros', instanciaElementoId: null });
      fixture.detectChanges();
      http.expectOne(RUTA_HILO).flush(HILO);
      const request = http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'GET');

      expect(request.request.params.get('elementoRef')).toBe('/otros');
      expect(request.request.params.has('seccion')).toBe(false);
      expect(selector()).toBeNull();
      request.flush(pagina([]));
    });

    it('«Ver todos» quita el elemento y también el filtro de sección', async () => {
      await crear(false);
      fixture.componentRef.setInput('filtros', FILTROS);
      cargar(HILO, [PRINCIPAL]);
      elegir('generales');
      http.expectOne(RUTA_HILO).flush(HILO);
      http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'GET').flush(pagina([]));
      let quitada = 0;
      componente.anclaQuitada.subscribe(() => quitada++);

      componente.verTodos();
      fixture.detectChanges();

      expect(componente.filtro()).toBe('todos');
      expect(quitada).toBe(1);
      http.expectOne(RUTA_HILO).flush(HILO);
      http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'GET').flush(pagina([]));
    });

    it('un comentario general escrito con una sección elegida muestra todo', async () => {
      await crear(true);
      fixture.componentRef.setInput('filtros', FILTROS);
      cargar(HILO, [PRINCIPAL]);
      elegir('seccion:subestructura');
      http.expectOne(RUTA_HILO).flush(HILO);
      http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'GET').flush(pagina([]));

      componente.texto.set('Un comentario general');
      componente.publicar();
      http
        .expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'POST')
        .flush({ ...PRINCIPAL, id: 'nuevo' });

      fixture.detectChanges();

      expect(componente.filtro()).toBe('todos');
      http.expectOne(RUTA_HILO).flush(HILO);
      const request = http.expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'GET');
      expect(request.request.params.has('seccion')).toBe(false);
      request.flush(pagina([{ ...PRINCIPAL, id: 'nuevo' }]));
    });
  });

  describe('edición, eliminación y moderación (HU-065)', () => {
    const FUTURO = '2999-01-01T00:00:00Z';
    const PASADO = '2000-01-01T00:00:00Z';
    const AUTORA = { id: 'usuario-1', role: 'ESTUDIANTE' };
    const CATEDRATICO = { id: 'cat-1', role: 'CATEDRATICO' };
    const PROPIO = {
      ...PRINCIPAL,
      contenido: 'Texto con erorr',
      editadoEn: null,
      editableHasta: FUTURO,
      eliminado: false,
      oculto: false,
      motivoOcultamiento: null,
    };
    const RUTA_COMENTARIO = '/api/v1/comentarios/comentario-1';

    const botones = (texto: string) =>
      Array.from(elemento().querySelectorAll<HTMLButtonElement>('button')).filter((b) =>
        b.textContent?.includes(texto),
      );

    it('el autor edita su comentario dentro del plazo y queda marcado como editado', async () => {
      await crear(true, AUTORA);
      cargar(HILO, [PROPIO]);

      botones('foro.comentario.editar')[0].click();
      fixture.detectChanges();
      expect(componente.textoEdicion()).toBe('Texto con erorr');
      componente.textoEdicion.set('Texto corregido');
      componente.guardarEdicion();
      const request = http.expectOne(RUTA_COMENTARIO);
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({ contenido: 'Texto corregido' });
      request.flush({ ...PROPIO, contenido: 'Texto corregido', editadoEn: PROPIO.creadoEn });
      fixture.detectChanges();

      expect(componente.editandoId()).toBeNull();
      expect(elemento().querySelector('.contenido')?.textContent).toBe('Texto corregido');
      expect(elemento().textContent).toContain('foro.comentario.editado');
    });

    it('pasado el plazo no se ofrece editar, y si el servidor lo rechaza lo explica', async () => {
      await crear(true, AUTORA);
      cargar(HILO, [{ ...PROPIO, editableHasta: PASADO }]);

      expect(botones('foro.comentario.editar')).toHaveLength(0);
      expect(botones('foro.comentario.eliminar')).toHaveLength(1);

      // El reloj del cliente puede ir atrasado: decide el servidor.
      componente.comentarios.set([PROPIO]);
      componente.abrirEdicion(PROPIO);
      componente.textoEdicion.set('Tarde');
      componente.guardarEdicion();
      http.expectOne(RUTA_COMENTARIO).flush(
        {
          detail: 'Un comentario solo se puede editar en los 15 minutos siguientes a publicarlo.',
          code: 'plazo_edicion_vencido',
        },
        { status: 409, statusText: 'Conflict' },
      );
      fixture.detectChanges();

      expect(componente.errorEdicion()).toContain('15 minutos');
      expect(componente.puedeEditar(componente.comentarios()[0])).toBe(false);
    });

    it('nadie más que el autor ve las acciones de editar o eliminar', async () => {
      await crear(true, { id: 'otra', role: 'ESTUDIANTE' });
      cargar(HILO, [PROPIO]);

      expect(botones('foro.comentario.editar')).toHaveLength(0);
      expect(botones('foro.comentario.eliminar')).toHaveLength(0);
      expect(botones('foro.comentario.ocultar')).toHaveLength(0);
      expect(botones('foro.moderacion.cerrarHilo')).toHaveLength(0);
    });

    it('eliminar se confirma y deja la marca sin perder las respuestas', async () => {
      await crear(true, AUTORA);
      cargar(HILO, [PROPIO, { ...RESPUESTA, editableHasta: FUTURO }]);

      componente.pedirEliminar(PROPIO);
      fixture.detectChanges();
      expect(componente.pideMotivo()).toBe(false);
      componente.confirmarAccion();
      const request = http.expectOne(RUTA_COMENTARIO);
      expect(request.request.method).toBe('DELETE');
      request.flush({ ...PROPIO, contenido: null, eliminado: true });
      fixture.detectChanges();

      expect(componente.accion()).toBeNull();
      expect(elemento().textContent).toContain('foro.comentario.eliminado');
      expect(elemento().textContent).not.toContain('Texto con erorr');
      expect(elemento().querySelector('.respuestas')?.textContent).toContain('Sí, mide 3 mm.');
    });

    it('un usuario común ve «Oculto por moderación» en lugar del texto', async () => {
      await crear(true, { id: 'otra', role: 'PROFESIONAL_EXTERNO' });
      cargar(HILO, [{ ...PROPIO, contenido: null, oculto: true }]);

      expect(elemento().textContent).toContain('foro.comentario.oculto');
      expect(elemento().querySelector('.contenido')).toBeNull();
    });

    it('un moderador ve el texto oculto y su motivo', async () => {
      await crear(true, CATEDRATICO);
      cargar(HILO, [{ ...PROPIO, oculto: true, motivoOcultamiento: 'Lenguaje ofensivo' }]);

      expect(elemento().querySelector('.moderado')?.textContent).toContain('Lenguaje ofensivo');
      expect(elemento().querySelector('.contenido')?.textContent).toBe('Texto con erorr');
      expect(botones('foro.comentario.ocultar')).toHaveLength(0);
    });

    it('un moderador oculta un comentario indicando el motivo', async () => {
      await crear(true, { id: 'admin-1', role: 'ADMINISTRADOR' });
      cargar(HILO, [PROPIO]);

      botones('foro.comentario.ocultar')[0].click();
      fixture.detectChanges();
      expect(componente.pideMotivo()).toBe(true);
      // Sin motivo no se envía nada.
      componente.confirmarAccion();
      http.expectNone(RUTA_COMENTARIO + '/ocultar');

      componente.motivo.set('  Lenguaje ofensivo ');
      componente.confirmarAccion();
      const request = http.expectOne(RUTA_COMENTARIO + '/ocultar');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ motivo: 'Lenguaje ofensivo' });
      request.flush({ ...PROPIO, oculto: true, motivoOcultamiento: 'Lenguaje ofensivo' });
      fixture.detectChanges();

      expect(componente.accion()).toBeNull();
      expect(elemento().querySelector('.moderado')?.textContent).toContain('Lenguaje ofensivo');
    });

    it('el error del servidor se muestra dentro del diálogo', async () => {
      await crear(true, CATEDRATICO);
      cargar(HILO, [PROPIO]);

      componente.pedirOcultar(PROPIO);
      componente.motivo.set('x');
      componente.confirmarAccion();
      http.expectOne(RUTA_COMENTARIO + '/ocultar').flush(
        {
          detail: 'Motivo requerido',
          errores: [{ campo: 'motivo', mensaje: 'Indica el motivo de la moderación.' }],
        },
        { status: 422, statusText: 'Unprocessable Entity' },
      );

      expect(componente.errorAccion()).toBe('Indica el motivo de la moderación.');
      expect(componente.accion()).not.toBeNull();
    });

    it('un moderador cierra el hilo con motivo y queda visible para lectura', async () => {
      await crear(true, CATEDRATICO);
      cargar(HILO, [PROPIO]);

      botones('foro.moderacion.cerrarHilo')[0].click();
      componente.motivo.set('Tema resuelto');
      componente.confirmarAccion();
      const request = http.expectOne('/api/v1/hilos/hilo-1/cerrar');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ motivo: 'Tema resuelto' });
      request.flush({ ...HILO, cerrado: true, motivoCierre: 'Tema resuelto' });
      fixture.detectChanges();

      expect(elemento().textContent).toContain('foro.hilo.avisoCerrado');
      expect(elemento().textContent).toContain('Tema resuelto');
      expect(elemento().querySelector('.contenido')?.textContent).toBe('Texto con erorr');
      expect(elemento().querySelector('form:not(.respuesta-formulario) textarea')).toBeNull();
      expect(botones('foro.moderacion.cerrarHilo')).toHaveLength(0);
    });
  });

  describe('menciones, avisos y límite de tasa (HU-066)', () => {
    function principales(cantidad: number) {
      return Array.from({ length: cantidad }, (_, i) => ({
        ...PRINCIPAL,
        id: `principal-${i}`,
        contenido: `Comentario ${i}`,
      }));
    }

    it('el cuadro para comentar explica cómo mencionar a alguien', async () => {
      await crear(true);
      cargar(HILO, []);

      expect(elemento().textContent).toContain('foro.hilo.ayudaMenciones');
    });

    it('al llegar desde un aviso muestra, resalta y enfoca el comentario aunque esté oculto por la paginación', async () => {
      await crear(true);
      fixture.componentRef.setInput('comentarioDestacado', 'principal-14');
      fixture.detectChanges();
      cargar(HILO, principales(20));
      await fixture.whenStable();

      const destacado = elemento().querySelector<HTMLElement>(
        'article[data-comentario-id="principal-14"]',
      );
      expect(destacado).not.toBeNull();
      expect(destacado?.classList).toContain('destacado');
      expect(document.activeElement).toBe(destacado);
    });

    it('si el comentario está en otra página la pide hasta encontrarlo', async () => {
      await crear(true);
      fixture.componentRef.setInput('comentarioDestacado', 'respuesta-lejana');
      fixture.detectChanges();
      cargar(HILO, principales(3), false);

      const siguiente = http.expectOne(
        (r) => r.url === RUTA_COMENTARIOS && r.params.get('pagina') === '1',
      );
      siguiente.flush(
        pagina(
          [{ ...RESPUESTA, id: 'respuesta-lejana', padreId: 'principal-0', contenido: 'Aquí' }],
          1,
        ),
      );
      fixture.detectChanges();

      expect(componente.destacadoId()).toBe('respuesta-lejana');
      expect(
        elemento().querySelector('article[data-comentario-id="respuesta-lejana"]')?.classList,
      ).toContain('destacado');
    });

    it('si el comentario ya no está, muestra el hilo sin insistir', async () => {
      await crear(true);
      fixture.componentRef.setInput('comentarioDestacado', 'no-existe');
      fixture.detectChanges();
      cargar(HILO, [PRINCIPAL]);

      expect(componente.destacadoId()).toBeNull();
      expect(elemento().querySelectorAll('article.comentario')).toHaveLength(1);
    });

    it('al superar el límite de tasa muestra el mensaje del servidor junto al formulario', async () => {
      await crear(true);
      cargar(HILO, [PRINCIPAL]);

      componente.texto.set('Otro más');
      componente.publicar();
      http
        .expectOne((r) => r.url === RUTA_COMENTARIOS && r.method === 'POST')
        .flush(
          {
            detail: 'Publicaste demasiados comentarios en poco tiempo. Espera un momento.',
            code: 'limite_foro_excedido',
          },
          { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '30' } },
        );
      fixture.detectChanges();

      expect(componente.errorPublicar()).toContain('Espera un momento');
      expect(componente.texto()).toBe('Otro más');
    });
  });
});
