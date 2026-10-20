import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { EjecucionOrden, OrdenMantenimiento } from '../../models/orden-mantenimiento.models';
import { DetalleOrdenPage } from './detalle-orden.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

const BASE = '/api/v1/ordenes-mantenimiento/orden-1';

function orden(overrides: Partial<OrdenMantenimiento> = {}): OrdenMantenimiento {
  return {
    id: 'orden-1',
    puenteId: 'puente-1',
    inspeccionOrigenId: null,
    reglaOrigenId: null,
    resultadoIcOrigenId: null,
    elementoRef: null,
    instanciaElementoId: null,
    tipo: 'CORRECTIVO',
    estado: 'EN_EJECUCION',
    descripcion: 'Reparar barandal',
    prioridadSugerida: 'ALTA',
    prioridadConfirmada: 'ALTA',
    justificacionPrioridad: null,
    fundamentoGeneracion: null,
    creadoPorId: null,
    aceptadoPorId: null,
    aceptadoEn: null,
    fechaProgramada: '2026-10-20',
    motivoCierreSinEjecucion: null,
    creadoEn: '2026-10-18T10:00:00Z',
    actualizadoEn: '2026-10-20T10:00:00Z',
    version: 2,
    reglaProvisional: null,
    ...overrides,
  };
}

const EJECUCION: EjecucionOrden = {
  ordenId: 'orden-1',
  iniciadoEn: '2026-10-20T15:00:00Z',
  fechaEjecucion: null,
  responsableUsuarioId: 'usuario-1',
  responsableUsuarioEmail: 'catedratico@cunoc.edu.gt',
  responsableNombre: null,
  actualizadoEn: '2026-10-20T15:00:00Z',
};

