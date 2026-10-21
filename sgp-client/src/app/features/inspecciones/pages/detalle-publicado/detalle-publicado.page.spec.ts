import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../../../../core/services/auth.store';
import { FotografiasLocalesService } from '../../../../offline/services/fotografias-locales.service';
import { FormularioDinamicoComponent } from '../../../formulario/components/formulario-dinamico/formulario-dinamico.component';
import { traduccionesDePrueba } from '../../../formulario/motor/testing/traducciones';
import { FormularioApiService } from '../../../formulario/services/formulario-api.service';
import { ReporteFotograficoComponent } from '../../components/reporte-fotografico/reporte-fotografico.component';
import { InspeccionPublicadaDetalleResponse } from '../../models/inspeccion.models';
import { InspeccionApiService } from '../../services/inspeccion-api.service';
import { ProcesadorFotografiasService } from '../../services/procesador-fotografias.service';
import { DetallePublicadoPage } from './detalle-publicado.page';

const historica: InspeccionPublicadaDetalleResponse = {
  id: 'original',
  puenteId: 'puente',
  puenteNombre: 'Puente histórico',
  versionFormularioId: 'v1',
  versionFormularioCodigo: 'SIECA-v1',
  esquemaFormulario: {
    type: 'object',
    properties: {
      identificacion: {
        type: 'object',
        title: 'Identificación versión 1',
        properties: {
          nombre_puente: { type: 'string', title: 'Nombre histórico' },
          ruta: { type: 'string', title: 'Ruta' },
        },
      },
      datos_generales: {
        type: 'object',
        title: 'Datos generales v1',
        properties: {
          longitud_m: { type: 'number', title: 'Longitud' },
        },
      },
    },
  },
  autorId: 'autor',
  nombreAutor: 'Ana Autora',
  rolAutor: 'CATEDRATICO',
  acompanantes: [{ id: 'acompanante', nombre: 'Luis Acompañante', numeroColegiado: '12345' }],
  estado: 'PUBLICADA',
  fechaInspeccion: '2026-10-15',
  dispositivoId: 'equipo-01',
  identificacionCapturada: { nombre_puente: 'Nombre capturado', ruta: 'CA-1' },
  datos: { identificacion: { nombre_puente: 'Nombre persistido' } },
  datosPermanentes: { longitudM: 42 },
  creadoEn: '2026-10-15T08:00:00Z',
  enviadoEn: '2026-10-16T09:00:00Z',
  publicadoEn: '2026-10-17T10:00:00Z',
  ic: {
    indiceCalculado: 12.5,
    estadoCalculado: 'BUENO',
    estadoConfirmado: 'REGULAR',
    justificacionAjuste: 'Evidencia observada en sitio',
  },
  supersedeAId: null,
  motivoCorreccion: null,
  superadaPorId: 'correccion',
  vigencia: 'SUPERADA',
};

