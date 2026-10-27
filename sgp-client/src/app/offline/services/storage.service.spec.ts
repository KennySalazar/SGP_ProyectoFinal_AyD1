import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { StorageService } from './storage.service';

describe('HU-057: almacenamiento del navegador', () => {
  afterEach(() => vi.unstubAllGlobals());
  it('advierte persistencia denegada y poco espacio sin rechazar una foto que cabe', async () => {
    vi.stubGlobal('navigator', {
      storage: {
        persist: vi.fn().mockResolvedValue(false),
        estimate: async () => ({ usage: 1000000, quota: 2000000 }),
      },
    });
    const storage = TestBed.inject(StorageService);
    await storage.prepararFotografia(300000);
    expect(storage.persistent()).toBe(false);
    expect(storage.pocoEspacio()).toBe(true);
    expect(storage.disponible()).toBe(1000000);
  });
  it('acepta persistencia concedida y rechaza una foto que excede la cuota', async () => {
    vi.stubGlobal('navigator', {
      storage: { persist: async () => true, estimate: async () => ({ usage: 999, quota: 1000 }) },
    });
    const storage = TestBed.inject(StorageService);
    await expect(storage.prepararFotografia(300000)).rejects.toThrow('espacioInsuficiente');
    expect(storage.persistent()).toBe(true);
  });
  it('permite intentar IndexedDB si las APIs de almacenamiento no están disponibles', async () => {
    vi.stubGlobal('navigator', {});
    const storage = TestBed.inject(StorageService);
    await storage.prepararFotografia(300000);
    expect(storage.persistent()).toBe(false);
    expect(storage.disponible()).toBeNull();
  });
});
