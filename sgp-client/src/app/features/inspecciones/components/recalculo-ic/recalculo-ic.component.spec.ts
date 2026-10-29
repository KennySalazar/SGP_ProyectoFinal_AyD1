import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthStore } from '../../../../core/services/auth.store';
import { traduccionesDePrueba } from '../../../formulario/motor/testing/traducciones';
import {
  ComparacionIcResponse,
  ResultadoIcHistorialResponse,
} from '../../models/recalculo-ic.models';
import { RecalculoIcComponent } from './recalculo-ic.component';

const original: ResultadoIcHistorialResponse = {
  id: 'o1',
  inspeccionId: 'i1',
  tipo: 'PUBLICACION',
  versionConfiguracionId: 'v1',
  versionConfiguracionCodigo: 'SIECA-v1',
  indiceCalculado: 0,
  estadoPorRango: 'MALO',
  estadoCalculado: 'MALO',
  estadoConfirmado: 'REGULAR',
  justificacionAjuste: 'Criterio original',
  condicionesAnulacion: [],
  desglose: {},
  hashEntrada: 'hash',
  versionAlgoritmo: 'v1',
  solicitadoPorId: 'autor',
  solicitadoPorNombre: 'Mario',
  calculadoEn: '2026-10-25T02:00:00Z',
  rotulo: 'IC original',
};
const recalculo: ResultadoIcHistorialResponse = {
  ...original,
  id: 'r1',
  tipo: 'RECALCULO',
  versionConfiguracionId: 'v2',
  versionConfiguracionCodigo: 'SIECA-v2',
  indiceCalculado: 25,
  estadoConfirmado: 'MALO',
  solicitadoPorNombre: 'Kenny',
  justificacionAjuste: null,
};
const comparacion = (): ComparacionIcResponse => ({
  inspeccionId: 'i1',
  original: structuredClone(original),
  recalculos: [],
});

