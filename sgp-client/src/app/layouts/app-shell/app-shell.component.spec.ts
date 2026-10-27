import { TestBed } from '@angular/core/testing';
import { signal, NO_ERRORS_SCHEMA } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { BehaviorSubject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from '../../core/services/auth.store';
import { PwaUpdateService } from '../../core/services/pwa-update.service';
import { RevisionApiService } from '../../features/revision/services/revision-api.service';
import { ConnectivityService } from '../../offline/services/connectivity.service';
import { SincronizacionService } from '../../offline/services/sincronizacion.service';
import { resumirSincronizacion } from '../../offline/services/diagnostico-sync';
import { AvisoSalidaComponent } from '../../features/salida/components/aviso-salida.component';
import { CampanaAvisosComponent } from '../../features/notificaciones/components/campana-avisos/campana-avisos.component';
import { AppShellComponent } from './app-shell.component';
import es from '../../../../public/i18n/es.json';

describe('HU-060: indicador permanente', () => {
  const user = signal({
    id: 'a',
    role: 'ESTUDIANTE',
    nombreUsuario: 'a',
    nombreCompleto: 'Usuario',
  });
  const online = signal(false);
  const resumen$ = new BehaviorSubject(resumirSincronizacion('a', [], [], []));
  beforeEach(() => {
    online.set(false);
    resumen$.next(resumirSincronizacion('a', [], [], []));
    TestBed.configureTestingModule({
      imports: [
        AppShellComponent,
        TranslocoTestingModule.forRoot({
          langs: { es },
          translocoConfig: { availableLangs: ['es'], defaultLang: 'es' },
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: AuthStore, useValue: { user } },
        { provide: ConnectivityService, useValue: { online } },
        {
          provide: SincronizacionService,
          useValue: { resumen$, iniciar: vi.fn(), sincronizando: signal(false) },
        },
        { provide: PwaUpdateService, useValue: { disponible: () => false } },
        { provide: RevisionApiService, useValue: {} },
      ],
    });
    TestBed.overrideComponent(AvisoSalidaComponent, { set: { template: '', providers: [] } });
    TestBed.overrideComponent(CampanaAvisosComponent, { set: { template: '', providers: [] } });
    // Los componentes hijos no forman parte de la prueba del indicador.
    TestBed.overrideComponent(AppShellComponent, {
      remove: { imports: [AvisoSalidaComponent, CampanaAvisosComponent] },
      add: { schemas: [NO_ERRORS_SCHEMA] },
    });
  });
  it('no anuncia todo sincronizado con cambios fuera de cola y reacciona a conectividad', () => {
    // Aislar hijos con selectores reales sin crear servicios HTTP de otras features.
    const fixture = TestBed.createComponent(AppShellComponent);
    resumen$.next(
      resumirSincronizacion(
        'a',
        [
          {
            id: 'i',
            propietarioId: 'a',
            puenteId: 'p',
            estado: 'BORRADOR',
            payload: {},
            actualizadoEn: '',
            sincronizado: false,
            actualizacionLocalPendiente: true,
          },
        ],
        [],
        [],
      ),
    );
    fixture.detectChanges();
    const indicador = () => fixture.nativeElement.querySelector('.connectivity').textContent;
    expect(indicador()).toContain('1');
    expect(indicador()).not.toContain('Todo sincronizado');
    online.set(true);
    fixture.detectChanges();
    expect(indicador()).toContain('En linea');
    resumen$.next(resumirSincronizacion('a', [], [], []));
    fixture.detectChanges();
    expect(indicador()).toContain('Todo sincronizado');
  });
});
