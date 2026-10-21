import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom, from } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthStore } from '../../core/services/auth.store';
import { TokenRefreshService } from '../../core/services/token-refresh.service';
import { InspeccionApiService } from '../../features/inspecciones/services/inspeccion-api.service';
import {
  IniciarInspeccionRequest,
  GuardarBorradorRequest,
} from '../../features/inspecciones/models/inspeccion.models';
import { ConnectivityService } from './connectivity.service';
import { SyncQueueItem, sgpDb } from '../db/sgp-db';
import { BorradorLocalPayload, InspeccionesLocalesService } from './inspecciones-locales.service';
import { liveQuery } from 'dexie';

@Injectable({ providedIn: 'root' })
export class SincronizacionService {
  private readonly auth = inject(AuthStore);
  private readonly tokenRefresh = inject(TokenRefreshService);
  private readonly api = inject(InspeccionApiService);
  private readonly connectivity = inject(ConnectivityService);
  private readonly inspeccionesLocales = inject(InspeccionesLocalesService);
  private ejecutando = false;
  private iniciado = false;
  readonly sincronizando = signal(false);
  readonly cola$ = from(liveQuery<SyncQueueItem[]>(() => sgpDb.cola_sync.toArray()));

  iniciar(): void {
    if (this.iniciado) return;
    this.iniciado = true;
    window.addEventListener('online', () => void this.sincronizarAhora());
    if (this.connectivity.online())
      void this.recuperarInterrumpidas().then(() => this.sincronizarAhora());
  }

  private async recuperarInterrumpidas(): Promise<void> {
    const propietarioId = this.auth.user()?.id;
    if (!propietarioId) return;
    const cola = await sgpDb.cola_sync.toArray();
    await Promise.all(
      cola
        .filter((item) => item.propietarioId === propietarioId && item.estado === 'ENVIANDO')
        .map((item) => sgpDb.cola_sync.update(item.id, { estado: 'PENDIENTE' })),
    );
  }

  async sincronizarAhora(): Promise<void> {
    if (this.ejecutando || !this.connectivity.online()) return;
    this.ejecutando = true;
    this.sincronizando.set(true);
    try {
      // El perfil cacheado permite consultar datos offline, pero no autoriza peticiones.
      // Renueva y valida la sesión antes de tocar el estado de cualquier operación.
      try {
        await firstValueFrom(this.tokenRefresh.refresh());
        await firstValueFrom(this.auth.loadMe());
      } catch {
        return;
      }
      const propietarioId = this.auth.user()?.id;
      if (!propietarioId) return;
      const cola = (await sgpDb.cola_sync.toArray())
        .filter(
          (item) =>
            item.tipo === 'INSPECCION' &&
            item.propietarioId === propietarioId &&
            (item.estado ?? 'PENDIENTE') === 'PENDIENTE',
        )
        .sort((a, b) => a.creadoEn.localeCompare(b.creadoEn));
      for (const item of cola) {
        if (!this.connectivity.online() || this.auth.user()?.id !== propietarioId) break;
        await this.procesar(item, propietarioId);
        if (await sgpDb.cola_sync.get(item.id)) break;
      }
    } finally {
      this.ejecutando = false;
      this.sincronizando.set(false);
    }
  }

  async reintentar(id: string): Promise<void> {
    const item = await sgpDb.cola_sync.get(id);
    if (!item || item.propietarioId !== this.auth.user()?.id) return;
    await sgpDb.cola_sync.update(id, { estado: 'PENDIENTE', intentos: 0, ultimoError: undefined });
    await this.sincronizarAhora();
  }

  private async procesar(item: SyncQueueItem, propietarioId: string): Promise<void> {
    for (let intento = item.intentos; intento < 5; intento++) {
      if (this.auth.user()?.id !== propietarioId || !this.connectivity.online()) return;
      await sgpDb.cola_sync.update(item.id, { estado: 'ENVIANDO', intentos: intento + 1 });
      try {
        if (item.operacion === 'CREAR_INSPECCION') {
          await firstValueFrom(
            this.api.iniciarInspeccion(
              item.payload as IniciarInspeccionRequest,
              item.idempotencyKey,
            ),
          );
        } else if (item.operacion === 'GUARDAR_BORRADOR') {
          const local = await sgpDb.inspecciones.get(item.entityId);
          if (!local || local.propietarioId !== propietarioId) return;
          const borrador = (local?.payload as BorradorLocalPayload | undefined)?.borrador;
          const request = {
            ...(item.payload as GuardarBorradorRequest),
            version: borrador?.version ?? (item.payload as GuardarBorradorRequest).version,
          };
          const actualizado = await firstValueFrom(
            this.api.guardarBorrador(item.entityId, request, item.idempotencyKey),
          );
          await this.inspeccionesLocales.confirmarGuardado(
            item.id,
            item.entityId,
            propietarioId,
            actualizado,
          );
        }
        await sgpDb.cola_sync.delete(item.id);
        await this.marcarConfirmada(item.entityId, propietarioId);
        return;
      } catch (error) {
        const http = error as HttpErrorResponse;
        const reintentable = http.status === 0 || http.status >= 500;
        const agotado = intento === 4;
        if (!reintentable || agotado) {
          const codigoError = typeof http.error?.code === 'string' ? http.error.code : undefined;
          await sgpDb.cola_sync.update(item.id, {
            estado: 'INTERVENCION',
            intentos: intento + 1,
            codigoError,
            ultimoError: String(
              http.error?.detail ??
                http.error?.message ??
                http.message ??
                'Error de sincronización',
            ),
          });
          if (codigoError === 'concurrencia_conflicto' && this.connectivity.online()) {
            try {
              const borrador = await firstValueFrom(this.api.obtenerBorrador(item.entityId, true));
              await this.inspeccionesLocales.cachearBorradorCanonico(borrador, propietarioId);
            } catch {
              // Se conserva la edición conflictiva si no puede consultarse el servidor.
            }
          }
          return;
        }
        await sgpDb.cola_sync.update(item.id, {
          estado: 'PENDIENTE',
          intentos: intento + 1,
          codigoError: undefined,
          ultimoError: http.message,
        });
        await new Promise((resolve) =>
          setTimeout(resolve, Math.min(16000, 1000 * 2 ** intento) + Math.random() * 350),
        );
      }
    }
  }

  private async marcarConfirmada(id: string, propietarioId: string): Promise<void> {
    const local = await sgpDb.inspecciones.get(id);
    if (!local || local.propietarioId !== propietarioId) return;
    const pendientes = await sgpDb.cola_sync.where('entityId').equals(id).toArray();
    await sgpDb.inspecciones.update(id, { sincronizado: pendientes.length === 0 });
  }
}