describe('Comparacion y recalculo del IC HU071', () => {
  let fixture: ComponentFixture<RecalculoIcComponent>;
  let http: HttpTestingController;
  const usuario = signal<{ role: string } | null>({ role: 'CATEDRATICO' });
  const html = () => fixture.nativeElement as HTMLElement;
  const ruta = '/api/v1/inspecciones/i1';
  async function renderizar() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }
  async function abrir(h = comparacion()) {
    fixture.componentRef.setInput('inspeccionId', 'i1');
    await renderizar();
    http.expectOne(ruta + '/resultados-ic').flush(h);
    await renderizar();
  }
  beforeEach(() => {
    usuario.set({ role: 'CATEDRATICO' });
    TestBed.configureTestingModule({
      imports: [RecalculoIcComponent, traduccionesDePrueba()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthStore, useValue: { user: usuario } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RecalculoIcComponent);
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  it.each(['CATEDRATICO', 'ADMINISTRADOR'])('permite solicitar a %s', async (role) => {
    usuario.set({ role });
    await abrir();
    expect(html().querySelector('[data-testid="recalcular-ic"]')).not.toBeNull();
    fixture.componentInstance.recalcular();
    const req = http.expectOne(ruta + '/recalculos-ic');
    expect(req.request.method).toBe('POST');
    req.flush(recalculo);
    await renderizar();
    expect(html().querySelectorAll('tbody tr').length).toBe(2);
    expect(fixture.componentInstance.historial()?.original).toEqual(original);
    expect(html().textContent).toContain('Kenny');
  });
  it('muestra el IC del recálculo con dos decimales', async () => {
    usuario.set({ role: 'CATEDRATICO' });
    await abrir();
    fixture.componentInstance.recalcular();
    http.expectOne(ruta + '/recalculos-ic').flush({ ...recalculo, indiceCalculado: 89.65517 });
    await renderizar();
    expect(html().textContent).toContain('89.66');
    expect(html().textContent).not.toContain('89.65517');
  });
  it.each(['ESTUDIANTE', 'PROFESIONAL_EXTERNO', null])(
    'oculta y bloquea la accion para %s',
    async (role) => {
      usuario.set(role ? { role } : null);
      await abrir();
      expect(html().querySelector('[data-testid="recalcular-ic"]')).toBeNull();
      fixture.componentInstance.recalcular();
      http.expectNone(ruta + '/recalculos-ic');
      expect(html().querySelectorAll('tbody tr').length).toBe(1);
    },
  );
  it('muestra IC cero, estado original confirmado, justificacion y fecha de Guatemala', async () => {
    await abrir();
    expect(html().textContent).toContain('0.00');
    expect(html().textContent).toContain('Criterio original');
    expect(html().textContent).toContain('24/10/2026 20:00:00');
    expect(html().textContent).toContain('Todavía no hay recálculos');
  });
  it('bloquea doble clic mientras espera y conserva el solicitante al repetir', async () => {
    await abrir({ ...comparacion(), recalculos: [recalculo] });
    fixture.componentInstance.recalcular();
    fixture.componentInstance.recalcular();
    await renderizar();
    expect(
      (html().querySelector('[data-testid="recalcular-ic"]') as HTMLButtonElement).disabled,
    ).toBe(true);
    http.expectOne(ruta + '/recalculos-ic').flush(recalculo);
    await renderizar();
    expect(fixture.componentInstance.historial()?.recalculos).toEqual([recalculo]);
    expect(html().textContent).toContain('ya estaba registrado');
  });
  it.each([403, 409, 422, 0])(
    'conserva los resultados ante error HTTP %s y permite reintentar',
    async (status) => {
      await abrir();
      fixture.componentInstance.recalcular();
      http
        .expectOne(ruta + '/recalculos-ic')
        .flush({ detail: 'No se puede recalcular' }, { status, statusText: 'Error' });
      await renderizar();
      expect(html().querySelector('[role="alert"]')).not.toBeNull();
      expect(fixture.componentInstance.historial()?.original).toEqual(original);
      expect(fixture.componentInstance.recalculando()).toBe(false);
      fixture.componentInstance.recalcular();
      http.expectOne(ruta + '/recalculos-ic').flush(recalculo);
    },
  );
  it('permite reintentar el historial sin bloquear el detalle publicado', async () => {
    fixture.componentRef.setInput('inspeccionId', 'i1');
    await renderizar();
    http
      .expectOne(ruta + '/resultados-ic')
      .flush({ detail: 'Historial no disponible' }, { status: 500, statusText: 'Error' });
    await renderizar();
    expect(html().textContent).toContain('Historial no disponible');
    fixture.componentInstance.cargar();
    http.expectOne(ruta + '/resultados-ic').flush(comparacion());
    await renderizar();
    expect(fixture.componentInstance.historial()?.original.id).toBe('o1');
  });
  it('cancela consultas de la inspeccion anterior al cambiar la ruta', async () => {
    fixture.componentRef.setInput('inspeccionId', 'i1');
    await renderizar();
    const anterior = http.expectOne(ruta + '/resultados-ic');
    fixture.componentRef.setInput('inspeccionId', 'i2');
    await renderizar();
    expect(anterior.cancelled).toBe(true);
    http
      .expectOne('/api/v1/inspecciones/i2/resultados-ic')
      .flush({ ...comparacion(), inspeccionId: 'i2' });
  });
  it('cancela el POST pendiente al salir y no mezcla resultados entre inspecciones', async () => {
    await abrir();
    fixture.componentInstance.recalcular();
    const anterior = http.expectOne(ruta + '/recalculos-ic');
    fixture.componentRef.setInput('inspeccionId', 'i2');
    await renderizar();
    expect(anterior.cancelled).toBe(true);
    expect(fixture.componentInstance.historial()).toBeNull();
    http
      .expectOne('/api/v1/inspecciones/i2/resultados-ic')
      .flush({ ...comparacion(), inspeccionId: 'i2' });
    await renderizar();
    expect(fixture.componentInstance.historial()?.recalculos.length).toBe(0);
  });
});
