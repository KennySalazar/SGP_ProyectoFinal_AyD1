import { HttpErrorResponse } from '@angular/common/http';
import { computed, effect, inject, Injectable, OnDestroy, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthStore } from '../../../core/services/auth.store';
import { ConnectivityService } from '../../../offline/services/connectivity.service';
import { StorageService } from '../../../offline/services/storage.service';
import { uuidV7 } from '../../../offline/services/uuid-v7';
import { FormularioApiService } from '../../formulario/services/formulario-api.service';
import { ResumenSalida, SalidaRequest } from '../models/salida.models';
import { SalidaApiService } from './salida-api.service';
import { SalidaLocalService } from './salida-local.service';

@Injectable({ providedIn: 'root' })
export class PreparacionSalidaService implements OnDestroy {
  private readonly auth = inject(AuthStore);
  private readonly conexion = inject(ConnectivityService);
  private readonly local = inject(SalidaLocalService);
  private readonly api = inject(SalidaApiService);
  private readonly formulario = inject(FormularioApiService);
  private readonly almacenamiento = inject(StorageService);
  private readonly resumen = signal<ResumenSalida | null>(null);
  private readonly distinta = signal(false);
  private operando = false;
  readonly ocupada = signal(false);
  readonly paso = signal(0);
  readonly error = signal('');
  readonly aviso = signal('');
  readonly persistente = signal<boolean | null>(null);
  readonly espacio = signal<number | null>(null);
  readonly ultima = computed(() =>
    this.resumen()?.propietarioId === this.auth.user()?.id ? this.resumen() : null,
  );
  readonly nuevaVersion = computed(() => !!this.ultima() && this.distinta());
  readonly permitida = computed(() => {
    const u = this.auth.user();
    return (
      !!u &&
      u.active &&
      u.activated &&
      u.verified &&
      ['CATEDRATICO', 'ESTUDIANTE', 'PROFESIONAL_EXTERNO'].includes(u.role) &&
      !this.auth.colegiadoPendiente()
    );
  });
  private readonly foco = () => {
    if (document.visibilityState !== 'hidden') void this.comprobarVersion();
  };
  private readonly intervalo = setInterval(() => {
    void this.comprobarVersion();
  }, 60000);

  constructor() {
    effect(() => {
      const usuario = this.auth.user()?.id;
      this.resumen.set(null);
      this.distinta.set(false);
      this.error.set('');
      this.aviso.set('');
      this.persistente.set(null);
      this.espacio.set(null);
      if (usuario) void this.cargar(usuario);
    });
    effect(() => {
      if (this.conexion.online() && this.permitida()) void this.comprobarVersion();
    });
    window.addEventListener('focus', this.foco);
    document.addEventListener('visibilitychange', this.foco);
  }

  ngOnDestroy(): void {
    clearInterval(this.intervalo);
    window.removeEventListener('focus', this.foco);
    document.removeEventListener('visibilitychange', this.foco);
  }

  private async cargar(usuario: string): Promise<void> {
    try {
      const ultima = await this.local.ultima(usuario);
      if (usuario !== this.auth.user()?.id) return;
      this.resumen.set(ultima ?? null);
      await this.medicion(usuario);
      await this.comprobarVersion();
    } catch {
      if (usuario === this.auth.user()?.id)
        this.error.set(
          'No se pudo abrir el almacenamiento de este navegador. Revisa sus permisos.',
        );
    }
  }

  cuerpo(ids: string[]): SalidaRequest {
    const nombre = 'sgp.dispositivo.salida';
    let dispositivo = localStorage.getItem(nombre);
    if (!dispositivo) {
      dispositivo = uuidV7();
      localStorage.setItem(nombre, dispositivo);
    }
    return { dispositivoId: dispositivo, puentesIds: [...new Set(ids)].sort() };
  }

  async comprobarVersion(): Promise<void> {
    const previa = this.ultima();
    if (!previa || !this.conexion.online() || !this.permitida()) return;
    try {
      const activa = await firstValueFrom(this.formulario.obtenerActiva());
      if (previa.propietarioId === this.auth.user()?.id && previa.id === this.ultima()?.id)
        this.distinta.set(activa.id !== previa.versionFormularioId);
    } catch {
      return;
    }
  }

