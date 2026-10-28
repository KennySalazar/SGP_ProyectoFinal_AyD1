import { Injectable, computed, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class StorageService {
  readonly persistent = signal<boolean | null>(null);
  readonly estimate = signal<StorageEstimate>({});
  readonly disponible = computed(() => {
    const { usage, quota } = this.estimate();
    return typeof usage === 'number' && typeof quota === 'number'
      ? Math.max(0, quota - usage)
      : null;
  });
  readonly pocoEspacio = computed(() => {
    const disponible = this.disponible();
    return (
      disponible !== null &&
      disponible < Math.max(10 * 1024 * 1024, (this.estimate().quota ?? 0) * 0.1)
    );
  });

  async requestPersistentStorage(): Promise<boolean> {
    try {
      const granted = navigator.storage?.persist ? await navigator.storage.persist() : false;
      this.persistent.set(granted);
      return granted;
    } catch {
      this.persistent.set(false);
      return false;
    }
  }

  async usage(): Promise<StorageEstimate> {
    const estimate = navigator.storage?.estimate ? await navigator.storage.estimate() : {};
    this.estimate.set(estimate);
    return estimate;
  }

  async prepararFotografia(bytes: number): Promise<void> {
    if (this.persistent() === null) await this.requestPersistentStorage();
    // La estimación puede no existir. IndexedDB sigue siendo la autoridad sobre la cuota.
    try {
      await this.usage();
    } catch {
      this.estimate.set({});
    }
    const disponible = this.disponible();
    if (disponible !== null && disponible < bytes + 128 * 1024)
      throw new Error('espacioInsuficiente');
  }
}
