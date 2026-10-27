import { RouterLink } from '@angular/router';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DecimalPipe, DatePipe, JsonPipe } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { firstValueFrom } from 'rxjs';
import { StorageService } from '../../services/storage.service';
import { AuthStore } from '../../../core/services/auth.store';
import { SincronizacionService } from '../../services/sincronizacion.service';
import { InspeccionesLocalesService } from '../../services/inspecciones-locales.service';
import { ConnectivityService } from '../../services/connectivity.service';
import { copiaTecnica } from '../../services/diagnostico-sync';
import { InspeccionApiService } from '../../../features/inspecciones/services/inspeccion-api.service';
import { ConflictoSincronizacionResponse } from '../../../features/inspecciones/models/inspeccion.models';

@Component({
  selector: 'app-diagnostics-page',
  standalone: true,
  imports: [DecimalPipe, DatePipe, JsonPipe, RouterLink, TranslocoPipe, ButtonModule],
  templateUrl: './diagnostics.page.html',
  styleUrl: './diagnostics.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DiagnosticsPage {
  readonly storage = inject(StorageService);
  readonly auth = inject(AuthStore);
  readonly sync = inject(SincronizacionService);
  readonly connectivity = inject(ConnectivityService);
  private readonly inspecciones = inject(InspeccionesLocalesService);
  private readonly api = inject(InspeccionApiService);
  readonly resumen = toSignal(this.sync.resumen$);
  readonly conflictos = signal<
    (ConflictoSincronizacionResponse & { inspeccionId: string; propietarioId: string })[]
  >([]);
  readonly cargandoConflictos = signal(false);
  readonly errorConflictos = signal(false);
  readonly errorAlmacenamiento = signal(false);
  readonly errorAccion = signal(false);
  private generacion = 0;
  private propietario?: string;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.generacion++);
    effect(() => {
      const owner = this.auth.user()?.id;
      const online = this.connectivity.online();
      const sincronizando = this.sync.sincronizando();
      untracked(() => {
        if (owner !== this.propietario) {
          this.propietario = owner;
          this.generacion++;
          this.conflictos.set([]);
          this.errorConflictos.set(false);
          this.cargandoConflictos.set(false);
        }
        if (owner && online && !sincronizando) void this.cargarConflictos();
      });
    });
    void this.refresh();
  }
  async persist(): Promise<void> {
    await this.storage.requestPersistentStorage();
    await this.refresh();
  }
  async refresh(): Promise<void> {
    this.errorAlmacenamiento.set(false);
    try {
      await this.storage.usage();
    } catch {
      this.errorAlmacenamiento.set(true);
    }
  }
  async liberarEspacio(): Promise<void> {
    const propietarioId = this.auth.user()?.id;
    if (!propietarioId) return;
    this.errorAccion.set(false);
    try {
      await this.inspecciones.liberarInspeccionesSincronizadas(propietarioId);
      await this.refresh();
    } catch {
      this.errorAccion.set(true);
    }
  }
  async reintentar(id: string): Promise<void> {
    this.errorAccion.set(false);
    try {
      await this.sync.reintentar(id);
    } catch {
      this.errorAccion.set(true);
    }
  }
  async sincronizar(): Promise<void> {
    this.errorAccion.set(false);
    try {
      await this.sync.sincronizarAhora();
      await this.refresh();
    } catch {
      this.errorAccion.set(true);
    }
  }
  async cargarConflictos(): Promise<void> {
    const owner = this.auth.user()?.id;
    if (!owner || !this.connectivity.online()) return;
    const generacion = ++this.generacion;
    const vigente = () => this.auth.user()?.id === owner && this.generacion === generacion;
    this.cargandoConflictos.set(true);
    this.errorConflictos.set(false);
    try {
      // Incluye inspecciones sincronizadas sin cola y las que ya no están en caché local.
      const ids = new Set((await this.sync.consultarResumen()).inspeccionesIds);
      let pagina = 0;
      while (vigente()) {
        const respuesta = await firstValueFrom(
          this.api.listarMisInspecciones(pagina, 100, undefined, true),
        );
        if (!vigente()) return;
        respuesta.content.filter((i) => i.autorId === owner).forEach((i) => ids.add(i.id));
        if (respuesta.last) break;
        pagina++;
      }
      const encontrados: (ConflictoSincronizacionResponse & {
        inspeccionId: string;
        propietarioId: string;
      })[] = [];
      let incompleta = false;
      for (const id of ids) {
        if (!vigente()) return;
        try {
          const conflictos = await firstValueFrom(this.api.listarConflictosSincronizacion(id));
          if (!vigente()) return;
          encontrados.push(
            ...conflictos.map((c) => ({
              ...c,
              inspeccionId: id,
              propietarioId: owner,
              copiaServidor: copiaTecnica(c.copiaServidor),
            })),
          );
        } catch {
          incompleta = true;
        }
      }
      if (vigente()) {
        this.conflictos.set(encontrados);
        this.errorConflictos.set(incompleta);
      }
    } catch {
      if (vigente()) this.errorConflictos.set(true);
    } finally {
      if (vigente()) this.cargandoConflictos.set(false);
    }
  }
}
