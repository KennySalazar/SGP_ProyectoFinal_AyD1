import { TestBed } from '@angular/core/testing';
import { computed, signal } from '@angular/core';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { AuthStore } from '../../../../core/services/auth.store';
import { UserSession } from '../../../../core/models/auth.models';
import { ConnectivityService } from '../../../../offline/services/connectivity.service';
import { PuenteApiService } from '../../../puentes/services/puente-api.service';
import { SalidaApiService } from '../../services/salida-api.service';
import { PreparacionSalidaService } from '../../services/preparacion-salida.service';
import { PrepararSalidaPage } from './preparar-salida.page';
import { ResumenSalida } from '../../models/salida.models';

describe('Pantalla preparar salida', () => {
  const online = signal(true);
  const actor = signal<UserSession>({
    id: 'u1',
    email: '',
    role: 'CATEDRATICO',
    verified: true,
    activated: true,
    active: true,
    twoFactorEnabled: false,
  });
  const ultima = signal<ResumenSalida | null>(null);
  const ocupada = signal(false);
  const preparar = vi.fn().mockResolvedValue(true);
  const estimar = vi.fn();
  const buscar = vi.fn();
  beforeEach(() => {
    online.set(true);
    ocupada.set(false);
    ultima.set(null);
    actor.set({ ...actor(), id: 'u1', role: 'CATEDRATICO' });
    preparar.mockClear();
    estimar.mockReturnValue(
      of({
        numeroPuentes: 1,
        numeroInspeccionesPrevias: 0,
        tamanoEstimadoBytes: 1000,
        codigoVersionFormulario: 'v3',
      }),
    );
    buscar.mockReturnValue(
      of({
        content: [
          {
            id: 'p1',
            codigo: 'GT-01',
            nombre: 'Puente',
            departamento: { nombre: 'Guatemala' },
            municipio: { nombre: 'Amatitlan' },
          },
        ],
        totalElements: 1,
        totalPages: 1,
      }),
    );
    TestBed.configureTestingModule({
      imports: [PrepararSalidaPage],
      providers: [
        { provide: AuthStore, useValue: { user: actor } },
        { provide: ConnectivityService, useValue: { online } },
        { provide: PuenteApiService, useValue: { listarDepartamentos: () => of({ content: [] }) } },
        { provide: SalidaApiService, useValue: { buscar, estimar } },
        {
          provide: PreparacionSalidaService,
          useValue: {
            permitida: computed(() => true),
            ocupada,
            ultima,
            nuevaVersion: signal(false),
            error: signal(''),
            aviso: signal(''),
            persistente: signal(null),
            espacio: signal(null),
            paso: signal(0),
            cuerpo: (ids: string[]) => ({ dispositivoId: 'd', puentesIds: ids }),
            preparar,
            quitar: vi.fn(),
          },
        },
      ],
    });
  });
  afterEach(() => TestBed.resetTestingModule());

  it('sin conexion deshabilita preparar y muestra fecha anterior', async () => {
    online.set(false);
    ultima.set({
      propietarioId: 'u1',
      id: 's',
      dispositivoId: 'd',
      preparadoEn: '2026-10-20T01:00:00Z',
      versionFormularioId: 'v3',
      codigoVersionFormulario: 'v3',
      numeroPuentes: 2,
      asignaciones: [],
    });
    const f = TestBed.createComponent(PrepararSalidaPage);
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    const botones = [...f.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
    expect(botones.find((b) => b.textContent?.trim() === 'Preparar salida')?.disabled).toBe(true);
    expect(f.nativeElement.textContent).toContain('Necesitas conexion');
    expect(f.nativeElement.textContent).toContain('v3');
  });

  it('requiere estimacion y conserva seleccion durante el guardado', async () => {
    const f = TestBed.createComponent(PrepararSalidaPage);
    f.detectChanges();
    await f.whenStable();
    const page = f.componentInstance;
    page.elegir('p1', true);
    await page.estimar();
    ocupada.set(true);
    f.detectChanges();
    expect(page.seleccion()).toEqual(['p1']);
    ocupada.set(false);
    await page.preparar();
    expect(preparar).toHaveBeenCalledWith(['p1']);
  });

  it('estudiante prepara todas las asignaciones sin seleccion arbitraria', async () => {
    actor.set({ ...actor(), role: 'ESTUDIANTE' });
    const f = TestBed.createComponent(PrepararSalidaPage);
    f.detectChanges();
    await f.whenStable();
    await f.componentInstance.estimar();
    await f.componentInstance.preparar();
    f.detectChanges();
    expect(preparar).toHaveBeenCalledWith([]);
    expect(f.nativeElement.querySelector('input[type=checkbox]')).toBeNull();
  });

  it('no permite seleccionar mas de 200 puentes', async () => {
    const f = TestBed.createComponent(PrepararSalidaPage);
    f.detectChanges();
    await f.whenStable();
    const p = f.componentInstance;
    p.seleccion.set(Array.from({ length: 200 }, (_, n) => String(n)));
    p.elegir('otro', true);
    expect(p.seleccion()).toHaveLength(200);
    expect(p.mensaje()).toContain('200');
  });

  it('al cambiar cuenta elimina la seleccion y la confirmacion anterior', async () => {
    const f = TestBed.createComponent(PrepararSalidaPage);
    f.detectChanges();
    await f.whenStable();
    f.componentInstance.elegir('p1', true);
    f.componentInstance.listo.set(true);
    actor.set({ ...actor(), id: 'u2' });
    f.detectChanges();
    await f.whenStable();
    expect(f.componentInstance.seleccion()).toEqual([]);
    expect(f.componentInstance.listo()).toBe(false);
  });
});
