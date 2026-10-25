import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, Pipe, PipeTransform } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { foroRoutes } from '../../foro.routes';
import { IrAForoPage } from './ir-a-foro.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

@Component({ template: '' })
class DestinoTest {}

const HILO_PUENTE = { id: 'hilo-1', puenteId: 'puente-1', inspeccionId: null };
const HILO_INSPECCION = { id: 'hilo-2', puenteId: 'puente-1', inspeccionId: 'inspeccion-1' };

describe('Característica: el aviso del foro lleva al hilo y al comentario HU066', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'foro', children: foroRoutes },
          { path: 'puentes/:id', component: DestinoTest },
          { path: 'inspecciones/:id/publicada', component: DestinoTest },
          { path: 'avisos', component: DestinoTest },
        ]),
        { provide: TranslocoService, useValue: { translate: (clave: string) => clave } },
      ],
    });
    TestBed.overrideComponent(IrAForoPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const url = () => TestBed.inject(Router).url;

  it('Escenario: una mención en el foro de un puente abre su hilo con el comentario', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/foro/comentarios/comentario-1');
    http
      .expectOne('/api/v1/comentarios/comentario-1')
      .flush({ id: 'comentario-1', hiloId: 'hilo-1' });
    http.expectOne('/api/v1/hilos/hilo-1').flush(HILO_PUENTE);
    await harness.fixture.whenStable();

    expect(url()).toBe('/puentes/puente-1?hilo=hilo-1&comentario=comentario-1');
  });

  it('Escenario: una mención en una inspección publicada abre su foro con el comentario', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/foro/comentarios/comentario-2');
    http
      .expectOne('/api/v1/comentarios/comentario-2')
      .flush({ id: 'comentario-2', hiloId: 'hilo-2' });
    http.expectOne('/api/v1/hilos/hilo-2').flush(HILO_INSPECCION);
    await harness.fixture.whenStable();

    expect(url()).toBe('/inspecciones/inspeccion-1/publicada?comentario=comentario-2');
  });

  it('Escenario: una alerta abre el hilo en el foro del puente', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/foro/hilos/hilo-1');
    http.expectOne('/api/v1/hilos/hilo-1').flush(HILO_PUENTE);
    await harness.fixture.whenStable();

    expect(url()).toBe('/puentes/puente-1?hilo=hilo-1');
  });

  it('Escenario: si el comentario ya no existe lo explica y ofrece volver a los avisos', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/foro/comentarios/borrado');
    http
      .expectOne('/api/v1/comentarios/borrado')
      .flush({ code: 'comentario_no_encontrado' }, { status: 404, statusText: 'Not Found' });
    harness.detectChanges();

    expect(url()).toBe('/foro/comentarios/borrado');
    expect(harness.routeNativeElement?.textContent).toContain('foro.irA.error');
    expect(harness.routeNativeElement?.querySelector('a')?.getAttribute('href')).toBe('/avisos');
  });
});
