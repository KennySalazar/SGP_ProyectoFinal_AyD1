import { RespaldoIcComponent } from '../../components/respaldo-ic/respaldo-ic.component';
import { SincronizacionService } from '../../../../offline/services/sincronizacion.service';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, EventEmitter, Input, Output, Pipe, PipeTransform, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthStore } from '../../../../core/services/auth.store';
import { RoleName, UserSession } from '../../../../core/models/auth.models';
import { AppShellComponent } from '../../../../layouts/app-shell/app-shell.component';
import { SelectorUbicacionComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.component';
import { SelectorUbicacionStubComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.testing';
import { IniciarInspeccionDialogComponent } from '../../../inspecciones/components/iniciar-inspeccion-dialog/iniciar-inspeccion-dialog.component';
import { ForoPuenteComponent } from '../../../foro/components/foro-puente/foro-puente.component';
import { MantenimientoPuenteComponent } from '../../../mantenimiento/components/mantenimiento-puente/mantenimiento-puente.component';
import { PuenteResponse } from '../../models/puente.models';
import { FichaPuentePage } from './ficha-puente.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

@Component({ template: '' })
class DetalleStubComponent {}

@Component({ selector: 'app-iniciar-inspeccion-dialog', standalone: true, template: '' })
class IniciarInspeccionDialogStubComponent {
  @Input() visible = false;
  @Input() puente: PuenteResponse | null = null;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() inspeccionIniciada = new EventEmitter<unknown>();
}

@Component({ selector: 'app-mantenimiento-puente', standalone: true, template: '' })
class MantenimientoPuenteStubComponent {
  @Input() puente: PuenteResponse | null = null;
}

@Component({ selector: 'app-foro-puente', standalone: true, template: '' })
class ForoPuenteStubComponent {
  @Input() puente: PuenteResponse | null = null;
  @Input() hiloInicial: string | null = null;
  @Input() comentarioDestacado: string | null = null;
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
        { provide: SincronizacionService, useValue: { iniciar: () => undefined } },
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'inspecciones/:id/publicada', component: DetalleStubComponent }]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: () => paramMapId,
              },
              queryParamMap: convertToParamMap({}),
            },
          },
        },
        {
          provide: AuthStore,
          useValue: authStoreMock,
        },
      ],
    });

    TestBed.overrideComponent(RespaldoIcComponent, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    TestBed.overrideComponent(FichaPuentePage, {
      remove: {
        imports: [
          TranslocoPipe,
          SelectorUbicacionComponent,
          IniciarInspeccionDialogComponent,
          MantenimientoPuenteComponent,
          ForoPuenteComponent,
        ],
      },
      add: {
        imports: [
          TraduccionTestPipe,
          SelectorUbicacionStubComponent,
          IniciarInspeccionDialogStubComponent,
          MantenimientoPuenteStubComponent,
          ForoPuenteStubComponent,
        ],
      },
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

  function renderizarFicha(): void {
    fixture.detectChanges();
    for (const request of http.match((req) => req.url.endsWith('/publicadas'))) {
      request.flush(paginaHistorial([]));
    }
    fixture.detectChanges();
  }

  function iniciarComponente(): void {
    fixture = TestBed.createComponent(FichaPuentePage);
    page = fixture.componentInstance;
  }

  it('muestra estado "Sin evaluar" en gris y nunca "Bueno" para puente recién registrado sin inspecciones (RN-INV-10)', () => {
    iniciarComponente();
    renderizarFicha();

    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    expect(peticion.request.method).toBe('GET');
    peticion.flush(puenteSinEvaluar);
    renderizarFicha();

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
    renderizarFicha();

    const peticion = http.expectOne('/api/v1/puentes/puente-evaluado');
    peticion.flush(puenteEvaluado);
    renderizarFicha();

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
    renderizarFicha();

    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    peticion.flush(puenteSinEvaluar);
    renderizarFicha();

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

  it('muestra historial vacío y no solicita login cuando usuario está autenticado', () => {
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
    renderizarFicha();

    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    peticion.flush(puenteSinEvaluar);
    renderizarFicha();

    const avisoRestringido = fixture.debugElement.query(By.css('.aviso-restringido'));
    expect(avisoRestringido).toBeNull();

    const avisoVacio = fixture.debugElement.query(By.css('.aviso-vacio'));
    expect(avisoVacio).not.toBeNull();
    expect(avisoVacio.nativeElement.textContent).toContain('inspecciones.historial.vacio');
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
    renderizarFicha();

    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    peticion.flush(puenteSinEvaluar);
    renderizarFicha();

    const botonEditar = fixture.debugElement.query(
      By.css('a[href="/puentes/puente-sin-evaluar/editar"], a[ng-reflect-router-link*="editar"]'),
    );
    expect(botonEditar).not.toBeNull();
  });

  it('muestra mensaje de error cuando el puente no existe o esta inactivo (404)', () => {
    paramMapId = 'puente-inexistente';
    iniciarComponente();
    renderizarFicha();

    const peticion = http.expectOne('/api/v1/puentes/puente-inexistente');
    peticion.flush({ code: 'puente_no_encontrado' }, { status: 404, statusText: 'Not Found' });
    renderizarFicha();

    expect(page.cargando()).toBe(false);
    expect(page.error()).toBe(true);

    const errorBox = fixture.debugElement.query(By.css('.estado-error'));
    expect(errorBox).not.toBeNull();
    expect(errorBox.nativeElement.textContent).toContain('puentes.ficha.notFound');
  });

  it('muestra etiqueta de kilometraje y notAvailable (N/A) cuando campos opcionales no tienen datos', () => {
    iniciarComponente();
    renderizarFicha();

    const puenteSinOpcionales: PuenteResponse = {
      ...puenteSinEvaluar,
      kilometraje: null,
    };
    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    peticion.flush(puenteSinOpcionales);
    renderizarFicha();

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

  it('muestra boton "Iniciar inspeccion" para Catedratico en puente activo', () => {
    authStoreMock.authenticated.set(true);
    authStoreMock.user.set({
      id: 'prof-1',
      email: 'catedratico@cunoc.edu.gt',
      role: 'CATEDRATICO',
      active: true,
      activated: true,
      verified: true,
      twoFactorEnabled: false,
    });

    iniciarComponente();
    renderizarFicha();

    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    peticion.flush(puenteSinEvaluar);
    renderizarFicha();

    const btn = fixture.debugElement.query(By.css('.btn-iniciar-inspeccion'));
    expect(btn).not.toBeNull();
    expect(page.puedeIniciarInspeccion()).toBe(true);

    btn.nativeElement.click();
    renderizarFicha();
    expect(page.mostrarModalIniciar()).toBe(true);
  });

  it('muestra boton "Iniciar inspeccion" para Profesional Externo verificado en puente activo', () => {
    authStoreMock.authenticated.set(true);
    authStoreMock.user.set({
      id: 'prof-ext-1',
      email: 'profesional@ejemplo.com',
      role: 'PROFESIONAL_EXTERNO',
      active: true,
      activated: true,
      verified: true,
      twoFactorEnabled: false,
      colegiadoVerificado: true,
    });

    iniciarComponente();
    renderizarFicha();

    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    peticion.flush(puenteSinEvaluar);
    renderizarFicha();

    const btn = fixture.debugElement.query(By.css('.btn-iniciar-inspeccion'));
    expect(btn).not.toBeNull();
    expect(page.puedeIniciarInspeccion()).toBe(true);
  });

  it('NO muestra boton "Iniciar inspeccion" para Profesional Externo NO verificado', () => {
    authStoreMock.authenticated.set(true);
    authStoreMock.user.set({
      id: 'prof-ext-2',
      email: 'profesional2@ejemplo.com',
      role: 'PROFESIONAL_EXTERNO',
      active: true,
      activated: true,
      verified: true,
      twoFactorEnabled: false,
      colegiadoVerificado: false,
    });

    iniciarComponente();
    renderizarFicha();

    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    peticion.flush(puenteSinEvaluar);
    renderizarFicha();

    const btn = fixture.debugElement.query(By.css('.btn-iniciar-inspeccion'));
    expect(btn).toBeNull();
    expect(page.puedeIniciarInspeccion()).toBe(false);
  });

  it('NO muestra boton "Iniciar inspeccion" para Estudiante o Administrador', () => {
    authStoreMock.authenticated.set(true);
    authStoreMock.user.set({
      id: 'est-1',
      email: 'estudiante@cunoc.edu.gt',
      role: 'ESTUDIANTE',
      active: true,
      activated: true,
      verified: true,
      twoFactorEnabled: false,
    });

    iniciarComponente();
    renderizarFicha();

    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    peticion.flush(puenteSinEvaluar);
    renderizarFicha();

    const btn = fixture.debugElement.query(By.css('.btn-iniciar-inspeccion'));
    expect(btn).toBeNull();
    expect(page.puedeIniciarInspeccion()).toBe(false);
  });

  it('NO muestra boton "Iniciar inspeccion" cuando el puente es inactivo', () => {
    authStoreMock.authenticated.set(true);
    authStoreMock.user.set({
      id: 'prof-1',
      email: 'catedratico@cunoc.edu.gt',
      role: 'CATEDRATICO',
      active: true,
      activated: true,
      verified: true,
      twoFactorEnabled: false,
    });

    iniciarComponente();
    renderizarFicha();

    const puenteInactivo: PuenteResponse = {
      ...puenteSinEvaluar,
      activo: false,
    };
    const peticion = http.expectOne('/api/v1/puentes/puente-sin-evaluar');
    peticion.flush(puenteInactivo);
    renderizarFicha();

    const btn = fixture.debugElement.query(By.css('.btn-iniciar-inspeccion'));
    expect(btn).toBeNull();
    expect(page.puedeIniciarInspeccion()).toBe(false);
  });

  function autenticar(role: RoleName = 'ESTUDIANTE'): void {
    authStoreMock.user.set({
      id: 'lector',
      email: 'lector@ejemplo.com',
      role,
      active: true,
      activated: true,
      verified: true,
      twoFactorEnabled: false,
    });
    authStoreMock.authenticated.set(true);
  }

  const rutaHistorial = '/api/v1/inspecciones/puentes/puente-sin-evaluar/publicadas';
  const reciente = {
    id: 'reciente',
    puenteId: 'puente-sin-evaluar',
    autorId: 'autor-1',
    nombreAutor: 'Autora reciente',
    fechaInspeccion: '2026-10-15',
    publicadoEn: '2026-10-18T12:00:00Z',
    estado: 'PUBLICADA',
  };
  const antigua = {
    ...reciente,
    id: 'antigua',
    nombreAutor: 'Autor anterior',
    publicadoEn: '2026-10-16T12:00:00Z',
  };

  function paginaHistorial(content: unknown[], number = 0, totalElements = content.length) {
    return {
      content,
      number,
      size: 20,
      totalElements,
      totalPages: Math.ceil(totalElements / 20),
      first: number === 0,
      last: (number + 1) * 20 >= totalElements,
      empty: content.length === 0,
    };
  }

  function abrirConHistorial(role: RoleName = 'ESTUDIANTE') {
    autenticar(role);
    iniciarComponente();
    fixture.detectChanges();
    http.expectOne('/api/v1/puentes/puente-sin-evaluar').flush(puenteSinEvaluar);
    fixture.detectChanges();
    return http.expectOne(rutaHistorial + '?pagina=0&tamanio=20');
  }

  it('consulta solo publicadas, conserva el orden recibido y muestra autor, fecha y etiqueta textual', () => {
    const request = abrirConHistorial();
    expect(request.request.method).toBe('GET');
    expect(page.cargandoHistorial()).toBe(true);
    expect(
      fixture.nativeElement.querySelector('.historial-publicado [role=status]').textContent,
    ).toContain('inspecciones.historial.cargando');
    request.flush(paginaHistorial([reciente, antigua]));
    fixture.detectChanges();
    const filas: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.historial-fila'),
    );
    expect(filas).toHaveLength(2);
    expect(filas[0].textContent).toContain('Autora reciente');
    expect(filas[0].textContent).toContain('18/10/2026');
    expect(filas[0].textContent).toContain('inspecciones.consulta.publicada');
    expect(filas[1].textContent).toContain('Autor anterior');
    expect(filas[0].querySelector('a')?.getAttribute('href')).toBe(
      '/inspecciones/reciente/publicada',
    );
    expect(filas[0].textContent).not.toContain('inspecciones.consulta.superada');
    http.expectNone('/api/v1/inspecciones');
  });

  it('pagina en el servidor con tamaño fijo 20 y permite regresar a la primera página', () => {
    abrirConHistorial().flush(paginaHistorial([reciente], 0, 42));
    fixture.detectChanges();
    const paginador = fixture.debugElement.query(By.css('p-paginator')).componentInstance;
    expect(paginador.rows).toBe(20);
    expect(paginador.totalRecords).toBe(42);
    paginador.onPageChange.emit({ page: 1, rows: 20, first: 20 });
    const siguiente = http.expectOne(rutaHistorial + '?pagina=1&tamanio=20');
    expect(page.cargandoHistorial()).toBe(true);
    siguiente.flush(paginaHistorial([antigua], 1, 42));
    fixture.detectChanges();
    expect(page.historial()?.number).toBe(1);
    expect(fixture.nativeElement.querySelector('.historial-fila').textContent).toContain(
      'Autor anterior',
    );
    page.cambiarPaginaHistorial({ page: 0, rows: 20 });
    http
      .expectOne(rutaHistorial + '?pagina=0&tamanio=20')
      .flush(paginaHistorial([reciente], 0, 42));
    fixture.detectChanges();
    expect(page.historial()?.number).toBe(0);
  });

  it('navega al detalle publicado desde el enlace del historial', async () => {
    abrirConHistorial().flush(paginaHistorial([reciente]));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.historial-fila a').click();
    await fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/inspecciones/reciente/publicada');
  });

  it('muestra estado vacío sin enlaces ni paginador', () => {
    abrirConHistorial().flush(paginaHistorial([]));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.historial-publicado').textContent).toContain(
      'inspecciones.historial.vacio',
    );
    expect(fixture.nativeElement.querySelector('.historial-fila')).toBeNull();
    expect(fixture.nativeElement.querySelector('p-paginator')).toBeNull();
  });

  it.each([401, 403, 404, 500])(
    'muestra error HTTP %s y permite reintentar sin alterar la ficha',
    (status) => {
      abrirConHistorial().flush({ detail: 'Error backend' }, { status, statusText: 'Error' });
      fixture.detectChanges();
      expect(page.errorHistorial()).toBe(true);
      expect(
        fixture.nativeElement.querySelector('.historial-publicado [role=alert]').textContent,
      ).toContain('inspecciones.historial.error');
      expect(fixture.nativeElement.querySelector('.page-title').textContent).toContain(
        puenteSinEvaluar.nombre,
      );
      fixture.nativeElement.querySelector('.historial-publicado button').click();
      http.expectOne(rutaHistorial + '?pagina=0&tamanio=20').flush(paginaHistorial([reciente]));
      fixture.detectChanges();
      expect(page.errorHistorial()).toBe(false);
      expect(fixture.nativeElement.querySelector('.historial-fila')).not.toBeNull();
    },
  );

  it.each<RoleName>(['ESTUDIANTE', 'CATEDRATICO', 'PROFESIONAL_EXTERNO', 'ADMINISTRADOR'])(
    'permite consultar el historial al rol autenticado %s',
    (role) => {
      abrirConHistorial(role).flush(paginaHistorial([reciente]));
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.historial-fila')).not.toBeNull();
    },
  );

  it('no solicita historial sin sesión y elimina datos al cerrar sesión', () => {
    abrirConHistorial().flush(paginaHistorial([reciente]));
    fixture.detectChanges();
    authStoreMock.authenticated.set(false);
    authStoreMock.user.set(null);
    fixture.detectChanges();
    expect(page.historial()).toBeNull();
    expect(fixture.nativeElement.querySelector('.historial-publicado')).toBeNull();
    http.expectNone((req) => req.url.endsWith('/publicadas'));
  });

  it('no muestra filas privadas aunque una respuesta inesperada las incluya', () => {
    abrirConHistorial().flush(
      paginaHistorial([
        reciente,
        { ...antigua, estado: 'BORRADOR', nombreAutor: 'Autor privado' },
        { ...antigua, estado: 'ENVIADA', nombreAutor: 'Autor enviado' },
      ]),
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.historial-fila')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('.historial-publicado').textContent).not.toContain(
      'Autor privado',
    );
    expect(fixture.nativeElement.querySelector('.historial-publicado').textContent).not.toContain(
      'Autor enviado',
    );
  });

  it('muestra SUPERADA únicamente cuando el resumen proporciona esa información', () => {
    abrirConHistorial().flush(paginaHistorial([{ ...reciente, superadaPorId: 'correccion' }]));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.historial-fila').textContent).toContain(
      'inspecciones.consulta.superada',
    );
    http.expectNone((req) => req.url.endsWith('/publicada'));
  });

  it('cancela la solicitud pendiente al salir de sesión sin conservar el historial', () => {
    const request = abrirConHistorial();
    authStoreMock.authenticated.set(false);
    authStoreMock.user.set(null);
    fixture.detectChanges();
    expect(request.cancelled).toBe(true);
    expect(page.historial()).toBeNull();
    expect(page.cargandoHistorial()).toBe(false);
  });
});
