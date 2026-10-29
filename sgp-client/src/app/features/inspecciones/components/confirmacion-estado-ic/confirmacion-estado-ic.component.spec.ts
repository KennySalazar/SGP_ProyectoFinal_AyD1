import { AuthStore } from '../../../../core/services/auth.store';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ConfirmacionEstadoIcComponent } from './confirmacion-estado-ic.component';
import { ConfirmacionEstadoIcResponse } from '../../models/inspeccion.models';
import { ConnectivityService } from '../../../../offline/services/connectivity.service';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

function respuesta(): ConfirmacionEstadoIcResponse {
  return {
    inspeccionId: 'inspeccion-1',
    calculo: {
      versionFormularioId: 'version-1',
      codigoFormulario: 'SIECA-v1',
      versionAlgoritmo: 'IC-v1',
      hashEntrada: 'a'.repeat(64),
      indiceCalculado: 80,
      estadoPorRango: 'BUENO',
      estadoCalculado: 'REGULAR',
      sumaPesos: 1,
      sumaCalificacionesPonderadas: 0.8,
      desglose: [],
      condicionesAnulacion: [],
    },
    estadoConfirmado: null,
    justificacion: null,
    realizadoPorId: null,
    realizadoEn: null,
    confirmacionVigente: false,
  };
}

