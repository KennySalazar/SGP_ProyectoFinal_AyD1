import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, Pipe, PipeTransform, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../../app.routes';
import { ApiErrorService } from '../../core/services/api-error.service';
import { AuthStore } from '../../core/services/auth.store';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';
import { TokenRefreshService } from '../../core/services/token-refresh.service';
import { AppShellComponent } from '../../layouts/app-shell/app-shell.component';
import { ConnectivityService } from '../../offline/services/connectivity.service';
import { CatalogoPuentesPage } from './pages/catalogo-puentes/catalogo-puentes.page';
import { RegistrarPuentePage } from './pages/registrar-puente/registrar-puente.page';
import { PuenteApiService } from './services/puente-api.service';
import { EstadoPuente } from './models/puente.models';

@Component({ selector: 'app-destino-test', template: '' })
class DestinoTest {}

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('Rutas públicas y protegidas de puentes', () => {
  const usuario = signal<{ role: string } | null>(null);

  beforeEach(() => {
    usuario.set(null);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'login', component: DestinoTest },
          { path: '', pathMatch: 'full', component: DestinoTest },
          ...routes,
        ]),
        {
          provide: AuthStore,
          useValue: { user: usuario, authenticated: () => usuario() !== null },
        },
        { provide: ConnectivityService, useValue: { online: signal(true) } },
        { provide: TranslocoService, useValue: { translate: (key: string) => key } },
        { provide: ApiErrorService, useValue: {} },
        {
          provide: PuenteApiService,
          useValue: {
            listarCatalogo: () => of({ content: [], totalElements: 0 }),
            listarDepartamentos: () => of({ content: [] }),
          },
        },
      ],
    });
    TestBed.overrideComponent(CatalogoPuentesPage, { set: { template: '' } });
    TestBed.overrideComponent(RegistrarPuentePage, { set: { template: '' } });
    TestBed.overrideComponent(AppShellComponent, { set: { template: '<router-outlet />' } });
  });

  it('permite abrir el catálogo sin sesión', async () => {
    const harness = await RouterTestingHarness.create();
    const page = await harness.navigateByUrl('/puentes', CatalogoPuentesPage);
    expect(page.pagina()?.totalElements).toBe(0);
    expect(TestBed.inject(Router).url).toBe('/puentes');
  });

  it('redirige al login cuando un visitante intenta registrar', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/puentes/nuevo');
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('conserva el acceso al registro para administradores', async () => {
    usuario.set({ role: 'ADMINISTRADOR' });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/puentes/nuevo');
    expect(TestBed.inject(Router).url).toBe('/puentes/nuevo');
    expect(harness.routeNativeElement?.querySelector('app-registrar-puente-page')).not.toBeNull();
  });

  it.each(['ESTUDIANTE', 'CATEDRATICO', 'PROFESIONAL_EXTERNO'])(
    'permite catálogo pero no registro al rol %s',
    async (role) => {
      usuario.set({ role });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/puentes', CatalogoPuentesPage);
      await harness.navigateByUrl('/puentes/nuevo');
      expect(TestBed.inject(Router).url).toBe('/');
    },
  );
});

