import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { firstValueFrom, from, Subscription } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthStore } from '../../core/services/auth.store';
import { TokenRefreshService } from '../../core/services/token-refresh.service';
import { InspeccionApiService } from '../../features/inspecciones/services/inspeccion-api.service';
import { MetadatosFotografia } from '../../features/inspecciones/models/fotografia.models';
import {
  IniciarInspeccionRequest,
  GuardarBorradorRequest,
  InspeccionResumenResponse,
} from '../../features/inspecciones/models/inspeccion.models';
import { ConnectivityService } from './connectivity.service';
import { SyncQueueItem, sgpDb } from '../db/sgp-db';
import { BorradorLocalPayload, InspeccionesLocalesService } from './inspecciones-locales.service';
import { FotografiasLocalesService } from './fotografias-locales.service';
import { liveQuery } from 'dexie';
import { esResultadoIncierto } from '../../features/inspecciones/services/operacion-idempotente.service';

@Injectable({ providedIn: 'root' })
export class SincronizacionService implements OnDestroy {
  private destruido = false;
  private comprobacion?: ReturnType<typeof setInterval>;
  private colaSubscription?: Subscription;
  private sesionSubscription?: Subscription;
  private readonly alConectar = () => void this.sincronizarAhora();
  private readonly auth = inject(AuthStore);
  private readonly tokenRefresh = inject(TokenRefreshService);
  private readonly api = inject(InspeccionApiService);
  private readonly connectivity = inject(ConnectivityService);
  private readonly inspeccionesLocales = inject(InspeccionesLocalesService);
  private readonly fotografiasLocales = inject(FotografiasLocalesService);
  private ejecutando = false;
  private iniciado = false;
  private nuevaCorrida = false;
  private colaActual: SyncQueueItem[] = [];
  readonly sincronizando = signal(false);
  readonly cola$ = from(liveQuery<SyncQueueItem[]>(() => sgpDb.cola_sync.toArray()));

  iniciar(): void {
    if (this.iniciado) return;
    this.iniciado = true;
    window.addEventListener('online', this.alConectar);
    this.sesionSubscription = this.auth.cambiosSesion$.subscribe((usuario) => {
      if (usuario) void this.sincronizarAhora();
    });
    let anteriores = new Set<string>();
    this.colaSubscription = this.cola$.subscribe((items) => {
      this.colaActual = items;
      const nuevos = items.some((item) => !anteriores.has(item.id));
      anteriores = new Set(items.map((item) => item.id));
      if (nuevos && this.connectivity.online()) setTimeout(() => void this.sincronizarAhora(), 250);
    });
    this.comprobacion = setInterval(() => {
      if (
        this.colaActual.some(
          (item) => item.propietarioId === this.auth.user()?.id && item.estado !== 'INTERVENCION',
        )
      )
        void this.sincronizarAhora();
    }, 30000);
    if (this.connectivity.online()) void this.sincronizarAhora();
  }

  ngOnDestroy(): void {
    this.destruido = true;
    clearInterval(this.comprobacion);
    this.colaSubscription?.unsubscribe();
    this.sesionSubscription?.unsubscribe();
    window.removeEventListener('online', this.alConectar);
  }

  private async recuperarInterrumpidas(): Promise<void> {
    const propietarioId = this.auth.user()?.id;
    if (!propietarioId) return;
    const cola = await sgpDb.cola_sync.toArray();
    await Promise.all(
      cola
        .filter(
          (item) =>
            item.propietarioId === propietarioId &&
            item.estado === 'ENVIANDO' &&
            (!item.iniciadoEn || Date.now() - Date.parse(item.iniciadoEn) > 60000),
        )
        .map((item) =>
          sgpDb.cola_sync.update(item.id, {
            estado: item.intentos >= 5 ? 'INTERVENCION' : 'PENDIENTE',
          }),
        ),
    );
  }

  async sincronizarAhora(): Promise<void> {
    if (this.destruido || !this.connectivity.online()) return;
    if (this.ejecutando) {
      this.nuevaCorrida = true;
      return;
    }
    if (navigator.locks) {
      await navigator.locks.request('sgp-cola-sync', { ifAvailable: true }, async (lock) => {
        if (lock) await this.ejecutarCola();
      });
    } else await this.ejecutarCola();
  }

