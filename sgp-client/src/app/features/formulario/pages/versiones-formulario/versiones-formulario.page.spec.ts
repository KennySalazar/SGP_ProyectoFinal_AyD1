import { AuthStore } from '../../../../core/services/auth.store';
import { Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { VersionFormulario, VersionFormularioResumen } from '../../models/formulario.models';
import { FormularioApiService } from '../../services/formulario-api.service';
import { VersionesFormularioPage } from './versiones-formulario.page';

@Pipe({ name: 'transloco', standalone: true })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

describe('VersionesFormularioPage', () => {
  let component: VersionesFormularioPage;
  let fixture: ComponentFixture<VersionesFormularioPage>;
  let apiMock: {
    listarVersiones: ReturnType<typeof vi.fn>;
    cargarPlantillaSieca: ReturnType<typeof vi.fn>;
    crear: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  const mockVersiones: VersionFormularioResumen[] = [
    {
      id: '1',
      codigo: 'SIECA-v1',
      estado: 'PUBLICADA',
      activa: true,
      versionAlgoritmoIc: 'IC-SIECA-v1',
      creadoEn: '2026-10-13T00:00:00Z',
      publicadoEn: '2026-10-13T01:00:00Z',
    },
  ];

  beforeEach(async () => {
    apiMock = {
      listarVersiones: vi.fn().mockReturnValue(of(mockVersiones)),
      cargarPlantillaSieca: vi.fn().mockReturnValue(
        of({
          id: '1',
          codigo: 'SIECA-v1',
          estado: 'BORRADOR',
          activa: false,
        } as unknown as VersionFormulario),
      ),
      crear: vi.fn().mockReturnValue(
        of({
          id: '2',
          codigo: 'SIECA-v2',
          estado: 'BORRADOR',
          activa: false,
        } as unknown as VersionFormulario),
      ),
    };

    TestBed.configureTestingModule({
      imports: [VersionesFormularioPage],
      providers: [
        {
          provide: AuthStore,
          useValue: { user: () => ({ role: 'ADMINISTRADOR' }) },
        },
        provideRouter([]),
        { provide: FormularioApiService, useValue: apiMock },
        {
          provide: ApiErrorService,
          useValue: { mostrar: vi.fn(), normalize: vi.fn() },
        },
        {
          provide: TranslocoService,
          useValue: { translate: (k: string) => k },
        },
      ],
    });

    TestBed.overrideComponent(VersionesFormularioPage, {
      remove: { imports: [TranslocoPipe] },
      add: { imports: [TraduccionTestPipe] },
    });

    await TestBed.compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockImplementation(() => Promise.resolve(true));

    fixture = TestBed.createComponent(VersionesFormularioPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse e inicializar la lista de versiones', () => {
    expect(component).toBeTruthy();
    expect(apiMock.listarVersiones).toHaveBeenCalled();
    expect(component.versiones()).toEqual(mockVersiones);
  });

  it('debe cargar plantilla inicial SIECA-v1', () => {
    component.cargarPlantillaInicial();
    expect(apiMock.cargarPlantillaSieca).toHaveBeenCalledWith('SIECA-v2');
    expect(router.navigate).toHaveBeenCalledWith(['/admin/formularios', '1']);
  });

  it('debe crear nueva versión y navegar al detalle', () => {
    component.nuevoForm.setValue({ codigo: 'SIECA-v2', versionBaseId: '1' });
    component.crearVersion();

    expect(apiMock.crear).toHaveBeenCalledWith({
      codigo: 'SIECA-v2',
      versionBaseId: '1',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/admin/formularios', '2']);
  });
  it('al elegir la plantilla canónica no envía base y solicita la plantilla SIECA', () => {
    component.nuevoForm.setValue({ codigo: ' SIECA-v2 ', versionBaseId: null });
    component.crearVersion();
    expect(apiMock.crear).toHaveBeenCalledWith({
      codigo: 'SIECA-v2',
      versionBaseId: null,
      cargarPlantillaInicial: true,
    });
    expect(router.navigate).toHaveBeenCalledWith(['/admin/formularios', '2']);
  });

  it('no envia solicitudes si el formulario es invalido o ya se esta creando', () => {
    component.nuevoForm.setValue({ codigo: '', versionBaseId: null });
    component.crearVersion();
    expect(apiMock.crear).not.toHaveBeenCalled();
    component.nuevoForm.setValue({ codigo: 'prueba', versionBaseId: null });
    component.creandoVersion.set(true);
    component.crearVersion();
    expect(apiMock.crear).not.toHaveBeenCalled();
  });
  it('evita cargar dos veces mientras una solicitud esta en curso', () => {
    component.cargandoPlantilla.set(true);
    component.cargarPlantillaInicial();
    expect(apiMock.cargarPlantillaSieca).not.toHaveBeenCalled();
  });
  it('elige un codigo libre aunque existan varias plantillas', () => {
    component.versiones.set([
      ...mockVersiones,
      { ...mockVersiones[0], id: '2', codigo: 'SIECA-v2' },
    ]);
    component.cargarPlantillaInicial();
    expect(apiMock.cargarPlantillaSieca).toHaveBeenCalledWith('SIECA-v3');
  });

  it('oculta la carga de plantilla y herramientas administrativas al catedratico', () => {
    vi.spyOn(component.auth, 'user').mockReturnValue({
      role: 'CATEDRATICO',
    } as never);
    fixture.destroy();
    fixture = TestBed.createComponent(VersionesFormularioPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.cargarPlantillaInicial();
    expect(apiMock.cargarPlantillaSieca).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('nav.accesos')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('formulario.loadTemplate');
  });
});
