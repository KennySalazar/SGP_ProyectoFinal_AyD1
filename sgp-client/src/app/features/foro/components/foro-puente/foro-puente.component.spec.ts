import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, EventEmitter, Input, Output, Pipe, PipeTransform, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../../../../core/services/auth.store';
import { PuenteResponse } from '../../../puentes/models/puente.models';
import { HiloForoComponent } from '../hilo-foro/hilo-foro.component';
import { ForoPuenteComponent } from './foro-puente.component';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

@Component({ selector: 'app-hilo-foro', standalone: true, template: '' })
class HiloForoStubComponent {
  @Input() hiloId = '';
  @Output() volver = new EventEmitter<void>();
}

const RUTA = '/api/v1/puentes/puente-1/hilos';
const PUENTE = { id: 'puente-1', nombre: 'Puente El Trébol', activo: true } as PuenteResponse;
const HILO = {
  id: 'hilo-1',
  puenteId: 'puente-1',
  inspeccionId: null,
  categoria: 'ALERTA',
  titulo: 'Socavación en la pila 2',
  autorId: 'usuario-1',
  autorUsuario: 'ana.estudiante',
  autorRol: 'ESTUDIANTE',
  creadoEn: '2026-10-20T09:00:00-06:00',
  actualizadoEn: '2026-10-20T10:30:00-06:00',
  cerrado: false,
  totalComentarios: 3,
};

function pagina(content: object[], number = 0, totalElements = content.length) {
  return {
    content,
    totalElements,
    totalPages: Math.max(1, Math.ceil(totalElements / 10)),
    number,
    size: 10,
    first: number === 0,
    last: (number + 1) * 10 >= totalElements,
    empty: content.length === 0,
  };
}

