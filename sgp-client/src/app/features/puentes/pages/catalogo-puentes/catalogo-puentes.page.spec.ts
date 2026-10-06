import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Paginator } from 'primeng/paginator';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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

  afterEach(() => {
    http.verify();
    fixture.destroy();
    vi.unstubAllGlobals();
  });

  it('abre departamentos con panel opaco y opciones etiquetadas fuera de la tabla', async () => {
    // JSDOM no implementa matchMedia; el overlay lo usa para el modo responsive.
    vi.stubGlobal(
      'matchMedia',
      vi.fn((media: string) => ({
        matches: false,
        media,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    );
    http.expectOne((req) => req.url === '/api/v1/puentes').flush({ content: [], totalElements: 0 });
    fixture.detectChanges();
    fixture.nativeElement.querySelector('#catalogo-departamento').click();
    fixture.detectChanges();
    await fixture.whenStable();
    const panel = document.body.querySelector('.p-select-overlay') as HTMLElement;
    expect(panel).not.toBeNull();
    expect(panel.style.backgroundColor).toBe('rgb(255, 255, 255)');
    expect(panel.style.opacity).toBe('1');
    expect(panel.closest('.tabla-contenedor')).toBeNull();
    const opciones = panel.querySelectorAll<HTMLElement>('.opcion-catalogo');
    expect(opciones).toHaveLength(2);
    expect(opciones[0].textContent).toContain('Guatemala');
    opciones[0].click();
    fixture.detectChanges();
    expect(page.filtros.controls.departamentoId.value).toBe('departamento-1');
    http.expectNone((req) => req.url === '/api/v1/puentes');
  });

  function responderPagina(number = 0, size = 20, totalElements = 41): void {
    http
      .expectOne((req) => req.url === '/api/v1/puentes')
      .flush({
        content: [],
        number,
        size,
        totalElements,
        totalPages: Math.ceil(totalElements / size),
        first: number === 0,
        last: (number + 1) * size >= totalElements,
        empty: true,
      });
    fixture.detectChanges();
  }

  it('navega con los controles y utiliza la página y el total del backend', () => {
    responderPagina();
    expect(fixture.nativeElement.querySelector('.p-paginator-prev').disabled).toBe(true);
    fixture.nativeElement.querySelector('.p-paginator-next').click();
    const siguiente = http.expectOne((req) => req.url === '/api/v1/puentes');
    expect(siguiente.request.params.get('pagina')).toBe('1');
    expect(siguiente.request.params.get('tamanio')).toBe('20');
    siguiente.flush({
      content: [
        {
          id: 'puente-21',
          nombre: 'Puente página 2',
          departamento: { nombre: 'Guatemala' },
          municipio: { nombre: 'Amatitlán' },
          estadoActual: 'Sin evaluar',
        },
      ],
      number: 1,
      size: 20,
      totalElements: 41,
      totalPages: 3,
      first: false,
      last: false,
      empty: false,
    });
    fixture.detectChanges();
    const paginator = fixture.debugElement.query(By.directive(Paginator))
      .componentInstance as Paginator;
    expect(paginator.first).toBe(20);
    expect(paginator.totalRecords).toBe(41);
    expect(fixture.nativeElement.querySelector('tbody').textContent).toContain('Puente página 2');
    fixture.nativeElement.querySelector('.p-paginator-prev').click();
    responderPagina();
    page.cambiarPagina({ page: 2, rows: 20 });
    responderPagina(2);
    expect(fixture.nativeElement.querySelector('.p-paginator-next').disabled).toBe(true);
  });

  it('conserva filtros aplicados al paginar e ignora cambios del formulario sin enviar', () => {
    responderPagina();
    page.filtros.patchValue({ departamentoId: 'departamento-1', estado: 'Regular' });
    page.aplicarFiltros();
    responderPagina();
    page.filtros.patchValue({ departamentoId: 'departamento-2', estado: 'Malo' });
    page.cambiarPagina({ page: 1, rows: 20 });
    const request = http.expectOne((req) => req.url === '/api/v1/puentes');
    expect(request.request.params.get('departamentoId')).toBe('departamento-1');
    expect(request.request.params.get('estado')).toBe('Regular');
    expect(request.request.params.get('pagina')).toBe('1');
    request.flush({ content: [], number: 1, size: 20, totalElements: 41 });
  });

  it('reinicia en página cero al cambiar tamaño, aplicar o limpiar filtros', () => {
    responderPagina();
    page.cambiarPagina({ page: 1, rows: 20 });
    responderPagina(1);
    expect(page.tamaniosPagina).toEqual([10, 20, 50, 100]);
    const selector = fixture.debugElement.query(By.css('.tamanio-pagina p-select'));
    selector.triggerEventHandler('onChange', { value: 100 });
    const cambio = http.expectOne((req) => req.url === '/api/v1/puentes');
    expect(cambio.request.params.get('pagina')).toBe('0');
    expect(cambio.request.params.get('tamanio')).toBe('100');
    cambio.flush({ content: [], number: 0, size: 100, totalElements: 250 });
    page.cambiarPagina({ page: 1, rows: 100 });
    responderPagina(1, 100, 250);
    page.filtros.patchValue({ estado: 'Bueno' });
    page.aplicarFiltros();
    const aplicado = http.expectOne((req) => req.url === '/api/v1/puentes');
    expect(aplicado.request.params.get('pagina')).toBe('0');
    expect(aplicado.request.params.get('tamanio')).toBe('100');
    aplicado.flush({ content: [], number: 0, size: 100, totalElements: 250 });
    page.cambiarPagina({ page: 1, rows: 100 });
    responderPagina(1, 100, 250);
    page.limpiarFiltros();
    const limpio = http.expectOne((req) => req.url === '/api/v1/puentes');
    expect(limpio.request.params.keys().sort()).toEqual(['pagina', 'tamanio']);
    expect(limpio.request.params.get('pagina')).toBe('0');
    expect(limpio.request.params.get('tamanio')).toBe('100');
    limpio.flush({ content: [], number: 0, size: 100, totalElements: 0 });
  });

  it('rechaza eventos inválidos y evita solicitudes duplicadas al paginar', () => {
    responderPagina();
    for (const rows of [0, -1, 101, 1.5, NaN]) page.cambiarPagina({ page: 1, rows });
    for (const pagina of [-1, 1.5, NaN]) page.cambiarPagina({ page: pagina, rows: 20 });
    page.cambiarPagina({ rows: 20 });
    page.cambiarPagina({ page: 1 });
    page.cambiarPagina({ page: 0, rows: 20 });
    http.expectNone((req) => req.url === '/api/v1/puentes');
    page.cambiarPagina({ page: 1, rows: 20 });
    page.cambiarPagina({ page: 2, rows: 50 });
    responderPagina(1);
    page.cambiarPagina({ page: 1, rows: 20 });
    http.expectNone((req) => req.url === '/api/v1/puentes');
  });

  it('reintenta la página solicitada después de un error', () => {
    responderPagina();
    page.cambiarPagina({ page: 1, rows: 20 });
    http
      .expectOne((req) => req.url === '/api/v1/puentes')
      .flush({}, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('p-paginator')).toBeNull();
    page.cargarCatalogo();
    const request = http.expectOne((req) => req.url === '/api/v1/puentes');
    expect(request.request.params.get('pagina')).toBe('1');
    expect(request.request.params.get('tamanio')).toBe('20');
    request.flush({ content: [], number: 1, size: 20, totalElements: 41 });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('p-paginator')).not.toBeNull();
  });

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

  it('destaca la lista y mantiene el mapa pendiente deshabilitado', () => {
    http.expectOne((req) => req.url === '/api/v1/puentes').flush({ content: [], totalElements: 0 });
    fixture.detectChanges();
    const buttons: NodeListOf<HTMLButtonElement> =
      fixture.nativeElement.querySelectorAll('.selector button');
    expect(buttons[0].getAttribute('aria-pressed')).toBe('true');
    buttons[1].click();
    fixture.detectChanges();
    expect(buttons[0].getAttribute('aria-pressed')).toBe('true');
    expect(buttons[1].getAttribute('aria-pressed')).toBe('false');
    expect(buttons[1].disabled).toBe(true);
    expect(buttons[1].textContent).toContain('puentes.catalogo.comingSoon');
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
      expect(row.classList.contains('fila-catalogo')).toBe(true);
      row.querySelectorAll('th, td').forEach((cell) => {
        const estilo = getComputedStyle(cell);
        expect(estilo.borderBottomStyle).toBe('solid');
        expect(estilo.borderBottomWidth).toBe('1px');
      });
      expect(row.querySelector('th[scope="row"]')?.textContent).toContain(`Puente ${index}`);
      expect(row.textContent).toContain('Guatemala');
      expect(row.textContent).toContain('Amatitlán');
      expect(row.querySelector(`.p-tag-${severidades[index]}`)?.textContent).toContain(
        estados[index],
      );
    });
    expect(fixture.nativeElement.querySelectorAll('thead th[scope="col"]')).toHaveLength(5);
    expect(fixture.nativeElement.querySelector('.tabla-contenedor')?.getAttribute('tabindex')).toBe(
      '0',
    );
    expect(fixture.nativeElement.querySelector('.estado-puente')?.getAttribute('data-estado')).toBe(
      'Bueno',
    );
    expect(fixture.nativeElement.querySelector('.resultados-cabecera h2')?.textContent).toContain(
      'puentes.catalogo.listTitle',
    );
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
      'puentes.catalogo.emptyCatalog',
    );
  });

  function responderConPuentes(number = 0, size = 20): void {
    const content = [0, 1].map((indice) => ({
      id: `uuid-privado-${indice}`,
      nombre: `Puente ${indice}`,
      departamento: { id: 'departamento-1', codigoIne: '01', nombre: 'Guatemala' },
      municipio: { id: 'municipio-1', codigoIne: '0114', nombre: 'Amatitlán' },
      estadoActual: 'Sin evaluar',
    }));
    http
      .expectOne((req) => req.url === '/api/v1/puentes')
      .flush({
        content,
        number,
        size,
        totalElements: 42,
        totalPages: Math.ceil(42 / size),
      });
    fixture.detectChanges();
  }

  it('selecciona una sola fila con clic o teclado y permite desmarcarla sin consultar la API', () => {
    responderConPuentes();
    const rows: NodeListOf<HTMLTableRowElement> =
      fixture.nativeElement.querySelectorAll('tbody tr');
    rows[0].click();
    fixture.detectChanges();
    expect(page.puenteSeleccionado()?.nombre).toBe('Puente 0');
    expect(rows[0].classList.contains('fila-seleccionada')).toBe(true);
    expect(rows[0].getAttribute('aria-selected')).toBe('true');
    expect(rows[0].querySelector('.pi-check')).not.toBeNull();
    rows[1].dispatchEvent(new KeyboardEvent('keydown', { code: 'Enter', bubbles: true }));
    fixture.detectChanges();
    expect(page.puenteSeleccionado()?.nombre).toBe('Puente 1');
    expect(rows[0].classList.contains('fila-seleccionada')).toBe(false);
    expect(rows[0].getAttribute('aria-selected')).toBe('false');
    expect(rows[1].classList.contains('fila-seleccionada')).toBe(true);
    expect(fixture.nativeElement.querySelector('.seleccion-catalogo').textContent).toContain(
      'puentes.catalogo.selectedBridge',
    );
    rows[1].dispatchEvent(new KeyboardEvent('keydown', { code: 'Space', bubbles: true }));
    fixture.detectChanges();
    expect(page.puenteSeleccionado()).toBeNull();
    expect(fixture.nativeElement.querySelector('.fila-seleccionada')).toBeNull();
    http.expectNone((req) => req.url === '/api/v1/puentes');
  });

  it('numera por posición entre páginas y reinicia numeración y selección al filtrar', () => {
    const numeros = () =>
      Array.from(
        fixture.nativeElement.querySelectorAll('.numero-puente') as NodeListOf<HTMLElement>,
      ).map((celda) => celda.textContent?.trim());
    responderConPuentes();
    expect(numeros()).toEqual(['1', '2']);
    expect(fixture.nativeElement.textContent).not.toContain('uuid-privado');
    fixture.nativeElement.querySelector('tbody tr').click();
    page.cambiarPagina({ page: 1, rows: 20 });
    expect(page.puenteSeleccionado()).toBeNull();
    responderConPuentes(1);
    expect(numeros()).toEqual(['21', '22']);
    fixture.nativeElement.querySelector('tbody tr').click();
    page.filtros.patchValue({ estado: 'Sin evaluar' });
    page.aplicarFiltros();
    expect(page.puenteSeleccionado()).toBeNull();
    responderConPuentes();
    expect(numeros()).toEqual(['1', '2']);
    page.cambiarPagina({ page: 0, rows: 10 });
    responderConPuentes(0, 10);
    page.cambiarPagina({ page: 1, rows: 10 });
    responderConPuentes(1, 10);
    expect(numeros()).toEqual(['11', '12']);
  });

  it('distingue catálogo vacío de filtros sin resultados según los filtros aplicados', () => {
    responderPagina(0, 20, 0);
    expect(fixture.nativeElement.querySelector('p-paginator')).toBeNull();
    expect(fixture.nativeElement.querySelector('tbody').textContent).toContain(
      'puentes.catalogo.emptyCatalog',
    );
    page.filtros.patchValue({ estado: 'Malo' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('tbody').textContent).toContain(
      'puentes.catalogo.emptyCatalog',
    );
    page.aplicarFiltros();
    responderPagina(0, 20, 0);
    expect(fixture.nativeElement.querySelector('tbody').textContent).toContain(
      'puentes.catalogo.noResults',
    );
    expect(fixture.nativeElement.querySelector('p-paginator')).toBeNull();
    fixture.nativeElement.querySelector('.sin-resultados button').click();
    const request = http.expectOne((req) => req.url === '/api/v1/puentes');
    expect(request.request.params.has('estado')).toBe(false);
    request.flush({ content: [], totalElements: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('tbody').textContent).toContain(
      'puentes.catalogo.emptyCatalog',
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
