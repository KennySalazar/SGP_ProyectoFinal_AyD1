import { esResultadoIncierto } from '../../services/operacion-idempotente.service';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  inject,
  Injector,
  signal,
  effect,
  untracked,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { CheckboxModule } from 'primeng/checkbox';
import { TagModule } from 'primeng/tag';
import { AuthStore } from '../../../../core/services/auth.store';
import { PuenteResponse } from '../../../puentes/models/puente.models';
import {
  AcompananteRequest,
  DatosPermanentesResponse,
  IniciarInspeccionRequest,
  IniciarInspeccionResponse,
} from '../../models/inspeccion.models';
import { uuidV7 } from '../../../../offline/services/uuid-v7';
import { InspeccionApiService } from '../../services/inspeccion-api.service';
import { InspeccionesLocalesService } from '../../../../offline/services/inspecciones-locales.service';
import { ConnectivityService } from '../../../../offline/services/connectivity.service';
import { FormularioApiService } from '../../../formulario/services/formulario-api.service';

function calcularDistanciaMetros(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371000.0;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

@Component({
  selector: 'app-iniciar-inspeccion-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TranslocoPipe,
    ButtonModule,
    DialogModule,
    InputTextModule,
    CheckboxModule,
    TagModule,
  ],
  templateUrl: './iniciar-inspeccion-dialog.component.html',
  styleUrl: './iniciar-inspeccion-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IniciarInspeccionDialogComponent implements OnChanges {
  @Input() visible = false;
  @Input() puente: PuenteResponse | null = null;
  @Output() readonly visibleChange = new EventEmitter<boolean>();
  @Output() readonly inspeccionIniciada = new EventEmitter<IniciarInspeccionResponse>();

  private readonly injector = inject(Injector);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthStore);
  private readonly api = inject(InspeccionApiService);
  private readonly inspeccionesLocales = inject(InspeccionesLocalesService);
  private readonly connectivity = inject(ConnectivityService);
  private readonly formularioApi = inject(FormularioApiService);
  private readonly router = inject(Router);

  readonly form: FormGroup = this.fb.group({
    fechaInspeccion: ['', [Validators.required]],
    nombreAutor: [''],
    confirmarDatosPermanentes: [false],
    nombreAcompanante: [''],
    colegiadoAcompanante: [''],
  });

  readonly cargandoPermanentes = signal(false);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly resultado = signal<IniciarInspeccionResponse | null>(null);
  readonly reporteFotografico = signal<import('@angular/core').Type<unknown> | null>(null);
  readonly errorReporte = signal(false);
  readonly pendienteAnterior = signal<IniciarInspeccionResponse | null>(null);
  private async recuperarPendiente(): Promise<void> {
    this.pendienteAnterior.set(null);
    const actor = this.auth.user()?.id,
      puente = this.puente?.id;
    if (!actor || !puente || typeof indexedDB === 'undefined') return;
    try {
      const { FotografiasLocalesService } =
        await import('../../../../offline/services/fotografias-locales.service');
      const datos = await this.injector
        .get(FotografiasLocalesService)
        .contextoPendiente(actor, puente);
      const contexto = datos as Partial<IniciarInspeccionResponse> | null;
      if (
        this.auth.user()?.id === actor &&
        this.puente?.id === puente &&
        contexto?.autorId === actor &&
        contexto.puenteId === puente &&
        typeof contexto.id === 'string'
      )
        this.pendienteAnterior.set(contexto as IniciarInspeccionResponse);
    } catch {
      /* El formulario puede seguir funcionando sin persistencia disponible. */
    }
  }
  continuarPendiente(): void {
    const contexto = this.pendienteAnterior();
    if (
      !contexto ||
      contexto.autorId !== this.auth.user()?.id ||
      contexto.puenteId !== this.puente?.id
    )
      return;
    this.resultado.set(contexto);
    void this.cargarReporte();
  }
  async cargarReporte(): Promise<void> {
    this.errorReporte.set(false);
    try {
      const modulo = await import('../reporte-fotografico/reporte-fotografico.component');
      this.reporteFotografico.set(modulo.ReporteFotograficoComponent);
    } catch {
      this.errorReporte.set(true);
    }
  }

  readonly datosPermanentes = signal<DatosPermanentesResponse | null>(null);
  readonly acompanantes = signal<AcompananteRequest[]>([]);

  readonly gpsEstado = signal<'obteniendo' | 'obtenido' | 'no_disponible'>('no_disponible');
  readonly gpsLatitud = signal<number | null>(null);
  readonly gpsLongitud = signal<number | null>(null);
  readonly gpsPrecision = signal<number | null>(null);

  private solicitudAutorId: string | null = null;
  constructor() {
    effect(() => {
      const actor = this.auth.user()?.id;
      untracked(() => {
        if (this.resultado() && this.resultado()?.autorId !== actor) {
          this.resultado.set(null);
          this.solicitudInicio = null;
          this.reporteFotografico.set(null);
          this.cerrar();
        }
      });
    });
  }
  private solicitudInicio: IniciarInspeccionRequest | null = null;

  readonly hoy: string = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Guatemala',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['visible'] && this.visible) {
      if (
        this.resultado()?.puenteId === this.puente?.id &&
        this.resultado()?.autorId === this.auth.user()?.id
      )
        return;
      if (
        this.solicitudInicio &&
        (this.solicitudInicio.puenteId !== this.puente?.id ||
          this.solicitudAutorId !== this.auth.user()?.id)
      )
        this.solicitudInicio = null;
      if (this.solicitudInicio) return;
      this.reiniciarEstado();
      this.cachearEsquemaActivo();
      void this.recuperarPendiente();
      this.obtenerGps();
      if (this.puente?.id) {
        this.cargarDatosPermanentes(this.puente.id);
      }
    }
  }

  private cachearEsquemaActivo(): void {
    if (!this.connectivity.online()) return;
    this.formularioApi.obtenerActiva().subscribe({
      next: (formulario) => {
        void this.inspeccionesLocales.guardarEsquemaLocal({
          id: formulario.id,
          version: String(formulario.version),
          schema: formulario.esquema,
          activo: true,
        });
      },
    });
  }

  cerrar(): void {
    this.visible = false;
    this.visibleChange.emit(false);
  }

  irAFormulario(inspeccionId: string): void {
    this.cerrar();
    this.router.navigate(['/inspecciones', inspeccionId]);
  }

  onVisibleChange(val: boolean): void {
    this.visible = val;
    this.visibleChange.emit(val);
  }

  obtenerGps(): void {
    if (typeof navigator !== 'undefined' && 'geolocation' in navigator) {
      this.gpsEstado.set('obteniendo');
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          this.gpsLatitud.set(pos.coords.latitude);
          this.gpsLongitud.set(pos.coords.longitude);
          this.gpsPrecision.set(
            pos.coords.accuracy ? Math.round(pos.coords.accuracy * 100) / 100 : null,
          );
          this.gpsEstado.set('obtenido');
        },
        () => {
          this.gpsLatitud.set(null);
          this.gpsLongitud.set(null);
          this.gpsPrecision.set(null);
          this.gpsEstado.set('no_disponible');
        },
        { enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 },
      );
    } else {
      this.gpsEstado.set('no_disponible');
    }
  }

  agregarAcompanante(): void {
    const nombre = (this.form.get('nombreAcompanante')?.value || '').trim();
    const colegiado = (this.form.get('colegiadoAcompanante')?.value || '').trim() || null;
    if (!nombre) {
      return;
    }
    this.acompanantes.update((list) => [...list, { nombre, colegiado }]);
    this.form.patchValue({ nombreAcompanante: '', colegiadoAcompanante: '' });
  }

  eliminarAcompanante(indice: number): void {
    this.acompanantes.update((list) => list.filter((_, i) => i !== indice));
  }

  iniciarInspeccion(): void {
    if (this.guardando() || this.form.invalid || !this.puente) {
      return;
    }

    const fecha = this.form.get('fechaInspeccion')?.value;
    if (fecha > this.hoy) {
      this.error.set('La fecha de inspección no puede ser posterior a la fecha actual.');
      return;
    }

    const anioConstruccion = this.datosPermanentes()?.anioConstruccion;
    if (anioConstruccion && fecha) {
      const anioInspeccion = parseInt(fecha.substring(0, 4), 10);
      if (anioInspeccion < anioConstruccion) {
        this.error.set(
          `La fecha de inspección (${anioInspeccion}) no puede ser anterior al año de construcción del puente (${anioConstruccion}).`,
        );
        return;
      }
    }

    this.guardando.set(true);
    this.error.set(null);

    const clientUuid = this.solicitudInicio?.id ?? uuidV7();
    const dispositivoId = this.obtenerDispositivoId();

    const request: IniciarInspeccionRequest = this.solicitudInicio ?? {
      id: clientUuid,
      puenteId: this.puente.id,
      fechaInspeccion: fecha,
      dispositivoId: dispositivoId,
      nombreAutor: (this.form.get('nombreAutor')?.value || '').trim() || undefined,
      latitud: this.gpsLatitud(),
      longitud: this.gpsLongitud(),
      precisionGpsM: this.gpsPrecision(),
      confirmarDatosPermanentes: !!this.form.get('confirmarDatosPermanentes')?.value,
      acompanantes: this.acompanantes().length > 0 ? this.acompanantes() : null,
    };

    this.solicitudInicio = request;
    this.form.disable({ emitEvent: false });
    this.solicitudAutorId = this.auth.user()?.id ?? null;
    const actor = this.solicitudAutorId;
    if (!actor) {
      this.guardando.set(false);
      this.form.enable({ emitEvent: false });
      this.error.set('Inicie sesión para crear una inspección.');
      return;
    }

    // Si no hay red, iniciar directamente en modo local
    if (!this.connectivity.online()) {
      void this.iniciarOffline(request, actor);
      return;
    }

    void this.inspeccionesLocales
      .encolarInicio(actor, request)
      .then((operacion) => {
        if (this.auth.user()?.id !== actor) {
          void this.inspeccionesLocales.marcarOperacionPendiente(
            operacion.id,
            'La sesión actual no corresponde al propietario de la inspección.',
          );
          this.guardando.set(false);
          return;
        }
        this.api.iniciarInspeccion(request, operacion.idempotencyKey).subscribe({
          next: (res) => {
            void this.inspeccionesLocales.confirmarInicio(operacion.id, res.id, actor);
            if (this.auth.user()?.id !== actor || this.puente?.id !== request.puenteId) return;
            this.guardando.set(false);
            this.resultado.set(res);
            this.solicitudInicio = null;
            void this.cargarReporte();
            this.inspeccionIniciada.emit(res);
          },
          error: async (err) => {
            if (this.auth.user()?.id !== actor || this.puente?.id !== request.puenteId) return;
            // Si falló por falta de red o error de conectividad, persistir offline
            if (err.status === 0 || !navigator.onLine || esResultadoIncierto(err)) {
              await this.iniciarOffline(request, actor);
              return;
            }
            if (!esResultadoIncierto(err)) {
              void this.inspeccionesLocales.marcarOperacionParaIntervencion(
                operacion.id,
                String(err.error?.detail ?? err.error?.message ?? err.message),
              );
              this.solicitudInicio = null;
              this.form.enable({ emitEvent: false });
            }
            this.guardando.set(false);
            const backendMsg =
              err.error?.message ||
              err.error?.detail ||
              'Ocurrió un error al iniciar la inspección. Verifique los datos ingresados.';
            this.error.set(backendMsg);
          },
        });
      })
      .catch((err: Error) => {
        this.guardando.set(false);
        this.form.enable({ emitEvent: false });
        this.error.set(err.message || 'No fue posible guardar la operación para sincronizar.');
      });
  }

  private async iniciarOffline(
    request: IniciarInspeccionRequest,
    actor: string | null,
  ): Promise<void> {
    try {
      const user = this.auth.user();
      const autorId = actor || user?.id || 'autor-local';
      const rolAutor = user?.role || 'PROFESIONAL_EXTERNO';
      const nombreAutor = (request.nombreAutor || user?.email || 'Inspector').trim();
      const esquemaActivo = await this.inspeccionesLocales.obtenerEsquemaActivoLocal();
      const versionFormularioId = esquemaActivo?.id || 'version-preparada-local';

      // Validación GPS > 500m
      let advertenciaGps = false;
      let distanciaCalculada: number | undefined = undefined;
      let mensajeAdvertencia: string | undefined = undefined;

      const pLat = this.puente?.latitud;
      const pLng = this.puente?.longitud;

      if (pLat != null && pLng != null && request.latitud != null && request.longitud != null) {
        distanciaCalculada = Math.round(
          calcularDistanciaMetros(pLat, pLng, request.latitud, request.longitud),
        );
        if (distanciaCalculada > 500) {
          advertenciaGps = true;
          mensajeAdvertencia = `El dispositivo se encuentra a más de 500 metros del puente (${distanciaCalculada} m).`;
        }
      }

      const sinGps = request.latitud == null || request.longitud == null;

      const res = await this.inspeccionesLocales.iniciarInspeccionLocal(
        request,
        autorId,
        rolAutor,
        nombreAutor,
        this.puente?.nombre || '',
        this.puente?.codigo || '',
        this.puente?.departamento?.nombre || '',
        this.puente?.municipio?.nombre,
        versionFormularioId,
        request.confirmarDatosPermanentes ? this.datosPermanentes() : null,
        sinGps,
      );

      res.advertenciaGps = advertenciaGps;
      res.distanciaAlPuenteM = distanciaCalculada;
      res.mensajeAdvertenciaGps = mensajeAdvertencia;

      this.guardando.set(false);
      this.resultado.set(res);
      this.solicitudInicio = null;
      void this.cargarReporte();
      this.inspeccionIniciada.emit(res);
    } catch (e: unknown) {
      this.guardando.set(false);
      this.form.enable({ emitEvent: false });
      this.error.set((e as Error).message || 'Error al guardar la inspección sin conexión.');
    }
  }

  private cargarDatosPermanentes(puenteId: string): void {
    this.cargandoPermanentes.set(true);
    if (!this.connectivity.online()) {
      this.inspeccionesLocales.obtenerDatosPermanentesLocal(puenteId).then(
        (datos) => {
          this.datosPermanentes.set(datos);
          this.cargandoPermanentes.set(false);
        },
        () => {
          this.datosPermanentes.set(null);
          this.cargandoPermanentes.set(false);
        },
      );
      return;
    }
    this.api.obtenerDatosPermanentes(puenteId).subscribe({
      next: (datos) => {
        this.datosPermanentes.set(datos);
        this.cargandoPermanentes.set(false);
      },
      error: async () => {
        const local = await this.inspeccionesLocales.obtenerDatosPermanentesLocal(puenteId);
        this.datosPermanentes.set(local);
        this.cargandoPermanentes.set(false);
      },
    });
  }

  private reiniciarEstado(): void {
    this.error.set(null);
    this.resultado.set(null);
    this.guardando.set(false);
    this.acompanantes.set([]);
    const emailUsuario = this.auth.user()?.email || '';
    this.form.enable({ emitEvent: false });
    this.form.reset({
      fechaInspeccion: this.hoy,
      nombreAutor: emailUsuario,
      confirmarDatosPermanentes: false,
      nombreAcompanante: '',
      colegiadoAcompanante: '',
    });
  }

  private obtenerDispositivoId(): string {
    const key = 'sgp_device_id';
    try {
      let id = localStorage.getItem(key);
      if (!id) {
        id = crypto.randomUUID();
        localStorage.setItem(key, id);
      }
      return id;
    } catch {
      return crypto.randomUUID();
    }
  }
}
