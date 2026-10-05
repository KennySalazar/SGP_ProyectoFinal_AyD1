import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CatalogoPuentesPage } from './catalogo-puentes.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('Base compartida del catálogo HU014', () => {
  let page: CatalogoPuentesPage;
  let http: HttpTestingController;
  let fixture: ComponentFixture<CatalogoPuentesPage>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.overrideComponent(CatalogoPuentesPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CatalogoPuentesPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('comparte una consulta al alternar entre lista y mapa', () => {
    const response = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
      first: true,
      last: true,
      empty: true,
    };
    expect(page.cargando()).toBe(true);
    http.expectOne((req) => req.url === '/api/v1/puentes').flush(response);
    expect(page.vista()).toBe('lista');
    page.vista.set('mapa');
    expect(page.pagina()).toEqual(response);
    page.vista.set('lista');
    expect(page.cargando()).toBe(false);
    http.expectNone('/api/v1/puentes');
  });

  it('el selector cambia la sección y comunica la opción seleccionada', () => {
    http.expectOne((req) => req.url === '/api/v1/puentes').flush({ content: [], totalElements: 0 });
    fixture.detectChanges();
    const buttons: NodeListOf<HTMLButtonElement> =
      fixture.nativeElement.querySelectorAll('.selector button');
    expect(buttons[0].getAttribute('aria-pressed')).toBe('true');
    buttons[1].click();
    fixture.detectChanges();
    expect(buttons[0].getAttribute('aria-pressed')).toBe('false');
    expect(buttons[1].getAttribute('aria-pressed')).toBe('true');
    expect(fixture.nativeElement.querySelector('h2').textContent).toContain('puentes.catalogo.map');
    buttons[0].click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h2').textContent).toContain(
      'puentes.catalogo.list',
    );
    http.expectNone('/api/v1/puentes');
  });

  it('permite reintentar tras un error sin duplicar solicitudes en curso', () => {
    page.cargarCatalogo();
    http
      .expectOne((req) => req.url === '/api/v1/puentes')
      .flush({}, { status: 503, statusText: 'Service Unavailable' });
    expect(page.errorConsulta()).toBe(true);
    expect(page.cargando()).toBe(false);
    page.cargarCatalogo();
    expect(page.errorConsulta()).toBe(false);
    http.expectOne((req) => req.url === '/api/v1/puentes').flush({ content: [] });
  });
});
