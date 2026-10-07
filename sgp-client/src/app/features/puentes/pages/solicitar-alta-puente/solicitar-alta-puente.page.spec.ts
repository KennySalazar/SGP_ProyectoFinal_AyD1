import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { SelectorUbicacionComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.component';
import { SelectorUbicacionStubComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.testing';
import { AvisoCercaniaComponent } from '../../components/aviso-cercania/aviso-cercania.component';
import { PuenteFormComponent } from '../../components/puente-form/puente-form.component';
import { UbicacionTerritorialResponse } from '../../models/puente.models';
import { SolicitarAltaPuentePage } from './solicitar-alta-puente.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('SolicitarAltaPuentePage: HU010', () => {
  let fixture: ComponentFixture<SolicitarAltaPuentePage>;
  let page: SolicitarAltaPuentePage;
  let http: HttpTestingController;

  const departamento = { id: 'departamento-1', codigoIne: '01', nombre: 'Guatemala' };
  const municipio = {
    id: 'municipio-1',
    departamentoId: departamento.id,
    codigoIne: '0114',
    nombre: 'Amatitlán',
  };

  const territorio: UbicacionTerritorialResponse = {
    latitud: 14.481,
    longitud: -90.615,
    zonaUtm: '15N',
    requiereSeleccion: false,
    candidatos: [{ departamento, municipio }],
  };

  function formulario(): PuenteFormComponent {
    return fixture.debugElement.query(By.directive(PuenteFormComponent)).componentInstance;
  }

  function formularioValido(): void {
    formulario().form.patchValue({ nombre: '  Puente nuevo  ', ruta: 'CA-9' });
    formulario().seleccionarCoordenada({
      latitud: territorio.latitud,
      longitud: territorio.longitud,
    });
    vi.advanceTimersByTime(300);
    http.expectOne((req) => req.url === '/api/v1/catalogos/ubicacion').flush(territorio);
  }

  function solicitudCreada() {
    return {
      id: 'solicitud-1',
      nombre: 'Puente nuevo',
      departamento,
      municipio,
      ruta: 'CA-9',
      kilometraje: null,
      latitud: 14.481,
      longitud: -90.615,
      justificacion: null,
      estado: 'PENDIENTE',
      motivoDecision: null,
      revisadoEn: null,
      creadoEn: '2026-10-06T12:00:00Z',
    };
  }

  function cargarPuentesExistentes(puentes: object[] = []): void {
    http
      .expectOne((req) => req.url === '/api/v1/puentes')
      .flush({
        content: puentes,
        totalElements: puentes.length,
        totalPages: 1,
        number: 0,
        size: 100,
        first: true,
        last: true,
        empty: puentes.length === 0,
      });
  }

  beforeEach(() => {
    vi.useFakeTimers();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: TranslocoService, useValue: { translate: (key: string) => key } },
        { provide: ApiErrorService, useValue: { clear: vi.fn() } },
      ],
    });
    TestBed.overrideComponent(SolicitarAltaPuentePage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    TestBed.overrideComponent(AvisoCercaniaComponent, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    TestBed.overrideComponent(PuenteFormComponent, {
      remove: { imports: [TranslocoPipe, SelectorUbicacionComponent] },
      add: { imports: [TraduccionTestPipe, SelectorUbicacionStubComponent] },
    });

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SolicitarAltaPuentePage);
    page = fixture.componentInstance;
    fixture.detectChanges();
    cargarPuentesExistentes();
  });

  afterEach(() => {
    try {
      http.verify();
    } finally {
      fixture.destroy();
      TestBed.resetTestingModule();
      vi.clearAllTimers();
      vi.useRealTimers();
    }
  });

  it('no envía la solicitud mientras el formulario no sea válido', () => {
    formulario().enviarFormulario();

    http.expectNone('/api/v1/solicitudes-puente');
  });

  it('envía los datos del puente con la justificación recortada', () => {
    formularioValido();
    page.justificacion.setValue('  No aparece en el catálogo  ');

    formulario().enviarFormulario();

    const request = http.expectOne('/api/v1/solicitudes-puente');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      nombre: 'Puente nuevo',
      departamentoId: departamento.id,
      municipioId: municipio.id,
      ruta: 'CA-9',
      kilometraje: null,
      latitud: 14.481,
      longitud: -90.615,
      justificacion: 'No aparece en el catálogo',
      confirmarCercania: false,
    });
  });

  it('envía justificacion nula cuando está vacía y evita duplicados', () => {
    formularioValido();

    formulario().enviarFormulario();
    formulario().enviarFormulario();

    const requests = http.match('/api/v1/solicitudes-puente');
    expect(requests).toHaveLength(1);
    expect(requests[0].request.body.justificacion).toBeNull();
    expect(page.guardando()).toBe(true);
    expect(page.justificacion.disabled).toBe(true);

    requests[0].flush(solicitudCreada(), { status: 201, statusText: 'Created' });

    expect(page.guardando()).toBe(false);
    expect(page.justificacion.enabled).toBe(true);
    expect(page.solicitudCreada()?.estado).toBe('PENDIENTE');
  });

  it('no envía si la justificación supera el máximo', () => {
    formularioValido();
    page.justificacion.setValue('x'.repeat(2001));

    formulario().enviarFormulario();

    http.expectNone('/api/v1/solicitudes-puente');
    expect(page.justificacion.touched).toBe(true);
    expect(page.mensajeJustificacion()).toBe('puentes.solicitud.justificationInvalid');
  });

  it('muestra la confirmación y permite solicitar otro puente', () => {
    formularioValido();
    formulario().enviarFormulario();
    http
      .expectOne('/api/v1/solicitudes-puente')
      .flush(solicitudCreada(), { status: 201, statusText: 'Created' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-puente-form')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('puentes.solicitud.success');

    page.solicitarOtra();
    fixture.detectChanges();
    // El formulario nuevo vuelve a cargar los puentes existentes.
    cargarPuentesExistentes([]);

    expect(page.solicitudCreada()).toBeNull();
    expect(formulario().form.getRawValue().nombre).toBe('');
    expect(page.justificacion.value).toBe('');
  });

  it('muestra los errores de campo de una respuesta 422', () => {
    formularioValido();
    formulario().enviarFormulario();

    http.expectOne('/api/v1/solicitudes-puente').flush(
      {
        detail: 'Revisa los campos indicados.',
        errores: [
          { campo: 'ruta', mensaje: 'La ruta indicada no es válida.' },
          { campo: 'justificacion', mensaje: 'La justificación no es válida.' },
        ],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    fixture.detectChanges();

    expect(formulario().mensajeCampo('ruta')).toBe('La ruta indicada no es válida.');
    expect(page.mensajeJustificacion()).toBe('La justificación no es válida.');
    expect(page.errorSolicitud()).toBe('Revisa los campos indicados.');
    expect(page.guardando()).toBe(false);
    expect(page.solicitudCreada()).toBeNull();
  });

  it('invalida el territorio si el backend rechaza la ubicación', () => {
    formularioValido();
    formulario().enviarFormulario();

    http.expectOne('/api/v1/solicitudes-puente').flush(
      {
        code: 'ubicacion_municipio_incongruente',
        detail: 'Las coordenadas no corresponden al municipio seleccionado.',
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    fixture.detectChanges();

    expect(formulario().territorio()).toBeNull();
    expect(formulario().ubicacionValidada()).toBe(false);
    expect(page.errorSolicitud()).toBe(
      'Las coordenadas no corresponden al municipio seleccionado.',
    );
  });

  it('usa un mensaje genérico cuando el error no trae detalle', () => {
    formularioValido();
    formulario().enviarFormulario();

    http
      .expectOne('/api/v1/solicitudes-puente')
      .flush(null, { status: 500, statusText: 'Server Error' });

    expect(page.errorSolicitud()).toBe('puentes.solicitud.saveFailed');
  });

  it('editar el formulario limpia los errores previos', () => {
    formularioValido();
    formulario().enviarFormulario();
    http
      .expectOne('/api/v1/solicitudes-puente')
      .flush({ detail: 'Error' }, { status: 500, statusText: 'Server Error' });

    formulario().form.controls.nombre.setValue('Otro nombre');

    expect(page.errorSolicitud()).toBeNull();
    expect(page.erroresCampos()).toEqual({});
  });

  it('ofrece volver a mis solicitudes y cancelar sin enviar nada', () => {
    const enlaces = [...fixture.nativeElement.querySelectorAll('a')] as HTMLAnchorElement[];

    expect(enlaces.filter((a) => a.getAttribute('href') === '/puentes/solicitudes')).toHaveLength(
      2,
    );
    expect(fixture.nativeElement.textContent).toContain('puentes.solicitud.cancel');
    expect(fixture.nativeElement.textContent).toContain('puentes.solicitud.backToMine');
    http.expectNone('/api/v1/solicitudes-puente');
  });

  it('carga los puentes existentes en el mapa al abrir el formulario', () => {
    expect(formulario().mostrarExistentes()).toBe(true);
  });

  describe('advertencia de cercanía (RN-INV-06)', () => {
    const cercano = {
      id: 'puente-1',
      codigo: 'GT-01-0114-0001',
      nombre: 'Puente existente',
      activo: true,
      distanciaMetros: 11.1,
      latitud: 14.4811,
      longitud: -90.615,
    };

    const inactivo = {
      ...cercano,
      id: 'puente-2',
      codigo: 'GT-01-0114-0002',
      nombre: 'Puente dado de baja',
      activo: false,
    };

    function responderConAdvertencia(cercanos: object[] = [cercano]): void {
      http.expectOne('/api/v1/solicitudes-puente').flush(
        {
          code: 'puente_cercano',
          detail: 'Existen puentes a menos de 100 metros.',
          requiereConfirmacion: true,
          puentesCercanos: cercanos,
          totalPuentesCercanos: cercanos.length,
        },
        { status: 409, statusText: 'Conflict' },
      );
      fixture.detectChanges();
    }

    it('muestra el aviso con los puentes cercanos, los pasa al mapa y no crea la solicitud', () => {
      formularioValido();
      formulario().enviarFormulario();
      responderConAdvertencia([cercano, inactivo]);

      expect(page.advertencia()?.requiereConfirmacion).toBe(true);
      expect(page.solicitudCreada()).toBeNull();
      expect(page.errorSolicitud()).toBeNull();
      expect(page.puntosCercanos().map((p) => [p.id, p.inactivo])).toEqual([
        ['puente-1', false],
        ['puente-2', true],
      ]);
      expect(fixture.nativeElement.querySelector('app-aviso-cercania')).not.toBeNull();
      expect(fixture.nativeElement.textContent).toContain('GT-01-0114-0002');
      expect(fixture.nativeElement.textContent).toContain('puentes.solicitud.proximityNote');
      // El botón de envío del formulario se oculta mientras se decide.
      expect(fixture.nativeElement.querySelector('app-puente-form button[type=submit]')).toBeNull();
      http.expectNone('/api/v1/solicitudes-puente');
    });

    it('confirmar reenvía la misma solicitud con confirmarCercania en true', () => {
      formularioValido();
      page.justificacion.setValue('Falta en el catálogo');
      formulario().enviarFormulario();
      responderConAdvertencia();

      page.confirmarSolicitud();

      const peticion = http.expectOne('/api/v1/solicitudes-puente');
      expect(peticion.request.body).toEqual({
        nombre: 'Puente nuevo',
        departamentoId: departamento.id,
        municipioId: municipio.id,
        ruta: 'CA-9',
        kilometraje: null,
        latitud: 14.481,
        longitud: -90.615,
        justificacion: 'Falta en el catálogo',
        confirmarCercania: true,
      });
      expect(page.advertencia()).toBeNull();

      peticion.flush(solicitudCreada(), { status: 201, statusText: 'Created' });
      expect(page.solicitudCreada()?.estado).toBe('PENDIENTE');
    });

    it('al confirmar usa la justificación vigente, aunque se editó con el aviso abierto', () => {
      formularioValido();
      page.justificacion.setValue('Primera versión');
      formulario().enviarFormulario();
      responderConAdvertencia();

      page.justificacion.setValue('Versión corregida');
      page.confirmarSolicitud();

      const peticion = http.expectOne('/api/v1/solicitudes-puente');
      expect(peticion.request.body.justificacion).toBe('Versión corregida');
      peticion.flush(solicitudCreada(), { status: 201, statusText: 'Created' });
    });

    it('volver al formulario conserva los datos y no envía nada', () => {
      formularioValido();
      const datos = formulario().form.getRawValue();
      formulario().enviarFormulario();
      responderConAdvertencia();

      page.cancelarConfirmacion();
      page.confirmarSolicitud();
      fixture.detectChanges();

      expect(page.advertencia()).toBeNull();
      expect(formulario().form.getRawValue()).toEqual(datos);
      expect(fixture.nativeElement.querySelector('app-aviso-cercania')).toBeNull();
      http.expectNone('/api/v1/solicitudes-puente');
    });

    it('editar el formulario invalida una confirmación pendiente', () => {
      formularioValido();
      formulario().enviarFormulario();
      responderConAdvertencia();

      formulario().form.controls.nombre.setValue('Otro nombre');
      page.confirmarSolicitud();

      expect(page.advertencia()).toBeNull();
      http.expectNone('/api/v1/solicitudes-puente');
    });

    it('con solo inactivos explica que el Administrador puede reactivarlo', () => {
      formularioValido();
      formulario().enviarFormulario();
      responderConAdvertencia([inactivo]);

      expect(fixture.nativeElement.textContent).toContain(
        'puentes.solicitud.proximityOnlyInactiveNote',
      );
    });

    it('un 409 que no es de cercanía se muestra como error normal', () => {
      formularioValido();
      formulario().enviarFormulario();

      http
        .expectOne('/api/v1/solicitudes-puente')
        .flush(
          { code: 'otro', detail: 'Conflicto distinto' },
          { status: 409, statusText: 'Conflict' },
        );

      expect(page.advertencia()).toBeNull();
      expect(page.errorSolicitud()).toBe('Conflicto distinto');
    });
  });
});
