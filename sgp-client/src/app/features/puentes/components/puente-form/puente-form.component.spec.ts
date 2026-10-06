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
import { PuenteFormValores, UbicacionTerritorialResponse } from '../../models/puente.models';
import { PuenteFormComponent } from './puente-form.component';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('PuenteFormComponent: formulario compartido de puentes', () => {
  let fixture: ComponentFixture<PuenteFormComponent>;
  let component: PuenteFormComponent;
  let http: HttpTestingController;
  let enviados: PuenteFormValores[];
  let cambios: number;
  let limpiarErrores: ReturnType<typeof vi.fn>;

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

  function urlUbicacion(): (req: { url: string }) => boolean {
    return (req) => req.url === '/api/v1/catalogos/ubicacion';
  }

  function resolverPunto(respuesta: UbicacionTerritorialResponse = territorio()): void {
    component.seleccionarCoordenada({
      latitud: respuesta.latitud,
      longitud: respuesta.longitud,
    });

    vi.advanceTimersByTime(300);

    const request = http.expectOne(urlUbicacion());

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('latitud')).toBe(String(respuesta.latitud));
    expect(request.request.params.get('longitud')).toBe(String(respuesta.longitud));

    request.flush(respuesta);
  }

  function formularioValido(): void {
    component.form.patchValue({
      nombre: '  Puente HU9  ',
      ruta: '  CA-9  ',
      kilometraje: null,
    });

    resolverPunto();
  }

  function selector(): SelectorUbicacionStubComponent {
    return fixture.debugElement.query(By.directive(SelectorUbicacionStubComponent))
      .componentInstance;
  }

  beforeEach(() => {
    vi.useFakeTimers();
    limpiarErrores = vi.fn();

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
          useValue: { clear: limpiarErrores },
        },
      ],
    });
    TestBed.overrideComponent(PuenteFormComponent, {
      remove: { imports: [TranslocoPipe, SelectorUbicacionComponent] },
      add: { imports: [TraduccionTestPipe, SelectorUbicacionStubComponent] },
    });

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PuenteFormComponent);
    component = fixture.componentInstance;

    enviados = [];
    cambios = 0;
    component.enviar.subscribe((valores) => enviados.push(valores));
    component.cambio.subscribe(() => cambios++);

    fixture.detectChanges();
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

  it('no consulta catálogos antes de seleccionar una ubicación', () => {
    expect(component.territorio()).toBeNull();
    expect(component.form.controls.departamentoId.disabled).toBe(true);
    expect(component.form.controls.municipioId.disabled).toBe(true);

    http.expectNone(() => true);
  });

  it('asigna departamento, municipio y zona UTM del punto', () => {
    resolverPunto();

    expect(component.form.getRawValue().departamentoId).toBe(departamento.id);
    expect(component.form.getRawValue().municipioId).toBe(municipio.id);
    expect(component.departamentos()).toEqual([departamento]);
    expect(component.municipios()).toEqual([municipio]);
    expect(component.zonaUtm()).toBe('15N');
    expect(component.ubicacionValidada()).toBe(true);
    expect(component.resolviendoUbicacion()).toBe(false);
    expect(component.form.controls.departamentoId.disabled).toBe(true);
    expect(component.form.controls.municipioId.disabled).toBe(true);
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

    expect(component.form.controls.departamentoId.value).toBe(departamento.id);
    expect(component.form.controls.municipioId.value).toBe('');
    expect(component.form.controls.municipioId.enabled).toBe(true);
    expect(component.ubicacionValidada()).toBe(false);

    component.form.controls.municipioId.setValue(otroMunicipio.id);

    expect(component.ubicacionValidada()).toBe(true);
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

    expect(component.form.controls.departamentoId.enabled).toBe(true);
    expect(component.ubicacionValidada()).toBe(false);

    component.form.controls.departamentoId.setValue(departamento.id);
    expect(component.form.controls.municipioId.value).toBe(municipio.id);

    component.form.controls.departamentoId.setValue(otroDepartamento.id);

    expect(component.municipios()).toEqual([otroMunicipio]);
    expect(component.form.controls.municipioId.value).toBe(otroMunicipio.id);
    expect(component.ubicacionValidada()).toBe(true);
  });

  it('rechaza un municipio ajeno a los candidatos territoriales', () => {
    formularioValido();
    component.form.controls.municipioId.setValue('municipio-no-candidato');

    component.enviarFormulario();

    expect(component.ubicacionValidada()).toBe(false);
    expect(enviados).toEqual([]);
  });

  it('espera la consulta territorial antes de permitir enviar', () => {
    component.form.patchValue({ nombre: 'Puente', ruta: 'CA-9' });
    component.seleccionarCoordenada({ latitud: 14.481, longitud: -90.615 });

    expect(component.resolviendoUbicacion()).toBe(true);

    component.enviarFormulario();
    expect(enviados).toEqual([]);

    vi.advanceTimersByTime(299);
    http.expectNone(urlUbicacion());

    vi.advanceTimersByTime(1);
    http.expectOne(urlUbicacion()).flush(territorio());

    expect(component.ubicacionValidada()).toBe(true);
  });

  it('consulta únicamente el último punto durante la espera', () => {
    component.seleccionarCoordenada({ latitud: 14.481, longitud: -90.615 });
    vi.advanceTimersByTime(150);

    component.seleccionarCoordenada({ latitud: 14.482, longitud: -90.616 });
    vi.advanceTimersByTime(300);

    const request = http.expectOne(urlUbicacion());

    expect(request.request.params.get('latitud')).toBe('14.482');
    expect(request.request.params.get('longitud')).toBe('-90.616');

    request.flush(territorio(14.482, -90.616));

    expect(component.territorio()?.latitud).toBe(14.482);
    expect(component.ubicacionValidada()).toBe(true);
  });

  it('cancela una petición anterior al seleccionar otro punto', () => {
    component.seleccionarCoordenada({ latitud: 14.481, longitud: -90.615 });
    vi.advanceTimersByTime(300);

    const anterior = http.expectOne(urlUbicacion());

    component.seleccionarCoordenada({ latitud: 14.482, longitud: -90.616 });

    expect(anterior.cancelled).toBe(true);

    vi.advanceTimersByTime(300);

    http.expectOne(urlUbicacion()).flush(territorio(14.482, -90.616));

    expect(component.territorio()?.latitud).toBe(14.482);
  });

  it('resuelve también coordenadas ingresadas manualmente', () => {
    component.form.patchValue({ latitud: 14.481, longitud: -90.615 });
    vi.advanceTimersByTime(300);

    http.expectOne(urlUbicacion()).flush(territorio());

    expect(component.ubicacionValidada()).toBe(true);
  });

  it('emite los valores normalizados cuando el formulario es válido', () => {
    formularioValido();

    component.enviarFormulario();

    expect(enviados).toEqual([
      {
        nombre: 'Puente HU9',
        departamentoId: departamento.id,
        municipioId: municipio.id,
        ruta: 'CA-9',
        kilometraje: null,
        latitud: 14.481,
        longitud: -90.615,
      },
    ]);
  });

  it.each(['', '   '])('no emite cuando ruta contiene "%s"', (ruta) => {
    formularioValido();
    component.form.controls.ruta.setValue(ruta);

    component.enviarFormulario();

    expect(component.form.controls.ruta.hasError('required')).toBe(true);
    expect(component.mensajeCampo('ruta')).toBe('puentes.required');
    expect(enviados).toEqual([]);
  });

  it.each([
    { latitud: 91, longitud: -90.615, campo: 'latitud' as const },
    { latitud: 14.481, longitud: -181, campo: 'longitud' as const },
  ])('rechaza coordenadas fuera de rango: $campo', (datos) => {
    formularioValido();
    component.form.patchValue({
      latitud: datos.latitud,
      longitud: datos.longitud,
    });

    vi.advanceTimersByTime(300);
    component.enviarFormulario();

    expect(component.form.controls[datos.campo].invalid).toBe(true);
    expect(component.ubicacionValidada()).toBe(false);
    http.expectNone(urlUbicacion());
    expect(enviados).toEqual([]);
  });

  it('bloquea el envío de un punto fuera de Guatemala', () => {
    component.form.patchValue({ nombre: 'Puente', ruta: 'CA-9' });
    component.seleccionarCoordenada({ latitud: 15, longitud: -87 });
    vi.advanceTimersByTime(300);

    http.expectOne(urlUbicacion()).flush(
      {
        code: 'ubicacion_fuera_de_guatemala',
        detail: 'Las coordenadas deben estar dentro de Guatemala.',
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    component.enviarFormulario();

    expect(component.errorUbicacion()).toBe('Las coordenadas deben estar dentro de Guatemala.');
    expect(component.resolviendoUbicacion()).toBe(false);
    expect(component.ubicacionValidada()).toBe(false);
    expect(enviados).toEqual([]);
  });

  it('bloquea puntos sin candidatos municipales', () => {
    resolverPunto({ ...territorio(), candidatos: [] });

    expect(component.errorUbicacion()).toBe('puentes.locationWithoutMunicipality');
    expect(component.ubicacionValidada()).toBe(false);

    component.enviarFormulario();
    expect(enviados).toEqual([]);
  });

  it('permite reintentar la consulta después de un error', () => {
    component.seleccionarCoordenada({ latitud: 14.481, longitud: -90.615 });
    vi.advanceTimersByTime(300);

    http
      .expectOne(urlUbicacion())
      .flush({ detail: 'Error temporal.' }, { status: 503, statusText: 'Service Unavailable' });

    expect(component.errorUbicacion()).toBe('Error temporal.');

    component.reintentarUbicacion();

    expect(component.errorUbicacion()).toBeNull();
    expect(component.resolviendoUbicacion()).toBe(true);

    vi.advanceTimersByTime(300);

    http.expectOne(urlUbicacion()).flush(territorio());

    expect(component.ubicacionValidada()).toBe(true);
  });

  it('quitar ubicación limpia el punto y conserva los datos del puente', () => {
    formularioValido();

    component.quitarUbicacion();

    const datos = component.form.getRawValue();

    expect(datos.latitud).toBeNull();
    expect(datos.longitud).toBeNull();
    expect(datos.departamentoId).toBe('');
    expect(datos.municipioId).toBe('');
    expect(datos.nombre).toBe('  Puente HU9  ');
    expect(datos.ruta).toBe('  CA-9  ');
    expect(component.territorio()).toBeNull();
    expect(component.zonaUtm()).toBeNull();
    expect(component.municipios()).toEqual([]);
    expect(component.form.controls.latitud.touched).toBe(false);
    expect(component.form.controls.longitud.touched).toBe(false);
    expect(component.ubicacionValidada()).toBe(false);
    expect(limpiarErrores).toHaveBeenCalled();
  });

  it('quitar ubicación cancela una consulta en curso', () => {
    component.seleccionarCoordenada({ latitud: 14.481, longitud: -90.615 });
    vi.advanceTimersByTime(300);

    const request = http.expectOne(urlUbicacion());

    component.quitarUbicacion();

    expect(request.cancelled).toBe(true);
    expect(component.resolviendoUbicacion()).toBe(false);
    expect(component.territorio()).toBeNull();

    vi.advanceTimersByTime(300);
    http.expectNone(urlUbicacion());
  });

  it('no permite cambiar la ubicación mientras se guarda', () => {
    formularioValido();

    fixture.componentRef.setInput('guardando', true);
    fixture.detectChanges();

    component.quitarUbicacion();
    component.seleccionarCoordenada({ latitud: 15, longitud: -91 });

    expect(component.form.getRawValue().latitud).toBe(14.481);
    expect(component.form.getRawValue().longitud).toBe(-90.615);
  });

  it('deshabilita el formulario mientras se guarda y lo reactiva después', () => {
    const otroMunicipio = { ...municipio, id: 'municipio-2', nombre: 'Otro' };

    resolverPunto({
      ...territorio(),
      requiereSeleccion: true,
      candidatos: [
        { departamento, municipio },
        { departamento, municipio: otroMunicipio },
      ],
    });
    expect(component.form.controls.municipioId.enabled).toBe(true);

    fixture.componentRef.setInput('guardando', true);
    fixture.detectChanges();

    expect(component.form.disabled).toBe(true);

    component.enviarFormulario();
    expect(enviados).toEqual([]);

    fixture.componentRef.setInput('guardando', false);
    fixture.detectChanges();

    expect(component.form.controls.nombre.enabled).toBe(true);
    expect(component.form.controls.departamentoId.disabled).toBe(true);
    expect(component.form.controls.municipioId.enabled).toBe(true);
  });

  it('muestra el error de campo recibido del servidor', () => {
    fixture.componentRef.setInput('erroresServidor', { ruta: 'La ruta indicada no es válida.' });

    expect(component.mensajeCampo('ruta')).toBe('La ruta indicada no es válida.');
  });

  it('invalida el territorio cuando el servidor rechaza la ubicación', () => {
    formularioValido();
    expect(component.ubicacionValidada()).toBe(true);

    fixture.componentRef.setInput(
      'errorTerritorioServidor',
      'Las coordenadas no corresponden al municipio seleccionado.',
    );
    fixture.detectChanges();

    expect(component.territorio()).toBeNull();
    expect(component.form.controls.departamentoId.value).toBe('');
    expect(component.form.controls.municipioId.value).toBe('');
    expect(component.errorUbicacion()).toBe(
      'Las coordenadas no corresponden al municipio seleccionado.',
    );
    expect(component.ubicacionValidada()).toBe(false);

    component.enviarFormulario();
    expect(enviados).toEqual([]);
  });

  it('precarga los valores iniciales y resuelve su territorio', () => {
    fixture.componentRef.setInput('valoresIniciales', {
      nombre: 'Puente existente',
      ruta: 'CA-2',
      kilometraje: 12.5,
      latitud: 14.481,
      longitud: -90.615,
    });
    fixture.detectChanges();

    expect(component.form.getRawValue()).toMatchObject({
      nombre: 'Puente existente',
      ruta: 'CA-2',
      kilometraje: 12.5,
      latitud: 14.481,
      longitud: -90.615,
    });

    vi.advanceTimersByTime(300);
    http.expectOne(urlUbicacion()).flush(territorio());

    expect(component.ubicacionValidada()).toBe(true);

    component.enviarFormulario();

    expect(enviados).toEqual([
      {
        nombre: 'Puente existente',
        departamentoId: departamento.id,
        municipioId: municipio.id,
        ruta: 'CA-2',
        kilometraje: 12.5,
        latitud: 14.481,
        longitud: -90.615,
      },
    ]);
  });

  it('avisa cuando el usuario edita el formulario', () => {
    cambios = 0;

    component.form.controls.nombre.setValue('Otro nombre');

    expect(cambios).toBe(1);
  });

  it('oculta el botón de envío y usa las etiquetas indicadas', () => {
    const boton = (): HTMLButtonElement | null =>
      fixture.nativeElement.querySelector('button[type="submit"]');

    expect(boton()?.textContent).toContain('puentes.register');

    fixture.componentRef.setInput('etiquetaEnviar', 'puentes.otraEtiqueta');
    fixture.detectChanges();
    expect(boton()?.textContent).toContain('puentes.otraEtiqueta');

    fixture.componentRef.setInput('guardando', true);
    fixture.componentRef.setInput('etiquetaEnviando', 'puentes.enviando');
    fixture.detectChanges();
    expect(boton()?.textContent).toContain('puentes.enviando');

    fixture.componentRef.setInput('ocultarAcciones', true);
    fixture.detectChanges();
    expect(boton()).toBeNull();
  });

  it('sincroniza el mapa con los campos y pasa los puentes cercanos', () => {
    const cercanos = [{ id: 'a', titulo: 'GT-01-0114-0001', latitud: 14.48, longitud: -90.61 }];
    fixture.componentRef.setInput('cercanos', cercanos);
    fixture.detectChanges();

    expect(selector().cercanos()).toEqual(cercanos);
    expect(selector().deshabilitado()).toBe(false);

    selector().coordenadaSeleccionada.emit({ latitud: 14.5, longitud: -90.5 });
    fixture.detectChanges();

    expect(component.form.getRawValue().latitud).toBe(14.5);
    expect(component.form.getRawValue().longitud).toBe(-90.5);
    expect(selector().latitud()).toBe(14.5);
    expect(selector().longitud()).toBe(-90.5);

    vi.advanceTimersByTime(300);
    http.expectOne(urlUbicacion()).flush(territorio(14.5, -90.5));
  });

  it('bloquea el mapa mientras se guarda', () => {
    fixture.componentRef.setInput('guardando', true);
    fixture.detectChanges();

    expect(selector().deshabilitado()).toBe(true);
  });
});
