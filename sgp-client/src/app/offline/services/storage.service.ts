import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class StorageService {
  readonly persistent = signal<boolean | null>(null);

  async requestPersistentStorage(): Promise<boolean> {
    if (!navigator.storage?.persist) {
      this.persistent.set(false);
      return false;
    }
    const granted = await navigator.storage.persist();
    this.persistent.set(granted);
    return granted;
  }

  async usage(): Promise<StorageEstimate> {
    return navigator.storage?.estimate ? navigator.storage.estimate() : {};
  }
}