  private async ejecutarCola(): Promise<void> {
    if (this.ejecutando) return;
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
      await this.recuperarInterrumpidas();
      const cola = (await sgpDb.cola_sync.toArray())
        .filter(
          (item) =>
            item.tipo === 'INSPECCION' &&
            !this.esFinal(item) &&
            item.propietarioId === propietarioId &&
            (item.estado ?? 'PENDIENTE') === 'PENDIENTE',
        )
        .sort((a, b) => a.creadoEn.localeCompare(b.creadoEn));
      for (const item of cola) {
        if (!this.connectivity.online() || this.auth.user()?.id !== propietarioId) break;
        const previas = await sgpDb.cola_sync.where('entityId').equals(item.entityId).toArray();
        if (previas.some((previa) => !this.esFinal(previa) && previa.creadoEn < item.creadoEn))
          continue;
        await this.procesar(item, propietarioId);
        // Cada inspección mantiene sus dependencias sin bloquear puentes independientes.
      }
      // Las fotografías dependen de que el borrador ya exista en el servidor. Se procesan
      // después de toda la cola de inspecciones y siempre de una en una.
      const fotos = (await sgpDb.cola_sync.toArray())
        .filter(
          (item) =>
            item.tipo === 'FOTO' &&
            item.propietarioId === propietarioId &&
            (item.estado ?? 'PENDIENTE') === 'PENDIENTE',
        )
        .sort((a, b) => a.creadoEn.localeCompare(b.creadoEn));
      for (const item of fotos) {
        if (!this.connectivity.online() || this.auth.user()?.id !== propietarioId) break;
        if (await this.tieneInspeccionPendiente(item, propietarioId)) continue;
        await this.procesarFoto(item, propietarioId);
      }
      const finales = (await sgpDb.cola_sync.toArray()).filter(
        (item) =>
          this.esFinal(item) &&
          item.propietarioId === propietarioId &&
          (item.estado ?? 'PENDIENTE') === 'PENDIENTE',
      );
      for (const item of finales) {
        if (this.auth.user()?.id !== propietarioId || !this.connectivity.online()) break;
        const dependencias = await sgpDb.cola_sync
          .where('entityId')
          .equals(item.entityId)
          .toArray();
        const fotos = await sgpDb.fotos.where('inspeccionId').equals(item.entityId).toArray();
        if (
          dependencias.some((otra) => otra.id !== item.id) ||
          fotos.some((foto) => foto.propietarioId === propietarioId && !foto.sincronizado)
        )
          continue;
        await this.procesar(item, propietarioId);
      }
    } finally {
      this.ejecutando = false;
      this.sincronizando.set(false);
      if (this.nuevaCorrida) {
        this.nuevaCorrida = false;
        setTimeout(() => void this.sincronizarAhora(), 250);
      }
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
      await sgpDb.cola_sync.update(item.id, {
        estado: 'ENVIANDO',
        intentos: intento + 1,
        iniciadoEn: new Date().toISOString(),
      });
      try {
        if (item.operacion === 'CREAR_INSPECCION') {
          const respuesta = await firstValueFrom(
            this.api.iniciarInspeccion(
              item.payload as IniciarInspeccionRequest,
              item.idempotencyKey,
            ),
          );
          const local = await sgpDb.inspecciones.get(item.entityId);
          if (local?.propietarioId === propietarioId) {
            const payload = local.payload as BorradorLocalPayload;
            if (respuesta.versionFormularioId !== payload.versionFormularioId)
              throw new HttpErrorResponse({
                status: 409,
                error: {
                  code: 'version_formulario_distinta',
                  detail: 'offline.hu061.versionMismatch',
                },
              });
            await sgpDb.inspecciones.update(local.id, {
              payload: {
                ...payload,
                borrador: { ...payload.borrador, ...respuesta, datos: payload.borrador.datos },
              },
            });
          }
        } else if (item.operacion === 'GUARDAR_BORRADOR') {
          const local = await sgpDb.inspecciones.get(item.entityId);
          if (!local || local.propietarioId !== propietarioId) return;
          const borrador = (local?.payload as BorradorLocalPayload | undefined)?.borrador;
          const request = item.peticionFijada
            ? (item.payload as GuardarBorradorRequest)
            : {
                ...(item.payload as GuardarBorradorRequest),
                version: borrador?.version ?? (item.payload as GuardarBorradorRequest).version,
              };
          if (!item.peticionFijada) {
            await sgpDb.cola_sync.update(item.id, { payload: request, peticionFijada: true });
            item = { ...item, payload: request, peticionFijada: true };
          }
          const actualizado = await firstValueFrom(
            this.api.guardarBorrador(item.entityId, request, item.idempotencyKey, true),
          );
          await this.inspeccionesLocales.confirmarGuardado(
            item.id,
            item.entityId,
            propietarioId,
            actualizado,
          );
        } else if (this.esFinal(item)) {
          const respuesta = await firstValueFrom(
            item.operacion === 'PUBLICAR_INSPECCION'
              ? this.api.publicar(item.entityId, item.idempotencyKey)
              : this.api.enviar(item.entityId, item.idempotencyKey),
          );
          await this.confirmarFinal(item, propietarioId, respuesta);
        } else throw new Error('offline.hu061.unknownOperation');
        await sgpDb.cola_sync.delete(item.id);
        await this.marcarConfirmada(item.entityId, propietarioId);
        return;
      } catch (error) {
        const http = error as HttpErrorResponse;
        const incierto = esResultadoIncierto(http);
        if (
          http.status === 401 ||
          (http.status === 409 && http.error?.code === 'operacion_en_proceso')
        ) {
          await sgpDb.cola_sync.update(item.id, {
            estado: 'PENDIENTE',
            intentos: 0,
            ultimoError: http.error?.detail ?? http.message,
          });
          if (http.status === 409)
            setTimeout(
              () => void this.sincronizarAhora(),
              Math.min(60000, Math.max(1000, Number(http.headers?.get('Retry-After') ?? 5) * 1000)),
            );
          return;
        }
        if (
          this.esFinal(item) &&
          http.status === 409 &&
          !['confirmacion_ic_desactualizada', 'calculo_ic_desactualizado'].includes(
            http.error?.code,
          )
        ) {
          try {
            const canonica = await firstValueFrom(this.api.obtenerBorrador(item.entityId, true));
            if (
              canonica.autorId === propietarioId &&
              (canonica.estado === 'PUBLICADA' ||
                (item.operacion === 'ENVIAR_INSPECCION' && canonica.estado === 'ENVIADA'))
            ) {
              await this.confirmarFinal(
                item,
                propietarioId,
                canonica as unknown as InspeccionResumenResponse,
              );
              await this.marcarConfirmada(item.entityId, propietarioId);
              return;
            }
          } catch {
            /* La consulta publicada ofrece otra vía de reconciliación autorizada. */
          }
          try {
            const publicada = await firstValueFrom(this.api.obtenerPublicada(item.entityId));
            if (publicada.autorId === propietarioId && publicada.estado === 'PUBLICADA') {
              await this.confirmarFinal(
                item,
                propietarioId,
                publicada as unknown as InspeccionResumenResponse,
              );
              await this.marcarConfirmada(item.entityId, propietarioId);
              return;
            }
          } catch {
            /* Un estado incierto conserva la intención y sus datos. */
          }
          await sgpDb.cola_sync.update(item.id, {
            estado: 'INTERVENCION',
            codigoError: http.error?.code,
            ultimoError: http.error?.detail ?? http.message,
          });
          return;
        }
        if (this.esFinal(item) && !incierto && http.status !== undefined) {
          // Un rechazo definitivo libera la edición. La siguiente confirmación obtiene otra clave.
          await sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, async () => {
            const local = await sgpDb.inspecciones.get(item.entityId);
            if (local?.propietarioId === propietarioId)
              await sgpDb.inspecciones.update(local.id, {
                intencionFinal: {
                  accion: item.operacion === 'PUBLICAR_INSPECCION' ? 'publicar' : 'enviar',
                  estado: 'RECHAZADA',
                  error: http.error?.detail ?? http.message,
                },
                sincronizado: false,
              });
            await sgpDb.cola_sync.delete(item.id);
          });
          return;
        }
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

  private async tieneInspeccionPendiente(
    item: SyncQueueItem,
    propietarioId: string,
  ): Promise<boolean> {
    const foto = await sgpDb.fotos.get(item.entityId);
    if (!foto || foto.propietarioId !== propietarioId) return false;
    const pendientes = await sgpDb.cola_sync.where('entityId').equals(foto.inspeccionId).toArray();
    return pendientes.some(
      (pendiente) => pendiente.tipo === 'INSPECCION' && !this.esFinal(pendiente),
    );
  }

  private async procesarFoto(item: SyncQueueItem, propietarioId: string): Promise<void> {
    const foto = await sgpDb.fotos.get(item.entityId);
    if (
      !foto ||
      foto.propietarioId !== propietarioId ||
      foto.sincronizado ||
      !foto.metadatos ||
      !foto.idempotencyKey
    ) {
      await sgpDb.cola_sync.delete(item.id);
      return;
    }
    for (let intento = item.intentos; intento < 5; intento++) {
      if (this.auth.user()?.id !== propietarioId || !this.connectivity.online()) return;
      await sgpDb.cola_sync.update(item.id, {
        estado: 'ENVIANDO',
        intentos: intento + 1,
        iniciadoEn: new Date().toISOString(),
      });
      try {
        const respuesta = await firstValueFrom(
          this.api.adjuntarFotografia(
            foto.inspeccionId,
            foto.blob,
            foto.metadatos as MetadatosFotografia,
            foto.idempotencyKey,
          ),
        );
        // Solo se libera el Blob una vez que el servidor confirmó la operación idempotente.
        await this.fotografiasLocales.confirmar(foto.id, propietarioId, respuesta);
        return;
      } catch (error) {
        const http = error as HttpErrorResponse;
        if (
          http.status === 401 ||
          (http.status === 409 && http.error?.code === 'operacion_en_proceso')
        ) {
          await sgpDb.cola_sync.update(item.id, {
            estado: 'PENDIENTE',
            intentos: 0,
            ultimoError: http.error?.detail ?? http.message,
          });
          if (http.status === 409) setTimeout(() => void this.sincronizarAhora(), 5000);
          return;
        }
        const reintentable = http.status === 0 || http.status >= 500;
        const agotado = intento === 4;
        if (!reintentable || agotado) {
          await sgpDb.cola_sync.update(item.id, {
            estado: 'INTERVENCION',
            intentos: intento + 1,
            codigoError: typeof http.error?.code === 'string' ? http.error.code : undefined,
            ultimoError: String(http.error?.detail ?? http.message ?? 'Error de carga fotográfica'),
          });
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

  private esFinal(item: SyncQueueItem): boolean {
    return item.operacion === 'ENVIAR_INSPECCION' || item.operacion === 'PUBLICAR_INSPECCION';
  }

  private async confirmarFinal(
    item: SyncQueueItem,
    propietarioId: string,
    resultado: InspeccionResumenResponse,
  ): Promise<void> {
    await sgpDb.transaction('rw', sgpDb.inspecciones, sgpDb.cola_sync, async () => {
      const local = await sgpDb.inspecciones.get(item.entityId);
      if (!local || local.propietarioId !== propietarioId) return;
      const payload = local.payload as BorradorLocalPayload;
      await sgpDb.inspecciones.update(local.id, {
        estado: resultado.estado,
        payload: { ...payload, borrador: { ...payload.borrador, estado: resultado.estado } },
        intencionFinal: {
          accion: item.operacion === 'PUBLICAR_INSPECCION' ? 'publicar' : 'enviar',
          estado: 'CONFIRMADA',
          resultado,
        },
      });
      await sgpDb.cola_sync.delete(item.id);
    });
  }

  private async marcarConfirmada(id: string, propietarioId: string): Promise<void> {
    const local = await sgpDb.inspecciones.get(id);
    if (!local || local.propietarioId !== propietarioId) return;
    const pendientes = await sgpDb.cola_sync.where('entityId').equals(id).toArray();
    await sgpDb.inspecciones.update(id, {
      sincronizado:
        pendientes.length === 0 &&
        !local.actualizacionLocalPendiente &&
        local.intencionFinal?.estado !== 'RECHAZADA',
    });
  }
}
