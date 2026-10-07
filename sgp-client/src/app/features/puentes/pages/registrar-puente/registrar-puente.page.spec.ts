import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { SelectorUbicacionComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.component';
import { SelectorUbicacionStubComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.testing';
import { PuenteFormComponent } from '../../components/puente-form/puente-form.component';
import { UbicacionTerritorialResponse } from '../../models/puente.models';
import { RegistrarPuentePage } from './registrar-puente.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('RegistrarPuentePage: HU009', () => {
  let fixture: ComponentFixture<RegistrarPuentePage>;
  let page: RegistrarPuentePage;
  let http: HttpTestingController;

  const departamento = {
    id: 'departamento-1',
    codigoIne: '01',
    nombre: 'Guatemala',
  };

  const municipio = {
    id: 'municipio-1',
    departamentoId: departamento.id,
    codigoIne: '0114',
    nombre: 'Amatitlán',
  };

  function territorio(latitud = 14.481, longitud = -90.615): UbicacionTerritorialResponse {
    return {
      latitud,
      longitud,
      zonaUtm: '15N',
      requiereSeleccion: false,
      candidatos: [{ departamento, municipio }],
    };
  }

  function formulario(): PuenteFormComponent {
    return fixture.debugElement.query(By.directive(PuenteFormComponent)).componentInstance;
  }

  function resolverPunto(respuesta: UbicacionTerritorialResponse = territorio()): void {
    formulario().seleccionarCoordenada({
      latitud: respuesta.latitud,
      longitud: respuesta.longitud,
    });

    vi.advanceTimersByTime(300);

    http.expectOne((req) => req.url === '/api/v1/catalogos/ubicacion').flush(respuesta);
  }

  function formularioValido(): void {
    formulario().form.patchValue({
      nombre: '  Puente HU9  ',
      ruta: '  CA-9  ',
      kilometraje: null,
    });

    resolverPunto();
  }

  function mostrarAdvertencia(): void {
    formulario().enviarFormulario();

    http.expectOne('/api/v1/puentes').flush(
      {
        type: 'about:blank',
        title: 'Hay puentes cercanos',
        status: 409,
        detail: 'Se requiere confirmar la cercanía.',
        code: 'puente_cercano',
        requiereConfirmacion: true,
        puentesCercanos: [
          {
            id: 'puente-existente',
            codigo: 'GT-01-0114-0001',
            nombre: 'Puente existente',
            activo: true,
            distanciaMetros: 11.1,
            latitud: 14.4811,
            longitud: -90.615,
          },
        ],
        totalPuentesCercanos: 1,
      },
      { status: 409, statusText: 'Conflict' },
    );

    fixture.detectChanges();
  }

  function puenteCompleto() {
    return {
      id: 'puente-nuevo',
      codigo: 'GT-01-0114-0001',
      nombre: 'Puente HU9',
      departamento,
      municipio,
      ruta: 'CA-9',
      kilometraje: null,
      latitud: 14.481,
      longitud: -90.615,
      utm: { zona: 15, hemisferio: 'N', epsg: 32615, este: 100.5, norte: 200.5 },
      activo: true,
      estadoActual: 'Sin evaluar',
      indiceCondicionActual: null,
      fechaUltimaInspeccion: null,
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
        {
          provide: TranslocoService,
          useValue: { translate: (key: string) => key },
        },
        {
          provide: ApiErrorService,
          useValue: { clear: vi.fn() },
        },
      ],
    });
    TestBed.overrideComponent(RegistrarPuentePage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    TestBed.overrideComponent(PuenteFormComponent, {
      remove: { imports: [TranslocoPipe, SelectorUbicacionComponent] },
      add: { imports: [TraduccionTestPipe, SelectorUbicacionStubComponent] },
    });

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RegistrarPuentePage);
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

  it('no envía el registro mientras el formulario no sea válido', () => {
    formulario().enviarFormulario();

    http.expectNone('/api/v1/puentes');
  });

  it('envía los datos mínimos y evita solicitudes duplicadas', () => {
    formularioValido();

    formulario().enviarFormulario();
    formulario().enviarFormulario();

    const requests = http.match('/api/v1/puentes');
    expect(requests).toHaveLength(1);

    expect(requests[0].request.body).toEqual({
      nombre: 'Puente HU9',
      departamentoId: departamento.id,
      municipioId: municipio.id,
      ruta: 'CA-9',
      kilometraje: null,
      latitud: 14.481,
      longitud: -90.615,
      confirmarCercania: false,
    });

    expect(page.guardando()).toBe(true);
    fixture.detectChanges();
    expect(formulario().form.disabled).toBe(true);

    requests[0].flush(puenteCompleto(), { status: 201, statusText: 'Created' });

    expect(page.puenteRegistrado()?.codigo).toBe('GT-01-0114-0001');
    expect(page.guardando()).toBe(false);
  });

  it('espera confirmación y expone los puntos cercanos para el mapa', () => {
    formularioValido();
    mostrarAdvertencia();

    expect(page.advertencia()?.requiereConfirmacion).toBe(true);
    expect(page.puntosCercanos()).toEqual([
      {
        id: 'puente-existente',
        titulo: 'GT-01-0114-0001 — Puente existente',
        latitud: 14.4811,
        longitud: -90.615,
      },
    ]);

    http.expectNone('/api/v1/puentes');

    page.confirmarRegistro();

    const request = http.expectOne('/api/v1/puentes');
    expect(request.request.body.confirmarCercania).toBe(true);

    request.flush(
      { ...puenteCompleto(), codigo: 'GT-01-0114-0002' },
      { status: 201, statusText: 'Created' },
    );

    expect(page.puenteRegistrado()?.codigo).toBe('GT-01-0114-0002');
    expect(page.advertencia()).toBeNull();
  });

  it('cancelar la confirmación conserva los datos', () => {
    formularioValido();
    const datos = formulario().form.getRawValue();
    mostrarAdvertencia();

    page.cancelarConfirmacion();
    page.confirmarRegistro();
    fixture.detectChanges();

    expect(page.advertencia()).toBeNull();
    expect(formulario().form.getRawValue()).toEqual(datos);
    http.expectNone('/api/v1/puentes');
  });

  it('editar los datos invalida una confirmación pendiente', () => {
    formularioValido();
    mostrarAdvertencia();

    formulario().form.controls.nombre.setValue('Otro nombre');
    page.confirmarRegistro();

    expect(page.advertencia()).toBeNull();
    http.expectNone('/api/v1/puentes');
  });

  it('muestra los errores de campo recibidos en una respuesta 422', () => {
    formularioValido();
    formulario().enviarFormulario();

    http.expectOne('/api/v1/puentes').flush(
      {
        detail: 'Revisa los campos indicados.',
        errores: [{ campo: 'ruta', mensaje: 'La ruta indicada no es válida.' }],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    fixture.detectChanges();

    expect(formulario().mensajeCampo('ruta')).toBe('La ruta indicada no es válida.');
    expect(page.errorRegistro()).toBe('Revisa los campos indicados.');
    expect(page.guardando()).toBe(false);
    expect(formulario().form.controls.ruta.enabled).toBe(true);
    expect(page.puenteRegistrado()).toBeNull();
  });

  it('invalida el territorio si el backend rechaza la congruencia municipal', () => {
    formularioValido();
    formulario().enviarFormulario();

    http.expectOne('/api/v1/puentes').flush(
      {
        code: 'ubicacion_municipio_incongruente',
        detail: 'Las coordenadas no corresponden al municipio seleccionado.',
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    fixture.detectChanges();

    expect(formulario().territorio()).toBeNull();
    expect(formulario().form.controls.departamentoId.value).toBe('');
    expect(formulario().form.controls.municipioId.value).toBe('');
    expect(formulario().ubicacionValidada()).toBe(false);

    formulario().enviarFormulario();
    http.expectNone('/api/v1/puentes');
  });

  it('quitar la ubicación cancela la confirmación pendiente y los puntos cercanos', () => {
    formularioValido();
    mostrarAdvertencia();

    formulario().quitarUbicacion();

    expect(page.advertencia()).toBeNull();
    expect(page.puntosCercanos()).toEqual([]);

    page.confirmarRegistro();
    http.expectNone('/api/v1/puentes');
  });

  it('registrar otro puente restablece formulario y ubicación', () => {
    formularioValido();
    formulario().enviarFormulario();

    http
      .expectOne('/api/v1/puentes')
      .flush(puenteCompleto(), { status: 201, statusText: 'Created' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-puente-form')).toBeNull();

    page.registrarOtro();
    fixture.detectChanges();
    // El formulario nuevo vuelve a cargar los puentes, incluido el recién registrado.
    cargarPuentesExistentes([]);

    expect(page.puenteRegistrado()).toBeNull();
    expect(formulario().territorio()).toBeNull();
    expect(formulario().form.getRawValue()).toEqual({
      nombre: '',
      departamentoId: '',
      municipioId: '',
      ruta: '',
      kilometraje: null,
      latitud: null,
      longitud: null,
    });
  });
});
