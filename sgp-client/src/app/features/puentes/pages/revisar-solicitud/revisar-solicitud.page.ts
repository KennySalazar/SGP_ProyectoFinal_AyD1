import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { TextareaModule } from 'primeng/textarea';
import { finalize } from 'rxjs';
import { PuntoMapa } from '../../../../shared/utils/ubicacion-guatemala';
import {
  AvisoCercaniaComponent,
  puntosDeCercanos,
} from '../../components/aviso-cercania/aviso-cercania.component';
import { SolicitudFichaComponent } from '../../components/solicitud-ficha/solicitud-ficha.component';
import {
  PuenteProblemDetails,
  SolicitudAltaPuenteResponse,
  SolicitudRevisionDetalleResponse,
} from '../../models/puente.models';
import { SolicitudAltaPuenteApiService } from '../../services/solicitud-alta-puente-api.service';

function textoObligatorio(control: AbstractControl): { required: true } | null {
  return typeof control.value === 'string' && control.value.trim().length > 0
    ? null
    : { required: true };
}

@Component({
  selector: 'app-revisar-solicitud-page',
  standalone: true,
  imports: [
    TranslocoPipe,
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    TextareaModule,
    SolicitudFichaComponent,
    AvisoCercaniaComponent,
  ],
  templateUrl: './revisar-solicitud.page.html',
  styleUrl: './revisar-solicitud.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RevisarSolicitudPage {
  private readonly api = inject(SolicitudAltaPuenteApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly transloco = inject(TranslocoService);

  /** Identificador de la ruta (`withComponentInputBinding`). */
  readonly id = input.required<string>();

  readonly motivo = new FormControl('', {
    nonNullable: true,
    validators: [textoObligatorio, Validators.maxLength(1000)],
  });

  readonly detalle = signal<SolicitudRevisionDetalleResponse | null>(null);
  readonly cargando = signal(true);
  readonly errorCarga = signal<string | null>(null);
  readonly rechazando = signal(false);
  readonly procesando = signal(false);
  readonly errorDecision = signal<string | null>(null);
  readonly decision = signal<SolicitudAltaPuenteResponse | null>(null);

  readonly pendiente = computed(() => this.detalle()?.solicitud.estado === 'PENDIENTE');
  readonly hayCercanos = computed(
    () => this.pendiente() && (this.detalle()?.totalPuentesCercanos ?? 0) > 0,
  );

  readonly puntosCercanos = computed<PuntoMapa[]>(() =>
    puntosDeCercanos(this.detalle()?.puentesCercanos ?? []),
  );

  constructor() {
    effect(() => {
      this.id();
      this.cargar();
    });
  }

  cargar(): void {
    this.cargando.set(true);
    this.errorCarga.set(null);

    this.api
      .obtenerParaRevision(this.id())
      .pipe(
        finalize(() => this.cargando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (detalle) => this.detalle.set(detalle),
        error: (error: HttpErrorResponse) => {
          this.detalle.set(null);
          this.errorCarga.set(
            error.status === 404
              ? this.transloco.translate('puentes.revision.notFound')
              : this.transloco.translate('puentes.revision.detailFailed'),
          );
        },
      });
  }

  aprobar(): void {
    const detalle = this.detalle();
    if (!detalle || !this.pendiente() || this.procesando()) return;

    this.iniciarDecision();

    // El aviso de cercanía ya se mostró en pantalla; aprobar equivale a confirmarlo.
    this.api
      .aprobar(detalle.solicitud.id, this.hayCercanos())
      .pipe(
        finalize(() => this.procesando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (resultado) => this.decision.set(resultado),
        error: (error: HttpErrorResponse) => this.mostrarErrorDecision(error),
      });
  }

  abrirRechazo(): void {
    if (!this.pendiente() || this.procesando()) return;

    this.errorDecision.set(null);
    this.rechazando.set(true);
  }

  cancelarRechazo(): void {
    if (this.procesando()) return;

    this.rechazando.set(false);
    this.motivo.reset('');
  }

  rechazar(): void {
    const detalle = this.detalle();
    if (!detalle || !this.pendiente() || this.procesando()) return;

    if (this.motivo.invalid) {
      this.motivo.markAsTouched();
      return;
    }

    this.iniciarDecision();

    this.api
      .rechazar(detalle.solicitud.id, this.motivo.value.trim())
      .pipe(
        finalize(() => this.procesando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (resultado) => this.decision.set(resultado),
        error: (error: HttpErrorResponse) => this.mostrarErrorDecision(error),
      });
  }

  mensajeMotivo(): string | null {
    if (!this.motivo.touched || this.motivo.valid) return null;

    return this.transloco.translate(
      this.motivo.hasError('required')
        ? 'puentes.revision.reasonRequired'
        : 'puentes.revision.reasonTooLong',
    );
  }

  private iniciarDecision(): void {
    this.procesando.set(true);
    this.errorDecision.set(null);
    this.motivo.disable({ emitEvent: false });
  }

  private mostrarErrorDecision(error: HttpErrorResponse): void {
    this.motivo.enable({ emitEvent: false });

    const problem = error.error as PuenteProblemDetails | null;
    this.errorDecision.set(
      problem?.detail ??
        problem?.title ??
        this.transloco.translate('puentes.revision.decisionFailed'),
    );

    // La solicitud cambió mientras se revisaba (ya resuelta o con nuevos puentes cercanos).
    if (error.status === 409) {
      this.rechazando.set(false);
      this.cargar();
    }
  }
}
