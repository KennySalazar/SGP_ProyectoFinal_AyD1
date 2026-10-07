import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { SelectorUbicacionComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.component';
import { SelectorUbicacionStubComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.testing';
import { PuenteFormComponent } from '../../components/puente-form/puente-form.component';
import { PuenteResponse, UbicacionTerritorialResponse } from '../../models/puente.models';
import { EditarPuentePage } from './editar-puente.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('EditarPuentePage', () => {
  let fixture: ComponentFixture<EditarPuentePage>;
  let page: EditarPuentePage;
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

  const puenteMock: PuenteResponse = {
    id: 'puente-1',
    codigo: 'GT-01-0114-0001',
    nombre: 'Puente Amatitlan',
    departamento,
    municipio,
    ruta: 'CA-9 Sur',
    kilometraje: 28.5,
    latitud: 14.481,
    longitud: -90.615,
    activo: true,
    estadoActual: 'Sin evaluar',
    indiceCondicionActual: null,
    fechaUltimaInspeccion: null,
    creadoEn: '2026-10-01T12:00:00Z',
    utm: {
      zona: 15,
      hemisferio: 'N',
      epsg: 32615,
      este: 541500,
      norte: 1601000,
    },
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

  beforeEach(() => {
    vi.useFakeTimers();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ id: 'puente-1' })),
            snapshot: { paramMap: convertToParamMap({ id: 'puente-1' }) },
          },
        },
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

    TestBed.overrideComponent(EditarPuentePage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });

    TestBed.overrideComponent(PuenteFormComponent, {
      remove: { imports: [TranslocoPipe, SelectorUbicacionComponent] },
      add: { imports: [TraduccionTestPipe, SelectorUbicacionStubComponent] },
    });

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EditarPuentePage);
    page = fixture.componentInstance;
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

  it('muestra estado de error si no se encuentra el puente', () => {
    fixture.detectChanges();

    const req = http.expectOne('/api/v1/puentes/puente-1');
    expect(req.request.method).toBe('GET');
    req.flush(null, { status: 404, statusText: 'Not Found' });

    fixture.detectChanges();

    expect(page.cargandoPuente()).toBe(false);
    expect(page.errorCargaPuente()).toBe(true);
    expect(fixture.debugElement.query(By.css('.estado-error'))).toBeTruthy();
  });

  it('carga el puente y muestra los campos calculados e inmutables en solo lectura', () => {
    fixture.detectChanges();

    const req = http.expectOne('/api/v1/puentes/puente-1');
    req.flush(puenteMock);

    fixture.detectChanges();

    expect(page.cargandoPuente()).toBe(false);
    expect(page.puente()).toEqual(puenteMock);

    const textoCodigo = fixture.debugElement.query(By.css('.code-value')).nativeElement.textContent;
    expect(textoCodigo).toContain('GT-01-0114-0001');

    const textoEstado = fixture.debugElement.query(By.css('.estado-puente')).nativeElement
      .textContent;
    expect(textoEstado).toContain('Sin evaluar');
  });

  it('precarga el formulario y permite guardar cambios', () => {
    fixture.detectChanges();

    http.expectOne('/api/v1/puentes/puente-1').flush(puenteMock);
    fixture.detectChanges();

    // Resolver territorio inicial disparado por valoresIniciales
    vi.advanceTimersByTime(300);
    http.expectOne((r) => r.url === '/api/v1/catalogos/ubicacion').flush(territorio());
    fixture.detectChanges();

    expect(formulario().form.getRawValue().nombre).toBe('Puente Amatitlan');

    // Modificar nombre
    formulario().form.controls.nombre.setValue('Puente Amatitlan Renovado');
    fixture.detectChanges();

    formulario().enviarFormulario();

    const peticionActualizar = http.expectOne('/api/v1/puentes/puente-1');
    expect(peticionActualizar.request.method).toBe('PUT');
    expect(peticionActualizar.request.body).toEqual({
      nombre: 'Puente Amatitlan Renovado',
      departamentoId: departamento.id,
      municipioId: municipio.id,
      ruta: 'CA-9 Sur',
      kilometraje: 28.5,
      latitud: 14.481,
      longitud: -90.615,
      confirmarCercania: false,
      codigo: 'GT-01-0114-0001',
    });

    peticionActualizar.flush({
      ...puenteMock,
      nombre: 'Puente Amatitlan Renovado',
    });

    fixture.detectChanges();

    expect(page.puente()?.nombre).toBe('Puente Amatitlan Renovado');
    expect(page.puenteActualizado()?.nombre).toBe('Puente Amatitlan Renovado');
    expect(fixture.debugElement.query(By.css('.feedback.success'))).toBeTruthy();
  });

  it('gestiona advertencia de cercania y permite confirmar actualizacion', () => {
    fixture.detectChanges();

    http.expectOne('/api/v1/puentes/puente-1').flush(puenteMock);
    fixture.detectChanges();

    vi.advanceTimersByTime(300);
    http.expectOne((r) => r.url === '/api/v1/catalogos/ubicacion').flush(territorio());
    fixture.detectChanges();

    formulario().enviarFormulario();

    const putInicial = http.expectOne('/api/v1/puentes/puente-1');
    putInicial.flush(
      {
        type: 'about:blank',
        title: 'Hay puentes cercanos',
        status: 409,
        detail: 'Se requiere confirmar cercania.',
        code: 'puente_cercano',
        requiereConfirmacion: true,
        puentesCercanos: [
          {
            id: 'puente-2',
            codigo: 'GT-01-0114-0002',
            nombre: 'Puente Vecino',
            activo: true,
            distanciaMetros: 25.0,
            latitud: 14.4812,
            longitud: -90.615,
          },
        ],
        totalPuentesCercanos: 1,
      },
      { status: 409, statusText: 'Conflict' },
    );

    fixture.detectChanges();

    expect(page.advertencia()).not.toBeNull();
    expect(page.puntosCercanos().length).toBe(1);
    expect(fixture.debugElement.query(By.css('.proximity'))).toBeTruthy();

    // Confirmar cercanía
    page.confirmarActualizacion();

    const putConfirmado = http.expectOne('/api/v1/puentes/puente-1');
    expect(putConfirmado.request.method).toBe('PUT');
    expect(putConfirmado.request.body.confirmarCercania).toBe(true);

    putConfirmado.flush(puenteMock);
    fixture.detectChanges();

    expect(page.advertencia()).toBeNull();
  });
});
