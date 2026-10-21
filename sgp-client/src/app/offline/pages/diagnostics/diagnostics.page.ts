import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { liveQuery } from 'dexie';
import { AsyncPipe, DecimalPipe } from '@angular/common';
import { sgpDb, SyncQueueItem } from '../../db/sgp-db';
import { StorageService } from '../../services/storage.service';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthStore } from '../../../core/services/auth.store';
import { SincronizacionService } from '../../services/sincronizacion.service';
import { InspeccionesLocalesService } from '../../services/inspecciones-locales.service';
@Component({
  selector: 'app-diagnostics-page',
  standalone: true,
  imports: [AsyncPipe, DecimalPipe, TranslocoPipe],
  templateUrl: './diagnostics.page.html',
  styleUrl: './diagnostics.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DiagnosticsPage {
  readonly storage = inject(StorageService);
  readonly auth = inject(AuthStore);
  readonly sync = inject(SincronizacionService);
  private readonly inspecciones = inject(InspeccionesLocalesService);
  readonly pending$ = liveQuery(async () => {
    const owner = this.auth.user()?.id;
    return (await sgpDb.cola_sync.toArray()).filter((item) => item.propietarioId === owner).length;
  });
  readonly estimate = signal<StorageEstimate>({});
  readonly cola$ = this.sync.cola$;
  propietario(item: SyncQueueItem): boolean {
    return item.propietarioId === this.auth.user()?.id;
  }
  async persist(): Promise<void> {
    await this.storage.requestPersistentStorage();
    this.estimate.set(await this.storage.usage());
  }
  async refresh(): Promise<void> {
    this.estimate.set(await this.storage.usage());
  }
  async liberarEspacio(): Promise<void> {
    const propietarioId = this.auth.user()?.id;
    if (propietarioId) await this.inspecciones.liberarInspeccionesSincronizadas(propietarioId);
  }
}
