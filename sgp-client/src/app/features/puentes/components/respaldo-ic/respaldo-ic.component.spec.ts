import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { traduccionesDePrueba } from '../../../formulario/motor/testing/traducciones';
import { RespaldoIcComponent } from './respaldo-ic.component';

describe('Respaldo del IC vigente del puente', () => {
  let fixture: ComponentFixture<RespaldoIcComponent>;
  let http: HttpTestingController;
  const puente = {
    id: 'puente-1',
    activo: true,
    indiceCondicionActual: 0,
    versionFormularioIdIcVigente: 'version-historica',
  };
  const resumen = {
    versionFormularioId: 'version-historica',
    estado: 'APROBADA',
    rotuloCalibracion: 'Aprobada por Mario el 22/10/2026',
    aprobadoPorId: 'catedratico-1',
    aprobadoPorNombre: 'Mario',
    aprobadoEn: '2026-10-23T02:00:00Z',
  };
  const ruta = '/api/v1/puentes/puente-1/estado-calibracion';
  const html = () => fixture.nativeElement as HTMLElement;
  async function mostrar(valor: unknown = puente) {
    fixture.componentRef.setInput('puente', valor);
    fixture.detectChanges();
    await fixture.whenStable();
  }
  async function renderizar() {
    fixture.detectChanges();
    await fixture.whenStable();
  }
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RespaldoIcComponent, traduccionesDePrueba()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RespaldoIcComponent);
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  it('consulta sin sesion el resumen publico de la version historica y acepta IC cero', async () => {
    await mostrar();
    const req = http.expectOne(ruta);
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(resumen);
    await renderizar();
    expect(html().textContent).toContain(resumen.rotuloCalibracion);
    expect(html().querySelectorAll('button, textarea').length).toBe(0);
  });

  it('muestra provisional solo cuando el servidor confirma que no esta aprobada', async () => {
    await mostrar();
    http.expectOne(ruta).flush({
      ...resumen,
      estado: 'SIN_APROBAR',
      rotuloCalibracion: 'Calibracion provisional · sin aprobar',
    });
    await renderizar();
    expect(html().textContent).toContain('Calibracion provisional · sin aprobar');
  });

  it('no consulta ni muestra respaldo para un puente sin IC', async () => {
    await mostrar({ ...puente, indiceCondicionActual: null });
    http.expectNone(ruta);
    expect(html().querySelector('[data-testid="respaldo-ic"]')).toBeNull();
  });

  it('tolera datos antiguos del cache sin version y no supone una aprobacion', async () => {
    await mostrar({ ...puente, versionFormularioIdIcVigente: undefined });
    http.expectNone(ruta);
    expect(html().textContent).toContain('Respaldo de calibracion no disponible');
    expect(html().textContent).not.toContain('sin aprobar');
  });

  it('no consulta el endpoint publico para puentes inactivos', async () => {
    await mostrar({ ...puente, activo: false });
    http.expectNone(ruta);
    expect(html().textContent).toContain('no disponible');
  });

  it('rechaza el respaldo de otra version si cambio el IC durante la consulta', async () => {
    await mostrar();
    http.expectOne(ruta).flush({ ...resumen, versionFormularioId: 'otra-version' });
    await renderizar();
    expect(html().textContent).toContain('Recargue el puente');
    expect(html().textContent).not.toContain('Aprobada por');
  });

  it('no convierte una respuesta vacia en calibracion provisional', async () => {
    await mostrar();
    http.expectOne(ruta).flush(null);
    await renderizar();
    expect(html().textContent).toContain('Recargue el puente');
    expect(html().textContent).not.toContain('sin aprobar');
  });

  it('permite reintentar un fallo de red sin mostrar una aprobacion supuesta', async () => {
    await mostrar();
    http.expectOne(ruta).flush('Error', { status: 503, statusText: 'Unavailable' });
    await renderizar();
    expect(html().textContent).toContain('No se pudo consultar');
    expect(html().textContent).not.toContain('sin aprobar');
    html().querySelector('button')!.click();
    await renderizar();
    http.expectOne(ruta).flush(resumen);
    await renderizar();
    expect(html().textContent).toContain(resumen.rotuloCalibracion);
  });

  it('cancela la consulta anterior cuando cambia el puente', async () => {
    await mostrar();
    const anterior = http.expectOne(ruta);
    await mostrar({ ...puente, id: 'puente-2', versionFormularioIdIcVigente: 'v2' });
    expect(anterior.cancelled).toBe(true);
    http.expectOne('/api/v1/puentes/puente-2/estado-calibracion').flush({
      ...resumen,
      versionFormularioId: 'v2',
    });
    await renderizar();
    expect(html().textContent).toContain(resumen.rotuloCalibracion);
  });

  it('retira el respaldo anterior al actualizar el puente a otra version', async () => {
    await mostrar();
    http.expectOne(ruta).flush(resumen);
    await renderizar();
    await mostrar({ ...puente, versionFormularioIdIcVigente: 'v2' });
    expect(html().textContent).not.toContain(resumen.rotuloCalibracion);
    http.expectOne(ruta).flush({
      ...resumen,
      versionFormularioId: 'v2',
      rotuloCalibracion: 'Calibracion provisional · sin aprobar',
    });
    await renderizar();
    expect(html().textContent).toContain('sin aprobar');
  });
});
