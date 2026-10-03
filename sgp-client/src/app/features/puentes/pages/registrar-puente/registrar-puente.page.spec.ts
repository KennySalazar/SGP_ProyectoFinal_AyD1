import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
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
    nombre: 'Amatitlan',
  };

  function pagina<T>(content: T[]) {
    return {
      content,
      totalElements: content.length,
      totalPages: content.length > 0 ? 1 : 0,
      number: 0,
      size: 100,
      first: true,
      last: true,
      empty: content.length === 0,
    };
  }

  function formularioValido(): void {
    page.form.controls.departamentoId.setValue(departamento.id);

    http
      .expectOne(
        (req) => req.url === `/api/v1/catalogos/departamentos/${departamento.id}/municipios`,
      )
      .flush(pagina([municipio]));

    page.form.patchValue({
      nombre: '  Puente HU9  ',
      municipioId: municipio.id,
      ruta: '  CA-9  ',
      kilometraje: null,
      latitud: 14.481,
      longitud: -90.615,
    });
  }

  function mostrarAdvertencia(): void {
    page.registrar();

    http.expectOne('/api/v1/puentes').flush(
      {
        type: 'about:blank',
        title: 'Hay puentes cercanos',
        status: 409,
        detail: 'Se requiere confirmar la cercania.',
        code: 'puente_cercano',
        requiereConfirmacion: true,
        puentesCercanos: [
          {
            id: 'puente-existente',
            codigo: 'GT-01-0114-0001',
            nombre: 'Puente existente',
            activo: true,
            distanciaMetros: 11.1,
          },
        ],
        totalPuentesCercanos: 1,
      },
      { status: 409, statusText: 'Conflict' },
    );
  }

  beforeEach(() => {
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

    http
      .expectOne((req) => req.url === '/api/v1/catalogos/departamentos')
      .flush(pagina([departamento]));
  });

  afterEach(() => {
    http.verify();
  });

  it.each(['', '   '])('no envia el registro cuando ruta contiene "%s"', (ruta) => {
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

    page.registrar();

    expect(page.form.controls[datos.campo].invalid).toBe(true);
    http.expectNone('/api/v1/puentes');
  });

  it('limpia el municipio al cambiar de departamento', () => {
    formularioValido();

    page.form.controls.departamentoId.setValue('departamento-2');

    expect(page.form.controls.municipioId.value).toBe('');
    expect(page.form.controls.municipioId.disabled).toBe(true);
    expect(page.municipios()).toEqual([]);
    expect(page.cargandoMunicipios()).toBe(true);

    http
      .expectOne((req) => req.url === '/api/v1/catalogos/departamentos/departamento-2/municipios')
      .flush(
        pagina([
          {
            ...municipio,
            id: 'municipio-2',
            departamentoId: 'departamento-2',
          },
        ]),
      );

    expect(page.form.controls.municipioId.enabled).toBe(true);
    expect(page.form.controls.municipioId.value).toBe('');
    expect(page.cargandoMunicipios()).toBe(false);
  });

  it('rechaza un municipio que no pertenece al departamento', () => {
    formularioValido();
    page.municipios.set([{ ...municipio, departamentoId: 'departamento-2' }]);

    page.registrar();

    expect(page.erroresCampos()['municipioId']).toBe('puentes.invalidTerritory');
    http.expectNone('/api/v1/puentes');
  });

  it('espera confirmacion antes de reenviar un puente cercano', () => {
    formularioValido();
    mostrarAdvertencia();

    expect(page.advertencia()?.requiereConfirmacion).toBe(true);
    expect(page.puenteRegistrado()).toBeNull();
    expect(page.guardando()).toBe(false);
    http.expectNone('/api/v1/puentes');

    page.confirmarRegistro();

    const request = http.expectOne('/api/v1/puentes');

    expect(request.request.body).toEqual({
      nombre: 'Puente HU9',
      departamentoId: departamento.id,
      municipioId: municipio.id,
      ruta: 'CA-9',
      kilometraje: null,
      latitud: 14.481,
      longitud: -90.615,
      confirmarCercania: true,
    });

    request.flush(
      {
        id: 'puente-nuevo',
        codigo: 'GT-01-0114-0002',
        activo: true,
        estadoActual: 'Sin evaluar',
      },
      { status: 201, statusText: 'Created' },
    );

    expect(page.puenteRegistrado()?.codigo).toBe('GT-01-0114-0002');
    expect(page.advertencia()).toBeNull();
    expect(page.guardando()).toBe(false);
  });

  it('volver al formulario conserva datos y cancela la confirmacion', () => {
    formularioValido();
    const datos = page.form.getRawValue();
    mostrarAdvertencia();

    page.cancelarConfirmacion();
    page.confirmarRegistro();

    expect(page.advertencia()).toBeNull();
    expect(page.form.getRawValue()).toEqual(datos);
    expect(page.puenteRegistrado()).toBeNull();
    http.expectNone('/api/v1/puentes');
  });

  it('editar los datos invalida una confirmacion pendiente', () => {
    formularioValido();
    mostrarAdvertencia();

    page.form.controls.nombre.setValue('Otro nombre');
    page.confirmarRegistro();

    expect(page.advertencia()).toBeNull();
    http.expectNone('/api/v1/puentes');
  });

  it('muestra el error de campo recibido en una respuesta 422', () => {
    formularioValido();
    page.registrar();

    http.expectOne('/api/v1/puentes').flush(
      {
        type: 'about:blank',
        title: 'Error de validacion',
        status: 422,
        detail: 'Revisa los campos indicados.',
        errores: [
          {
            campo: 'ruta',
            mensaje: 'La ruta indicada no es valida.',
          },
        ],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    expect(page.mensajeCampo('ruta')).toBe('La ruta indicada no es valida.');
    expect(page.errorRegistro()).toBe('Revisa los campos indicados.');
    expect(page.form.enabled).toBe(true);
    expect(page.guardando()).toBe(false);
    expect(page.puenteRegistrado()).toBeNull();
  });

  it('evita solicitudes duplicadas mientras guarda', () => {
    formularioValido();

    page.registrar();
    page.registrar();

    const requests = http.match('/api/v1/puentes');

    expect(requests).toHaveLength(1);
    expect(requests[0].request.body.confirmarCercania).toBe(false);
    expect(page.guardando()).toBe(true);
    expect(page.form.disabled).toBe(true);

    requests[0].flush(
      { detail: 'Servicio temporalmente no disponible.' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    expect(page.guardando()).toBe(false);
    expect(page.form.enabled).toBe(true);
    expect(page.errorRegistro()).toBe('Servicio temporalmente no disponible.');
  });

  it('permite reintentar la carga de municipios despues de un error', () => {
    page.form.controls.departamentoId.setValue(departamento.id);

    http
      .expectOne(
        (req) => req.url === `/api/v1/catalogos/departamentos/${departamento.id}/municipios`,
      )
      .flush({ detail: 'Error temporal.' }, { status: 503, statusText: 'Service Unavailable' });

    expect(page.errorMunicipios()).toBe(true);
    expect(page.form.controls.municipioId.disabled).toBe(true);

    page.reintentarMunicipios();

    http
      .expectOne(
        (req) => req.url === `/api/v1/catalogos/departamentos/${departamento.id}/municipios`,
      )
      .flush(pagina([municipio]));

    expect(page.errorMunicipios()).toBe(false);
    expect(page.municipios()).toEqual([municipio]);
    expect(page.form.controls.municipioId.enabled).toBe(true);
  });
});
