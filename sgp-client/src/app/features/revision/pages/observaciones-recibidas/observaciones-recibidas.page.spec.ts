import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { traduccionesDePrueba } from '../../../formulario/motor/testing/traducciones';
import { ObservacionesRecibidasPage } from './observaciones-recibidas.page';

describe('Observaciones recibidas del autor', () => {
  let fixture: ComponentFixture<ObservacionesRecibidasPage>;
  let http: HttpTestingController;
  const url = '/api/v1/observaciones/recibidas';
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ObservacionesRecibidasPage, traduccionesDePrueba()],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ObservacionesRecibidasPage);
    fixture.detectChanges();
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });
  it('lista los ciclos del autor sin enviar un identificador de propietario', async () => {
    const req = http.expectOne((r) => r.url === url);
    expect(req.request.params.keys().sort()).toEqual(['pagina', 'tamanio']);
    req.flush({
      content: [
        {
          revisionId: 'c1',
          inspeccionId: 'i1',
          numeroCiclo: 1,
          estadoRevision: 'RESUELTA',
          enviadaEn: '2026-10-19T12:00:00Z',
        },
        {
          revisionId: 'c2',
          inspeccionId: 'i1',
          numeroCiclo: 2,
          estadoRevision: 'EN_CURSO',
          enviadaEn: '2026-10-19T13:00:00Z',
        },
      ],
      number: 0,
      size: 20,
      totalElements: 21,
      empty: false,
    });
    fixture.detectChanges();
    await fixture.whenStable();
    const html = fixture.nativeElement as HTMLElement;
    expect(
      Array.from(html.querySelectorAll('article a')).map((a) => a.getAttribute('href')),
    ).toEqual(['/observaciones/c1', '/observaciones/c2']);
    expect(html.textContent).toContain('RESUELTA');
    expect(html.querySelector('app-editor-observaciones')).toBeNull();
    fixture.componentInstance.cambiarPagina({ page: 1 });
    expect(fixture.componentInstance.pagina()).toBeNull();
    const siguiente = http.expectOne((r) => r.url === url);
    expect(siguiente.request.params.get('pagina')).toBe('1');
    siguiente.flush({ content: [], number: 1, size: 20, totalElements: 0, empty: true });
    http.expectNone((r) => r.method !== 'GET');
  });
  it('muestra vacío sin cargar datos vivos ni esquemas activos', () => {
    http
      .expectOne((r) => r.url === url)
      .flush({ content: [], number: 0, size: 20, totalElements: 0, empty: true });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No tienes ciclos con observaciones.');
    http.expectNone((r) => r.url.includes('inspecciones') || r.url.includes('formularios'));
  });
  it.each([401, 403, 404])('no conserva datos privados ante %s y permite reintentar', (status) => {
    http.expectOne((r) => r.url === url).flush({}, { status, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.componentInstance.pagina()).toBeNull();
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    fixture.componentInstance.cargar();
    http
      .expectOne((r) => r.url === url)
      .flush({ content: [], number: 0, size: 20, totalElements: 0, empty: true });
    expect(fixture.componentInstance.error()).toBe(false);
  });
});
