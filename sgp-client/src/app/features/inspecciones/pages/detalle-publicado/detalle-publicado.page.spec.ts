import { RecalculoIcComponent } from '../../components/recalculo-ic/recalculo-ic.component';
import { AprobacionCalibracionComponent } from '../../../formulario/components/aprobacion-calibracion/aprobacion-calibracion.component';
import { AprobacionCalibracionApiService } from '../../../formulario/services/aprobacion-calibracion-api.service';
import { of as estadoRespaldo } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, input, output, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../../../../core/services/auth.store';
import { ForoInspeccionComponent } from '../../../foro/components/foro-inspeccion/foro-inspeccion.component';
import { AnclaConTotal, AnclaForo } from '../../../foro/models/foro.models';
import { FotografiasLocalesService } from '../../../../offline/services/fotografias-locales.service';
import { FormularioDinamicoComponent } from '../../../formulario/components/formulario-dinamico/formulario-dinamico.component';
import { traduccionesDePrueba } from '../../../formulario/motor/testing/traducciones';
import { FormularioApiService } from '../../../formulario/services/formulario-api.service';
import { DocumentosInspeccionComponent } from '../../components/documentos-inspeccion/documentos-inspeccion.component';
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

/** El foro real tiene su propia prueba; aquí importa lo que la página le entrega y recibe. */
@Component({ selector: 'app-foro-inspeccion', standalone: true, template: '' })
class ForoInspeccionStubComponent {
  readonly inspeccionId = input.required<string>();
  readonly ancla = input<AnclaForo | null>(null);
  readonly comentarioDestacado = input<string | null>(null);
  readonly etiquetaDeAncla = input<(a: AnclaForo) => string>();
  readonly anclaQuitada = output<void>();
  readonly anclasCambian = output<AnclaConTotal[]>();
  readonly irAElemento = output<AnclaForo>();
  enfocados = 0;
  enfocarFormulario(): void {
    this.enfocados++;
  }
}

/** Los documentos PDF tienen su propia prueba; aquí no deben pedir nada al servidor. */
@Component({ selector: 'app-documentos-inspeccion', standalone: true, template: '' })
class DocumentosInspeccionStubComponent {
  readonly inspeccion = input<unknown>();
  readonly soloLectura = input(false);
}

@Component({ selector: 'app-recalculo-ic', standalone: true, template: '' })
class RecalculoIcStubComponent {
  readonly inspeccionId = input.required<string>();
}

