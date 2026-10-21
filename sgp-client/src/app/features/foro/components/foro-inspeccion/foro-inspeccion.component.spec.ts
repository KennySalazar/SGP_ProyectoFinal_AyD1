import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, input, output, Pipe, PipeTransform, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { afterEach, describe, expect, it } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { AnclaForo } from '../../models/foro.models';
import { HiloForoComponent } from '../hilo-foro/hilo-foro.component';
import { ForoInspeccionComponent } from './foro-inspeccion.component';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

/** El hilo real tiene su propia prueba; aquí solo importa lo que el contenedor le entrega. */
@Component({ selector: 'app-hilo-foro', standalone: true, template: '' })
class HiloForoStubComponent {
  readonly hiloId = input.required<string>();
  readonly ancla = input<AnclaForo | null>(null);
  readonly etiquetaDeAncla = input<(a: AnclaForo) => string>();
  readonly mostrarVolver = input(true);
  readonly anclaQuitada = output<void>();
  readonly comentarioPublicado = output<void>();
  readonly irAElemento = output<AnclaForo>();
  enfocado = false;
  enfocarFormulario(): void {
    this.enfocado = true;
  }
}

const RUTA = '/api/v1/inspecciones/insp-1/hilo';
const RESPUESTA = {
  hilo: { id: 'hilo-9', inspeccionId: 'insp-1' },
  anclas: [{ elementoRef: '/subestructura/estribo', instanciaElementoId: null, total: 2 }],
};

