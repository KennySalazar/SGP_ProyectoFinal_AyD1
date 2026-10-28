import { InspeccionesLocalesService } from '../../../../offline/services/inspecciones-locales.service';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, EventEmitter, Input, Output, Pipe, PipeTransform, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { UserSession } from '../../../../core/models/auth.models';
import { AuthStore } from '../../../../core/services/auth.store';
import { PuenteResponse } from '../../../puentes/models/puente.models';
import { IniciarInspeccionDialogComponent } from '../../components/iniciar-inspeccion-dialog/iniciar-inspeccion-dialog.component';
import { InspeccionResumenResponse } from '../../models/inspeccion.models';
import { MisInspeccionesPage } from './mis-inspecciones.page';

@Pipe({ name: 'transloco' })
class TraduccionTestPipe implements PipeTransform {
  transform(key: string): string {
    return key;
  }
}

@Component({ selector: 'app-iniciar-inspeccion-dialog', standalone: true, template: '' })
class IniciarInspeccionDialogStubComponent {
  @Input() visible = false;
  @Input() puente: PuenteResponse | null = null;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() inspeccionIniciada = new EventEmitter<unknown>();
}

const LISTADO = '/api/v1/inspecciones?pagina=0&tamanio=20';

function inspeccion(overrides: Partial<InspeccionResumenResponse>): InspeccionResumenResponse {
  return {
    id: 'inspeccion-1',
    puenteId: 'puente-1',
    codigoPuente: 'GT-01',
    nombrePuente: 'Puente El Trébol',
    departamentoNombre: 'Guatemala',
    municipioNombre: 'Guatemala',
    versionFormularioId: 'version-1',
    codigoVersionFormulario: 'MCGRP-1',
    autorId: 'usuario-1',
    rolAutorCreacion: 'CATEDRATICO',
    nombreAutorCapturado: 'catedratico@cunoc.edu.gt',
    estado: 'PUBLICADA',
    tipo: 'ORIGINAL',
    fechaInspeccion: '2026-10-01',
    porcentajeAvance: 100,
    creadoEn: '2026-10-01T10:00:00Z',
    ...overrides,
  } as InspeccionResumenResponse;
}

