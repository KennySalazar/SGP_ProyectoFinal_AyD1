import { TestBed, ComponentFixture } from '@angular/core/testing';
import { signal } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../../../../core/services/auth.store';
import { FotografiasLocalesService } from '../../../../offline/services/fotografias-locales.service';
import { InspeccionApiService } from '../../services/inspeccion-api.service';
import { ProcesadorFotografiasService } from '../../services/procesador-fotografias.service';
import { FotografiaResponse } from '../../models/fotografia.models';
import { ReporteFotograficoComponent } from './reporte-fotografico.component';
describe('Reporte fotográfico', () => {
  let fixture: ComponentFixture<ReporteFotograficoComponent>;
  const auth = { user: signal<{ id: string } | null>({ id: 'autor' }) };
  const api = {
    listarFotografias: vi.fn(),
    contenidoFotografia: vi.fn(),
    adjuntarFotografia: vi.fn(),
    retirarFotografia: vi.fn(),
  };
  const locales = {
    guardarContexto: vi.fn(),
    guardar: vi.fn(),
    pendientes: vi.fn(),
    confirmar: vi.fn(),
  };
  beforeEach(async () => {
    auth.user.set({ id: 'autor' });
    Object.values(api).forEach((f) => f.mockReset());
    Object.values(locales).forEach((f) => f.mockReset());
    api.listarFotografias.mockReturnValue(of([]));
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
  const foto: FotografiaResponse = {
    id: 'foto',
    inspeccionId: 'galeria',
    categoria: 'ACCESO',
    latitud: 14,
    longitud: -90,
    capturadoEn: '2026-10-14T12:00:00Z',
    mimeReal: 'image/jpeg',
    tamanoBytes: 4,
    anchoPx: 10,
    altoPx: 10,
  };
  async function cargarGaleria(fotos = [foto]) {
    api.listarFotografias.mockReturnValue(of(fotos));
    api.contenidoFotografia.mockReturnValue(of(new Blob(['imagen'], { type: 'image/jpeg' })));
    fixture.componentRef.setInput('inspeccion', {
      id: 'galeria',
      autorId: 'autor',
      puenteId: 'puente',
      estado: 'BORRADOR',
    });
    fixture.detectChanges();
    await fixture.whenStable();
    await vi.waitFor(() => {
      expect(fixture.componentInstance.confirmadas()).toHaveLength(fotos.length);
      expect(fixture.componentInstance.confirmadas().every((foto) => !!foto.miniatura)).toBe(true);
    });
    fixture.detectChanges();
  }
  function prepararAdjunto() {
    const c = fixture.componentInstance;
    c.borrador.set({
      blob: new Blob(['jpeg'], { type: 'image/jpeg' }),
      latitud: 14,
      longitud: -90,
      capturadoEn: '2026-10-14T12:00:00Z',
      orientacion: 1,
    });
    c.categoria.setValue('ACCESO');
    return c;
  }
  it('muestra fotos existentes, miniaturas y solo cuenta evidencia confirmada', async () => {
    await cargarGaleria([foto, { ...foto, id: 'otra', categoria: 'OTRA' }]);
    const c = fixture.componentInstance;
    expect(fixture.nativeElement.querySelectorAll('.fotografias figure')).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('.fotografias img').src).toBe('blob:prueba');
    expect(api.contenidoFotografia).toHaveBeenCalledWith('galeria', 'foto', true);
    expect(c.faltantes()).toEqual(['SUPERESTRUCTURA', 'SUBESTRUCTURA', 'CAUCE']);
  });
  it('solicita confirmación y permite cancelar sin enviar DELETE', async () => {
    await cargarGaleria();
    const c = fixture.componentInstance;
    c.solicitarRetirada(foto);
    expect(c.fotoARetirar()).toEqual(foto);
    expect(api.retirarFotografia).not.toHaveBeenCalled();
    c.cancelarRetirada();
    expect(c.fotoARetirar()).toBeNull();
    expect(c.confirmadas()).toHaveLength(1);
  });
  it('retira una confirmada y actualiza galería, categorías y URLs sin borrar pendientes', async () => {
    await cargarGaleria();
    const c = fixture.componentInstance;
    const pendiente = {
      id: 'local',
      propietarioId: 'autor',
      inspeccionId: 'galeria',
      idempotencyKey: 'misma-clave',
      blob: new Blob(['local']),
      mime: 'image/jpeg',
      sincronizado: false,
      metadatos: { ...foto, id: 'local' },
    };
    locales.pendientes.mockResolvedValue([pendiente]);
    c.pendientes.set([pendiente]);
    api.retirarFotografia.mockReturnValue(of(undefined));
    api.listarFotografias.mockReturnValue(of([]));
    await c.retirar(foto);
    fixture.detectChanges();
    expect(api.retirarFotografia).toHaveBeenCalledWith('galeria', 'foto');
    expect(c.confirmadas()).toEqual([]);
    expect(c.faltantes()).toContain('ACCESO');
    expect(c.conteos().find((conteo) => conteo.categoria === 'ACCESO')?.cantidad).toBe(0);
    expect(c.pendientes()).toEqual([pendiente]);
    expect(locales.confirmar).not.toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:prueba');
    expect(fixture.nativeElement.querySelectorAll('.fotografias figure')).toHaveLength(0);
  });
  it('informa el 422 del límite y conserva blob, metadatos y operación para reintentar', async () => {
    const c = prepararAdjunto();
    api.adjuntarFotografia.mockReturnValue(
      throwError(() => ({ status: 422, error: { code: 'limite_fotografias_excedido' } })),
    );
    await c.adjuntar();
    expect(c.error()).toBe('limite');
    const pendiente = c.pendientes()[0];
    expect(pendiente.blob.size).toBe(4);
    await c.reintentar(pendiente);
    expect(api.adjuntarFotografia.mock.calls[0]).toEqual(api.adjuntarFotografia.mock.calls[1]);
    expect(locales.guardar).toHaveBeenCalledWith(pendiente);
    expect(locales.confirmar).not.toHaveBeenCalled();
    expect(c.pendientes()).toEqual([pendiente]);
  });
  it('impide adjuntar una nueva al alcanzar 60 confirmadas', async () => {
    const c = prepararAdjunto();
    c.confirmadas.set(Array.from({ length: 60 }, (_, i) => ({ ...foto, id: String(i) })));
    fixture.detectChanges();
    await c.adjuntar();
    expect(c.limiteAlcanzado()).toBe(true);
    expect(fixture.nativeElement.querySelector('input[type=file]').disabled).toBe(true);
    expect(locales.guardar).not.toHaveBeenCalled();
    expect(api.adjuntarFotografia).not.toHaveBeenCalled();
  });
  it('reintenta la misma operación pendiente aunque ya haya 60 confirmadas', async () => {
    const c = prepararAdjunto();
    api.adjuntarFotografia.mockReturnValue(throwError(() => ({ status: 0 })));
    await c.adjuntar();
    const pendiente = c.pendientes()[0];
    const fotos = Array.from({ length: 60 }, (_, i) => ({
      ...foto,
      id: String(i),
      inspeccionId: 'inspeccion',
    }));
    c.confirmadas.set(fotos);
    api.adjuntarFotografia.mockReturnValue(of({ ...foto, id: pendiente.id }));
    api.listarFotografias.mockReturnValue(of(fotos));
    api.contenidoFotografia.mockReturnValue(of(new Blob(['imagen'])));
    await c.reintentar(pendiente);
    expect(api.adjuntarFotografia.mock.calls[0]).toEqual(api.adjuntarFotografia.mock.calls[1]);
    expect(locales.confirmar).toHaveBeenCalledWith(pendiente.id, 'autor');
    expect(c.pendientes()).toEqual([]);
    expect(c.limiteAlcanzado()).toBe(true);
  });
  it('un 409 de retirada conserva la foto y bloquea nuevas modificaciones', async () => {
    await cargarGaleria();
    api.retirarFotografia.mockReturnValue(
      throwError(() => ({ status: 409, error: { code: 'estado_inspeccion_no_permite_archivos' } })),
    );
    const c = fixture.componentInstance;
    await c.retirar(foto);
    expect(c.error()).toBe('estado');
    expect(c.confirmadas()).toHaveLength(1);
    expect(c.editable()).toBe(false);
    await c.retirar(foto);
    expect(api.retirarFotografia).toHaveBeenCalledTimes(1);
  });
  it('consulta en modo lectura sin acceder a pendientes privados ni permitir modificaciones', async () => {
    locales.guardarContexto.mockClear();
    locales.pendientes.mockClear();
    auth.user.set({ id: 'catedratico' });
    await cargarGaleria();
    const c = prepararAdjunto();
    expect(c.confirmadas()).toHaveLength(1);
    expect(c.editable()).toBe(false);
    expect(fixture.nativeElement.querySelector('input[type=file]')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.fotografias button')).toHaveLength(1);
    await c.adjuntar();
    await c.abrirCamara();
    await c.retirar(foto);
    expect(api.adjuntarFotografia).not.toHaveBeenCalled();
    expect(api.retirarFotografia).not.toHaveBeenCalled();
    expect(locales.guardarContexto).not.toHaveBeenCalled();
    expect(locales.pendientes).not.toHaveBeenCalled();
    expect(c.pendientes()).toEqual([]);
  });
  it('respeta el modo de solo lectura incluso para el autor de un BORRADOR', async () => {
    await cargarGaleria();
    fixture.componentRef.setInput('soloLectura', true);
    fixture.detectChanges();
    const c = prepararAdjunto();
    await c.adjuntar();
    await c.retirar(foto);
    expect(c.editable()).toBe(false);
    expect(fixture.nativeElement.querySelector('input[type=file]')).toBeNull();
    expect(api.adjuntarFotografia).not.toHaveBeenCalled();
    expect(api.retirarFotografia).not.toHaveBeenCalled();
  });
  it('mantiene la galería al actualizar los datos del mismo borrador por autoguardado', async () => {
    await cargarGaleria();
    const llamadas = api.listarFotografias.mock.calls.length;
    fixture.componentRef.setInput('inspeccion', {
      id: 'galeria',
      autorId: 'autor',
      puenteId: 'puente',
      estado: 'BORRADOR',
      version: 2,
    });
    fixture.detectChanges();
    await fixture.whenStable();
    expect(api.listarFotografias).toHaveBeenCalledTimes(llamadas);
    expect(fixture.componentInstance.confirmadas()).toHaveLength(1);
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