describe('Confirmacion del estado calculado HU032', () => {
  let fixture: ComponentFixture<ConfirmacionEstadoIcComponent>;
  let component: ConfirmacionEstadoIcComponent;
  let http: HttpTestingController;
  const url = '/api/v1/inspecciones/inspeccion-1/confirmacion-estado';
  let usuarioActual = 'usuario-1';

  beforeEach(() => {
    usuarioActual = 'usuario-1';
    TestBed.configureTestingModule({
      providers: [
        { provide: TranslocoService, useValue: { translate: (clave: string) => clave } },
        { provide: AuthStore, useValue: { user: () => ({ id: usuarioActual }) } },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    TestBed.overrideComponent(ConfirmacionEstadoIcComponent, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    fixture = TestBed.createComponent(ConfirmacionEstadoIcComponent);
    fixture.componentRef.setInput('inspeccionId', 'inspeccion-1');
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });
  afterEach(() => {
    http.verify();
    fixture.destroy();
  });

  function consultar(res = respuesta()) {
    component.consultar();
    const req = http.expectOne(url);
    expect(req.request.method).toBe('GET');
    req.flush(res);
    fixture.detectChanges();
  }

  it('consulta bajo demanda y muestra IC preliminar, estado automatico y version', () => {
    http.expectNone(url);
    consultar();
    expect(fixture.nativeElement.textContent).toContain('80');
    expect(fixture.nativeElement.textContent).toContain('SIECA-v1 / IC-v1');
    expect(component.form.controls.estado.value).toBe('REGULAR');
  });

  it('muestra el índice con dos decimales y la versión del formulario recibida', () => {
    fixture.componentRef.setInput('versionFormularioCodigo', 'SIECA-v7');
    const res = respuesta();
    res.calculo.indiceCalculado = 94.44444;
    consultar(res);
    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('94.44');
    expect(texto).not.toContain('94.444');
    expect(texto).toContain('SIECA-v7 / IC-v1');
    expect(texto).not.toContain('SIECA-v1 / IC-v1');
  });

  it('no repite la tarjeta de respaldo académico de la calibración', () => {
    consultar();
    expect(fixture.nativeElement.querySelector('app-aprobacion-calibracion')).toBeNull();
  });

  it('indica "por usted" cuando confirmó el usuario actual y nunca muestra identificadores', () => {
    const res = respuesta();
    Object.assign(res, {
      confirmacionVigente: true,
      estadoConfirmado: 'REGULAR',
      realizadoPorId: 'usuario-1',
      realizadoEn: '2026-10-28T21:19:00Z',
    });
    consultar(res);
    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('inspecciones.confirmacionIc.registroUsted');
    expect(texto).not.toContain('usuario-1');
  });

  it('si confirmó otra persona muestra solo la fecha, sin su identificador', () => {
    const res = respuesta();
    Object.assign(res, {
      confirmacionVigente: true,
      estadoConfirmado: 'REGULAR',
      realizadoPorId: '8537b096-963f-4b52-a98e-7530eae9c875',
      realizadoEn: '2026-10-28T21:19:00Z',
    });
    consultar(res);
    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('inspecciones.confirmacionIc.registro');
    expect(texto).not.toContain('registroUsted');
    expect(texto).not.toContain('8537b096');
  });

  it('HU-061 aceptación: sin red oculta el IC anterior y muestra disponible al sincronizar', () => {
    consultar();
    TestBed.inject(ConnectivityService).online.set(false);
    fixture.detectChanges();
    expect(component.calculoVisible()).toBeNull();
    expect(fixture.nativeElement.querySelector('dl')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain(
      'inspecciones.confirmacionIc.icPreliminarOffline',
    );
    component.consultar();
    component.guardar();
    http.expectNone(url);
  });

  it('confirma el estado automatico sin exigir justificacion ni enviar identidad', () => {
    consultar();
    component.guardar();
    const req = http.expectOne(url);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      estadoConfirmado: 'REGULAR',
      hashEntrada: 'a'.repeat(64),
      justificacion: null,
    });
    req.flush({
      ...respuesta(),
      estadoConfirmado: 'REGULAR',
      confirmacionVigente: true,
      realizadoPorId: 'autor-1',
      realizadoEn: '2026-10-17T08:00:00Z',
    });
    expect(component.exito()).toBe(true);
    expect(component.calculo()?.indiceCalculado).toBe(80);
  });

  it('rechaza cambios sin justificacion incluso cuando solo contiene espacios', () => {
    consultar();
    component.form.setValue({ estado: 'MALO', justificacion: '   ' });
    component.guardar();
    http.expectNone(url);
    expect(component.error()).toBe('inspecciones.confirmacionIc.justificacionRequerida');
  });

  it('ajusta con justificacion, mantiene IC y estado automatico y no duplica solicitudes', () => {
    consultar();
    component.form.setValue({ estado: 'MALO', justificacion: '  Perdida de capacidad  ' });
    component.guardar();
    component.guardar();
    const req = http.expectOne(url);
    expect(req.request.body.justificacion).toBe('Perdida de capacidad');
    expect(req.request.body.estadoConfirmado).toBe('MALO');
    req.flush({
      ...respuesta(),
      estadoConfirmado: 'MALO',
      justificacion: 'Perdida de capacidad',
      confirmacionVigente: true,
    });
    expect(component.calculo()?.estadoCalculado).toBe('REGULAR');
    expect(component.calculo()?.indiceCalculado).toBe(80);
    expect(component.respuesta()?.estadoConfirmado).toBe('MALO');
  });

  it('muestra condicion critica y exige justificacion al ajustar el Malo forzado', () => {
    const res = respuesta();
    res.calculo.estadoCalculado = 'MALO';
    res.calculo.condicionesAnulacion = [
      {
        reglaId: 'critica-1',
        descripcion: 'Cimentacion expuesta',
        elementoRef: '/pilas',
        instanciaId: null,
        rutaDatos: '/pilas',
        indicador: 'socavacion',
        valorDetectado: 'SI',
        estadoResultante: 'MALO',
      },
    ];
    consultar(res);
    expect(fixture.nativeElement.textContent).toContain('Cimentacion expuesta');
    component.form.controls.estado.setValue('REGULAR');
    component.guardar();
    http.expectNone(url);
    component.form.controls.justificacion.setValue('Revision profesional documentada');
    component.guardar();
    const req = http.expectOne(url);
    req.flush({ ...res, estadoConfirmado: 'REGULAR', confirmacionVigente: true });
    expect(component.calculo()?.indiceCalculado).toBe(80);
    expect(component.calculo()?.estadoCalculado).toBe('MALO');
  });

  it('rechaza justificaciones que superan 10000 caracteres', () => {
    consultar();
    component.form.controls.justificacion.setValue('a'.repeat(10001));
    component.guardar();
    http.expectNone(url);
  });

  it('recupera la confirmacion vigente y sus datos de autor y fecha', () => {
    consultar({
      ...respuesta(),
      confirmacionVigente: true,
      estadoConfirmado: 'MALO',
      justificacion: 'Criterio profesional',
      realizadoPorId: 'autor-1',
      realizadoEn: '2026-10-17T08:00:00Z',
    });
    expect(component.form.getRawValue()).toEqual({
      estado: 'MALO',
      justificacion: 'Criterio profesional',
    });
    expect(component.respuesta()?.realizadoPorId).toBe('autor-1');
  });

  it('descarta la decision previa cuando el backend la identifica como no vigente', () => {
    consultar({
      ...respuesta(),
      estadoConfirmado: 'MALO',
      justificacion: 'Anterior',
      confirmacionVigente: false,
    });
    expect(component.form.getRawValue()).toEqual({ estado: 'REGULAR', justificacion: '' });
  });

  it('invalida la respuesta al modificar el formulario y bloquea operaciones mientras se guarda', () => {
    consultar();
    fixture.componentRef.setInput('revision', 1);
    fixture.componentRef.setInput('bloqueado', true);
    fixture.detectChanges();
    expect(component.respuesta()).toBeNull();
    component.consultar();
    component.guardar();
    http.expectNone(url);
    fixture.componentRef.setInput('bloqueado', false);
    fixture.detectChanges();
    consultar();
  });

  it('ignora una consulta que termina despues de modificar los datos', () => {
    component.consultar();
    const req = http.expectOne(url);
    fixture.componentRef.setInput('revision', 1);
    fixture.detectChanges();
    req.flush(respuesta());
    expect(component.respuesta()).toBeNull();
  });

  it('maneja 403 y no permite seguir ajustando como otro usuario', () => {
    component.consultar();
    http.expectOne(url).flush({ code: 'access_denied' }, { status: 403, statusText: 'Forbidden' });
    expect(component.error()).toBe('inspecciones.confirmacionIc.noAutorizado');
    component.consultar();
    http.expectNone(url);
  });

  it('maneja una inspeccion publicada con 409 y orienta a crear correccion', () => {
    consultar();
    component.guardar();
    http
      .expectOne(url)
      .flush({ code: 'inspeccion_publicada' }, { status: 409, statusText: 'Conflict' });
    expect(component.error()).toBe('inspecciones.confirmacionIc.noEditable');
    expect(component.respuesta()).toBeNull();
    component.consultar();
    http.expectNone(url);
  });

  it('obliga a consultar de nuevo tras un hash desactualizado y permite reintentar', () => {
    consultar();
    component.guardar();
    http
      .expectOne(url)
      .flush({ code: 'calculo_ic_desactualizado' }, { status: 409, statusText: 'Conflict' });
    expect(component.error()).toBe('inspecciones.confirmacionIc.desactualizado');
    component.guardar();
    http.expectNone(url);
    consultar();
  });

  it('muestra errores de validacion del servidor y conserva la entrada para corregirla', () => {
    consultar();
    component.guardar();
    http
      .expectOne(url)
      .flush(
        { detail: 'Configuracion incompleta' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    expect(component.error()).toBe('Configuracion incompleta');
    expect(component.procesando()).toBe(false);
    expect(component.calculo()?.indiceCalculado).toBe(80);
  });
  it('explica la seccion pendiente sin mostrar rutas tecnicas y permite volver a consultar', () => {
    consultar();
    component.consultar();
    http
      .expectOne(url)
      .flush(
        { code: 'datos_ic_incompletos', detail: 'Falta evaluar la ruta /subestructura' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();
    expect(component.seccionPendiente()).toBe('Subestructura');
    expect(component.respuesta()).toBeNull();
    expect(component.restringido()).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('/subestructura');
    expect(fixture.nativeElement.textContent).toContain(
      'inspecciones.confirmacionIc.seccionPendiente',
    );
    consultar();
    expect(component.seccionPendiente()).toBeNull();
    expect(component.calculo()?.indiceCalculado).toBe(80);
  });

  it('usa una indicacion general cuando los datos pendientes no identifican una seccion conocida', () => {
    component.consultar();
    http
      .expectOne(url)
      .flush(
        { code: 'datos_ic_incompletos', detail: 'Falta evaluar el elemento pilas' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    expect(component.error()).toBe('inspecciones.confirmacionIc.datosPendientes');
    expect(component.seccionPendiente()).toBeNull();
    expect(component.procesando()).toBe(false);
  });
});
