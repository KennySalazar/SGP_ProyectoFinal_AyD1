import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { MisSolicitudesPage } from './mis-solicitudes.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('MisSolicitudesPage: HU010', () => {
  let fixture: ComponentFixture<MisSolicitudesPage>;
  let page: MisSolicitudesPage;
  let http: HttpTestingController;

  const departamento = { id: 'd1', codigoIne: '01', nombre: 'Guatemala' };
  const municipio = { id: 'm1', departamentoId: 'd1', codigoIne: '0114', nombre: 'Amatitlán' };

  function solicitud(nombre: string, estado: string, motivoDecision: string | null = null) {
    return {
      id: nombre,
      nombre,
      departamento,
      municipio,
      ruta: 'CA-9',
      kilometraje: null,
      latitud: 14.481,
      longitud: -90.615,
      justificacion: null,
      estado,
      motivoDecision,
      revisadoEn: null,
      creadoEn: '2026-10-06T12:00:00Z',
    };
  }

  function pagina(content: unknown[], overrides: object = {}) {
    return {
      content,
      totalElements: content.length,
      totalPages: 1,
      number: 0,
      size: 10,
      first: true,
      last: true,
      empty: content.length === 0,
      ...overrides,
    };
  }

  function responder(cuerpo: object): void {
    http.expectOne((req) => req.url === '/api/v1/solicitudes-puente/mias').flush(cuerpo);
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.overrideComponent(MisSolicitudesPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MisSolicitudesPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    fixture.destroy();
    TestBed.resetTestingModule();
  });

  it('consulta la primera página al abrir y muestra el estado de cada solicitud', () => {
    const peticion = http.expectOne((req) => req.url === '/api/v1/solicitudes-puente/mias');
    expect(peticion.request.params.get('pagina')).toBe('0');
    expect(peticion.request.params.get('tamanio')).toBe('10');
    peticion.flush(
      pagina([
        solicitud('Puente A', 'PENDIENTE'),
        solicitud('Puente B', 'RECHAZADA', 'Ya existe en el catálogo'),
        solicitud('Puente C', 'APROBADA'),
      ]),
    );
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Puente A');
    expect(texto).toContain('puentes.solicitud.estado.PENDIENTE');
    expect(texto).toContain('puentes.solicitud.estado.RECHAZADA');
    expect(texto).toContain('puentes.solicitud.estado.APROBADA');
    expect(texto).toContain('Ya existe en el catálogo');
    expect(texto).toContain('Amatitlán, Guatemala');
    expect(page.cargando()).toBe(false);
  });

  it('muestra un mensaje cuando no hay solicitudes', () => {
    responder(pagina([]));

    expect(fixture.nativeElement.textContent).toContain('puentes.solicitud.empty');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('muestra el error y permite reintentar', () => {
    http
      .expectOne((req) => req.url === '/api/v1/solicitudes-puente/mias')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(page.error()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('puentes.solicitud.loadFailed');

    page.cargar(0);
    responder(pagina([solicitud('Puente A', 'PENDIENTE')]));

    expect(page.error()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Puente A');
  });

  it('cambia de página consultando la página indicada', () => {
    responder(pagina([solicitud('Puente A', 'PENDIENTE')], { totalElements: 25, totalPages: 3 }));

    page.cambiarPagina({ page: 2 });

    const peticion = http.expectOne((req) => req.url === '/api/v1/solicitudes-puente/mias');
    expect(peticion.request.params.get('pagina')).toBe('2');
    peticion.flush(pagina([], { number: 2, totalElements: 25, totalPages: 3 }));
  });
});
