import { CalibracionAdicionalApiService } from '../../services/calibracion-adicional-api.service';
import { CalibracionAdicionalComponent } from './calibracion-adicional/calibracion-adicional.component';
import { ReglasIcComponent } from './reglas-ic/reglas-ic.component';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { of, throwError, Subject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { VersionFormulario } from '../../models/formulario.models';
import { FormularioApiService } from '../../services/formulario-api.service';
import { EditorVersionFormularioPage } from './editor-version-formulario.page';
@Pipe({ name: 'transloco', standalone: true })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}
describe('EditorVersionFormularioPage', () => {
  let component: EditorVersionFormularioPage;
  let fixture: ComponentFixture<EditorVersionFormularioPage>;
  let apiMock: {
    obtenerPorId: ReturnType<typeof vi.fn>;
    actualizarBorrador: ReturnType<typeof vi.fn>;
    calibrarPesos: ReturnType<typeof vi.fn>;
    configurarEscala: ReturnType<typeof vi.fn>;
    analizarMapeo: ReturnType<typeof vi.fn>;
    guardarMapeos: ReturnType<typeof vi.fn>;
    publicar: ReturnType<typeof vi.fn>;
    crear: ReturnType<typeof vi.fn>;
  };
  let router: Router;
  const mockVersion: VersionFormulario = {
    id: 'v-1',
    codigo: 'SIECA-v1',
    estado: 'BORRADOR',
    activa: false,
    esquema: { type: 'object' },
    vinculosAlmacenamiento: {},
    reglasEvaluacionIc: {
      elementos: [{ elemento_ref: '/vigas', nombre: 'Vigas', peso: null }],
      reglas_evaluacion_ic: {
        reglas_anulacion: [{ id: 'RN-IC-05a', pendiente: null }],
        version_algoritmo: 'ic-prueba',
        agregacion: {
          indicadores_de_un_elemento: 'MAXIMO',
          instancias_repetibles: 'MAXIMO',
          elementos_ausentes_portantes: 4,
          elementos_ausentes_no_portantes: 4,
        },
        rangos_estado: { BUENO: 75, REGULAR: 50, MALO: 0 },
        escala_severidad: { NO_PRESENTA: 0, LEVE: 1, MODERADO: 2, SEVERO: 4 },
      },
    },
    versionAlgoritmoIc: 'IC-SIECA-v1',
    creadoPorId: 'u-1',
    creadoEn: '2026-10-13T00:00:00Z',
    actualizadoEn: '2026-10-13T00:00:00Z',
    version: 1,
    pesos: [
      {
        id: 'p-1',
        elementoRef: '/vigas',
        peso: 1.5,
        descripcion: 'Vigas',
        creadoEn: '2026-10-13T00:00:00Z',
      },
    ],
    mapeos: [],
  };
  beforeEach(async () => {
    apiMock = {
      obtenerPorId: vi.fn().mockReturnValue(of(mockVersion)),
      actualizarBorrador: vi.fn().mockReturnValue(of(mockVersion)),
      calibrarPesos: vi.fn().mockReturnValue(of(mockVersion.pesos)),
      configurarEscala: vi.fn().mockReturnValue(of(mockVersion)),
      analizarMapeo: vi.fn().mockReturnValue(
        of({
          versionDestinoId: 'v-1',
          versionDestinoCodigo: 'SIECA-v1',
          camposOrigen: [],
          camposDestino: [],
          mapeosDeclarados: [],
          camposOrigenSinMapeo: [],
          mapeoCompleto: true,
        }),
      ),
      guardarMapeos: vi.fn().mockReturnValue(of([])),
      publicar: vi.fn().mockReturnValue(of({ ...mockVersion, estado: 'PUBLICADA', activa: true })),
      crear: vi.fn().mockReturnValue(of({ ...mockVersion, id: 'v-2', codigo: 'SIECA-v2' })),
    };
    TestBed.configureTestingModule({
      imports: [EditorVersionFormularioPage],
      providers: [
        provideRouter([]),
        {
          provide: CalibracionAdicionalApiService,
          useValue: { consultarActa: () => of({ estado: 'SIN_ACTA', acta: null }) },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'v-1' } } },
        },
        { provide: FormularioApiService, useValue: apiMock },
        { provide: ApiErrorService, useValue: { mostrar: vi.fn(), normalize: vi.fn() } },
        { provide: TranslocoService, useValue: { translate: (k: string) => k } },
      ],
    });
    TestBed.overrideComponent(EditorVersionFormularioPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    TestBed.overrideComponent(ReglasIcComponent, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });
    TestBed.overrideComponent(CalibracionAdicionalComponent, {
      set: { template: '', imports: [], providers: [] },
    });
    await TestBed.compileComponents();
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockImplementation(() => Promise.resolve(true));
    fixture = TestBed.createComponent(EditorVersionFormularioPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });
  it('debe crearse y cargar el detalle de la versión', () => {
    expect(component).toBeTruthy();
    expect(apiMock.obtenerPorId).toHaveBeenCalledWith('v-1');
    expect(component.version()?.codigo).toBe('SIECA-v1');
    expect(component.pesosEditables()).toHaveLength(1);
  });
  it('debe guardar cambios del esquema', () => {
    component.modoEdicion.set('codigo');
    component.esquemaTexto.set('{"type": "object", "title": "Nuevo"}');
    component.guardarEsquema();
    expect(apiMock.actualizarBorrador).toHaveBeenCalled();
  });
  it('debe rechazar esquema con JSON malformado en modo código', () => {
    component.modoEdicion.set('codigo');
    component.esquemaTexto.set('{invalido json');
    component.guardarEsquema();
    expect(component.errorValidacion()).not.toBeNull();
    expect(apiMock.actualizarBorrador).not.toHaveBeenCalled();
  });
  it('debe alternar entre modo visual y modo código', () => {
    expect(component.modoEdicion()).toBe('visual');
    component.cambiarModoEdicion('codigo');
    expect(component.modoEdicion()).toBe('codigo');
    component.cambiarModoEdicion('visual');
    expect(component.modoEdicion()).toBe('visual');
  });
  it('debe permitir crear y editar una sección visualmente', () => {
    component.abrirCrearSeccion();
    expect(component.dialogoSeccion()).toBe(true);
    expect(component.modoSeccion()).toBe('crear');
    component.seccionForm.setValue({
      clave: 'superestructura_arcos',
      titulo: 'Superestructura de Arcos',
      descripcion: 'Evaluación de arcos',
      esRepetible: false,
    });
    component.guardarSeccionModal();
    expect(component.dialogoSeccion()).toBe(false);
    const secCreada = component
      .seccionesVisuales()
      .find((s) => s.clave === 'superestructura_arcos');
    expect(secCreada).toBeTruthy();
    expect(secCreada?.titulo).toBe('Superestructura de Arcos');
    component.abrirEditarSeccion(secCreada!);
    expect(component.modoSeccion()).toBe('editar');
    component.seccionForm.controls.titulo.setValue('Superestructura de Arcos Modificada');
    component.guardarSeccionModal();
    const secEditada = component
      .seccionesVisuales()
      .find((s) => s.clave === 'superestructura_arcos');
    expect(secEditada?.titulo).toBe('Superestructura de Arcos Modificada');
  });
  it('debe permitir agregar y eliminar preguntas visualmente', () => {
    component.seccionesVisuales.set([{ clave: 'geometria', titulo: 'Geometría', preguntas: [] }]);
    component.abrirCrearPregunta('geometria');
    expect(component.dialogoPregunta()).toBe(true);
    expect(component.modoPregunta()).toBe('crear');
    component.preguntaForm.setValue({
      seccionClave: 'geometria',
      clave: 'ancho_calzada',
      titulo: 'Ancho de la calzada (m)',
      descripcion: 'Medida en metros',
      tipo: 'number',
      obligatorio: true,
      soloLectura: false,
      longitudMinima: null,
      longitudMaxima: null,
      valorMinimo: 1,
      valorMaximo: 50,
      vinculo: 'puente.ancho_calzada_m',
      opcionNueva: '',
    });
    component.guardarPreguntaModal();
    expect(component.dialogoPregunta()).toBe(false);
    const sec = component.seccionesVisuales().find((s) => s.clave === 'geometria');
    expect(sec?.preguntas).toHaveLength(1);
    expect(sec?.preguntas[0].clave).toBe('ancho_calzada');
    expect(sec?.preguntas[0].obligatorio).toBe(true);
    component.eliminarPregunta('geometria', 'ancho_calzada');
    expect(sec?.preguntas).toHaveLength(0);
  });
  it('debe gestionar opciones en preguntas de selección', () => {
    component.preguntaForm.controls.opcionNueva.setValue('BUENO');
    component.agregarOpcion();
    expect(component.opcionesSeleccion()).toContain('BUENO');
    component.eliminarOpcion(0);
    expect(component.opcionesSeleccion()).not.toContain('BUENO');
  });
  it('debe permitir seleccionar pestañas de sección y obtener la sección actual', () => {
    component.seccionesVisuales.set([
      { clave: 'sec_1', titulo: 'Sección 1', preguntas: [] },
      { clave: 'sec_2', titulo: 'Sección 2', preguntas: [] },
    ]);
    component.seleccionarSeccionVisual('sec_2');
    expect(component.seccionVisualActiva()).toBe('sec_2');
    expect(component.seccionVisualActual()?.clave).toBe('sec_2');
    expect(component.seccionVisualActual()?.titulo).toBe('Sección 2');
    expect(component.limpiarTituloSeccion('1. Identificación del puente')).toBe(
      'Identificación del puente',
    );
    expect(component.limpiarTituloSeccion('8. Otros')).toBe('Otros');
    expect(component.limpiarTituloSeccion('Sin número')).toBe('Sin número');
  });
  it('debe gestionar vínculos de almacenamiento de forma visual', () => {
    expect(component.modoVinculos()).toBe('visual');
    component.cambiarModoVinculos('codigo');
    expect(component.modoVinculos()).toBe('codigo');
    component.cambiarModoVinculos('visual');
    expect(component.modoVinculos()).toBe('visual');
    component.abrirCrearVinculo();
    expect(component.dialogoVinculo()).toBe(true);
    expect(component.modoVinculo()).toBe('crear');
    component.vinculoForm.setValue({
      campoRef: '/identificacion/codigo',
      columnaDestino: 'puente.codigo',
      tipo: 'Inventario puente',
      descripcion: 'Código del puente',
    });
    component.guardarVinculoModal();
    expect(component.dialogoVinculo()).toBe(false);
    expect(component.vinculosVisuales()).toHaveLength(1);
    const vinc = component.vinculosVisuales()[0];
    expect(vinc.campoRef).toBe('/identificacion/codigo');
    expect(vinc.columnaDestino).toBe('puente.codigo');
    component.filtroVinculos.set('puente');
    expect(component.vinculosFiltrados()).toHaveLength(1);
    component.filtroVinculos.set('inexistente');
    expect(component.vinculosFiltrados()).toHaveLength(0);
    component.filtroVinculos.set('');
    component.abrirEditarVinculo(vinc);
    expect(component.modoVinculo()).toBe('editar');
    component.vinculoForm.controls.columnaDestino.setValue('puente.codigo_oficial');
    component.guardarVinculoModal();
    expect(component.vinculosVisuales()[0].columnaDestino).toBe('puente.codigo_oficial');
    component.guardarVinculos();
    expect(apiMock.actualizarBorrador).toHaveBeenCalled();
    component.eliminarVinculo(vinc.id);
    expect(component.vinculosVisuales()).toHaveLength(0);
  });
  it('debe calibrar y guardar pesos', () => {
    component.guardarPesos();
    expect(apiMock.calibrarPesos).toHaveBeenCalledWith('v-1', [
      { elementoRef: '/vigas', peso: 1.5, descripcion: 'Vigas' },
    ]);
  });
  it('debe publicar la versión exitosamente', () => {
    component.ejecutarPublicacion();
    expect(apiMock.publicar).toHaveBeenCalledWith('v-1', { mapeos: [] });
    expect(component.version()?.estado).toBe('PUBLICADA');
    expect(component.version()?.activa).toBe(true);
  });
  it('debe describir vínculos y campos de manera amigable para el usuario', () => {
    component.seccionesVisuales.set([
      {
        clave: 'identificacion',
        titulo: '1. Identificación del puente',
        preguntas: [
          { clave: 'codigo', titulo: 'Código del puente', tipo: 'string', obligatorio: true },
        ],
      },
    ]);
    const infoPuente = component.describirVinculo('puente.codigo');
    expect(infoPuente.categoria).toBe('puente');
    expect(infoPuente.etiquetaCampo).toBe('Código del puente');
    expect(component.formatearDestinoHumano('puente.codigo')).toBe('Código del puente');
    const infoMunicipio = component.describirVinculo('municipio.nombre (vía puente.municipio_id)');
    expect(infoMunicipio.categoria).toBe('puente');
    expect(infoMunicipio.etiquetaCampo).toBe('Municipio del puente');
    const infoTecnico = component.describirVinculo(
      'inspeccion_dato_tecnico.drenajes_aledanos_existen',
    );
    expect(infoTecnico.categoria).toBe('tecnico');
    expect(infoTecnico.etiquetaCampo).toBe('Drenajes aledaños');
    const infoInspeccion = component.describirVinculo('inspeccion.fecha_inspeccion');
    expect(infoInspeccion.categoria).toBe('inspeccion');
    expect(infoInspeccion.etiquetaCampo).toBe('Fecha oficial de inspección');
    const infoNulo = component.describirVinculo(null);
    expect(infoNulo.categoria).toBe('otro');
    expect(infoNulo.nombreCompleto).toBe('Documento de inspección');
    expect(component.obtenerNombrePregunta('/identificacion/codigo')).toBe('Código del puente');
    expect(component.obtenerNombreSeccion('/identificacion/codigo')).toBe(
      'Identificación del puente',
    );
    expect(component.obtenerTituloCampoRef('/identificacion/codigo')).toBe(
      'Código del puente (Identificación del puente)',
    );
    expect(component.obtenerTituloCampoRef('/otro/desconocido')).toBe('otro › desconocido');
  });
  it('muestra elementos del catalogo aunque no tengan pesos guardados', () => {
    apiMock.obtenerPorId.mockReturnValue(of({ ...mockVersion, pesos: [] }));
    component.ngOnInit();
    expect(component.pesosEditables()).toEqual([
      { elementoRef: '/vigas', descripcion: 'Vigas', peso: null },
    ]);
    expect(component.puedePublicar()).toBe(false);
    component.cambiarSeccion('pesos');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('/vigas');
  });
  it.each([null, 0, -1, NaN, Infinity])('rechaza peso invalido y no inventa valor: %s', (peso) => {
    component.actualizarPeso('/vigas', peso);
    component.guardarPesos();
    expect(component.pesosEditables()[0].peso).toBe(peso);
    expect(apiMock.calibrarPesos).not.toHaveBeenCalled();
  });
  it('no permite publicar con pesos modificados sin guardar', () => {
    component.actualizarPeso('/vigas', 2);
    component.abrirConfirmarPublicar();
    component.ejecutarPublicacion();
    expect(component.calibracionPendiente()).toBe(true);
    expect(apiMock.publicar).not.toHaveBeenCalled();
  });
  it('guardar pesos actualiza la version y permite publicar con escala guardada', () => {
    component.actualizarPeso('/vigas', 2);
    apiMock.calibrarPesos.mockReturnValue(of([{ ...mockVersion.pesos[0], peso: 2 }]));
    component.guardarPesos();
    expect(component.version()?.pesos[0].peso).toBe(2);
    expect(component.calibracionPendiente()).toBe(false);
    expect(component.puedePublicar()).toBe(true);
  });
  it.each([null, -1, 5, NaN])('rechaza escala incompleta o fuera de rango: %s', (valor) => {
    component.actualizarEscala('LEVE', valor);
    component.guardarEscala();
    expect(apiMock.configurarEscala).not.toHaveBeenCalled();
    expect(component.puedePublicar()).toBe(false);
  });
  it('exige No presenta igual a cero', () => {
    component.actualizarEscala('NO_PRESENTA', 1);
    component.guardarEscala();
    expect(component.escalaCorrecta()).toBe(false);
    expect(apiMock.configurarEscala).not.toHaveBeenCalled();
  });
  it('guarda la escala mediante su endpoint y conserva metadatos del servidor', () => {
    component.actualizarEscala('LEVE', 0.5);
    const reglas = {
      ...mockVersion.reglasEvaluacionIc,
      reglas_evaluacion_ic: {
        escala_severidad: { NO_PRESENTA: 0, LEVE: 0.5, MODERADO: 2, SEVERO: 4 },
      },
    };
    apiMock.configurarEscala.mockReturnValue(
      of({ ...mockVersion, reglasEvaluacionIc: reglas, calibradoPorId: 'admin-1' }),
    );
    component.guardarEscala();
    expect(apiMock.configurarEscala).toHaveBeenCalledWith('v-1', {
      valores: { NO_PRESENTA: 0, LEVE: 0.5, MODERADO: 2, SEVERO: 4 },
    });
    expect(component.version()?.calibradoPorId).toBe('admin-1');
    expect(component.calibracionPendiente()).toBe(false);
  });
  it('conserva pesos locales pendientes al guardar la escala', () => {
    component.actualizarPeso('/vigas', 2);
    component.guardarEscala();
    expect(component.pesosEditables()[0].peso).toBe(2);
    expect(component.calibracionPendiente()).toBe(true);
    expect(component.puedePublicar()).toBe(false);
  });
  it('bloquea edicion y solicitudes sobre versiones publicadas', () => {
    component.version.set({ ...mockVersion, estado: 'PUBLICADA' });
    component.actualizarPeso('/vigas', 9);
    component.actualizarEscala('LEVE', 3);
    component.guardarPesos();
    component.guardarEscala();
    component.ejecutarPublicacion();
    expect(component.pesosEditables()[0].peso).toBe(1.5);
    expect(component.escalaEditable().LEVE).toBe(1);
    expect(apiMock.calibrarPesos).not.toHaveBeenCalled();
    expect(apiMock.configurarEscala).not.toHaveBeenCalled();
    expect(apiMock.publicar).not.toHaveBeenCalled();
  });
  it('no duplica guardados ni publica mientras se guarda la escala', () => {
    const pendiente = new Subject<VersionFormulario>();
    apiMock.configurarEscala.mockReturnValue(pendiente);
    component.guardarEscala();
    component.guardarEscala();
    component.guardarPesos();
    component.ejecutarPublicacion();
    expect(apiMock.configurarEscala).toHaveBeenCalledTimes(1);
    expect(apiMock.calibrarPesos).not.toHaveBeenCalled();
    expect(apiMock.publicar).not.toHaveBeenCalled();
    pendiente.next(mockVersion);
    pendiente.complete();
    expect(component.guardandoEscala()).toBe(false);
  });
  it('mantiene la escala anterior cuando el servidor rechaza el cambio', () => {
    component.actualizarEscala('LEVE', 0.5);
    apiMock.configurarEscala.mockReturnValue(throwError(() => ({ status: 403 })));
    component.guardarEscala();
    expect(component.guardandoEscala()).toBe(false);
    expect(component.calibracionPendiente()).toBe(true);
    expect(component.puedePublicar()).toBe(false);
  });
  it('bloquea la publicacion mientras las reglas tengan cambios pendientes', () => {
    expect(component.puedePublicar()).toBe(true);
    component.estadoReglas.set({ pendiente: true, guardando: false });
    component.cambiarSeccion('publicar');
    component.ejecutarPublicacion();
    expect(component.puedePublicar()).toBe(false);
    expect(apiMock.publicar).not.toHaveBeenCalled();
  });
  it('rechaza peso cero, admite peso cinco y limpia el aviso de una operacion anterior', () => {
    component.mensajeExito.set('Escala guardada');
    component.actualizarPeso('/vigas', 0);
    expect(component.mensajeExito()).toBeNull();
    expect(component.pesosPendientes()).toHaveLength(1);
    component.guardarPesos();
    expect(apiMock.calibrarPesos).not.toHaveBeenCalled();
    component.actualizarPeso('/vigas', 5);
    expect(component.errorValidacion()).toBeNull();
    expect(component.pesosPendientes()).toHaveLength(0);
    component.guardarPesos();
    expect(apiMock.calibrarPesos).toHaveBeenCalledWith('v-1', [
      expect.objectContaining({ elementoRef: '/vigas', peso: 5 }),
    ]);
  });
  it('muestra vinculos en editor visual sin selector de codigo JSON', () => {
    component.cambiarSeccion('vinculos');
    fixture.detectChanges();
    const botones = [...fixture.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
    expect(botones.some((b) => b.textContent?.includes('formulario.modeCode'))).toBe(false);
    expect(fixture.nativeElement.querySelector('textarea.codigo-editor')).toBeNull();
  });
  it('presenta pesos pendientes como informacion con detalles cerrados', () => {
    component.actualizarPeso('/vigas', null);
    component.cambiarSeccion('pesos');
    fixture.detectChanges();
    const detalles = fixture.nativeElement.querySelector(
      '.aviso-activa details',
    ) as HTMLDetailsElement;
    expect(detalles).not.toBeNull();
    expect(detalles.open).toBe(false);
    expect(detalles.textContent).toContain('Vigas');
  });
});
