import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthStore } from '../../../../core/services/auth.store';
import { UserSession } from '../../../../core/models/auth.models';
import { AppShellComponent } from '../../../../layouts/app-shell/app-shell.component';
import { SelectorUbicacionComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.component';
import { SelectorUbicacionStubComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.testing';
import { PuenteResponse } from '../../models/puente.models';
import { FichaPuentePage } from './ficha-puente.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('FichaPuentePage', () => {
  let fixture: ComponentFixture<FichaPuentePage>;
  let page: FichaPuentePage;
  let http: HttpTestingController;
  let paramMapId = 'puente-sin-evaluar';

  const authStoreMock = {
    authenticated: signal(false),
    user: signal<UserSession | null>(null),
  };

  const departamento = {
    id: 'depto-1',
    codigoIne: '01',
    nombre: 'Guatemala',
  };

  const municipio = {
    id: 'muni-1',
    departamentoId: departamento.id,
    codigoIne: '0101',
    nombre: 'Guatemala',
  };

  const puenteSinEvaluar: PuenteResponse = {
    id: 'puente-sin-evaluar',
    codigo: 'GT-01-0101-0001',
    nombre: 'Puente El Trébol',
    departamento,
    municipio,
    ruta: 'Calzada Roosevelt',
    kilometraje: 5.2,
    latitud: 14.612,
    longitud: -90.541,
    activo: true,
    estadoActual: 'Sin evaluar',
    indiceCondicionActual: null,
    fechaUltimaInspeccion: null,
    creadoEn: '2026-10-01T10:00:00Z',
    utm: {
      zona: 15,
      hemisferio: 'N',
      epsg: 32615,
      este: 549300,
      norte: 1615800,
    },
  };

  const puenteEvaluado: PuenteResponse = {
    id: 'puente-evaluado',
    codigo: 'GT-01-0101-0002',
    nombre: 'Puente La Asunción',
    departamento,
    municipio,
    ruta: 'Diagonal 14',
    kilometraje: 2.1,
    latitud: 14.63,
    longitud: -90.51,
    activo: true,
    estadoActual: 'Bueno',
    indiceCondicionActual: 85.5,
    fechaUltimaInspeccion: '2026-09-15',
    creadoEn: '2026-08-01T10:00:00Z',
    utm: {
      zona: 15,
      hemisferio: 'N',
      epsg: 32615,
      este: 552700,
      norte: 1617800,
    },
  };

  beforeEach(() => {
    paramMapId = 'puente-sin-evaluar';
    authStoreMock.authenticated.set(false);
    authStoreMock.user.set(null);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: () => paramMapId,
              },
            },
          },
        },
        {
          provide: AuthStore,
          useValue: authStoreMock,
        },
      ],
    });

    TestBed.overrideComponent(FichaPuentePage, {
      remove: { imports: [TranslocoPipe, SelectorUbicacionComponent] },
      add: { imports: [TraduccionTestPipe, SelectorUbicacionStubComponent] },
    });

    TestBed.overrideComponent(AppShellComponent, {
      set: { template: '<ng-content />' },
    });

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    fixture?.destroy();
    TestBed.resetTestingModule();
  });

  function iniciarComponente(): void {
    fixture = TestBed.createComponent(FichaPuentePage);
    page = fixture.componentInstance;
  }

  it('muestra estado "Sin evaluar" en gris y nunca "Bueno" para puente recién registrado sin inspecciones (RN-INV-10)', () => {
    iniciarComponente();
    fixture.detectChanges();

    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    expect(peticion.request.method).toBe('GET');
    peticion.flush(puenteSinEvaluar);
    fixture.detectChanges();

    expect(page.cargando()).toBe(false);
    expect(page.puente()?.estadoActual).toBe('Sin evaluar');

    const estadoTag = fixture.debugElement.query(By.css('.estado-tag'));
    expect(estadoTag).not.toBeNull();
    expect(estadoTag.nativeElement.textContent).toContain('Sin evaluar');
    expect(estadoTag.nativeElement.textContent).not.toContain('Bueno');
    expect(page.severidadEstado(page.puente()?.estadoActual)).toBe('secondary');

    const icValor = fixture.debugElement.query(By.css('.ic-valor'));
    expect(icValor.nativeElement.textContent).toContain('puentes.ficha.notAvailable');
  });

  it('muestra estado, IC y fecha de última inspección de solo lectura cuando cuenta con inspección (RN-INV-09)', () => {
    paramMapId = 'puente-evaluado';
    iniciarComponente();
    fixture.detectChanges();

    const peticion = http.expectOne('/api/v1/puentes/puente-evaluado');
    peticion.flush(puenteEvaluado);
    fixture.detectChanges();

    expect(page.cargando()).toBe(false);

    const estadoTag = fixture.debugElement.query(By.css('.estado-tag'));
    expect(estadoTag.nativeElement.textContent).toContain('Bueno');
    expect(page.severidadEstado(page.puente()?.estadoActual)).toBe('success');

    const icValor = fixture.debugElement.query(By.css('.ic-valor'));
    expect(icValor.nativeElement.textContent).toContain('85.5');

    const resumen = fixture.debugElement.query(By.css('.condicion-resumen'));
    expect(resumen.nativeElement.textContent).toContain('2026-09-15');
  });

  it('muestra aviso restringido y enlace de login para visitante no autenticado (RN-USR-10)', () => {
    authStoreMock.authenticated.set(false);
    authStoreMock.user.set(null);

    iniciarComponente();
    fixture.detectChanges();

    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    peticion.flush(puenteSinEvaluar);
    fixture.detectChanges();

    // Debe mostrar los datos publicos
    const titulo = fixture.debugElement.query(By.css('.page-title'));
    expect(titulo.nativeElement.textContent).toContain('Puente El Trébol');

    // Debe mostrar mapa de ubicacion
    const mapa = fixture.debugElement.query(By.css('app-selector-ubicacion'));
    expect(mapa).not.toBeNull();

    // Debe mostrar la advertencia de restriccion y el boton de login
    const avisoRestringido = fixture.debugElement.query(By.css('.aviso-restringido'));
    expect(avisoRestringido).not.toBeNull();
    expect(avisoRestringido.nativeElement.textContent).toContain('puentes.ficha.restrictedNotice');

    const botonLogin = fixture.debugElement.query(By.css('.btn-login-restringido'));
    expect(botonLogin).not.toBeNull();
  });

  it('muestra aviso de documentacion vacia y no solicita login cuando usuario está autenticado', () => {
    authStoreMock.authenticated.set(true);
    authStoreMock.user.set({
      id: 'usuario-1',
      email: 'estudiante@ejemplo.com',
      role: 'ESTUDIANTE',
      active: true,
      activated: true,
      verified: true,
      twoFactorEnabled: false,
    });

    iniciarComponente();
    fixture.detectChanges();

    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    peticion.flush(puenteSinEvaluar);
    fixture.detectChanges();

    const avisoRestringido = fixture.debugElement.query(By.css('.aviso-restringido'));
    expect(avisoRestringido).toBeNull();

    const avisoVacio = fixture.debugElement.query(By.css('.aviso-vacio'));
    expect(avisoVacio).not.toBeNull();
    expect(avisoVacio.nativeElement.textContent).toContain('puentes.ficha.noDocumentation');
  });

  it('muestra boton de edicion cuando el usuario es Administrador', () => {
    authStoreMock.authenticated.set(true);
    authStoreMock.user.set({
      id: 'admin-1',
      email: 'admin@ejemplo.com',
      role: 'ADMINISTRADOR',
      active: true,
      activated: true,
      verified: true,
      twoFactorEnabled: false,
    });

    iniciarComponente();
    fixture.detectChanges();

    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    peticion.flush(puenteSinEvaluar);
    fixture.detectChanges();

    const botonEditar = fixture.debugElement.query(
      By.css('a[href="/puentes/puente-sin-evaluar/editar"], a[ng-reflect-router-link*="editar"]'),
    );
    expect(botonEditar).not.toBeNull();
  });

  it('muestra mensaje de error cuando el puente no existe o esta inactivo (404)', () => {
    paramMapId = 'puente-inexistente';
    iniciarComponente();
    fixture.detectChanges();

    const peticion = http.expectOne('/api/v1/puentes/puente-inexistente');
    peticion.flush({ code: 'puente_no_encontrado' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(page.cargando()).toBe(false);
    expect(page.error()).toBe(true);

    const errorBox = fixture.debugElement.query(By.css('.estado-error'));
    expect(errorBox).not.toBeNull();
    expect(errorBox.nativeElement.textContent).toContain('puentes.ficha.notFound');
  });

  it('muestra etiqueta de kilometraje y notAvailable (N/A) cuando campos opcionales no tienen datos', () => {
    iniciarComponente();
    fixture.detectChanges();

    const puenteSinOpcionales: PuenteResponse = {
      ...puenteSinEvaluar,
      kilometraje: null,
    };
    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    peticion.flush(puenteSinOpcionales);
    fixture.detectChanges();

    const etiquetas = fixture.debugElement
      .queryAll(By.css('.dato-etiqueta'))
      .map((el) => el.nativeElement.textContent.trim());
    expect(etiquetas).toContain('puentes.ficha.kilometer');

    const filas = fixture.debugElement.queryAll(By.css('.dato-fila'));
    const filaKm = filas.find((f) =>
      f
        .query(By.css('.dato-etiqueta'))
        ?.nativeElement.textContent.includes('puentes.ficha.kilometer'),
    );
    expect(filaKm?.query(By.css('.dato-valor'))?.nativeElement.textContent.trim()).toBe(
      'puentes.ficha.notAvailable',
    );
  });
});