describe('Lista integrada en el catálogo público sin sesión', () => {
  let http: HttpTestingController;
  const usuario = signal<{ role: string; email: string } | null>(null);
  const refresh = vi.fn();
  const estados: EstadoPuente[] = ['Bueno', 'Regular', 'Malo', 'Sin evaluar'];
  const puentes = estados.map((estadoActual, index) => ({
    id: `puente-${index}`,
    codigo: `P-${index}`,
    nombre: `Puente público ${index}`,
    departamento: { id: 'departamento-1', codigoIne: '01', nombre: 'Guatemala' },
    municipio: {
      id: 'municipio-1',
      departamentoId: 'departamento-1',
      codigoIne: '0114',
      nombre: 'Amatitlán',
    },
    latitud: null,
    longitud: null,
    activo: true,
    estadoActual,
    fotografias: ['foto-privada.jpg'],
    danos: 'Daño privado',
  }));

  beforeEach(() => {
    refresh.mockReset();
    usuario.set(null);
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        {
          provide: AuthStore,
          useValue: {
            user: usuario,
            authenticated: () => usuario() !== null,
            accessToken: () => (usuario() ? 'token-test' : null),
          },
        },
        { provide: TokenRefreshService, useValue: { refresh } },
      ],
    });
    TestBed.overrideComponent(CatalogoPuentesPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    TestBed.overrideComponent(AppShellComponent, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    expect(refresh).not.toHaveBeenCalled();
  });

  async function abrirCatalogo() {
    const harness = await RouterTestingHarness.create();
    const page = await harness.navigateByUrl('/puentes', CatalogoPuentesPage);
    const departamentos = http.expectOne((req) => req.url === '/api/v1/catalogos/departamentos');
    expect(departamentos.request.headers.has('Authorization')).toBe(usuario() !== null);
    departamentos.flush({ content: [puentes[0].departamento] });
    return { harness, page };
  }

  function responderCatalogo(number = 0, content = puentes, totalElements = 24) {
    const request = http.expectOne((req) => req.url === '/api/v1/puentes');
    expect(request.request.headers.has('Authorization')).toBe(usuario() !== null);
    request.flush({
      content,
      number,
      size: 20,
      totalElements,
      totalPages: Math.ceil(totalElements / 20),
      first: number === 0,
      last: (number + 1) * 20 >= totalElements,
      empty: content.length === 0,
    });
    return request.request;
  }

  it('renderiza la lista pública con los cuatro estados sin mostrar datos privados', async () => {
    const { harness } = await abrirCatalogo();
    responderCatalogo();
    harness.detectChanges();
    const element = harness.routeNativeElement!;
    expect(TestBed.inject(Router).url).toBe('/puentes');
    expect(element.querySelectorAll('h1')).toHaveLength(1);
    expect(element.querySelectorAll('tbody tr')).toHaveLength(4);
    estados.forEach((estado) =>
      expect(element.querySelector('tbody')?.textContent).toContain(estado),
    );
    expect(element.querySelector('img')).toBeNull();
    expect(element.textContent).not.toContain('foto-privada.jpg');
    expect(element.textContent).not.toContain('Daño privado');
    expect(element.querySelector('p-paginator')).not.toBeNull();
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/login');
  });

  it('integra filtros y paginación sin consultar nuevamente al pulsar el mapa pendiente', async () => {
    const { harness, page } = await abrirCatalogo();
    responderCatalogo();
    page.filtros.patchValue({ departamentoId: 'departamento-1', estado: 'Sin evaluar' });
    harness.detectChanges();
    harness
      .routeNativeElement!.querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const filtrado = responderCatalogo(0, [puentes[3]]);
    expect(filtrado.params.get('departamentoId')).toBe('departamento-1');
    expect(filtrado.params.get('estado')).toBe('Sin evaluar');
    harness.detectChanges();
    (harness.routeNativeElement!.querySelector('.p-paginator-next') as HTMLButtonElement).click();
    const siguiente = responderCatalogo(1, [puentes[3]]);
    expect(siguiente.params.get('pagina')).toBe('1');
    expect(siguiente.params.get('estado')).toBe('Sin evaluar');
    harness.detectChanges();
    const selector =
      harness.routeNativeElement!.querySelectorAll<HTMLButtonElement>('.selector button');
    selector[1].click();
    harness.detectChanges();
    expect(selector[1].disabled).toBe(true);
    expect(harness.routeNativeElement!.querySelector('p-table')).not.toBeNull();
    selector[0].click();
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('tbody')?.textContent).toContain(
      'Puente público 3',
    );
    expect(page.pagina()?.number).toBe(1);
    expect(page.filtros.getRawValue().estado).toBe('Sin evaluar');
    http.expectNone((req) => req.url === '/api/v1/puentes');
    (
      harness.routeNativeElement!.querySelector(
        '.filtros-acciones button[type="button"]',
      ) as HTMLButtonElement
    ).click();
    const limpio = responderCatalogo(0, [], 0);
    expect(limpio.params.keys().sort()).toEqual(['pagina', 'tamanio']);
    expect(limpio.params.get('pagina')).toBe('0');
    harness.detectChanges();
    expect(
      harness.routeNativeElement!.querySelector('tbody [role="status"]')?.textContent,
    ).toContain('puentes.catalogo.emptyCatalog');
  });

  it.each(['ADMINISTRADOR', 'ESTUDIANTE', 'CATEDRATICO', 'PROFESIONAL_EXTERNO'])(
    'muestra el layout y acceso al catálogo para %s sin pedir iniciar sesión',
    async (role) => {
      usuario.set({ role, email: 'usuario@example.test' });
      const { harness } = await abrirCatalogo();
      responderCatalogo();
      harness.detectChanges();
      const element = harness.routeNativeElement!;
      expect(element.querySelectorAll('app-shell')).toHaveLength(1);
      expect(element.querySelectorAll('main')).toHaveLength(1);
      expect(element.querySelector('a[href="/login"]')).toBeNull();
      const enlace = element.querySelector('nav a[href="/puentes"]');
      expect(enlace?.textContent).toContain('nav.bridgeCatalog');
      expect(enlace?.classList.contains('active')).toBe(true);
      expect(element.querySelector('.user-data')?.textContent).toContain('usuario@example.test');
      expect(element.querySelector('header.page-heading a[href="/puentes/nuevo"]') !== null).toBe(
        role === 'ADMINISTRADOR',
      );
      expect(element.querySelector('nav a[href="/puentes/nuevo"]') !== null).toBe(
        role === 'ADMINISTRADOR',
      );
      expect(element.querySelector('p-table')).not.toBeNull();
    },
  );

  it('vuelve a la presentación pública si la sesión se pierde sin duplicar consultas', async () => {
    usuario.set({ role: 'ESTUDIANTE', email: 'usuario@example.test' });
    const { harness } = await abrirCatalogo();
    responderCatalogo();
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('app-shell')).not.toBeNull();
    usuario.set(null);
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('app-shell')).toBeNull();
    expect(harness.routeNativeElement!.querySelector('a[href="/login"]')).not.toBeNull();
    expect(harness.routeNativeElement!.querySelectorAll('main')).toHaveLength(1);
    expect(harness.routeNativeElement!.querySelector('p-table')).not.toBeNull();
    http.expectNone((req) => req.url === '/api/v1/puentes');
  });

  it('permite reintentar errores sin sesión ni solicitudes de renovación', async () => {
    const { harness } = await abrirCatalogo();
    http
      .expectOne((req) => req.url === '/api/v1/puentes')
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/puentes');
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).not.toBeNull();
    expect(harness.routeNativeElement!.querySelector('p-table')).toBeNull();
    (
      harness.routeNativeElement!.querySelector(
        'button:not(.selector button):not(.filtros button)',
      ) as HTMLButtonElement
    ).click();
    responderCatalogo();
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('p-table')).not.toBeNull();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).toBeNull();
  });
});