describe('ForoInspeccionComponent', () => {
  let fixture: ComponentFixture<ForoInspeccionComponent>;
  let componente: ForoInspeccionComponent;
  let http: HttpTestingController;
  let anclas: unknown[][];
  let errorGlobal: { limpiado: number };

  async function crear(autenticado: boolean): Promise<void> {
    errorGlobal = { limpiado: 0 };
    await TestBed.configureTestingModule({
      imports: [ForoInspeccionComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthStore, useValue: { authenticated: signal(autenticado) } },
        { provide: ApiErrorService, useValue: { clear: () => errorGlobal.limpiado++ } },
      ],
    })
      .overrideComponent(ForoInspeccionComponent, {
        remove: { imports: [TranslocoPipe, HiloForoComponent] },
        add: { imports: [TraduccionTestPipe, HiloForoStubComponent] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(ForoInspeccionComponent);
    componente = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    anclas = [];
    componente.anclasCambian.subscribe((a) => anclas.push(a));
    fixture.componentRef.setInput('inspeccionId', 'insp-1');
    fixture.detectChanges();
  }

  const elemento = () => fixture.nativeElement as HTMLElement;
  const hijo = () => fixture.debugElement.children.find((c) => c.name === 'app-hilo-foro');

  afterEach(() => http.verify());

  it('quien tiene sesión abre el hilo, que se crea si todavía no existe', async () => {
    await crear(true);

    const request = http.expectOne(RUTA);
    expect(request.request.method).toBe('POST');
    request.flush(RESPUESTA);
    fixture.detectChanges();

    expect(componente.hiloId()).toBe('hilo-9');
    expect(hijo()).toBeDefined();
    expect(anclas).toEqual([RESPUESTA.anclas]);
  });

  it('un visitante solo consulta el hilo y nunca lo crea', async () => {
    await crear(false);

    const request = http.expectOne(RUTA);
    expect(request.request.method).toBe('GET');
    request.flush(RESPUESTA);
    fixture.detectChanges();

    expect(componente.estado()).toBe('listo');
  });

  it('si un visitante consulta una inspección sin hilo se le invita a iniciar sesión', async () => {
    await crear(false);

    http.expectOne(RUTA).flush(null, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(componente.estado()).toBe('sinHilo');
    expect(elemento().textContent).toContain('foro.inspeccion.sinHilo');
    expect(elemento().querySelector('a[href="/login"]')).not.toBeNull();
    expect(hijo()).toBeUndefined();
    expect(errorGlobal.limpiado).toBe(1);
  });

  it('un fallo al abrir el foro se explica y se puede reintentar', async () => {
    await crear(true);
    http.expectOne(RUTA).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(elemento().querySelector('[role="alert"]')?.textContent).toContain(
      'foro.inspeccion.error',
    );
    expect(errorGlobal.limpiado).toBe(1);

    (elemento().querySelector('[role="alert"] button') as HTMLButtonElement).click();
    http.expectOne(RUTA).flush(RESPUESTA);
    fixture.detectChanges();

    expect(componente.estado()).toBe('listo');
  });

  it('un 404 con sesión es un error, no un hilo que falta', async () => {
    await crear(true);

    http.expectOne(RUTA).flush(null, { status: 404, statusText: 'Not Found' });

    expect(componente.estado()).toBe('error');
  });

  it('entrega al hilo el elemento elegido sin botón de volver', async () => {
    await crear(true);
    http.expectOne(RUTA).flush(RESPUESTA);
    const ancla = { elementoRef: '/subestructura/estribo', instanciaElementoId: null };
    fixture.componentRef.setInput('ancla', ancla);
    fixture.detectChanges();

    const stub = hijo()?.componentInstance as HiloForoStubComponent;
    expect(stub.hiloId()).toBe('hilo-9');
    expect(stub.ancla()).toEqual(ancla);
    expect(stub.mostrarVolver()).toBe(false);
  });

  it('avisa cuando se deja de filtrar por el elemento', async () => {
    await crear(true);
    http.expectOne(RUTA).flush(RESPUESTA);
    fixture.detectChanges();
    let quitada = 0;
    componente.anclaQuitada.subscribe(() => quitada++);

    (hijo()?.componentInstance as HiloForoStubComponent).anclaQuitada.emit();

    expect(quitada).toBe(1);
  });

  it('reenvía la petición de ir al elemento del formulario', async () => {
    await crear(true);
    http.expectOne(RUTA).flush(RESPUESTA);
    fixture.detectChanges();
    const destinos: AnclaForo[] = [];
    componente.irAElemento.subscribe((a) => destinos.push(a));
    const ancla = { elementoRef: '/subestructura/pilas', instanciaElementoId: 'pila-2' };

    (hijo()?.componentInstance as HiloForoStubComponent).irAElemento.emit(ancla);

    expect(destinos).toEqual([ancla]);
  });

  it('al comentar vuelve a leer los totales de los marcadores', async () => {
    await crear(true);
    http.expectOne(RUTA).flush(RESPUESTA);
    fixture.detectChanges();

    (hijo()?.componentInstance as HiloForoStubComponent).comentarioPublicado.emit();
    const request = http.expectOne(RUTA);
    expect(request.request.method).toBe('GET');
    const actualizadas = [
      { elementoRef: '/subestructura/estribo', instanciaElementoId: null, total: 3 },
    ];
    request.flush({ ...RESPUESTA, anclas: actualizadas });

    expect(anclas.at(-1)).toEqual(actualizadas);
  });

  it('si no puede releer los totales conserva los que tenía', async () => {
    await crear(true);
    http.expectOne(RUTA).flush(RESPUESTA);
    fixture.detectChanges();

    (hijo()?.componentInstance as HiloForoStubComponent).comentarioPublicado.emit();
    http.expectOne(RUTA).flush(null, { status: 500, statusText: 'Server Error' });

    expect(anclas).toHaveLength(1);
    expect(componente.estado()).toBe('listo');
  });

  it('enfoca el formulario de comentario del hilo', async () => {
    await crear(true);
    http.expectOne(RUTA).flush(RESPUESTA);
    fixture.detectChanges();

    componente.enfocarFormulario();

    expect((hijo()?.componentInstance as HiloForoStubComponent).enfocado).toBe(true);
  });

  it('al cambiar de inspección carga el hilo de la nueva', async () => {
    await crear(true);
    http.expectOne(RUTA).flush(RESPUESTA);
    fixture.componentRef.setInput('inspeccionId', 'insp-2');
    fixture.detectChanges();

    http
      .expectOne('/api/v1/inspecciones/insp-2/hilo')
      .flush({ hilo: { id: 'hilo-10' }, anclas: [] });

    expect(componente.hiloId()).toBe('hilo-10');
  });
});