describe('ForoPuenteComponent', () => {
  let fixture: ComponentFixture<ForoPuenteComponent>;
  let componente: ForoPuenteComponent;
  let http: HttpTestingController;

  async function crear(autenticado: boolean): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ForoPuenteComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthStore, useValue: { authenticated: signal(autenticado) } },
        { provide: TranslocoService, useValue: { translate: (clave: string) => clave } },
      ],
    })
      .overrideComponent(ForoPuenteComponent, {
        remove: { imports: [TranslocoPipe, HiloForoComponent] },
        add: { imports: [TraduccionTestPipe, HiloForoStubComponent] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(ForoPuenteComponent);
    componente = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('puente', PUENTE);
    fixture.detectChanges();
  }

  function listado(contenido: object[], number = 0, total = contenido.length): void {
    http
      .expectOne((r) => r.url === RUTA && r.params.get('pagina') === String(number))
      .flush(pagina(contenido, number, total));
    fixture.detectChanges();
  }

  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => {
    http.verify();
    vi.unstubAllGlobals();
  });

  it('un visitante lee los hilos pero no puede abrir uno e invita a iniciar sesión', async () => {
    await crear(false);
    listado([HILO]);

    const elemento = fixture.nativeElement as HTMLElement;
    expect(texto()).toContain('Socavación en la pila 2');
    expect(texto()).toContain('foro.categorias.ALERTA');
    expect(texto()).toContain('@ana.estudiante');
    expect(texto()).not.toContain('foro.nuevoHilo.abrir');
    expect(elemento.querySelector('a[href="/login"]')).not.toBeNull();
  });

  it('pide los hilos del puente de diez en diez', async () => {
    await crear(false);
    const request = http.expectOne((r) => r.url === RUTA);

    expect(request.request.params.get('pagina')).toBe('0');
    expect(request.request.params.get('tamanio')).toBe('10');
    request.flush(pagina([]));
  });

  it('un autor sin nombre de usuario se muestra con su rol, nunca con su correo', async () => {
    await crear(false);
    listado([{ ...HILO, autorUsuario: null, autorRol: 'PROFESIONAL_EXTERNO' }]);

    expect(texto()).toContain('foro.roles.PROFESIONAL_EXTERNO');
    expect(texto()).not.toContain('@');
  });

  it('cada hilo indica su categoría con texto y la cantidad de comentarios', async () => {
    await crear(false);
    listado([
      HILO,
      { ...HILO, id: 'hilo-2', categoria: 'CONSULTA', totalComentarios: 1, cerrado: true },
    ]);

    expect(texto()).toContain('foro.categorias.CONSULTA');
    expect(texto()).toContain('foro.comentarioUno');
    expect(texto()).toContain('foro.comentarios');
    expect(texto()).toContain('foro.hilo.cerrado');
  });

  it('sin hilos explica distinto a un visitante y a quien puede abrir el primero', async () => {
    await crear(false);
    listado([]);
    expect(texto()).toContain('foro.sinHilos');
    expect(texto()).not.toContain('foro.sinHilosAutenticado');
    fixture.destroy();
    TestBed.resetTestingModule();

    await crear(true);
    listado([]);
    expect(texto()).toContain('foro.sinHilosAutenticado');
  });

  it('muestra un error con opción de reintentar si el foro no carga', async () => {
    await crear(false);
    http.expectOne((r) => r.url === RUTA).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('foro.error');

    componente.cargar(0);
    listado([HILO]);
    expect(componente.error()).toBe(false);
    expect(texto()).toContain('Socavación en la pila 2');
  });

  it('pagina cuando hay más de diez hilos', async () => {
    await crear(false);
    listado([HILO], 0, 25);

    expect((fixture.nativeElement as HTMLElement).querySelector('p-paginator')).not.toBeNull();
    componente.cambiarPagina({ page: 2 });
    listado([{ ...HILO, id: 'hilo-21' }], 2, 25);
    expect(componente.pagina()?.number).toBe(2);
  });

  it('quien tiene sesión abre un hilo con título, categoría y primer comentario', async () => {
    await crear(true);
    listado([]);
    expect(texto()).toContain('foro.nuevoHilo.abrir');

    componente.abrirNuevoHilo();
    componente.nuevoTitulo.set('  Grieta en el cabezal  ');
    componente.nuevaCategoria.set('CONSULTA');
    componente.nuevoComentario.set('  ¿Alguien la ha medido?  ');
    componente.crearHilo();

    const request = http.expectOne((r) => r.url === RUTA && r.method === 'POST');
    expect(request.request.body).toEqual({
      titulo: 'Grieta en el cabezal',
      categoria: 'CONSULTA',
      comentario: '¿Alguien la ha medido?',
    });
    request.flush(HILO);

    expect(componente.creando()).toBe(false);
    expect(componente.hiloCreado()).toBe(true);
    listado([HILO]);
    expect(texto()).toContain('foro.nuevoHilo.creado');
  });

  it('no deja abrir un hilo incompleto ni con un título más largo que el límite', async () => {
    await crear(true);
    listado([]);
    componente.abrirNuevoHilo();

    expect(componente.formularioValido()).toBe(false);
    componente.nuevoTitulo.set('Tema');
    componente.nuevaCategoria.set('CONSULTA');
    expect(componente.formularioValido()).toBe(false);
    componente.nuevoComentario.set('Texto');
    expect(componente.formularioValido()).toBe(true);
    componente.nuevoTitulo.set('t'.repeat(121));
    expect(componente.formularioValido()).toBe(false);

    componente.crearHilo();
    http.expectNone((r) => r.method === 'POST');
  });

  it('muestra junto a cada campo los errores del servidor al abrir el hilo', async () => {
    await crear(true);
    listado([]);
    componente.abrirNuevoHilo();
    componente.nuevoTitulo.set('Tema');
    componente.nuevaCategoria.set('CONSULTA');
    componente.nuevoComentario.set('Texto');
    componente.crearHilo();

    http
      .expectOne((r) => r.url === RUTA && r.method === 'POST')
      .flush(
        {
          detail: 'El hilo contiene datos inválidos.',
          code: 'hilo_invalido',
          errores: [{ campo: 'titulo', mensaje: 'El título es obligatorio.' }],
        },
        { status: 422, statusText: 'Unprocessable Entity' },
      );

    expect(componente.erroresCampo()['titulo']).toBe('El título es obligatorio.');
    expect(componente.errorCrear()).toBeNull();
    expect(componente.creando()).toBe(true);
  });

  it('abre la conversación de un hilo y al volver recarga la lista', async () => {
    await crear(false);
    listado([HILO]);

    componente.abrirHilo('hilo-1');
    fixture.detectChanges();
    const hijo = fixture.nativeElement.querySelector('app-hilo-foro');
    expect(hijo).not.toBeNull();
    expect(componente.hiloAbierto()).toBe('hilo-1');

    componente.volverALista();
    fixture.detectChanges();
    listado([{ ...HILO, totalComentarios: 4 }]);
    expect(componente.hiloAbierto()).toBeNull();
    expect(texto()).toContain('Socavación en la pila 2');
  });
});