describe('Detalle publicado de HU-030', () => {
  let fixture: ComponentFixture<DetallePublicadoPage>;
  let parametros: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let respuesta: InspeccionPublicadaDetalleResponse;
  const usuario = signal({ id: 'autor', role: 'CATEDRATICO' });
  const autenticado = signal(true);
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
    autenticado.set(true);
    Object.values(api).forEach((fn) => fn.mockReset());
    Object.values(formularios).forEach((fn) => fn.mockReset());
    Object.values(locales).forEach((fn) => fn.mockReset());
    api.obtenerPublicada.mockImplementation(() => of(respuesta));
    api.exportar.mockReturnValue(of({ id: 'original' }));
    api.obtenerOrdenesMantenimiento.mockReturnValue(of([]));
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
    vi.stubGlobal(
      'URL',
      class extends URL {
        static override createObjectURL = vi.fn(() => 'blob:foto');
        static override revokeObjectURL = vi.fn();
      },
    );
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    TestBed.overrideComponent(AprobacionCalibracionComponent, {
      set: { template: '', imports: [] },
    });
    await TestBed.configureTestingModule({
      imports: [DetallePublicadoPage, traduccionesDePrueba()],
      providers: [
        {
          provide: AprobacionCalibracionApiService,
          useValue: {
            actualizada: estadoRespaldo(),
            estado: () =>
              estadoRespaldo({
                estado: 'SIN_APROBAR',
                rotuloCalibracion: 'Calibracion provisional · sin aprobar',
              }),
            consultar: () => estadoRespaldo(null),
          },
        },
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: parametros, snapshot: { queryParamMap: convertToParamMap({}) } },
        },
        { provide: AuthStore, useValue: { user: usuario, authenticated: autenticado } },
        { provide: InspeccionApiService, useValue: api },
        { provide: FormularioApiService, useValue: formularios },
        { provide: FotografiasLocalesService, useValue: locales },
        { provide: ProcesadorFotografiasService, useValue: {} },
      ],
    })
      .overrideComponent(DetallePublicadoPage, {
        remove: {
          imports: [ForoInspeccionComponent, DocumentosInspeccionComponent, RecalculoIcComponent],
        },
        add: {
          imports: [
            ForoInspeccionStubComponent,
            DocumentosInspeccionStubComponent,
            RecalculoIcStubComponent,
          ],
        },
      })
      .compileComponents();
  });

  it('entrega la inspeccion publicada al comparador sin cambiar el IC original', async () => {
    await abrir();
    const comparador = fixture.debugElement.query(By.directive(RecalculoIcStubComponent))
      .componentInstance as RecalculoIcStubComponent;
    expect(comparador.inspeccionId()).toBe('original');
    expect(fixture.componentInstance.detalle()?.ic).toEqual(historica.ic);
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
    expect(condicion.textContent).toContain('12.50');
    expect(condicion.textContent).toContain('Condición Buena (75 - 100)');
    expect(condicion.textContent).toContain('Condición Regular · ajustada por el evaluador');
    expect(condicion.textContent).not.toContain('(50 - 74)');
    expect(condicion.textContent).toContain('Evidencia observada en sitio');
    expect(condicion.querySelector('.ic-ajuste-nota')).not.toBeNull();
  });

  it('sin ajuste muestra la condición con su rango y no avisa de ajuste', async () => {
    respuesta.ic = { ...respuesta.ic!, estadoConfirmado: 'BUENO', justificacionAjuste: null };
    const html = await abrir();
    const condicion = html.querySelector('.condicion')!;
    expect(condicion.querySelector('.ic-estado-textual')?.textContent).toContain(
      'Condición Buena (75 - 100)',
    );
    expect(condicion.textContent).not.toContain('ajustada por el evaluador');
    expect(condicion.querySelector('.ic-ajuste-nota')).toBeNull();
  });

  it('muestra el índice con dos decimales', async () => {
    respuesta.ic = { ...respuesta.ic!, indiceCalculado: 96.2963 };
    const html = await abrir();
    expect(html.querySelector('.condicion .indice')?.textContent?.trim()).toBe('96.30');
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
    respuesta = {
      ...respuesta,
      id: 'correccion',
      superadaPorId: null,
      vigencia: 'VIGENTE',
    };
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

  describe('comentarios anclados (HU-064)', () => {
    const SECCION = '/identificacion';

    const foro = () =>
      fixture.debugElement.query(By.directive(ForoInspeccionStubComponent))
        .componentInstance as ForoInspeccionStubComponent;
    const botonDe = (html: HTMLElement, ref: string) =>
      html.querySelector<HTMLButtonElement>(`[data-elemento-ref="${ref}"] button`);

    it('muestra el foro de la inspección debajo del reporte fotográfico', async () => {
      const html = await abrir();

      expect(foro().inspeccionId()).toBe('original');
      expect(foro().ancla()).toBeNull();
      expect(html.querySelector('section.foro app-foro-inspeccion')).not.toBeNull();
    });

    it('cada elemento tiene un solo botón de icono, sin número mientras no tiene comentarios', async () => {
      const html = await abrir();

      expect(html.querySelectorAll(`[data-elemento-ref="${SECCION}"] button`)).toHaveLength(1);
      const boton = botonDe(html, SECCION)!;
      expect(boton.querySelector('i.pi-comment')).not.toBeNull();
      expect(boton.querySelector('.cantidad')).toBeNull();
      expect(boton.textContent?.trim()).toBe('');
      expect(boton.getAttribute('aria-label')).toBe('Comentar Identificación versión 1');
    });

    it('con comentarios el botón muestra su número, en texto', async () => {
      const html = await abrir();
      foro().anclasCambian.emit([{ elementoRef: SECCION, instanciaElementoId: null, total: 3 }]);
      fixture.detectChanges();

      const boton = botonDe(html, SECCION)!;
      expect(boton.querySelector('i.pi-comments')).not.toBeNull();
      expect(boton.querySelector('.cantidad')?.textContent?.trim()).toBe('3');
      expect(boton.getAttribute('aria-label')).toBe('3 comentarios en Identificación versión 1');
    });

    it('al pulsarlo el foro recibe el elemento y el foco, tenga o no comentarios', async () => {
      const html = await abrir();

      botonDe(html, SECCION)!.click();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(foro().ancla()).toEqual({ elementoRef: SECCION, instanciaElementoId: null });
      expect(foro().enfocados).toBe(1);
    });

    it('sin sesión solo ve el botón de los elementos que ya tienen comentarios', async () => {
      autenticado.set(false);
      const html = await abrir();
      expect(botonDe(html, SECCION)).toBeNull();

      foro().anclasCambian.emit([{ elementoRef: SECCION, instanciaElementoId: null, total: 2 }]);
      fixture.detectChanges();

      expect(botonDe(html, SECCION)?.querySelector('.cantidad')?.textContent?.trim()).toBe('2');
    });

    it('volver a todo el hilo quita el elemento elegido', async () => {
      const html = await abrir();
      botonDe(html, SECCION)!.click();
      fixture.detectChanges();
      expect(foro().ancla()).not.toBeNull();

      foro().anclaQuitada.emit();
      fixture.detectChanges();

      expect(foro().ancla()).toBeNull();
    });

    it('nombra cada elemento con el título que le da el esquema de su versión', async () => {
      await abrir();

      expect(fixture.componentInstance.etiquetaDeAncla()({ elementoRef: SECCION })).toBe(
        'Identificación versión 1',
      );
    });

    describe('comentarios por campo', () => {
      const CAMPO = '/identificacion/ruta';
      const interruptor = (html: HTMLElement) =>
        html.querySelector<HTMLButtonElement>('.herramientas-comentarios button');

      it('un campo suelto también se puede comentar y entrega su ancla', async () => {
        const html = await abrir();

        const boton = botonDe(html, CAMPO)!;
        expect(boton).not.toBeNull();
        // El nombre dice a qué sección pertenece el campo, no solo cómo se llama.
        expect(boton.getAttribute('aria-label')).toBe('Comentar Identificación versión 1 › Ruta');
        boton.click();
        fixture.detectChanges();

        expect(foro().ancla()).toEqual({ elementoRef: CAMPO, instanciaElementoId: null });
      });

      it('el mismo nombre de campo en dos secciones se distingue por su ruta', async () => {
        await abrir();
        const etiqueta = fixture.componentInstance.etiquetaDeAncla();

        expect(etiqueta({ elementoRef: '/identificacion/ruta' })).toBe(
          'Identificación versión 1 › Ruta',
        );
        expect(etiqueta({ elementoRef: '/datos_generales/longitud_m' })).toBe(
          'Datos generales v1 › Longitud',
        );
        expect(etiqueta({ elementoRef: '/identificacion/ruta' })).not.toBe(
          etiqueta({ elementoRef: '/datos_generales/longitud_m' }),
        );
      });

      it('el icono de un campo empieza escondido y se queda fijo si tiene comentarios', async () => {
        const html = await abrir();
        const anclaje = () => html.querySelector(`[data-elemento-ref="${CAMPO}"]`) as HTMLElement;
        expect(anclaje().classList.contains('fijo')).toBe(false);

        foro().anclasCambian.emit([{ elementoRef: CAMPO, instanciaElementoId: null, total: 2 }]);
        fixture.detectChanges();

        expect(anclaje().classList.contains('fijo')).toBe(true);
        expect(botonDe(html, CAMPO)?.querySelector('.cantidad')?.textContent?.trim()).toBe('2');
      });

      it('el interruptor deja a la vista el icono de todos los campos y se puede apagar', async () => {
        const html = await abrir();
        const iconos = () =>
          Array.from(
            html.querySelectorAll<HTMLElement>('app-formulario-dinamico .anclaje.en-campo'),
          );
        expect(interruptor(html)?.getAttribute('aria-pressed')).toBe('false');
        expect(iconos().length).toBeGreaterThan(0);
        expect(iconos().some((icono) => icono.classList.contains('fijo'))).toBe(false);

        interruptor(html)!.click();
        fixture.detectChanges();

        expect(interruptor(html)?.getAttribute('aria-pressed')).toBe('true');
        expect(iconos().every((icono) => icono.classList.contains('fijo'))).toBe(true);

        interruptor(html)!.click();
        fixture.detectChanges();

        expect(interruptor(html)?.getAttribute('aria-pressed')).toBe('false');
        expect(iconos().some((icono) => icono.classList.contains('fijo'))).toBe(false);
      });

      it('sin sesión no se ofrece el interruptor, porque no hay nada que comentar', async () => {
        autenticado.set(false);
        const html = await abrir();

        expect(interruptor(html)).toBeNull();
      });
    });

    describe('ir al elemento desde un comentario', () => {
      const encabezado = (html: HTMLElement) =>
        html.querySelector('app-formulario-dinamico .cabecera h2')?.textContent?.trim();

      it('muestra la sección del elemento y deja el foco en su botón', async () => {
        const html = await abrir();
        expect(encabezado(html)).toBe('Identificación versión 1');

        foro().irAElemento.emit({
          elementoRef: '/datos_generales',
          instanciaElementoId: null,
        });
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(encabezado(html)).toBe('Datos generales v1');
        expect(document.activeElement).toBe(botonDe(html, '/datos_generales'));
      });

      it('un comentario sobre un campo lleva a ese campo, en su sección', async () => {
        const html = await abrir();

        foro().irAElemento.emit({
          elementoRef: '/datos_generales/longitud_m',
          instanciaElementoId: null,
        });
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(encabezado(html)).toBe('Datos generales v1');
        expect(document.activeElement).toBe(botonDe(html, '/datos_generales/longitud_m'));
      });

      it('si el campo ya no está en el formulario lleva al elemento que lo contiene', async () => {
        const html = await abrir();

        foro().irAElemento.emit({
          elementoRef: '/datos_generales/campo_que_ya_no_existe',
          instanciaElementoId: null,
        });
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(encabezado(html)).toBe('Datos generales v1');
        expect(document.activeElement).toBe(botonDe(html, '/datos_generales'));
      });

      it('abre el grupo plegado que contiene al elemento', async () => {
        const html = await abrir();
        const detalles = document.createElement('details');
        const objetivo = document.createElement('div');
        objetivo.dataset['elementoRef'] = '/identificacion/ruta_plegada';
        objetivo.dataset['instanciaId'] = '';
        objetivo.innerHTML = '<button type="button">Comentar</button>';
        detalles.append(objetivo);
        html.querySelector('app-formulario-dinamico')!.append(detalles);
        expect(detalles.open).toBe(false);

        foro().irAElemento.emit({
          elementoRef: '/identificacion/ruta_plegada',
          instanciaElementoId: null,
        });
        fixture.detectChanges();
        await fixture.whenStable();

        expect(detalles.open).toBe(true);
        expect(document.activeElement).toBe(objetivo.querySelector('button'));
      });

      it('no falla si el elemento ya no está en el formulario', async () => {
        const html = await abrir();

        foro().irAElemento.emit({
          elementoRef: '/no_existe/elemento',
          instanciaElementoId: null,
        });
        fixture.detectChanges();
        await fixture.whenStable();

        expect(encabezado(html)).toBe('Identificación versión 1');
      });
    });
  });
});
