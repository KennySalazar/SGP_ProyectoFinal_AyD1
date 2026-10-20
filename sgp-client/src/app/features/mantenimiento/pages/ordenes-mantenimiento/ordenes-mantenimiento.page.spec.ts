import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { OrdenesMantenimientoPage } from './ordenes-mantenimiento.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

const BASE = '/api/v1/ordenes-mantenimiento';

describe('OrdenesMantenimientoPage', () => {
  let fixture: ComponentFixture<OrdenesMantenimientoPage>;
  let page: OrdenesMantenimientoPage;
  let http: HttpTestingController;

  beforeEach(async () => {
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
  });

  afterEach(() => http.verify());

  it('lista las órdenes con su estado y enlace al detalle', () => {
    http.expectOne(`${BASE}?pagina=0&tamanio=20`).flush({
      content: [
        {
          id: 'orden-1',
          descripcion: 'Limpiar drenajes',
          tipo: 'PREVENTIVO',
          estado: 'EN_EJECUCION',
          prioridadConfirmada: 'MEDIA',
          fechaProgramada: '2026-10-20',
          elementoRef: '/superestructura/drenaje',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      size: 20,
      number: 0,
    });
    fixture.detectChanges();

    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.textContent).toContain('Limpiar drenajes');
    expect(elemento.textContent).toContain('mantenimiento.ordenes.estados.EN_EJECUCION');
    expect(elemento.querySelector('a[href="/mantenimiento/ordenes/orden-1"]')).not.toBeNull();
  });

  it('filtra por estado desde la primera página', () => {
    http.expectOne(`${BASE}?pagina=0&tamanio=20`).flush({ content: [], totalElements: 0 });

    page.cambiarEstado('EJECUTADA');

    http
      .expectOne(`${BASE}?pagina=0&tamanio=20&estado=EJECUTADA`)
      .flush({ content: [], totalElements: 0 });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'mantenimiento.ordenes.vacio',
    );
  });
});
