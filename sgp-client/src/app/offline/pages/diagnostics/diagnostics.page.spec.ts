import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { BehaviorSubject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../../../core/services/auth.store';
import { InspeccionApiService } from '../../../features/inspecciones/services/inspeccion-api.service';
import { ConnectivityService } from '../../services/connectivity.service';
import { InspeccionesLocalesService } from '../../services/inspecciones-locales.service';
import { StorageService } from '../../services/storage.service';
import { SincronizacionService } from '../../services/sincronizacion.service';
import { resumirSincronizacion } from '../../services/diagnostico-sync';
import { DiagnosticsPage } from './diagnostics.page';
import es from '../../../../../public/i18n/es.json';

describe('HU-060: diagnóstico accesible y privado', () => {
  const user = signal({ id: 'a' });
  const online = signal(true);
  const resumen$ = new BehaviorSubject(resumirSincronizacion('a', [], [], []));
  const sync = {
    resumen$,
    sincronizando: signal(false),
    reintentar: vi.fn(),
    sincronizarAhora: vi.fn(),
    consultarResumen: vi.fn(),
  };
  const api = { listarMisInspecciones: vi.fn(), listarConflictosSincronizacion: vi.fn() };
  beforeEach(() => {
    user.set({ id: 'a' });
    online.set(true);
    vi.clearAllMocks();
    resumen$.next(resumirSincronizacion('a', [], [], []));
    sync.consultarResumen.mockResolvedValue({ inspeccionesIds: ['i'] });
    api.listarMisInspecciones.mockReturnValue(
      of({
        content: [
          { id: 'remota', autorId: 'a' },
          { id: 'ajena', autorId: 'b' },
        ],
        last: true,
      }),
    );
    api.listarConflictosSincronizacion.mockImplementation((id: string) =>
      of([
        {
          id: 'c-' + id,
          versionServidor: 3,
          versionCliente: 2,
          detectadoEn: '2026-10-20T12:00:00Z',
          copiaServidor: { version: 3, datos: { ancho: 10, password: 'secreto' } },
        },
      ]),
    );
    TestBed.configureTestingModule({
      imports: [
        DiagnosticsPage,
        TranslocoTestingModule.forRoot({
          langs: { es },
          translocoConfig: { availableLangs: ['es'], defaultLang: 'es' },
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: AuthStore, useValue: { user } },
        { provide: ConnectivityService, useValue: { online } },
        { provide: SincronizacionService, useValue: sync },
        { provide: InspeccionApiService, useValue: api },
        {
          provide: InspeccionesLocalesService,
          useValue: { liberarInspeccionesSincronizadas: vi.fn() },
        },
      ],
    });
    vi.spyOn(TestBed.inject(StorageService), 'usage').mockResolvedValue({
      usage: 100,
      quota: 1000,
    });
  });
  async function crear() {
    const fixture = TestBed.createComponent(DiagnosticsPage);
    fixture.detectChanges();
    await fixture.whenStable();
    await vi.waitFor(() => expect(fixture.componentInstance.cargandoConflictos()).toBe(false));
    fixture.detectChanges();
    return fixture;
  }
  it('consulta conflictos locales y remotos incluso con cola vacía, y omite copias sensibles', async () => {
    const fixture = await crear();
    expect(api.listarConflictosSincronizacion).toHaveBeenCalledWith('remota');
    expect(api.listarConflictosSincronizacion).not.toHaveBeenCalledWith('ajena');
    expect(fixture.componentInstance.conflictos()).toHaveLength(2);
    expect(fixture.nativeElement.textContent).not.toContain('secreto');
    expect(fixture.nativeElement.querySelector('details summary')).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('h1')).toHaveLength(1);
    expect(fixture.nativeElement.querySelectorAll('input,textarea')).toHaveLength(0);
  });
  it('muestra cinco intentos, motivo seguro y delega el reintento al motor existente', async () => {
    const fixture = await crear();
    resumen$.next(
      resumirSincronizacion(
        'a',
        [],
        [],
        [
          {
            id: 'op',
            tipo: 'FOTO',
            entityId: 'f',
            propietarioId: 'a',
            idempotencyKey: 'secreto',
            intentos: 5,
            estado: 'INTERVENCION',
            creadoEn: '',
            ultimoError: 'token=privado',
          },
        ],
      ),
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('5 / 5');
    expect(fixture.nativeElement.textContent).toContain('Se agotaron cinco intentos');
    expect(fixture.nativeElement.textContent).not.toContain('privado');
    await fixture.componentInstance.reintentar('op');
    expect(sync.reintentar).toHaveBeenCalledWith('op');
    online.set(false);
    fixture.detectChanges();
    const boton = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((b) => b.textContent?.includes('Reintentar'))!;
    expect(boton.disabled).toBe(true);
  });
  it('retira los conflictos al cambiar de cuenta y no muestra operaciones de la anterior', async () => {
    const fixture = await crear();
    online.set(false);
    user.set({ id: 'b' });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.conflictos()).toEqual([]);
    expect(fixture.nativeElement.textContent).not.toContain('5 / 5');
  });
  it('conserva controles nativos de teclado, estados accesibles y foco en botones', async () => {
    const fixture = await crear();
    const boton = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    boton.focus();
    expect(document.activeElement).toBe(boton);
    expect(fixture.nativeElement.querySelector('[aria-live="polite"]')).not.toBeNull();
    expect(
      fixture.nativeElement.querySelector('[aria-labelledby="conflictos-titulo"]'),
    ).not.toBeNull();
  });
});
