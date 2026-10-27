import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { OrdenesMantenimientoPage } from './ordenes-mantenimiento.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

const BASE = '/api/v1/ordenes-mantenimiento';
const TOTALES = {
  total: 7,
  porEstado: {
    PROPUESTA: 3,
    PROGRAMADA: 1,
    EN_EJECUCION: 0,
    EJECUTADA: 1,
    DESCARTADA: 1,
    CANCELADA: 1,
  },
};

describe('OrdenesMantenimientoPage', () => {
  let fixture: ComponentFixture<OrdenesMantenimientoPage>;
  let page: OrdenesMantenimientoPage;
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe = vi.fn();
        unobserve = vi.fn();
        disconnect = vi.fn();
      },
    );
    await TestBed.configureTestingModule({
      imports: [OrdenesMantenimientoPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
      ],
    })
      .overrideComponent(OrdenesMantenimientoPage, {
        remove: { imports: [TranslocoPipe] },
        add: { imports: [TraduccionTestPipe] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(OrdenesMantenimientoPage);
    page = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.url === '/api/v1/puentes')
      .flush({
        content: [{ id: 'puente-1', codigo: 'GT-01', nombre: 'Puente El Trébol' }],
        totalElements: 1,
      });
  });

  afterEach(() => {
    http.verify();
    vi.unstubAllGlobals();
  });

  function listado(estados: string[]) {
    return http.expectOne(
      (r) =>
        r.url === BASE && JSON.stringify(r.params.getAll('estado')) === JSON.stringify(estados),
    );
  }

  it('abre en Propuestas con el total de cada pestaña y el rótulo provisional', () => {
    listado(['PROPUESTA']).flush({
      content: [
        {
          id: 'orden-1',
          puenteId: 'puente-1',
          descripcion: 'Limpiar drenajes',
          tipo: 'PREVENTIVO',
          estado: 'PROPUESTA',
          prioridadConfirmada: 'MEDIA',
          creadoEn: '2026-10-20T15:00:00Z',
          fechaProgramada: null,
          elementoRef: '/superestructura/drenaje',
          reglaProvisional: true,
        },
      ],
      totalElements: 1,
      size: 20,
      number: 0,
    });
    http.expectOne(`${BASE}/totales`).flush(TOTALES);
    fixture.detectChanges();

    const elemento = fixture.nativeElement as HTMLElement;
    expect(page.total('propuestas')).toBe(3);
    expect(page.total('cerradas')).toBe(2);
    expect(elemento.textContent).toContain('Limpiar drenajes');
    expect(elemento.textContent).toContain('GT-01 · Puente El Trébol');
    expect(elemento.textContent).toContain('mantenimiento.ordenes.provisional');
    expect(elemento.textContent).toContain('mantenimiento.ordenes.tipos.PREVENTIVO');
    expect(elemento.textContent).toContain('20/10/2026');
    expect(elemento.querySelector('a[href="/mantenimiento/ordenes/orden-1"]')).not.toBeNull();
  });

  it('la pestaña de cerradas pide descartadas y canceladas a la vez', () => {
    listado(['PROPUESTA']).flush({ content: [], totalElements: 0 });
    http.expectOne(`${BASE}/totales`).flush(TOTALES);

    page.cambiarPestana('cerradas');

    listado(['DESCARTADA', 'CANCELADA']).flush({ content: [], totalElements: 0 });
  });

  it('combina los filtros en la lista y en los totales', () => {
    listado(['PROPUESTA']).flush({ content: [], totalElements: 0 });
    http.expectOne(`${BASE}/totales`).flush(TOTALES);

    page.tipo.set('CORRECTIVO');
    page.prioridad.set('ALTA');
    page.puenteId.set('puente-1');
    page.desde.set(new Date(2026, 9, 1));
    page.hasta.set(new Date(2026, 9, 20));
    page.aplicarFiltros();

    const lista = listado(['PROPUESTA']);
    expect(lista.request.params.get('tipo')).toBe('CORRECTIVO');
    expect(lista.request.params.get('desde')).toBe('2026-10-01');
    expect(lista.request.params.get('hasta')).toBe('2026-10-20');
    lista.flush({ content: [], totalElements: 0 });
    const totales = http.expectOne((r) => r.url === `${BASE}/totales`);
    expect(totales.request.params.get('puenteId')).toBe('puente-1');
    expect(totales.request.params.get('prioridad')).toBe('ALTA');
    totales.flush(TOTALES);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'mantenimiento.ordenes.vacioFiltros',
    );
  });

  it('muestra el error con opción de reintentar', () => {
    listado(['PROPUESTA']).flush(null, { status: 500, statusText: 'Error' });
    http.expectOne(`${BASE}/totales`).flush(TOTALES);
    fixture.detectChanges();

    expect(page.error()).toBe(true);
    const boton = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      '.estado-lista button',
    );
    boton?.click();

    listado(['PROPUESTA']).flush({ content: [], totalElements: 0 });
    http.expectOne(`${BASE}/totales`).flush(TOTALES);
    expect(page.error()).toBe(false);
  });
});
