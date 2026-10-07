import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { RevisionSolicitudesPage } from './revision-solicitudes.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('RevisionSolicitudesPage: HU011', () => {
  let fixture: ComponentFixture<RevisionSolicitudesPage>;
  let page: RevisionSolicitudesPage;
  let http: HttpTestingController;

  const departamento = { id: 'd1', codigoIne: '01', nombre: 'Guatemala' };
  const municipio = { id: 'm1', departamentoId: 'd1', codigoIne: '0114', nombre: 'Amatitlán' };

  function fila(nombre: string, estado: string) {
    return {
      solicitud: {
        id: `id-${nombre}`,
        nombre,
        departamento,
        municipio,
        ruta: 'CA-9',
        kilometraje: null,
        latitud: 14.481,
        longitud: -90.615,
        justificacion: null,
        estado,
        motivoDecision: null,
        revisadoEn: null,
        puenteCreadoId: null,
        puenteCreadoCodigo: null,
        creadoEn: '2026-10-06T12:00:00Z',
      },
      solicitanteEmail: 'catedratico@sgp.local',
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

  function esperarConsulta() {
    return http.expectOne((req) => req.url === '/api/v1/solicitudes-puente');
  }

  function responder(cuerpo: object): void {
    esperarConsulta().flush(cuerpo);
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.overrideComponent(RevisionSolicitudesPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RevisionSolicitudesPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    fixture.destroy();
    TestBed.resetTestingModule();
  });

  it('abre con las solicitudes pendientes y muestra solicitante, estado y acceso a revisión', () => {
    const peticion = esperarConsulta();
    expect(peticion.request.params.get('estado')).toBe('PENDIENTE');
    expect(peticion.request.params.get('pagina')).toBe('0');
    expect(peticion.request.params.get('tamanio')).toBe('10');
    peticion.flush(pagina([fila('Puente A', 'PENDIENTE')]));
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Puente A');
    expect(texto).toContain('catedratico@sgp.local');
    expect(texto).toContain('Amatitlán, Guatemala');
    expect(texto).toContain('puentes.solicitud.estado.PENDIENTE');

    const enlace = fixture.nativeElement.querySelector('tbody a') as HTMLAnchorElement;
    expect(enlace.getAttribute('href')).toBe('/puentes/solicitudes/revision/id-Puente%20A');
  });

  it('cambia el filtro por estado y vuelve a consultar desde la primera página', () => {
    responder(pagina([fila('Puente A', 'PENDIENTE')]));

    page.filtrar('RECHAZADA');

    const peticion = esperarConsulta();
    expect(peticion.request.params.get('estado')).toBe('RECHAZADA');
    expect(peticion.request.params.get('pagina')).toBe('0');
    peticion.flush(pagina([fila('Puente B', 'RECHAZADA')]));
    fixture.detectChanges();

    expect(page.estado()).toBe('RECHAZADA');
    expect(fixture.nativeElement.textContent).toContain('Puente B');
    expect(fixture.nativeElement.textContent).not.toContain('Puente A');
  });

  it('no consulta de nuevo al elegir el estado ya activo', () => {
    responder(pagina([]));

    page.filtrar('PENDIENTE');

    http.expectNone('/api/v1/solicitudes-puente');
  });

  it('muestra un mensaje cuando no hay solicitudes', () => {
    responder(pagina([]));

    expect(fixture.nativeElement.textContent).toContain('puentes.revision.empty');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('muestra el error y permite reintentar', () => {
    esperarConsulta().flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(page.error()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('puentes.revision.loadFailed');

    page.cargar(0);
    responder(pagina([fila('Puente A', 'PENDIENTE')]));

    expect(page.error()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Puente A');
  });

  it('cambia de página manteniendo el filtro', () => {
    responder(pagina([fila('Puente A', 'PENDIENTE')], { totalElements: 25, totalPages: 3 }));

    page.cambiarPagina({ page: 2 });

    const peticion = esperarConsulta();
    expect(peticion.request.params.get('pagina')).toBe('2');
    expect(peticion.request.params.get('estado')).toBe('PENDIENTE');
    peticion.flush(pagina([], { number: 2, totalElements: 25, totalPages: 3 }));
  });
});