  async preparar(ids: string[]): Promise<boolean> {
    if (this.operando || !this.permitida() || !this.conexion.online()) return false;
    const usuario = this.auth.user()!.id;
    this.operando = true;
    this.ocupada.set(true);
    this.paso.set(0);
    this.error.set('');
    this.aviso.set('');
    let clave: string | undefined;
    try {
      const solicitud = await this.local.solicitud(usuario, this.cuerpo(ids));
      clave = solicitud.clave;
      this.exigirUsuario(usuario);
      const salida = await firstValueFrom(this.api.preparar(solicitud.cuerpo, solicitud.clave));
      this.exigirUsuario(usuario);
      const ultima = await this.local.guardar(usuario, salida, (indice) => this.paso.set(indice));
      this.exigirUsuario(usuario);
      this.resumen.set(ultima);
      this.distinta.set(false);
      this.paso.set(4);
      try {
        const persistente = await this.almacenamiento.requestPersistentStorage();
        if (usuario === this.auth.user()?.id) this.persistente.set(persistente);
        await this.medicion(usuario);
      } catch {
        if (usuario === this.auth.user()?.id)
          this.aviso.set(
            'La salida esta guardada. No fue posible consultar la persistencia o el espacio del navegador.',
          );
      }
      return true;
    } catch (error) {
      if (
        error instanceof HttpErrorResponse &&
        clave &&
        error.status >= 400 &&
        error.status < 500 &&
        error.status !== 401 &&
        !(error.status === 409 && error.error?.code === 'operacion_en_proceso')
      ) {
        await this.local.descartarSolicitud(usuario, clave).catch(() => {});
      }
      if (usuario === this.auth.user()?.id) this.error.set(this.mensaje(error));
      return false;
    } finally {
      this.operando = false;
      this.ocupada.set(false);
    }
  }

  async quitar(): Promise<void> {
    if (this.operando || !this.permitida()) return;
    const usuario = this.auth.user()!.id;
    this.operando = true;
    this.ocupada.set(true);
    this.error.set('');
    this.aviso.set('');
    try {
      await this.local.quitar(usuario);
      if (usuario === this.auth.user()?.id) {
        this.resumen.set(null);
        this.distinta.set(false);
        this.paso.set(0);
        this.aviso.set('Se quitaron los datos preparados de esta cuenta en este dispositivo.');
      }
      await this.medicion(usuario);
    } catch (e) {
      if (usuario === this.auth.user()?.id) this.error.set(this.mensaje(e));
    } finally {
      this.operando = false;
      this.ocupada.set(false);
    }
  }

  private exigirUsuario(usuario: string): void {
    if (usuario !== this.auth.user()?.id)
      throw new Error('La sesion cambio. Vuelve a preparar la salida con tu cuenta.');
  }
  private async medicion(usuario: string): Promise<void> {
    const datos = await this.almacenamiento.usage();
    if (usuario === this.auth.user()?.id)
      this.espacio.set(
        typeof datos.quota === 'number' && typeof datos.usage === 'number'
          ? Math.max(0, datos.quota - datos.usage)
          : null,
      );
  }
  private mensaje(e: unknown): string {
    if (e instanceof HttpErrorResponse) {
      if (e.status === 0 || e.status >= 500)
        return 'No se pudo descargar la salida. Lo preparado antes sigue intacto. Reintenta cuando el servidor este disponible.';
      if (e.status === 401) return 'La sesion vencio. Inicia sesion de nuevo y reintenta.';
      return (
        e.error?.detail ?? 'No se pudo preparar la salida. Actualiza la seleccion y reintenta.'
      );
    }
    const detalle = e && typeof e === 'object' ? (e as { name?: string; message?: string }) : null;
    if (/quota/i.test(String(detalle?.name) + String(detalle?.message)))
      return 'No hay espacio suficiente. La preparacion anterior sigue intacta. Libera espacio y reintenta.';
    return e instanceof Error
      ? e.message
      : 'No fue posible guardar la salida. La preparacion anterior sigue intacta.';
  }
}
