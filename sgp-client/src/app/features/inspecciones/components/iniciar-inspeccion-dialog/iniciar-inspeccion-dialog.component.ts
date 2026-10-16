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
      void this.recuperarPendiente();
      this.obtenerGps();
      if (this.puente?.id) {
        this.cargarDatosPermanentes(this.puente.id);
      }
    }
  }

  cerrar(): void {
    this.visible = false;
    this.visibleChange.emit(false);
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
    this.api.iniciarInspeccion(request, clientUuid).subscribe({
      next: (res) => {
        if (this.auth.user()?.id !== actor || this.puente?.id !== request.puenteId) return;
        this.guardando.set(false);
        this.resultado.set(res);
        this.solicitudInicio = null;
        void this.cargarReporte();
        this.inspeccionIniciada.emit(res);
      },
      error: (err) => {
        this.guardando.set(false);
        const backendMsg =
          err.error?.message ||
          err.error?.detail ||
          'Ocurrió un error al iniciar la inspección. Verifique los datos ingresados.';
        this.error.set(backendMsg);
      },
    });
  }

  private cargarDatosPermanentes(puenteId: string): void {
    this.cargandoPermanentes.set(true);
    this.api.obtenerDatosPermanentes(puenteId).subscribe({
      next: (datos) => {
        this.datosPermanentes.set(datos);
        this.cargandoPermanentes.set(false);
      },
      error: () => {
        this.datosPermanentes.set(null);
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
