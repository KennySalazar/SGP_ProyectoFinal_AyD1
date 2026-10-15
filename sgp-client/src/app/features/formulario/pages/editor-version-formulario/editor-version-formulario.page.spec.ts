import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
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
    reglasEvaluacionIc: {},
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

    // Editar sección
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

    // Eliminar pregunta
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
  });

  it('debe gestionar vínculos de almacenamiento de forma visual', () => {
    expect(component.modoVinculos()).toBe('visual');
    component.cambiarModoVinculos('codigo');
    expect(component.modoVinculos()).toBe('codigo');
    component.cambiarModoVinculos('visual');
    expect(component.modoVinculos()).toBe('visual');

    // Crear vínculo
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

    // Filtro
    component.filtroVinculos.set('puente');
    expect(component.vinculosFiltrados()).toHaveLength(1);
    component.filtroVinculos.set('inexistente');
    expect(component.vinculosFiltrados()).toHaveLength(0);
    component.filtroVinculos.set('');

    // Editar vínculo
    component.abrirEditarVinculo(vinc);
    expect(component.modoVinculo()).toBe('editar');
    component.vinculoForm.controls.columnaDestino.setValue('puente.codigo_oficial');
    component.guardarVinculoModal();

    expect(component.vinculosVisuales()[0].columnaDestino).toBe('puente.codigo_oficial');

    // Guardar vínculos vía API
    component.guardarVinculos();
    expect(apiMock.actualizarBorrador).toHaveBeenCalled();

    // Eliminar vínculo
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
});