describe('DetalleOrdenPage', () => {
  let fixture: ComponentFixture<DetalleOrdenPage>;
  let page: DetalleOrdenPage;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DetalleOrdenPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 'orden-1' }) } },
        },
        { provide: TranslocoService, useValue: { translate: (clave: string) => clave } },
      ],
    })
      .overrideComponent(DetalleOrdenPage, {
        remove: { imports: [TranslocoPipe] },
        add: { imports: [TraduccionTestPipe] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(DetalleOrdenPage);
    page = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function cargar(
    datos: OrdenMantenimiento,
    ejecucion: EjecucionOrden | null = EJECUCION,
    conPuente = true,
  ): void {
    http.expectOne(BASE).flush(datos);
    http.expectOne(`${BASE}/historial`).flush([]);
    if (conPuente) {
      http.expectOne('/api/v1/puentes/puente-1').flush({
        id: 'puente-1',
        codigo: 'GT-01',
        nombre: 'Puente El Trébol',
      });
    }
    if (datos.estado === 'EN_EJECUCION' || datos.estado === 'EJECUTADA') {
      http.expectOne(`${BASE}/ejecucion`).flush(ejecucion);
      http.expectOne(`${BASE}/evidencias`).flush([]);
      if (datos.estado === 'EN_EJECUCION') {
        http
          .expectOne('/api/v1/ordenes-mantenimiento/responsables')
          .flush([{ id: 'usuario-1', email: 'catedratico@cunoc.edu.gt', rol: 'CATEDRATICO' }]);
      }
    }
    fixture.detectChanges();
  }

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('ofrece iniciar la ejecución de una orden programada', () => {
    cargar(orden({ estado: 'PROGRAMADA', version: 1 }));

    expect(texto()).toContain('mantenimiento.ordenes.acciones.iniciar');
    page.abrirAccion('iniciar');
    page.confirmarAccion();

    const request = http.expectOne(`${BASE}/iniciar`);
    expect(request.request.body).toEqual({ version: 1 });
    request.flush(orden());
    expect(page.accion()).toBeNull();
    cargar(orden(), EJECUCION, false);
  });

  it('carga el responsable registrado y guarda una persona externa con la fecha', () => {
    cargar(orden());

    expect(page.responsableUsuarioId()).toBe('usuario-1');
    expect(page.modoResponsable()).toBe('usuario');

    page.cambiarModoResponsable('externo');
    page.responsableNombre.set('  Constructora Los Altos ');
    page.fechaEjecucion.set(new Date(2026, 9, 20));
    page.guardarEjecucion();

    const request = http.expectOne(`${BASE}/ejecucion`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      fechaEjecucion: '2026-10-20',
      responsableUsuarioId: null,
      responsableNombre: 'Constructora Los Altos',
    });
    request.flush({ ...EJECUCION, responsableNombre: 'Constructora Los Altos' });
    expect(page.ejecucionGuardada()).toBe(true);
  });

  it('no permite cerrar mientras falten requisitos y lo indica en el diálogo', () => {
    cargar(orden());

    expect(page.puedeCerrar()).toBe(false);
    page.abrirAccion('cerrar');
    fixture.detectChanges();

    const pendientes = (fixture.nativeElement as HTMLElement).ownerDocument.querySelectorAll(
      '.requisitos li:not(.cumplido)',
    );
    expect(pendientes.length).toBe(2);
  });

  it('muestra dentro del diálogo lo que falta cuando el servidor rechaza el cierre', () => {
    cargar(orden());
    page.abrirAccion('cerrar');
    page.confirmarAccion();

    http.expectOne(`${BASE}/cerrar`).flush(
      {
        code: 'cierre_incompleto',
        detail: 'No se puede cerrar la orden como ejecutada.',
        errores: [
          { campo: 'fechaEjecucion', mensaje: 'Falta la fecha de ejecución.' },
          { campo: 'evidencias', mensaje: 'Falta al menos una fotografía de evidencia.' },
        ],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    expect(page.accion()).toBe('cerrar');
    expect(page.errorAccion()?.campos.map((c) => c.campo)).toEqual([
      'fechaEjecucion',
      'evidencias',
    ]);
    expect(page.procesando()).toBe(false);
  });

  it('exige motivo para retirar una evidencia', () => {
    cargar(orden());
    const evidencia = {
      id: 'evidencia-1',
      ordenId: 'orden-1',
      elementoRef: null,
      mimeReal: 'image/jpeg',
      tamanoBytes: 1000,
      anchoPx: 10,
      altoPx: 10,
      subidoPorId: 'usuario-1',
      subidoEn: '2026-10-20T16:00:00Z',
    };

    page.abrirRetiro(evidencia);
    page.motivoRetiro.set('  ');
    page.retirarEvidencia();
    http.expectNone(`${BASE}/evidencias/evidencia-1/retirar`);

    page.motivoRetiro.set('Foto de otro puente');
    page.retirarEvidencia();
    const request = http.expectOne(`${BASE}/evidencias/evidencia-1/retirar`);
    expect(request.request.body).toEqual({ motivo: 'Foto de otro puente' });
    request.flush(null);
    http.expectOne(`${BASE}/evidencias`).flush([]);
    expect(page.evidenciaRetirar()).toBeNull();
  });

  it('una orden ejecutada solo muestra su registro, sin acciones', () => {
    cargar(orden({ estado: 'EJECUTADA' }), {
      ...EJECUCION,
      fechaEjecucion: '2026-10-20',
      responsableNombre: 'Constructora Los Altos',
    });

    expect(page.enEjecucion()).toBe(false);
    expect(texto()).not.toContain('mantenimiento.ordenes.acciones.cerrar');
    expect(texto()).not.toContain('mantenimiento.ordenes.ejecucion.guardar');
    expect(texto()).toContain('catedratico@cunoc.edu.gt · Constructora Los Altos');
  });

  it('muestra el origen de una propuesta generada: inspección, fundamento y rótulo provisional', () => {
    cargar(
      orden({
        estado: 'PROPUESTA',
        inspeccionOrigenId: 'inspeccion-1',
        reglaProvisional: true,
        fundamentoGeneracion: { reglaCodigo: 'R-01', umbral: 3, valorEncontrado: 4 },
      }),
    );

    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.textContent).toContain('mantenimiento.ordenes.provisional');
    expect(elemento.querySelector('a[href="/inspecciones/inspeccion-1/publicada"]')).not.toBeNull();
    expect(page.fundamento()).toEqual([
      { clave: 'reglaCodigo', valor: 'R-01' },
      { clave: 'umbral', valor: '3' },
      { clave: 'valorEncontrado', valor: '4' },
    ]);
    expect(elemento.textContent).toContain('mantenimiento.ordenes.fundamentoCampos.reglaCodigo');
  });

  it('programa con la fecha elegida y muestra el error del servidor junto al campo', () => {
    cargar(orden({ estado: 'PROPUESTA', version: 0 }));

    page.abrirAccion('programar');
    page.fechaProgramada.set(new Date(2026, 9, 25));
    page.confirmarAccion();

    const request = http.expectOne(`${BASE}/programar`);
    expect(request.request.body).toEqual({ fechaProgramada: '2026-10-25', version: 0 });
    request.flush(
      {
        detail: 'La fecha programada no puede ser anterior a hoy.',
        errores: [
          { campo: 'fechaProgramada', mensaje: 'La fecha programada no puede ser anterior a hoy.' },
        ],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    fixture.detectChanges();

    expect(page.errorCampo('fechaProgramada')).toBe(
      'La fecha programada no puede ser anterior a hoy.',
    );
    expect(page.erroresGenerales()).toEqual([]);
    const error = document.querySelector('.error-campo');
    expect(error?.textContent).toContain('La fecha programada no puede ser anterior a hoy.');
  });

  it('ofrece reintentar cuando falla la carga del detalle', () => {
    http.expectOne(`${BASE}/historial`).flush([]);
    http.expectOne(BASE).flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(page.errorCarga()).toBe(true);
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('.estado-error button')
      ?.click();
    cargar(orden({ estado: 'PROPUESTA' }));
    expect(page.errorCarga()).toBe(false);
  });
});
