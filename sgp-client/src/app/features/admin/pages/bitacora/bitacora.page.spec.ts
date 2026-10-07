import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RegistroAuditoria } from '../../models/auditoria.models';
import { BitacoraPage } from './bitacora.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('Característica: consulta de bitácora HU007', () => {
  let fixture: ComponentFixture<BitacoraPage>;
  let page: BitacoraPage;
  let http: HttpTestingController;
  const registro: RegistroAuditoria = {
    id: 'registro-1',
    usuarioId: 'usuario-1',
    usuarioEmail: 'admin@ejemplo.com',
    accion: 'MODIFICAR',
    entidad: 'puente',
    entidadId: 'puente-1',
    procesoAutomatico: null,
    creadoEn: '2026-10-06T14:00:00-06:00',
  };

  beforeEach(() => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({
        matches: false,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
      })),
    );
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TranslocoService, useValue: { translate: (key: string) => key } },
      ],
    });
    TestBed.overrideComponent(BitacoraPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(BitacoraPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    fixture.destroy();
    vi.unstubAllGlobals();
  });

  function responderPagina(
    content: RegistroAuditoria[] = [registro],
    number = 0,
    size = 20,
    totalElements = 41,
  ): void {
    http
      .expectOne((req) => req.url === '/api/v1/auditoria')
      .flush({
        content,
        number,
        size,
        totalElements,
        totalPages: Math.ceil(totalElements / size),
      });
    fixture.detectChanges();
  }

  it('Escenario: abrir la bitácora muestra la primera página y fechas de Guatemala', () => {
    // Dado el acceso a la bitácora
    const request = http.expectOne((req) => req.url === '/api/v1/auditoria');
    // Cuando se consulta la página inicial
    expect(request.request.params.get('pagina')).toBe('0');
    expect(request.request.params.get('tamanio')).toBe('20');
    request.flush({ content: [registro], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    fixture.detectChanges();
    // Entonces se muestran usuario, acción y hora local
    expect(fixture.nativeElement.textContent).toContain('admin@ejemplo.com');
    expect(fixture.nativeElement.textContent).toContain('06/10/2026');
    expect(fixture.nativeElement.textContent).toContain('14:00:00');
    expect(fixture.nativeElement.textContent).toContain('auditoria.acciones.MODIFICAR');
  });

  it('Escenario: combinar correo y días completos y conservarlos al paginar', () => {
    // Dado un listado y un correo con rango de fechas
    responderPagina();
    page.filtros.setValue({
      usuarioEmail: 'admin@ejemplo.com',
      desde: '2026-10-01',
      hasta: '2026-10-06',
    });
    http.expectNone((req) => req.url === '/api/v1/auditoria');
    // Cuando aplica filtros y luego pasa a la siguiente página
    page.aplicarFiltros();
    let request = http.expectOne((req) => req.url === '/api/v1/auditoria');
    expect(request.request.params.get('usuarioEmail')).toBe('admin@ejemplo.com');
    expect(request.request.params.get('desde')).toBe('2026-10-01T00:00:00-06:00');
    expect(request.request.params.get('hasta')).toBe('2026-10-06T23:59:59.999999-06:00');
    request.flush({ content: [registro], number: 0, size: 20, totalElements: 41, totalPages: 3 });
    page.filtros.controls.usuarioEmail.setValue('otro@ejemplo.com');
    page.cambiarPagina({ page: 1 });
    request = http.expectOne((req) => req.url === '/api/v1/auditoria');
    // Entonces se conserva la consulta aplicada, sin incorporar filtros aún no enviados
    expect(request.request.params.get('pagina')).toBe('1');
    expect(request.request.params.get('usuarioEmail')).toBe('admin@ejemplo.com');
    expect(request.request.params.get('desde')).toBe('2026-10-01T00:00:00-06:00');
    request.flush({ content: [], number: 1, size: 20, totalElements: 41, totalPages: 3 });
  });

  it('Escenario: cambiar tamaño reinicia la página y no ofrece más de 100 registros', () => {
    // Dado un listado y un tamaño de 100
    responderPagina();
    expect(Math.max(...page.tamanios)).toBe(100);
    page.tamanio.setValue(100);
    // Cuando cambia el tamaño
    page.cambiarTamanio();
    const request = http.expectOne((req) => req.url === '/api/v1/auditoria');
    // Entonces consulta desde la primera página con el tamaño indicado
    expect(request.request.params.get('pagina')).toBe('0');
    expect(request.request.params.get('tamanio')).toBe('100');
    request.flush({ content: [], number: 0, size: 100, totalElements: 0, totalPages: 0 });
  });

  it('Escenario: validar rango invertido y correo antes de consultar', () => {
    // Dado un rango invertido
    responderPagina();
    page.filtros.patchValue({ desde: '2026-10-07', hasta: '2026-10-06' });
    // Cuando se aplica, entonces no se envía la petición
    page.aplicarFiltros();
    expect(page.filtros.hasError('rangoFechas')).toBe(true);
    http.expectNone((req) => req.url === '/api/v1/auditoria');
    page.filtros.reset({ usuarioEmail: 'correo-invalido', desde: '', hasta: '' });
    page.aplicarFiltros();
    expect(page.filtros.controls.usuarioEmail.invalid).toBe(true);
    http.expectNone((req) => req.url === '/api/v1/auditoria');
  });

  it('Escenario: limpiar filtros elimina correo y fechas de la consulta', () => {
    // Dado filtros en el formulario
    responderPagina();
    page.filtros.patchValue({ usuarioEmail: 'admin@ejemplo.com', desde: '2026-10-01' });
    // Cuando los limpia, entonces vuelve al listado sin filtros
    page.limpiarFiltros();
    const request = http.expectOne((req) => req.url === '/api/v1/auditoria');
    expect(request.request.params.has('usuarioEmail')).toBe(false);
    expect(request.request.params.has('desde')).toBe(false);
    expect(page.filtros.controls.usuarioEmail.value).toBe('');
    request.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('Escenario: mostrar valores anteriores y posteriores en una tabla de texto segura', async () => {
    // Dado un registro seleccionado
    responderPagina();
    page.abrirDetalle(registro);
    // Cuando se obtiene el detalle
    http.expectOne('/api/v1/auditoria/registro-1').flush({
      ...registro,
      cambios: [
        { campo: 'nombre', anterior: 'Puente anterior', posterior: '<script>alert(1)</script>' },
        { campo: 'ruta', anterior: 'CA-9', posterior: null },
      ],
    });
    fixture.detectChanges();
    await fixture.whenStable();
    // Entonces se muestran las columnas legibles y los valores como texto, sin ejecutar HTML
    const tabla = document.querySelector('.cambios');
    expect(tabla?.textContent).toContain('auditoria.before');
    expect(tabla?.textContent).toContain('auditoria.after');
    expect(tabla?.textContent).toContain('Puente anterior');
    expect(tabla?.textContent).toContain('<script>alert(1)</script>');
    expect(tabla?.textContent).toContain('auditoria.noValue');
    expect(tabla?.querySelector('script')).toBeNull();
    expect(fixture.nativeElement.querySelector('pre')).toBeNull();
  });

  it('Escenario: agrupar una creación y conservar los datos técnicos en una sección cerrada', async () => {
    // Dado una creación con datos generales, ubicación, estado y campos técnicos
    responderPagina();
    page.abrirDetalle(registro);
    const cambios = [
      'nombre',
      'municipio.nombre',
      'municipio.id',
      'municipio.departamentoId',
      'departamento.id',
      'activo',
      'id',
      'creadoEn',
      'campoNuevo',
    ].map((campo) => ({ campo, anterior: null, posterior: `valor-${campo}` }));
    // Cuando se muestra el detalle
    http.expectOne('/api/v1/auditoria/registro-1').flush({ ...registro, accion: 'CREAR', cambios });
    fixture.detectChanges();
    await fixture.whenStable();
    // Entonces se ocultan los identificadores territoriales sin alterar el detalle recibido
    expect(page.gruposDetalle().flatMap((grupo) => grupo.cambios)).toHaveLength(cambios.length - 3);
    expect(page.detalle()?.cambios).toEqual(cambios);
    const tecnico = document.querySelector<HTMLDetailsElement>('[data-grupo="tecnicos"]');
    expect(tecnico?.open).toBe(false);
    for (const campo of ['municipio.id', 'municipio.departamentoId', 'departamento.id']) {
      expect(document.querySelector('.p-dialog')?.textContent).not.toContain(`valor-${campo}`);
    }
    expect(document.querySelector<HTMLDetailsElement>('[data-grupo="generales"]')?.open).toBe(true);
    expect(document.querySelector('[data-grupo="otros"]')?.textContent).toContain(
      'valor-campoNuevo',
    );
    // Cuando se despliega la sección, entonces sus valores permanecen disponibles
    tecnico!.open = true;
    expect(tecnico?.textContent).toContain('valor-creadoEn');
    expect(page.nombreCampo('municipio.nombre')).toBe(
      'auditoria.campos.municipio · auditoria.campos.nombre',
    );
  });

  it('Escenario: identificar acciones ejecutadas por un proceso automático', () => {
    // Dado un registro sin usuario y con la marca del proceso
    // Cuando se muestra el listado, entonces se identifica al proceso
    responderPagina([
      { ...registro, usuarioId: null, usuarioEmail: null, procesoAutomatico: 'provision-admin' },
    ]);
    expect(fixture.nativeElement.textContent).toContain('provision-admin');
    expect(fixture.nativeElement.textContent).toContain('auditoria.automatic');
  });

  it('Escenario: una respuesta 403 muestra el acceso restringido y elimina el listado', () => {
    // Dado una consulta con acceso revocado
    http
      .expectOne((req) => req.url === '/api/v1/auditoria')
      .flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    // Entonces la interfaz muestra la restricción sin conservar registros
    expect(page.pagina()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('auditoria.forbidden');
    expect(fixture.nativeElement.textContent).not.toContain('auditoria.retry');
  });

  it('Escenario: un fallo temporal permite reintentar el listado', () => {
    // Dado una consulta que falla
    http
      .expectOne((req) => req.url === '/api/v1/auditoria')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    expect(page.errorConsulta()).toBe('consulta');
    // Cuando reintenta, entonces muestra la página obtenida
    page.cargarPagina();
    responderPagina();
    expect(page.errorConsulta()).toBeNull();
    expect(page.pagina()?.content).toHaveLength(1);
  });

  it('Escenario: cerrar el detalle descarta una respuesta que llega después', () => {
    // Dado un detalle pendiente
    responderPagina();
    page.abrirDetalle(registro);
    const request = http.expectOne('/api/v1/auditoria/registro-1');
    // Cuando se cierra antes de la respuesta
    page.cerrarDetalle();
    request.flush({ ...registro, cambios: [] });
    // Entonces el detalle permanece cerrado y vacío
    expect(page.mostrarDetalle()).toBe(false);
    expect(page.detalle()).toBeNull();
  });
});
