import { Injectable, inject, signal } from '@angular/core';
import { SwUpdate } from '@angular/service-worker';
import { sgpDb } from '../../offline/db/sgp-db';

@Injectable({ providedIn: 'root' })
export class PwaUpdateService {
  private readonly updates = inject(SwUpdate, { optional: true });
  readonly disponible = signal(false);
  readonly pospuesta = signal(false);
  readonly preparando = signal(false);
  readonly error = signal(false);

  constructor() {
    if (this.updates?.isEnabled) {
      this.updates.versionUpdates.subscribe((evento) => {
        if (evento.type === 'VERSION_READY') {
          this.disponible.set(true);
          this.pospuesta.set(false);
        }
      });
      void this.updates.checkForUpdate().catch(() => {});
    }
  }

  async recargar(): Promise<void> {
    if (this.preparando()) return;
    this.preparando.set(true);
    this.error.set(false);
    try {
      // Espera las escrituras IndexedDB ya iniciadas. La cola durable sobrevive a la recarga.
      await sgpDb.transaction('r', sgpDb.inspecciones, sgpDb.cola_sync, sgpDb.fotos, async () => {
        await sgpDb.cola_sync.count();
      });
      window.location.reload();
    } catch {
      this.error.set(true);
      this.preparando.set(false);
    }
  }
}
