import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { RevisionBandejaResponse } from '../../models/revision.models';
import { RevisionApiService } from '../../services/revision-api.service';
import { traduccionesDePrueba } from '../../../formulario/motor/testing/traducciones';
import { BandejaRevisionPage } from './bandeja-revision.page';

describe('Bandeja: navegación al detalle HU-037', () => {
  const api = {
    listar: vi.fn(),
    iniciar: vi.fn(),
    cargarContador: vi.fn(),
    listarRevisadas: vi.fn(),
  };
  beforeEach(() => {
    vi.clearAllMocks();
    api.listarRevisadas.mockReturnValue(
      of({ content: [], empty: true, number: 0, size: 20, totalElements: 0 }),
    );
    api.listar.mockReturnValue(
      of({ content: [], empty: true, number: 0, size: 20, totalElements: 0 }),
    );
    api.iniciar.mockReturnValue(of({}));
    api.cargarContador.mockReturnValue(of({ pendientes: 0 }));
    TestBed.configureTestingModule({
      imports: [BandejaRevisionPage, traduccionesDePrueba()],
      providers: [
        provideRouter([]),
        { provide: RevisionApiService, useValue: api },
        { provide: ApiErrorService, useValue: { normalize: vi.fn() } },
      ],
    });
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });
  it('inicia una pendiente y navega al ciclo conservando contador y recarga', () => {
    const page = TestBed.createComponent(BandejaRevisionPage).componentInstance;
    const llamadas = api.cargarContador.mock.calls.length;
    page.iniciar({ revisionId: 'ciclo', estadoRevision: 'PENDIENTE' } as RevisionBandejaResponse);
    expect(api.iniciar).toHaveBeenLastCalledWith('ciclo');
    expect(api.cargarContador.mock.calls.length).toBe(llamadas + 1);
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/revisiones', 'ciclo']);
  });
  it('reanuda sin ejecutar iniciar, ni cambiar filtros o contador', () => {
    const page = TestBed.createComponent(BandejaRevisionPage).componentInstance;
    page.puente.set('Central');
    const inicios = api.iniciar.mock.calls.length;
    const contadores = api.cargarContador.mock.calls.length;
    const listas = api.listar.mock.calls.length;
    page.iniciar({ revisionId: 'ciclo', estadoRevision: 'EN_CURSO' } as RevisionBandejaResponse);
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/revisiones', 'ciclo']);
    expect(api.iniciar.mock.calls.length).toBe(inicios);
    expect(api.cargarContador.mock.calls.length).toBe(contadores);
    expect(api.listar.mock.calls.length).toBe(listas);
    expect(page.puente()).toBe('Central');
  });
  it('muestra los tres veredictos resueltos y abre el ciclo sin iniciarlo', () => {
    const revisadas = ['APROBADA', 'RECHAZADA', 'CAMBIOS_SOLICITADOS'].map((veredicto, i) => ({
      revisionId: 'resuelta-' + i,
      inspeccionId: 'inspeccion',
      puenteCodigo: 'P-001',
      puenteNombre: 'Puente Central',
      estudianteNombre: 'Ana López',
      numeroCiclo: i + 1,
      enviadaEn: '2026-10-18T12:00:00Z',
      resueltaEn: '2026-10-19T12:00:00Z',
      veredicto,
    }));
    api.listarRevisadas.mockReturnValue(
      of({ content: revisadas, empty: false, number: 0, size: 20, totalElements: 3 }),
    );
    const fixture = TestBed.createComponent(BandejaRevisionPage);
    fixture.componentInstance.cambiarVista('revisadas');
    fixture.detectChanges();
    const html = fixture.nativeElement as HTMLElement;
    expect(html.textContent).toContain('Aprobada');
    expect(html.textContent).toContain('Rechazada');
    expect(html.textContent).toContain('Cambios solicitados');
    expect(html.querySelector('a[href="/revisiones/resuelta-0"]')).not.toBeNull();
    expect(api.iniciar).not.toHaveBeenCalled();
    expect(api.listar).toHaveBeenCalledTimes(1);
    fixture.componentInstance.cambiarPagina({ page: 1, rows: 10 });
    expect(api.listarRevisadas).toHaveBeenLastCalledWith(1, 10);
    fixture.destroy();
  });
  it('carga revisadas aunque pendientes siga cargando y conserva sus filtros', () => {
    const pendientes = new Subject<never>();
    api.listar.mockReturnValue(pendientes);
    const fixture = TestBed.createComponent(BandejaRevisionPage);
    const page = fixture.componentInstance;
    page.puente.set('Central');
    page.cambiarVista('revisadas');
    expect(api.listarRevisadas).toHaveBeenCalledWith(0, 20);
    expect(page.revisadas()?.empty).toBe(true);
    page.cambiarVista('pendientes');
    expect(page.puente()).toBe('Central');
    expect(api.listar).toHaveBeenCalledTimes(1);
    fixture.destroy();
  });
  it('muestra un error recuperable y recarga revisadas al volver a la pestaña', () => {
    api.listarRevisadas.mockReturnValue(throwError(() => ({ status: 500 })));
    const fixture = TestBed.createComponent(BandejaRevisionPage);
    const page = fixture.componentInstance;
    page.cambiarVista('revisadas');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No se pudieron cargar');
    api.listarRevisadas.mockReturnValue(
      of({ content: [], empty: true, number: 0, size: 20, totalElements: 0 }),
    );
    page.cargarRevisadas();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Aún no tienes revisiones resueltas');
    page.cambiarVista('pendientes');
    page.cambiarVista('revisadas');
    expect(api.listarRevisadas).toHaveBeenCalledTimes(3);
    fixture.destroy();
  });
  it('no navega si el inicio falla', () => {
    api.iniciar.mockReturnValue(throwError(() => ({ status: 409 })));
    const page = TestBed.createComponent(BandejaRevisionPage).componentInstance;
    page.iniciar({ revisionId: 'ciclo', estadoRevision: 'PENDIENTE' } as RevisionBandejaResponse);
    expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
  });
});