describe('MisInspeccionesPage', () => {
  let fixture: ComponentFixture<MisInspeccionesPage>;
  let page: MisInspeccionesPage;
  let http: HttpTestingController;

  const authStoreMock = { user: signal<UserSession | null>(null) };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MisInspeccionesPage],
      providers: [
        {
          provide: InspeccionesLocalesService,
          useValue: { listarPorUsuario: vi.fn().mockResolvedValue([]) },
        },
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthStore, useValue: authStoreMock },
        { provide: TranslocoService, useValue: { translate: (clave: string) => clave } },
      ],
    })
      .overrideComponent(MisInspeccionesPage, {
        remove: { imports: [TranslocoPipe, IniciarInspeccionDialogComponent] },
        add: { imports: [TraduccionTestPipe, IniciarInspeccionDialogStubComponent] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(MisInspeccionesPage);
    page = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    await Promise.resolve();
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  function cargarListado(contenido: InspeccionResumenResponse[]): void {
    http.expectOne(LISTADO).flush({
      content: contenido,
      number: 0,
      size: 20,
      totalElements: contenido.length,
      empty: contenido.length === 0,
    });
    fixture.detectChanges();
  }

  function textoDe(selector: string): string {
    return (fixture.nativeElement as HTMLElement).querySelector(selector)?.textContent ?? '';
  }

  it('ofrece corregir una inspección publicada vigente', () => {
    cargarListado([inspeccion({})]);

    expect(page.puedeCorregir(inspeccion({}))).toBe(true);
    expect(textoDe('.acciones')).toContain('inspecciones.correccion.corregir');
  });

  it('marca como superada la inspección reemplazada y no permite corregirla', () => {
    const superada = inspeccion({ superadaPorId: 'correccion-1' });
    cargarListado([superada]);

    expect(page.puedeCorregir(superada)).toBe(false);
    expect(textoDe('.fila-titulo')).toContain('inspecciones.correccion.tagSuperada');
    expect(textoDe('.acciones')).toContain('inspecciones.mis.noActions');
  });

  it('identifica las correcciones y muestra su motivo', () => {
    cargarListado([
      inspeccion({
        estado: 'BORRADOR',
        tipo: 'CORRECCION',
        supersedeAId: 'inspeccion-0',
        motivoCorreccion: 'Fecha errónea',
      }),
    ]);

    expect(textoDe('.fila-titulo')).toContain('inspecciones.correccion.tagCorreccion');
    expect(textoDe('.motivo-correccion')).toContain('inspecciones.correccion.motivoListado');
  });

  it('crea la corrección con el motivo y abre su borrador', () => {
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const publicada = inspeccion({});
    cargarListado([publicada]);

    page.abrirCorreccion(publicada);
    page.motivoCorreccion.set('  Fecha errónea  ');
    page.crearCorreccion();

    const request = http.expectOne('/api/v1/inspecciones/inspeccion-1/correcciones');
    expect(request.request.body).toEqual({ motivo: 'Fecha errónea' });
    request.flush(inspeccion({ id: 'correccion-1', estado: 'BORRADOR', tipo: 'CORRECCION' }));

    expect(page.inspeccionCorregir()).toBeNull();
    expect(navegar).toHaveBeenCalledWith(['/inspecciones', 'correccion-1', 'borrador']);
  });

  it('no envía la corrección sin motivo', () => {
    const publicada = inspeccion({});
    cargarListado([publicada]);

    page.abrirCorreccion(publicada);
    page.motivoCorreccion.set('   ');
    page.crearCorreccion();

    http.expectNone('/api/v1/inspecciones/inspeccion-1/correcciones');
  });

  it('muestra en el diálogo el rechazo del servidor', () => {
    const publicada = inspeccion({});
    cargarListado([publicada]);

    page.abrirCorreccion(publicada);
    page.motivoCorreccion.set('Fecha errónea');
    page.crearCorreccion();

    http
      .expectOne('/api/v1/inspecciones/inspeccion-1/correcciones')
      .flush(
        { code: 'inspeccion_superada', detail: 'La inspección ya fue superada.' },
        { status: 409, statusText: 'Conflict' },
      );

    expect(page.errorCorreccion()).toBe('La inspección ya fue superada.');
    expect(page.inspeccionCorregir()).toBe(publicada);
    expect(page.corrigiendo()).toBe(false);
  });
  it('conserva el estado del servidor frente a una copia local ya sincronizada', async () => {
    cargarListado([]);
    authStoreMock.user.set({ id: 'usuario-1', role: 'ESTUDIANTE' } as UserSession);
    vi.mocked(TestBed.inject(InspeccionesLocalesService).listarPorUsuario).mockResolvedValue([
      {
        id: 'inspeccion-1',
        puenteId: 'puente-1',
        propietarioId: 'usuario-1',
        estado: 'ENVIADA',
        actualizadoEn: '2026-10-01T00:00:00Z',
        sincronizado: true,
        payload: { borrador: inspeccion({ estado: 'ENVIADA' }) },
      },
    ]);
    page.cargar();
    await Promise.resolve();
    cargarListado([inspeccion({ estado: 'EN_REVISION' })]);
    expect(page.pagina()?.content[0].estado).toBe('EN_REVISION');
  });

  it('ofrece consultar la revision para una inspeccion con cambios solicitados', () => {
    cargarListado([inspeccion({ estado: 'CAMBIOS_SOLICITADOS' })]);
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent;
    expect(texto).toContain('inspecciones.mis.viewReview');
    expect(texto).not.toContain('inspecciones.mis.delete');
  });
});
