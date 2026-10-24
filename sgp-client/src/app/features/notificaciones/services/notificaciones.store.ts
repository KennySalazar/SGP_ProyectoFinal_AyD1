import { effect, inject, Injectable, OnDestroy, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter, firstValueFrom, Subscription, timer } from 'rxjs';
import { AuthStore } from '../../../core/services/auth.store';
import { ConnectivityService } from '../../../offline/services/connectivity.service';
import { NotificacionOfflineService } from '../../../offline/services/notificacion-offline.service';
import { NotificacionResponse } from '../models/notificacion.models';
import { NotificacionApiService } from './notificacion-api.service';

@Injectable({ providedIn: 'root' })
export class NotificacionesStore implements OnDestroy {
  private readonly api = inject(NotificacionApiService);
  private readonly offline = inject(NotificacionOfflineService);
  private readonly connectivity = inject(ConnectivityService);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  readonly errorLectura = signal(false);
  readonly abriendoAviso = signal(false);

  readonly unreadCount = signal<number>(0);
  readonly avisos = signal<NotificacionResponse[]>([]);
  readonly totalElements = signal<number>(0);
  readonly totalPages = signal<number>(0);
  readonly pagina = signal<number>(0);
  readonly tamanio = signal<number>(20);
  readonly soloNoLeidos = signal<boolean>(false);
  readonly loading = signal<boolean>(false);
  readonly sinConexionAviso = signal<boolean>(false);

  private intervalSub?: Subscription;
  private navSub?: Subscription;

  constructor() {
    // Escucha cambios en autenticación para iniciar o detener el sondeo cada 60s y al navegar
    effect(() => {
      const isAuth = this.auth.authenticated();
      if (isAuth) {
        this.iniciarSondeo();
      } else {
        this.detenerSondeo();
      }
    });
  }

  ngOnDestroy(): void {
    this.detenerSondeo();
  }

