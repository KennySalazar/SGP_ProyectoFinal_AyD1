import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { SelectorUbicacionComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.component';
import { SelectorUbicacionStubComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.testing';
import { SolicitudFichaComponent } from '../../components/solicitud-ficha/solicitud-ficha.component';
import { DetalleSolicitudPage } from './detalle-solicitud.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('DetalleSolicitudPage: detalle para el Catedrático', () => {
  let fixture: ComponentFixture<DetalleSolicitudPage>;
  let page: DetalleSolicitudPage;
  let http: HttpTestingController;

  const URL = '/api/v1/solicitudes-puente/mias/s1';

  function solicitud(overrides: object = {}) {
    return {
      id: 's1',
      nombre: 'Puente propuesto',
      departamento: { id: 'd1', codigoIne: '01', nombre: 'Guatemala' },
      municipio: { id: 'm1', departamentoId: 'd1', codigoIne: '0101', nombre: 'Guatemala' },
      ruta: 'CA-9',
      kilometraje: null,
      latitud: 14.6,
      longitud: -90.5,
      justificacion: null,
      estado: 'PENDIENTE',
      motivoDecision: null,
      revisadoEn: null,
      puenteCreadoId: null,
      puenteCreadoCodigo: null,
      creadoEn: '2026-10-06T12:00:00Z',
      ...overrides,
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: TranslocoService, useValue: { translate: (key: string) => key } },
      ],
    });
    TestBed.overrideComponent(DetalleSolicitudPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    TestBed.overrideComponent(SolicitudFichaComponent, {
      remove: { imports: [TranslocoPipe, SelectorUbicacionComponent] },
      add: { imports: [TraduccionTestPipe, SelectorUbicacionStubComponent] },
    });

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DetalleSolicitudPage);
    page = fixture.componentInstance;
    fixture.componentRef.setInput('id', 's1');
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    fixture.destroy();
    TestBed.resetTestingModule();
  });

  it('carga la solicitud propia y muestra la ficha sin solicitante ni revisor', () => {
    http.expectOne(URL).flush(solicitud());
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Puente propuesto');
    expect(texto).not.toContain('puentes.revision.requester');
    expect(texto).not.toContain('puentes.revision.reviewedBy');
    expect(fixture.nativeElement.querySelector('a[href="/puentes/solicitudes"]')).not.toBeNull();
  });

  it('muestra el motivo cuando la solicitud fue rechazada', () => {
    http.expectOne(URL).flush(
      solicitud({
        estado: 'RECHAZADA',
        motivoDecision: 'Ya existe en el catálogo',
        revisadoEn: '2026-10-07T15:30:00Z',
      }),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ya existe en el catálogo');
    expect(fixture.nativeElement.textContent).toContain('puentes.revision.rejectionReason');
  });

  it('muestra el código del puente cuando fue aprobada', () => {
    http
      .expectOne(URL)
      .flush(solicitud({ estado: 'APROBADA', puenteCreadoCodigo: 'GT-01-0101-0001' }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('GT-01-0101-0001');
  });

  it('informa cuando la solicitud no existe o no es suya', () => {
    http.expectOne(URL).flush(null, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(page.error()).toBe('puentes.solicitud.notFound');
    expect(fixture.nativeElement.textContent).toContain('puentes.solicitud.notFound');
  });

  it('permite reintentar cuando falla la carga', () => {
    http.expectOne(URL).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(page.error()).toBe('puentes.solicitud.detailFailed');

    page.cargar();
    http.expectOne(URL).flush(solicitud());
    fixture.detectChanges();

    expect(page.error()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Puente propuesto');
  });
});
