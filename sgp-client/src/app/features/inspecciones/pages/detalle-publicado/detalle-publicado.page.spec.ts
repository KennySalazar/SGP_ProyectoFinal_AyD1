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
  readonly etiquetaDeAncla = input<(a: AnclaForo) => string>();
  readonly anclaQuitada = output<void>();
  readonly anclasCambian = output<AnclaConTotal[]>();
  readonly irAElemento = output<AnclaForo>();
  enfocados = 0;
  enfocarFormulario(): void {
    this.enfocados++;
  }
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
    await TestBed.configureTestingModule({
      imports: [DetallePublicadoPage, traduccionesDePrueba()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: parametros } },
        { provide: AuthStore, useValue: { user: usuario, authenticated: autenticado } },
        { provide: InspeccionApiService, useValue: api },
        { provide: FormularioApiService, useValue: formularios },
        { provide: FotografiasLocalesService, useValue: locales },
        { provide: ProcesadorFotografiasService, useValue: {} },
      ],
    })
      .overrideComponent(DetallePublicadoPage, {
        remove: { imports: [ForoInspeccionComponent] },
        add: { imports: [ForoInspeccionStubComponent] },
      })
      .compileComponents();
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

    it('no marca los campos sueltos: el botón va en los elementos', async () => {
      const html = await abrir();

      expect(html.querySelector('[data-elemento-ref="/identificacion/ruta"]')).toBeNull();
    });

    describe('ir al elemento desde un comentario', () => {
      const encabezado = (html: HTMLElement) =>
        html.querySelector('app-formulario-dinamico .cabecera h2')?.textContent?.trim();

      it('muestra la sección del elemento y deja el foco en su botón', async () => {
        const html = await abrir();
        expect(encabezado(html)).toBe('Identificación versión 1');

        foro().irAElemento.emit({ elementoRef: '/datos_generales', instanciaElementoId: null });
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(encabezado(html)).toBe('Datos generales v1');
        expect(document.activeElement).toBe(botonDe(html, '/datos_generales'));
      });

      it('un comentario sobre un campo lleva al elemento que lo contiene', async () => {
        const html = await abrir();

        foro().irAElemento.emit({
          elementoRef: '/datos_generales/longitud_m',
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

        foro().irAElemento.emit({ elementoRef: '/no_existe/elemento', instanciaElementoId: null });
        fixture.detectChanges();
        await fixture.whenStable();

        expect(encabezado(html)).toBe('Identificación versión 1');
      });
    });
  });
});
