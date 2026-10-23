import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { TextareaModule } from 'primeng/textarea';
import { forkJoin, of, Subscription } from 'rxjs';
import { AuthStore } from '../../../../core/services/auth.store';
import { ConnectivityService } from '../../../../offline/services/connectivity.service';
import {
  AprobacionCalibracion,
  EstadoCalibracion,
} from '../../models/aprobacion-calibracion.models';
import { AprobacionCalibracionApiService } from '../../services/aprobacion-calibracion-api.service';

@Component({
  selector: 'app-aprobacion-calibracion',
  standalone: true,
  imports: [ReactiveFormsModule, TranslocoPipe, ButtonModule, DialogModule, TextareaModule],
  templateUrl: './aprobacion-calibracion.component.html',
  styleUrl: './aprobacion-calibracion.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AprobacionCalibracionComponent {
  readonly versionId = input.required<string>();
  readonly estadoVersion = input('PUBLICADA');
  readonly gestion = input(false);
  readonly bloqueado = input(false);
  readonly aprobada = output<AprobacionCalibracion>();
  private readonly api = inject(AprobacionCalibracionApiService);
  private readonly auth = inject(AuthStore);
  private readonly conexion = inject(ConnectivityService);
  private readonly traduccion = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private lectura?: Subscription;
  private secuencia = 0;
  readonly estado = signal<EstadoCalibracion | null>(null);
  readonly aprobacion = signal<AprobacionCalibracion | null>(null);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);
  readonly exito = signal(false);
  readonly dialogo = signal(false);
  readonly denegado = signal(false);
  readonly observaciones = new FormControl('', {
    nonNullable: true,
    validators: Validators.maxLength(2000),
  });
  readonly offline = computed(() => !this.conexion.online());
  readonly puedeVerHistorial = computed(
    () => this.gestion() && ['ADMINISTRADOR', 'CATEDRATICO'].includes(this.auth.user()?.role ?? ''),
  );
  readonly puedeAprobar = computed(
    () =>
      this.gestion() &&
      this.auth.user()?.role === 'CATEDRATICO' &&
      this.estadoVersion() === 'PUBLICADA' &&
      this.estado()?.estado === 'SIN_APROBAR' &&
      !this.cargando() &&
      !this.guardando() &&
      !this.denegado() &&
      !this.offline() &&
      !this.bloqueado(),
  );

  constructor() {
    effect(() => {
      const id = this.versionId();
      const version = this.estadoVersion();
      this.gestion();
      this.auth.user();
      const online = this.conexion.online();
      untracked(() => {
        this.lectura?.unsubscribe();
        this.secuencia++;
        this.estado.set(null);
        this.aprobacion.set(null);
        this.error.set(null);
        this.denegado.set(false);
        this.exito.set(false);
        this.dialogo.set(false);
        this.observaciones.reset();
        this.cargando.set(false);
        if (id && version === 'PUBLICADA' && online) this.cargar();
      });
    });
    this.api.actualizada.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((id) => {
      if (id === this.versionId() && !this.gestion()) this.cargar();
    });
  }

  cargar(): void {
    if (
      !this.versionId() ||
      this.estadoVersion() !== 'PUBLICADA' ||
      this.offline() ||
      this.guardando()
    )
      return;
    this.lectura?.unsubscribe();
    const intento = ++this.secuencia;
    this.cargando.set(true);
    this.lectura = forkJoin({
      estado: this.api.estado(this.versionId()),
      aprobacion: this.puedeVerHistorial() ? this.api.consultar(this.versionId()) : of(null),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (datos) => {
          if (intento !== this.secuencia) return;
          this.estado.set(datos.estado);
          this.aprobacion.set(datos.aprobacion);
          if (datos.aprobacion && this.gestion()) this.aprobada.emit(datos.aprobacion);
          this.cargando.set(false);
        },
        error: () => {
          if (intento !== this.secuencia) return;
          this.cargando.set(false);
          this.error.set(this.texto('consultaError'));
        },
      });
  }

  abrir(): void {
    if (!this.puedeAprobar()) return;
    this.observaciones.reset();
    this.error.set(null);
    this.exito.set(false);
    this.dialogo.set(true);
  }

  cancelar(): void {
    if (this.guardando()) return;
    this.dialogo.set(false);
    this.observaciones.reset();
  }

  confirmar(): void {
    if (!this.dialogo() || !this.puedeAprobar() || this.observaciones.invalid) return;
    const id = this.versionId();
    this.guardando.set(true);
    this.error.set(null);
    this.api
      .aprobar(id, this.observaciones.value)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (aprobacion) => {
          this.guardando.set(false);
          if (id !== this.versionId()) return;
          this.aprobacion.set(aprobacion);
          this.estado.set({
            versionFormularioId: id,
            estado: 'APROBADA',
            rotuloCalibracion: aprobacion.rotuloCalibracion,
            aprobadoPorId: aprobacion.aprobadoPorId,
            aprobadoPorNombre: aprobacion.aprobadoPorNombre,
            aprobadoEn: aprobacion.aprobadoEn,
          });
          this.exito.set(true);
          this.dialogo.set(false);
          this.observaciones.reset();
          this.aprobada.emit(aprobacion);
        },
        error: (fallo) => {
          this.guardando.set(false);
          if (id !== this.versionId()) return;
          const codigo = fallo?.error?.code;
          if (codigo === 'calibracion_ya_aprobada') {
            this.error.set(this.texto('duplicada'));
            this.denegado.set(true);
            this.cargar();
          } else if (codigo === 'calibracion_version_no_publicada') {
            this.denegado.set(true);
            this.error.set(this.texto('soloPublicada'));
          } else if (fallo?.status === 403) {
            this.denegado.set(true);
            this.error.set(this.texto('sinPermiso'));
          } else if (fallo?.status === 401) {
            this.denegado.set(true);
            this.error.set(this.texto('sesionVencida'));
          } else if (fallo?.status === 0 || fallo?.status >= 500) {
            this.denegado.set(true);
            this.error.set(this.texto('resultadoIncierto'));
            this.cargar();
          } else {
            this.error.set(this.texto('guardarError'));
          }
        },
      });
  }

  reintentar(): void {
    if (this.guardando()) return;
    this.error.set(null);
    this.denegado.set(false);
    this.cargar();
  }

  fecha(valor: string): string {
    return new Intl.DateTimeFormat('es-GT', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      timeZone: 'America/Guatemala',
    }).format(new Date(valor));
  }

  private texto(clave: string): string {
    return this.traduccion.translate(`formulario.hu054.${clave}`);
  }
}
