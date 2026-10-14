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
    component.esquemaTexto.set('{"type": "object", "title": "Nuevo"}');
    component.guardarEsquema();

    expect(apiMock.actualizarBorrador).toHaveBeenCalled();
  });

  it('debe rechazar esquema con JSON malformado', () => {
    component.esquemaTexto.set('{invalido json');
    component.guardarEsquema();

    expect(component.errorValidacion()).not.toBeNull();
    expect(apiMock.actualizarBorrador).not.toHaveBeenCalled();
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