describe('Detalle publicado de HU-030', () => {
  let fixture: ComponentFixture<DetallePublicadoPage>;
  let parametros: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let respuesta: InspeccionPublicadaDetalleResponse;
  const usuario = signal({ id: 'autor', role: 'CATEDRATICO' });
  const api = {
    obtenerPublicada: vi.fn(),
    listarFotografias: vi.fn(),
    contenidoFotografia: vi.fn(),
    guardarBorrador: vi.fn(),
    enviar: vi.fn(),
    publicar: vi.fn(),
    adjuntarFotografia: vi.fn(),
    retirarFotografia: vi.fn(),
    exportar: vi.fn(),
    obtenerOrdenesMantenimiento: vi.fn(),
  };
  const formularios = { obtenerActiva: vi.fn(), obtenerEsquemaPublicado: vi.fn() };
  const locales = { guardarContexto: vi.fn(), pendientes: vi.fn(), guardar: vi.fn() };

  beforeEach(async () => {
    respuesta = structuredClone(historica);
    parametros = new BehaviorSubject(convertToParamMap({ id: 'original' }));
    usuario.set({ id: 'autor', role: 'CATEDRATICO' });
    Object.values(api).forEach((fn) => fn.mockReset());
    Object.values(formularios).forEach((fn) => fn.mockReset());
    Object.values(locales).forEach((fn) => fn.mockReset());
    api.obtenerPublicada.mockImplementation(() => of(respuesta));
    api.listarFotografias.mockReturnValue(
      of([
        {
          id: 'foto',
          inspeccionId: 'original',
          categoria: 'ACCESO',
        },
      ]),
    );
    api.contenidoFotografia.mockReturnValue(of(new Blob(['jpeg'], { type: 'image/jpeg' })));
    api.obtenerOrdenesMantenimiento.mockReturnValue(of([]));
    api.exportar.mockReturnValue(of({ id: 'original', estado: 'PUBLICADA' }));
    vi.stubGlobal(
      'URL',
      class extends URL {
        static override createObjectURL = vi.fn(() => 'blob:foto');
        static override revokeObjectURL = vi.fn();
      },
    );
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    await TestBed.configureTestingModule({
      imports: [DetallePublicadoPage, traduccionesDePrueba()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: parametros } },
        { provide: AuthStore, useValue: { user: usuario } },
        { provide: InspeccionApiService, useValue: api },
        { provide: FormularioApiService, useValue: formularios },
        { provide: FotografiasLocalesService, useValue: locales },
        { provide: ProcesadorFotografiasService, useValue: {} },
      ],
    }).compileComponents();
  });

  async function abrir() {
    fixture = TestBed.createComponent(DetallePublicadoPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  afterEach(() => {
    fixture?.destroy();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    for (const metodo of [
      'guardarBorrador',
      'enviar',
      'publicar',
      'adjuntarFotografia',
      'retirarFotografia',
    ] as const) {
      expect(api[metodo]).not.toHaveBeenCalled();
    }
    expect(formularios.obtenerActiva).not.toHaveBeenCalled();
    expect(formularios.obtenerEsquemaPublicado).not.toHaveBeenCalled();
  });

  it('renderiza el esquema histórico y los datos persistidos con todos los controles deshabilitados', async () => {
    const antes = structuredClone(respuesta);
    const html = await abrir();
    const formulario: FormularioDinamicoComponent = fixture.debugElement.query(
      By.directive(FormularioDinamicoComponent),
    ).componentInstance;
    expect(formulario.esquema()).toEqual(respuesta.esquemaFormulario);
    expect(formulario.soloLectura()).toBe(true);
    expect(formulario.construido()?.form.disabled).toBe(true);
    expect(formulario.construido()?.form.getRawValue()).toMatchObject({
      identificacion: { nombre_puente: 'Nombre persistido', ruta: 'CA-1' },
      datos_generales: { longitud_m: 42 },
    });
    expect(html.textContent).toContain('Identificación versión 1');
    const inputs = html.querySelectorAll<HTMLInputElement>('app-formulario-dinamico input');
    expect(inputs.length).toBeGreaterThan(0);
    expect(Array.from(inputs).every((input) => input.disabled)).toBe(true);
    expect(respuesta).toEqual(antes);
  });

  it('muestra IC definitivo, estado calculado, confirmado y justificación sin calcular', async () => {
    const html = await abrir();
    const condicion = html.querySelector('.condicion')!;
    expect(condicion.textContent).toContain('12.5');
    expect(condicion.textContent).toContain('Bueno');
    expect(condicion.textContent).toContain('Regular');
    expect(condicion.textContent).toContain('Evidencia observada en sitio');
  });

  it('muestra autor, acompañantes, versión, todas las fechas y dispositivo', async () => {
    const texto = (await abrir()).querySelector('.metadatos')!.textContent;
    for (const dato of [
      'Ana Autora',
      'Luis Acompañante',
      '12345',
      'SIECA-v1',
      '15/10/2026',
      '16/10/2026',
      '17/10/2026',
      'equipo-01',
    ]) {
      expect(texto).toContain(dato);
    }
  });

  it('muestra SUPERADA y un enlace a la corrección publicada', async () => {
    const html = await abrir();
    expect(html.querySelector('.superada')?.textContent).toContain('SUPERADA');
    expect(html.querySelector('.superada a')?.getAttribute('href')).toBe(
      '/inspecciones/correccion/publicada',
    );
  });

  it('reutiliza miniaturas y descarga del original sin acciones de carga o retirada ni escrituras locales', async () => {
    const html = await abrir();
    const reporte: ReporteFotograficoComponent = fixture.debugElement.query(
      By.directive(ReporteFotograficoComponent),
    ).componentInstance;
    expect(reporte.soloLectura()).toBe(true);
    expect(reporte.editable()).toBe(false);
    await vi.waitFor(() => expect(reporte.confirmadas()[0]?.miniatura).toBe('blob:foto'));
    fixture.detectChanges();
    expect(html.querySelector('.fotografias img')?.getAttribute('src')).toBe('blob:foto');
    expect(html.querySelector('input[type=file]')).toBeNull();
    const botones = Array.from(
      html.querySelectorAll<HTMLButtonElement>('app-reporte-fotografico button'),
    );
    expect(botones.some((b) => b.textContent?.includes('Retirar'))).toBe(false);
    botones.find((b) => b.textContent?.includes('original'))!.click();
    await fixture.whenStable();
    expect(api.contenidoFotografia).toHaveBeenCalledWith('original', 'foto', true);
    expect(api.contenidoFotografia).toHaveBeenCalledWith('original', 'foto');
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled();
    expect(locales.guardarContexto).not.toHaveBeenCalled();
    expect(locales.pendientes).not.toHaveBeenCalled();
  });

  it.each([null, {}])(
    'muestra error claro si falta el esquema histórico (%s), sin fallback',
    async (esquema) => {
      respuesta.esquemaFormulario = esquema;
      const html = await abrir();
      expect(html.querySelector('[role=alert]')?.textContent).toContain('esquema histórico');
      expect(html.querySelector('app-formulario-dinamico')).toBeNull();
    },
  );

  it.each([401, 403, 404, 500])(
    'respeta el error HTTP %s del backend sin mostrar datos',
    async (status) => {
      api.obtenerPublicada.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status,
              error: { detail: 'Detalle del backend' },
            }),
        ),
      );
      const html = await abrir();
      expect(html.querySelector('[role=alert]')?.textContent).toContain('Detalle del backend');
      expect(fixture.componentInstance.estadoHttp()).toBe(status);
      expect(html.querySelector('app-reporte-fotografico')).toBeNull();
      expect(html.querySelector('app-formulario-dinamico')).toBeNull();
      expect(api.listarFotografias).not.toHaveBeenCalled();
    },
  );

  it('indica IC y envío ausentes, sin inventar valores ni ajustes', async () => {
    respuesta.ic = null;
    respuesta.enviadoEn = null;
    respuesta.superadaPorId = null;
    const html = await abrir();
    expect(html.textContent).toContain('No hay un IC definitivo registrado');
    expect(html.textContent).toContain('No registrado');
    expect(html.querySelector('.superada')).toBeNull();
  });

  it('carga la corrección al cambiar el id en la misma ruta', async () => {
    await abrir();
    respuesta = { ...respuesta, id: 'correccion', superadaPorId: null, vigencia: 'VIGENTE' };
    parametros.next(convertToParamMap({ id: 'correccion' }));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(api.obtenerPublicada).toHaveBeenLastCalledWith('correccion');
    expect(fixture.componentInstance.detalle()?.id).toBe('correccion');
  });

  it('muestra banner de versión histórica y tabla de correspondencia cuando no es la activa', async () => {
    respuesta.relacionVersionActiva = {
      esVersionActiva: false,
      codigoVersionInspeccion: 'SIECA-v1',
      codigoVersionActiva: 'SIECA-v2',
      versionActivaId: 'v2',
      mapeosDeclarados: true,
      totalEquivalentes: 1,
      totalNuevos: 1,
      totalEliminados: 0,
      mapeos: [
        {
          id: 'map-1',
          elementoRefOrigen: 'subestructura.estribo',
          elementoRefDestino: 'subestructura.estribo_entrada',
          tipo: 'EQUIVALENTE',
          observacion: 'Renombrado menor',
        },
      ],
    };
    const html = await abrir();
    const banner = html.querySelector('.version-historica-banner');
    expect(banner).not.toBeNull();
    expect(banner?.textContent).toContain('SIECA-v1');
    expect(banner?.textContent).toContain('SIECA-v2');

    const toggle = html.querySelector<HTMLButtonElement>('.version-banner-accion button');
    expect(toggle).not.toBeNull();
    toggle?.click();
    fixture.detectChanges();

    const tablaMapeo = html.querySelector('.tabla-mapeo-contenedor');
    expect(tablaMapeo).not.toBeNull();
    expect(tablaMapeo?.textContent).toContain('subestructura.estribo');
    expect(tablaMapeo?.textContent).toContain('subestructura.estribo_entrada');
  });

  it('permite exportar la inspección en JSON invocando el servicio', async () => {
    const html = await abrir();
    const botones = Array.from(
      html.querySelectorAll<HTMLButtonElement>('.acciones-publicada button'),
    );
    expect(botones.length).toBeGreaterThanOrEqual(2);

    fixture.componentInstance.exportar();
    await fixture.whenStable();

    expect(api.exportar).toHaveBeenCalledWith('original');
  });

  it('muestra propuestas de mantenimiento asociadas si existen', async () => {
    api.obtenerOrdenesMantenimiento.mockReturnValue(
      of([
        {
          id: 'prop-1',
          tipo: 'REPARACION',
          estado: 'PENDIENTE',
          descripcion: 'Desplazamiento visible',
          prioridadSugerida: 'ALTA',
          prioridadConfirmada: 'ALTA',
          creadoEn: '2026-10-20T10:00:00Z',
        },
      ]),
    );

    const html = await abrir();
    expect(html.textContent).toContain('REPARACION');
    expect(html.textContent).toContain('Desplazamiento visible');
  });
});
