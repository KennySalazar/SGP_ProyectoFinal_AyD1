import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CatalogoPuentesPage } from './catalogo-puentes.page';
import { EstadoPuente } from '../../models/puente.models';

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
    http
      .expectOne((req) => req.url === '/api/v1/catalogos/departamentos')
      .flush({
        content: [
          { id: 'departamento-1', codigoIne: '01', nombre: 'Guatemala' },
          { id: 'departamento-2', codigoIne: '02', nombre: 'El Progreso' },
        ],
      });
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

  it('muestra datos públicos y cada estado con texto y severidad visual', () => {
    const estados: EstadoPuente[] = ['Bueno', 'Regular', 'Malo', 'Sin evaluar'];
    const severidades = ['success', 'warn', 'danger', 'secondary'];
    const content = estados.map((estadoActual, index) => ({
      id: `puente-${index}`,
      nombre: `Puente ${index}`,
      departamento: { id: 'departamento-1', codigoIne: '01', nombre: 'Guatemala' },
      municipio: {
        id: 'municipio-1',
        departamentoId: 'departamento-1',
        codigoIne: '0114',
        nombre: 'Amatitlán',
      },
      activo: true,
      estadoActual,
      fotografias: ['fotografia-privada.jpg'],
      danos: 'Daño detallado privado',
    }));
    http.expectOne((req) => req.url === '/api/v1/puentes').flush({ content, totalElements: 4 });
    fixture.detectChanges();
    const rows: NodeListOf<HTMLTableRowElement> =
      fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(4);
    rows.forEach((row, index) => {
      expect(row.querySelector('th[scope="row"]')?.textContent).toContain(`Puente ${index}`);
      expect(row.textContent).toContain('Guatemala');
      expect(row.textContent).toContain('Amatitlán');
      expect(row.querySelector(`.p-tag-${severidades[index]}`)?.textContent).toContain(
        estados[index],
      );
    });
    expect(fixture.nativeElement.querySelectorAll('thead th[scope="col"]')).toHaveLength(4);
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('fotografia-privada.jpg');
    expect(fixture.nativeElement.textContent).not.toContain('Daño detallado privado');
  });

  it('oculta la lista mientras carga o cuando falla la consulta', () => {
    expect(fixture.nativeElement.querySelector('p-table')).toBeNull();
    http.expectOne((req) => req.url === '/api/v1/puentes').flush({ content: [], totalElements: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('p-table')).not.toBeNull();
    page.cargarCatalogo();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('p-table')).toBeNull();
    http
      .expectOne((req) => req.url === '/api/v1/puentes')
      .flush({}, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('p-table')).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it.each([
    { departamentoId: 'departamento-1', estado: null },
    { departamentoId: null, estado: 'Malo' as EstadoPuente },
    { departamentoId: 'departamento-2', estado: 'Sin evaluar' as EstadoPuente },
  ])('envía los filtros al backend al enviar el formulario: $departamentoId/$estado', (filtros) => {
    http.expectOne((req) => req.url === '/api/v1/puentes').flush({ content: [], totalElements: 0 });
    page.filtros.patchValue(filtros);
    fixture.detectChanges();
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const request = http.expectOne((req) => req.url === '/api/v1/puentes');
    expect(request.request.params.get('departamentoId')).toBe(filtros.departamentoId);
    expect(request.request.params.get('estado')).toBe(filtros.estado);
    expect(request.request.params.get('pagina')).toBe('0');
    request.flush({ content: [], totalElements: 0 });
  });

  it('limpia ambos filtros y muestra el mensaje sin resultados', () => {
    http.expectOne((req) => req.url === '/api/v1/puentes').flush({ content: [], totalElements: 0 });
    page.filtros.patchValue({ departamentoId: 'departamento-1', estado: 'Bueno' });
    page.aplicarFiltros();
    http.expectOne((req) => req.url === '/api/v1/puentes').flush({ content: [], totalElements: 0 });
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.filtros-acciones button[type="button"]').click();
    expect(page.filtros.getRawValue()).toEqual({ departamentoId: null, estado: null });
    const request = http.expectOne((req) => req.url === '/api/v1/puentes');
    expect(request.request.params.keys().sort()).toEqual(['pagina', 'tamanio']);
    request.flush({ content: [], totalElements: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('tbody [role="status"]').textContent).toContain(
      'puentes.catalogo.noResults',
    );
  });

  it('reintenta con los filtros aplicados aunque el formulario tenga cambios sin enviar', () => {
    http.expectOne((req) => req.url === '/api/v1/puentes').flush({ content: [], totalElements: 0 });
    page.filtros.patchValue({ estado: 'Regular' });
    page.aplicarFiltros();
    http
      .expectOne((req) => req.url === '/api/v1/puentes')
      .flush({}, { status: 503, statusText: 'Service Unavailable' });
    page.filtros.patchValue({ estado: 'Malo' });
    page.cargarCatalogo();
    const request = http.expectOne((req) => req.url === '/api/v1/puentes');
    expect(request.request.params.get('estado')).toBe('Regular');
    request.flush({ content: [], totalElements: 0 });
  });

  it('un error de departamentos permite filtrar por estado y reintentar su carga', () => {
    http.expectOne((req) => req.url === '/api/v1/puentes').flush({ content: [], totalElements: 0 });
    page.cargarDepartamentos();
    http
      .expectOne((req) => req.url === '/api/v1/catalogos/departamentos')
      .flush({}, { status: 503, statusText: 'Service Unavailable' });
    expect(page.errorDepartamentos()).toBe(true);
    page.filtros.patchValue({ estado: 'Sin evaluar' });
    page.aplicarFiltros();
    http.expectOne((req) => req.url === '/api/v1/puentes').flush({ content: [], totalElements: 0 });
    page.cargarDepartamentos();
    http.expectOne((req) => req.url === '/api/v1/catalogos/departamentos').flush({ content: [] });
    expect(page.errorDepartamentos()).toBe(false);
    expect(page.cargandoDepartamentos()).toBe(false);
  });
});
