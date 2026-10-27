import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Pipe, PipeTransform, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { UserSession } from '../../../../core/models/auth.models';
import { AuthStore } from '../../../../core/services/auth.store';
import { PuenteResponse } from '../../../puentes/models/puente.models';
import { MantenimientoPuenteComponent } from './mantenimiento-puente.component';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

const BASE = '/api/v1/ordenes-mantenimiento';
const PUENTE = { id: 'puente-1', nombre: 'Puente El Trébol', activo: true } as PuenteResponse;
const ORDEN = {
  id: 'orden-1',
  puenteId: 'puente-1',
  descripcion: 'Reparar barandal',
  tipo: 'CORRECTIVO',
  estado: 'PROGRAMADA',
  prioridadConfirmada: 'ALTA',
  creadoEn: '2026-10-20T15:00:00Z',
  fechaProgramada: '2026-10-25',
  motivoCierreSinEjecucion: null,
  reglaProvisional: null,
};

describe('MantenimientoPuenteComponent', () => {
  let fixture: ComponentFixture<MantenimientoPuenteComponent>;
  let componente: MantenimientoPuenteComponent;
  let http: HttpTestingController;
  const usuario = signal<UserSession | null>(null);

  async function crear(rol: string): Promise<void> {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe = vi.fn();
        unobserve = vi.fn();
        disconnect = vi.fn();
      },
    );
    usuario.set({ role: rol } as UserSession);
    await TestBed.configureTestingModule({
      imports: [MantenimientoPuenteComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthStore, useValue: { user: usuario } },
        { provide: TranslocoService, useValue: { translate: (clave: string) => clave } },
      ],
    })
      .overrideComponent(MantenimientoPuenteComponent, {
        remove: { imports: [TranslocoPipe] },
        add: { imports: [TraduccionTestPipe] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(MantenimientoPuenteComponent);
    componente = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('puente', PUENTE);
    fixture.detectChanges();
  }

  function listados(abiertas: unknown[], cerradas: unknown[] = []): void {
    const pendientes = http.match((r) => r.url === BASE);
    expect(pendientes).toHaveLength(2);
    for (const request of pendientes) {
      expect(request.request.params.get('puenteId')).toBe('puente-1');
      const abiertasPedidas = request.request.params.getAll('estado')?.includes('PROPUESTA');
      const contenido = abiertasPedidas ? abiertas : cerradas;
      request.flush({ content: contenido, totalElements: contenido.length, number: 0 });
    }
    fixture.detectChanges();
  }

  afterEach(() => {
    http.verify();
    vi.unstubAllGlobals();
  });

  it('un estudiante ve las órdenes abiertas sin acciones', async () => {
    await crear('ESTUDIANTE');
    listados([ORDEN]);

    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.textContent).toContain('Reparar barandal');
    expect(elemento.textContent).toContain('mantenimiento.ordenes.estados.PROGRAMADA');
    expect(elemento.textContent).not.toContain('mantenimiento.puente.nuevaOrden');
    expect(elemento.querySelector('a[href="/mantenimiento/ordenes/orden-1"]')).toBeNull();
  });

  it('separa las abiertas del historial de cerradas', async () => {
    await crear('PROFESIONAL_EXTERNO');
    const pendientes = http.match((r) => r.url === BASE);
    expect(pendientes.map((r) => r.request.params.getAll('estado'))).toEqual([
      ['PROPUESTA', 'PROGRAMADA', 'EN_EJECUCION'],
      ['EJECUTADA', 'DESCARTADA', 'CANCELADA'],
    ]);
    pendientes.forEach((r) => r.flush({ content: [], totalElements: 0, number: 0 }));
  });

  it('el catedrático crea una orden manual que aparece como propuesta', async () => {
    await crear('CATEDRATICO');
    listados([]);
    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.textContent).toContain('mantenimiento.puente.nuevaOrden');

    componente.abrirNuevaOrden();
    http.expectOne('/api/v1/inspecciones/puentes/puente-1/publicadas?pagina=0&tamanio=100').flush({
      content: [{ id: 'inspeccion-1', fechaInspeccion: '2026-10-01', nombreAutor: 'A' }],
    });
    componente.nuevoTipo.set('CORRECTIVO');
    componente.nuevaPrioridad.set('ALTA');
    componente.nuevaDescripcion.set('  Reparar barandal  ');
    componente.nuevaInspeccion.set('inspeccion-1');
    componente.crearOrden();

    const request = http.expectOne((r) => r.url === BASE && r.method === 'POST');
    expect(request.request.body).toEqual({
      puenteId: 'puente-1',
      tipo: 'CORRECTIVO',
      prioridad: 'ALTA',
      descripcion: 'Reparar barandal',
      elementoRef: null,
      inspeccionOrigenId: 'inspeccion-1',
    });
    request.flush({ ...ORDEN, estado: 'PROPUESTA' });

    expect(componente.creando()).toBe(false);
    expect(componente.ordenCreada()).toBe(true);
    http
      .expectOne((r) => r.url === BASE && r.method === 'GET')
      .flush({ content: [{ ...ORDEN, estado: 'PROPUESTA' }], totalElements: 1, number: 0 });
  });

  it('muestra junto a cada campo los errores del servidor al crear', async () => {
    await crear('ADMINISTRADOR');
    listados([]);
    componente.abrirNuevaOrden();
    http.expectOne((r) => r.url.includes('/publicadas')).flush({ content: [] });
    componente.nuevoTipo.set('CORRECTIVO');
    componente.nuevaPrioridad.set('ALTA');
    componente.nuevaDescripcion.set('Reparar');
    componente.nuevoElemento.set('/no/existe');
    componente.crearOrden();

    http
      .expectOne((r) => r.url === BASE && r.method === 'POST')
      .flush(
        {
          detail: 'Solicitud invalida',
          errores: [{ campo: 'elementoRef', mensaje: 'El elemento no existe en el formulario.' }],
        },
        { status: 422, statusText: 'Unprocessable Entity' },
      );

    expect(componente.erroresCampo()['elementoRef']).toBe(
      'El elemento no existe en el formulario.',
    );
    expect(componente.errorCrear()).toBeNull();
    expect(componente.creando()).toBe(true);
  });
});
