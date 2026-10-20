import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { RevisionBandejaResponse } from '../../models/revision.models';
import { RevisionApiService } from '../../services/revision-api.service';
import { traduccionesDePrueba } from '../../../formulario/motor/testing/traducciones';
import { BandejaRevisionPage } from './bandeja-revision.page';

describe('Bandeja: navegación al detalle HU-037', () => {
  const api = { listar: vi.fn(), iniciar: vi.fn(), cargarContador: vi.fn() };
  beforeEach(() => {
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
  it('no navega si el inicio falla', () => {
    api.iniciar.mockReturnValue(throwError(() => ({ status: 409 })));
    const page = TestBed.createComponent(BandejaRevisionPage).componentInstance;
    page.iniciar({ revisionId: 'ciclo', estadoRevision: 'PENDIENTE' } as RevisionBandejaResponse);
    expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
  });
});
