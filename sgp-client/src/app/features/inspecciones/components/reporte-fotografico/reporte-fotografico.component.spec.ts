import { TestBed, ComponentFixture } from '@angular/core/testing';
import { signal } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../../../../core/services/auth.store';
import { FotografiasLocalesService } from '../../../../offline/services/fotografias-locales.service';
import { InspeccionApiService } from '../../services/inspeccion-api.service';
import { ProcesadorFotografiasService } from '../../services/procesador-fotografias.service';
import { ReporteFotograficoComponent } from './reporte-fotografico.component';
describe('Reporte fotográfico', () => {
  let fixture: ComponentFixture<ReporteFotograficoComponent>;
  const auth = { user: signal<{ id: string } | null>({ id: 'autor' }) };
  const api = {
    listarFotografias: vi.fn(),
    consultarEvidenciaFotografica: vi.fn(),
    contenidoFotografia: vi.fn(),
    adjuntarFotografia: vi.fn(),
  };
  const locales = {
    guardarContexto: vi.fn(),
    guardar: vi.fn(),
    pendientes: vi.fn(),
    confirmar: vi.fn(),
    registrarError: vi.fn(),
  };
  beforeEach(async () => {
    auth.user.set({ id: 'autor' });
    Object.values(api).forEach((f) => f.mockReset());
    Object.values(locales).forEach((f) => f.mockReset());
    api.listarFotografias.mockReturnValue(of([]));
    api.consultarEvidenciaFotografica.mockReturnValue(
      of({ valida: false, totalDisponibles: 0, maximoPermitido: 60, faltantes: [] }),
    );
    locales.registrarError.mockResolvedValue(undefined);
    locales.pendientes.mockResolvedValue([]);
    locales.guardar.mockResolvedValue(undefined);
    locales.confirmar.mockResolvedValue(undefined);
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn().mockReturnValue('blob:prueba'),
      revokeObjectURL: vi.fn(),
    });
    await TestBed.configureTestingModule({
      imports: [
        ReporteFotograficoComponent,
        TranslocoTestingModule.forRoot({
          langs: { es: {} },
          translocoConfig: { availableLangs: ['es'], defaultLang: 'es' },
        }),
      ],
      providers: [
        { provide: AuthStore, useValue: auth },
        { provide: InspeccionApiService, useValue: api },
        { provide: FotografiasLocalesService, useValue: locales },
        { provide: ProcesadorFotografiasService, useValue: {} },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ReporteFotograficoComponent);
    fixture.componentRef.setInput('inspeccion', {
      id: 'inspeccion',
      autorId: 'autor',
      estado: 'BORRADOR',
    });
    fixture.detectChanges();
    await fixture.whenStable();
  });
  afterEach(() => {
    fixture.destroy();
    vi.unstubAllGlobals();
  });
  it('persiste antes de enviar y reintenta con exactamente la misma operación', async () => {
    const c = fixture.componentInstance;
    const blob = new Blob(['jpeg'], { type: 'image/jpeg' });
    c.borrador.set({
      blob,
      latitud: 14,
      longitud: -90,
      capturadoEn: '2026-10-14T12:00:00Z',
      orientacion: 1,
    });
    c.categoria.setValue('ACCESO');
    api.adjuntarFotografia.mockReturnValue(throwError(() => ({ status: 0 })));
    await c.adjuntar();
    expect(c.pendientes()).toHaveLength(1);
    const pendiente = c.pendientes()[0];
    expect(pendiente.id[14]).toBe('7');
    expect(locales.guardar.mock.invocationCallOrder[0]).toBeLessThan(
      api.adjuntarFotografia.mock.invocationCallOrder[0],
    );
    api.adjuntarFotografia.mockReturnValue(of({ id: pendiente.id }));
    await c.reintentar(pendiente);
    expect(api.adjuntarFotografia.mock.calls[0]).toEqual(api.adjuntarFotografia.mock.calls[1]);
    expect(locales.confirmar).toHaveBeenCalledWith(pendiente.id, 'autor');
  });
  it('no envía una fotografía sin categoría', async () => {
    fixture.componentInstance.borrador.set({
      blob: new Blob(),
      latitud: 14,
      longitud: -90,
      capturadoEn: '2026-10-14T12:00:00Z',
      orientacion: 1,
    });
    await fixture.componentInstance.adjuntar();
    expect(api.adjuntarFotografia).not.toHaveBeenCalled();
  });
  it('limpia fotos y previsualizaciones cuando cambia la sesión', async () => {
    fixture.componentInstance.previsualizacion.set('blob:prueba');
    auth.user.set({ id: 'otro' });
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.previsualizacion()).toBeNull();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:prueba');
    expect(fixture.componentInstance.pendientes()).toEqual([]);
  });
});