  private iniciarSondeo(): void {
    this.detenerSondeo();

    // Carga inicial inmediata del contador
    void this.actualizarConteo();

    // Actualizar al navegar
    this.navSub = this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        if (this.connectivity.online() && this.auth.authenticated()) {
          void this.actualizarConteo();
        }
      });

    // Actualizar cada 60 segundos si hay conexión
    this.intervalSub = timer(60000, 60000).subscribe(() => {
      if (this.connectivity.online() && this.auth.authenticated()) {
        void this.actualizarConteo();
      }
    });
  }

  private detenerSondeo(): void {
    this.intervalSub?.unsubscribe();
    this.intervalSub = undefined;
    this.navSub?.unsubscribe();
    this.navSub = undefined;
  }

  async actualizarConteo(): Promise<void> {
    if (!this.auth.authenticated()) {
      return;
    }

    if (!this.connectivity.online()) {
      const conteoLocal = await this.offline.obtenerConteo();
      this.unreadCount.set(conteoLocal);
      this.sinConexionAviso.set(true);
      return;
    }

    this.api.obtenerResumen().subscribe({
      next: (res) => {
        this.unreadCount.set(res.noLeidas);
        this.sinConexionAviso.set(false);
        void this.offline.guardarConteo(res.noLeidas);
      },
      error: async () => {
        this.sinConexionAviso.set(true);
        const conteoLocal = await this.offline.obtenerConteo();
        this.unreadCount.set(conteoLocal);
      },
    });
  }

  async cargarAvisos(pagina = 0, soloNoLeidos = this.soloNoLeidos()): Promise<void> {
    this.loading.set(true);
    this.pagina.set(pagina);
    this.soloNoLeidos.set(soloNoLeidos);

    if (!this.connectivity.online()) {
      await this.cargarDesdeOffline(pagina, soloNoLeidos);
      return;
    }

    this.api.listar(soloNoLeidos, pagina, this.tamanio()).subscribe({
      next: (res) => {
        this.avisos.set(res.content);
        this.totalElements.set(res.totalElements);
        this.totalPages.set(res.totalPages);
        this.sinConexionAviso.set(false);
        this.loading.set(false);

        // Guardar copia local en Dexie
        const itemsLocales = res.content.map((n) => ({
          id: n.id,
          tipo: n.tipo,
          titulo: n.titulo,
          mensaje: n.mensaje,
          entidad: n.entidad,
          entidadId: n.entidadId,
          rutaDestino: n.rutaDestino,
          claveEvento: n.claveEvento,
          leidoEn: n.leidoEn,
          creadoEn: n.creadoEn,
        }));
        void this.offline.guardarAvisos(itemsLocales, this.unreadCount());
      },
      error: async () => {
        await this.cargarDesdeOffline(pagina, soloNoLeidos);
      },
    });
  }

  private async cargarDesdeOffline(pagina: number, soloNoLeidos: boolean): Promise<void> {
    this.sinConexionAviso.set(true);
    const conteoLocal = await this.offline.obtenerConteo();
    this.unreadCount.set(conteoLocal);

    const itemsLocales = await this.offline.obtenerAvisos(soloNoLeidos);
    const total = itemsLocales.length;
    const tam = this.tamanio();
    const inicio = pagina * tam;
    const paginados = itemsLocales.slice(inicio, inicio + tam).map((n) => ({
      ...n,
      leido: Boolean(n.leidoEn),
    }));

    this.avisos.set(paginados);
    this.totalElements.set(total);
    this.totalPages.set(Math.ceil(total / tam) || 1);
    this.loading.set(false);
  }

  async marcarLeida(aviso: NotificacionResponse): Promise<void> {
    if (this.abriendoAviso()) return;
    this.abriendoAviso.set(true);
    this.errorLectura.set(false);
    try {
      const actual = this.avisos().find((a) => a.id === aviso.id) || aviso;
      if (!actual.leido) {
        if (
          !this.connectivity.online() &&
          ['VERSION_FORMULARIO', 'CATALOGO_REGLAS'].includes(aviso.entidad?.toUpperCase() || '')
        ) {
          this.sinConexionAviso.set(true);
          this.errorLectura.set(true);
          return;
        }
        let leidoEn = new Date().toISOString();
        if (this.connectivity.online()) {
          try {
            const respuesta = await firstValueFrom(this.api.marcarLeida(aviso.id));
            leidoEn = respuesta.leidoEn || leidoEn;
          } catch {
            this.errorLectura.set(true);
            return;
          }
        }
        this.avisos.update((avisos) =>
          avisos.map((a) => (a.id === aviso.id ? { ...a, leido: true, leidoEn } : a)),
        );
        this.unreadCount.update((c) => Math.max(0, c - 1));
        await this.offline.marcarLeidaLocal(aviso.id).catch(() => undefined);
      }
      if (aviso.entidad?.toUpperCase() === 'CATALOGO_REGLAS' && aviso.entidadId) {
        await this.router.navigate(['/mantenimiento', 'reglas'], {
          queryParams: { versionId: aviso.entidadId },
        });
      } else {
        const destino = this.obtenerRutaEntidad(aviso.entidad, aviso.entidadId);
        if (destino) await this.router.navigate(destino);
      }
    } finally {
      this.abriendoAviso.set(false);
    }
  }

  async marcarTodasLeidas(): Promise<void> {
    const ahora = new Date().toISOString();
    const actualizados = this.avisos().map((a) => ({
      ...a,
      leido: true,
      leidoEn: a.leidoEn || ahora,
    }));
    this.avisos.set(actualizados);
    this.unreadCount.set(0);
    await this.offline.marcarTodasLeidasLocal();

    if (this.connectivity.online()) {
      this.api.marcarTodasLeidas().subscribe({
        next: () => {
          void this.actualizarConteo();
        },
      });
    }
  }

  obtenerRutaEntidad(entidad?: string, entidadId?: string): string[] | null {
    if (!entidad || !entidadId) {
      return null;
    }
    const entidadUpper = entidad.toUpperCase();
    if (entidadUpper === 'VERSION_FORMULARIO') {
      return ['/admin/formularios', entidadId];
    }
    if (entidadUpper === 'INSPECCION') {
      return ['/inspecciones', entidadId];
    }
    // El estudiante consulta el resultado privado; el revisor inicia o reanuda desde su bandeja.
    // entidadId identifica el ciclo, no la inspección.
    if (entidadUpper === 'REVISION') {
      return this.auth.user()?.role === 'ESTUDIANTE'
        ? ['/observaciones', entidadId]
        : ['/revisiones'];
    }
    if (
      entidadUpper === 'ORDEN' ||
      entidadUpper === 'ORDEN_MANTENIMIENTO' ||
      entidadUpper === 'MANTENIMIENTO'
    ) {
      return ['/mantenimiento', 'ordenes', entidadId];
    }
    if (entidadUpper === 'PROPUESTA' || entidadUpper === 'PROPUESTA_MANTENIMIENTO') {
      return ['/mantenimiento', 'propuestas', entidadId];
    }
    if (entidadUpper === 'SOLICITUD_ALTA_PUENTE' || entidadUpper === 'SOLICITUD_PUENTE') {
      return ['/puentes', 'solicitudes', 'revision', entidadId];
    }
    if (entidadUpper === 'PUENTE') {
      return ['/puentes', entidadId];
    }
    return null;
  }
}
