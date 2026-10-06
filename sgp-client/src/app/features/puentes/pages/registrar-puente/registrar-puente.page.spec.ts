import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { UbicacionTerritorialResponse } from '../../models/puente.models';
import { RegistrarPuentePage } from './registrar-puente.page';

describe('RegistrarPuentePage: HU009', () => {
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

  function territorio(
    latitud = 14.481,
    longitud = -90.615,
  ): UbicacionTerritorialResponse {
    return {
      latitud,
      longitud,
      zonaUtm: '15N',
      requiereSeleccion: false,
      candidatos: [{ departamento, municipio }],
    };
  }

  function resolverPunto(
    respuesta: UbicacionTerritorialResponse = territorio(),
  ): void {
    page.seleccionarCoordenada({
      latitud: respuesta.latitud,
      longitud: respuesta.longitud,
    });

    vi.advanceTimersByTime(300);

    const request = http.expectOne(
      (req) => req.url === '/api/v1/catalogos/ubicacion',
    );

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('latitud')).toBe(
      String(respuesta.latitud),
    );
    expect(request.request.params.get('longitud')).toBe(
      String(respuesta.longitud),
    );

    request.flush(respuesta);
  }

  function formularioValido(): void {
    page.form.patchValue({
      nombre: '  Puente HU9  ',
      ruta: '  CA-9  ',
      kilometraje: null,
    });

    resolverPunto();
  }

  function mostrarAdvertencia(): void {
    page.registrar();

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

    http = TestBed.inject(HttpTestingController);
    page = TestBed.runInInjectionContext(() => new RegistrarPuentePage());
  });

  afterEach(() => {
    try {
      http.verify();
    } finally {
      TestBed.resetTestingModule();
      vi.clearAllTimers();
      vi.useRealTimers();
    }
  });

  it('no consulta catálogos antes de seleccionar una ubicación', () => {
    expect(page.territorio()).toBeNull();
    expect(page.form.controls.departamentoId.disabled).toBe(true);
    expect(page.form.controls.municipioId.disabled).toBe(true);

    http.expectNone(() => true);
  });

  it('asigna departamento, municipio y zona UTM del punto', () => {
    resolverPunto();

    expect(page.form.getRawValue().departamentoId).toBe(departamento.id);
    expect(page.form.getRawValue().municipioId).toBe(municipio.id);
    expect(page.departamentos()).toEqual([departamento]);
    expect(page.municipios()).toEqual([municipio]);
    expect(page.zonaUtm()).toBe('15N');
    expect(page.ubicacionValidada()).toBe(true);
    expect(page.resolviendoUbicacion()).toBe(false);
    expect(page.form.controls.departamentoId.disabled).toBe(true);
    expect(page.form.controls.municipioId.disabled).toBe(true);
  });

  it('permite elegir entre municipios candidatos del mismo departamento', () => {
    const otroMunicipio = {
      ...municipio,
      id: 'municipio-2',
      codigoIne: '0101',
      nombre: 'Guatemala',
    };

    resolverPunto({
      ...territorio(),
      requiereSeleccion: true,
      candidatos: [
        { departamento, municipio },
        { departamento, municipio: otroMunicipio },
      ],
    });

    expect(page.form.controls.departamentoId.value).toBe(departamento.id);
    expect(page.form.controls.municipioId.value).toBe('');
    expect(page.form.controls.municipioId.enabled).toBe(true);
    expect(page.ubicacionValidada()).toBe(false);

    page.form.controls.municipioId.setValue(otroMunicipio.id);

    expect(page.ubicacionValidada()).toBe(true);
  });

  it('filtra municipios al elegir entre departamentos candidatos', () => {
    const otroDepartamento = {
      id: 'departamento-2',
      codigoIne: '09',
      nombre: 'Quetzaltenango',
    };
    const otroMunicipio = {
      id: 'municipio-2',
      departamentoId: otroDepartamento.id,
      codigoIne: '0901',
      nombre: 'Quetzaltenango',
    };

    resolverPunto({
      ...territorio(),
      requiereSeleccion: true,
      candidatos: [
        { departamento, municipio },
        { departamento: otroDepartamento, municipio: otroMunicipio },
      ],
    });

    expect(page.form.controls.departamentoId.enabled).toBe(true);
    expect(page.ubicacionValidada()).toBe(false);

    page.form.controls.departamentoId.setValue(departamento.id);
    expect(page.form.controls.municipioId.value).toBe(municipio.id);

    page.form.controls.departamentoId.setValue(otroDepartamento.id);

    expect(page.municipios()).toEqual([otroMunicipio]);
    expect(page.form.controls.municipioId.value).toBe(otroMunicipio.id);
    expect(page.ubicacionValidada()).toBe(true);
  });

  it('rechaza un municipio ajeno a los candidatos territoriales', () => {
    formularioValido();
    page.form.controls.municipioId.setValue('municipio-no-candidato');

    page.registrar();

    expect(page.ubicacionValidada()).toBe(false);
    http.expectNone('/api/v1/puentes');
  });

  it('espera la consulta territorial antes de permitir guardar', () => {
    page.form.patchValue({ nombre: 'Puente', ruta: 'CA-9' });
    page.seleccionarCoordenada({ latitud: 14.481, longitud: -90.615 });

    expect(page.resolviendoUbicacion()).toBe(true);

    page.registrar();
    http.expectNone('/api/v1/puentes');

    vi.advanceTimersByTime(299);
    http.expectNone((req) => req.url === '/api/v1/catalogos/ubicacion');

    vi.advanceTimersByTime(1);
    http
      .expectOne((req) => req.url === '/api/v1/catalogos/ubicacion')
      .flush(territorio());

    expect(page.ubicacionValidada()).toBe(true);
  });

  it('consulta únicamente el último punto durante la espera', () => {
    page.seleccionarCoordenada({ latitud: 14.481, longitud: -90.615 });
    vi.advanceTimersByTime(150);

    page.seleccionarCoordenada({ latitud: 14.482, longitud: -90.616 });
    vi.advanceTimersByTime(300);

    const request = http.expectOne(
      (req) => req.url === '/api/v1/catalogos/ubicacion',
    );

    expect(request.request.params.get('latitud')).toBe('14.482');
    expect(request.request.params.get('longitud')).toBe('-90.616');

    request.flush(territorio(14.482, -90.616));

    expect(page.territorio()?.latitud).toBe(14.482);
    expect(page.ubicacionValidada()).toBe(true);
  });

  it('cancela una petición anterior al seleccionar otro punto', () => {
    page.seleccionarCoordenada({ latitud: 14.481, longitud: -90.615 });
    vi.advanceTimersByTime(300);

    const anterior = http.expectOne(
      (req) => req.url === '/api/v1/catalogos/ubicacion',
    );

    page.seleccionarCoordenada({ latitud: 14.482, longitud: -90.616 });

    expect(anterior.cancelled).toBe(true);

    vi.advanceTimersByTime(300);

    http
      .expectOne((req) => req.url === '/api/v1/catalogos/ubicacion')
      .flush(territorio(14.482, -90.616));

    expect(page.territorio()?.latitud).toBe(14.482);
  });

  it('resuelve también coordenadas ingresadas manualmente', () => {
    page.form.patchValue({ latitud: 14.481, longitud: -90.615 });
    vi.advanceTimersByTime(300);

    http
      .expectOne((req) => req.url === '/api/v1/catalogos/ubicacion')
      .flush(territorio());

    expect(page.ubicacionValidada()).toBe(true);
  });

  it.each(['', '   '])('no registra cuando ruta contiene "%s"', (ruta) => {
    formularioValido();
    page.form.controls.ruta.setValue(ruta);

    page.registrar();

    expect(page.form.controls.ruta.hasError('required')).toBe(true);
    expect(page.mensajeCampo('ruta')).toBe('puentes.required');
    http.expectNone('/api/v1/puentes');
  });

  it.each([
    { latitud: 91, longitud: -90.615, campo: 'latitud' as const },
    { latitud: 14.481, longitud: -181, campo: 'longitud' as const },
  ])('rechaza coordenadas fuera de rango: $campo', (datos) => {
    formularioValido();
    page.form.patchValue({
      latitud: datos.latitud,
      longitud: datos.longitud,
    });

    vi.advanceTimersByTime(300);
    page.registrar();

    expect(page.form.controls[datos.campo].invalid).toBe(true);
    expect(page.ubicacionValidada()).toBe(false);
    http.expectNone((req) => req.url === '/api/v1/catalogos/ubicacion');
    http.expectNone('/api/v1/puentes');
  });

  it('bloquea el registro de un punto fuera de Guatemala', () => {
    page.form.patchValue({ nombre: 'Puente', ruta: 'CA-9' });
    page.seleccionarCoordenada({ latitud: 15, longitud: -87 });
    vi.advanceTimersByTime(300);

    http
      .expectOne((req) => req.url === '/api/v1/catalogos/ubicacion')
      .flush(
        {
          code: 'ubicacion_fuera_de_guatemala',
          detail: 'Las coordenadas deben estar dentro de Guatemala.',
        },
        { status: 422, statusText: 'Unprocessable Entity' },
      );

    page.registrar();

    expect(page.errorUbicacion()).toBe(
      'Las coordenadas deben estar dentro de Guatemala.',
    );
    expect(page.resolviendoUbicacion()).toBe(false);
    expect(page.ubicacionValidada()).toBe(false);
    http.expectNone('/api/v1/puentes');
  });

  it('bloquea puntos sin candidatos municipales', () => {
    resolverPunto({ ...territorio(), candidatos: [] });

    expect(page.errorUbicacion()).toBe('puentes.locationWithoutMunicipality');
    expect(page.ubicacionValidada()).toBe(false);

    page.registrar();
    http.expectNone('/api/v1/puentes');
  });

  it('permite reintentar la consulta después de un error', () => {
    page.seleccionarCoordenada({ latitud: 14.481, longitud: -90.615 });
    vi.advanceTimersByTime(300);

    http
      .expectOne((req) => req.url === '/api/v1/catalogos/ubicacion')
      .flush(
        { detail: 'Error temporal.' },
        { status: 503, statusText: 'Service Unavailable' },
      );

    expect(page.errorUbicacion()).toBe('Error temporal.');

    page.reintentarUbicacion();

    expect(page.errorUbicacion()).toBeNull();
    expect(page.resolviendoUbicacion()).toBe(true);

    vi.advanceTimersByTime(300);

    http
      .expectOne((req) => req.url === '/api/v1/catalogos/ubicacion')
      .flush(territorio());

    expect(page.ubicacionValidada()).toBe(true);
  });

  it('envía los datos mínimos y evita solicitudes duplicadas', () => {
    formularioValido();

    page.registrar();
    page.registrar();

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
    expect(page.form.disabled).toBe(true);

    requests[0].flush(
      {
        id: 'puente-nuevo',
        codigo: 'GT-01-0114-0001',
        activo: true,
        estadoActual: 'Sin evaluar',
      },
      { status: 201, statusText: 'Created' },
    );

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
      { id: 'puente-nuevo', codigo: 'GT-01-0114-0002' },
      { status: 201, statusText: 'Created' },
    );

    expect(page.puenteRegistrado()?.codigo).toBe('GT-01-0114-0002');
    expect(page.advertencia()).toBeNull();
  });

  it('cancelar la confirmación conserva los datos', () => {
    formularioValido();
    const datos = page.form.getRawValue();
    mostrarAdvertencia();

    page.cancelarConfirmacion();
    page.confirmarRegistro();

    expect(page.advertencia()).toBeNull();
    expect(page.form.getRawValue()).toEqual(datos);
    http.expectNone('/api/v1/puentes');
  });

  it('editar los datos invalida una confirmación pendiente', () => {
    formularioValido();
    mostrarAdvertencia();

    page.form.controls.nombre.setValue('Otro nombre');
    page.confirmarRegistro();

    expect(page.advertencia()).toBeNull();
    http.expectNone('/api/v1/puentes');
  });

  it('muestra los errores de campo recibidos en una respuesta 422', () => {
    formularioValido();
    page.registrar();

    http.expectOne('/api/v1/puentes').flush(
      {
        detail: 'Revisa los campos indicados.',
        errores: [
          { campo: 'ruta', mensaje: 'La ruta indicada no es válida.' },
        ],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    expect(page.mensajeCampo('ruta')).toBe('La ruta indicada no es válida.');
    expect(page.errorRegistro()).toBe('Revisa los campos indicados.');
    expect(page.guardando()).toBe(false);
    expect(page.form.controls.ruta.enabled).toBe(true);
    expect(page.puenteRegistrado()).toBeNull();
  });

  it('invalida el territorio si el backend rechaza la congruencia municipal', () => {
    formularioValido();
    page.registrar();

    http.expectOne('/api/v1/puentes').flush(
      {
        code: 'ubicacion_municipio_incongruente',
        detail: 'Las coordenadas no corresponden al municipio seleccionado.',
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    expect(page.territorio()).toBeNull();
    expect(page.form.controls.departamentoId.value).toBe('');
    expect(page.form.controls.municipioId.value).toBe('');
    expect(page.ubicacionValidada()).toBe(false);

    page.registrar();
    http.expectNone('/api/v1/puentes');
  });

  it('quitar ubicación limpia el punto y conserva los datos del puente', () => {
    formularioValido();
    mostrarAdvertencia();

    page.quitarUbicacion();

    const datos = page.form.getRawValue();

    expect(datos.latitud).toBeNull();
    expect(datos.longitud).toBeNull();
    expect(datos.departamentoId).toBe('');
    expect(datos.municipioId).toBe('');
    expect(datos.nombre).toBe('  Puente HU9  ');
    expect(datos.ruta).toBe('  CA-9  ');
    expect(page.territorio()).toBeNull();
    expect(page.zonaUtm()).toBeNull();
    expect(page.municipios()).toEqual([]);
    expect(page.advertencia()).toBeNull();
    expect(page.puntosCercanos()).toEqual([]);
    expect(page.form.controls.latitud.touched).toBe(false);
    expect(page.form.controls.longitud.touched).toBe(false);
    expect(page.ubicacionValidada()).toBe(false);

    page.confirmarRegistro();
    http.expectNone('/api/v1/puentes');
  });

  it('quitar ubicación cancela una consulta en curso', () => {
    page.seleccionarCoordenada({ latitud: 14.481, longitud: -90.615 });
    vi.advanceTimersByTime(300);

    const request = http.expectOne(
      (req) => req.url === '/api/v1/catalogos/ubicacion',
    );

    page.quitarUbicacion();

    expect(request.cancelled).toBe(true);
    expect(page.resolviendoUbicacion()).toBe(false);
    expect(page.territorio()).toBeNull();

    vi.advanceTimersByTime(300);
    http.expectNone((req) => req.url === '/api/v1/catalogos/ubicacion');
  });

  it('no permite quitar la ubicación mientras guarda', () => {
    formularioValido();
    page.registrar();

    page.quitarUbicacion();

    expect(page.form.getRawValue().latitud).toBe(14.481);
    expect(page.form.getRawValue().longitud).toBe(-90.615);

    http.expectOne('/api/v1/puentes').flush(
      { detail: 'Error temporal.' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    expect(page.guardando()).toBe(false);
  });

  it('registrar otro puente restablece formulario y ubicación', () => {
    formularioValido();
    page.registrar();

    http.expectOne('/api/v1/puentes').flush(
      { id: 'puente-nuevo', codigo: 'GT-01-0114-0001' },
      { status: 201, statusText: 'Created' },
    );

    page.registrarOtro();

    expect(page.puenteRegistrado()).toBeNull();
    expect(page.territorio()).toBeNull();
    expect(page.form.getRawValue()).toEqual({
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