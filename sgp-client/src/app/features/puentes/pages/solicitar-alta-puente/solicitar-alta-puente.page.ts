import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { TextareaModule } from 'primeng/textarea';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { PuenteFormComponent } from '../../components/puente-form/puente-form.component';
import {
  PuenteFormValores,
  PuenteProblemDetails,
  SolicitudAltaPuenteResponse,
} from '../../models/puente.models';
import { SolicitudAltaPuenteApiService } from '../../services/solicitud-alta-puente-api.service';

const CODIGOS_UBICACION_RECHAZADA = [
  'ubicacion_fuera_de_guatemala',
  'ubicacion_municipio_incongruente',
  'ubicacion_sin_municipio_activo',
];

@Component({
  selector: 'app-solicitar-alta-puente-page',
  standalone: true,
  imports: [
    TranslocoPipe,
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    TextareaModule,
    PuenteFormComponent,
  ],
  templateUrl: './solicitar-alta-puente.page.html',
  styleUrl: './solicitar-alta-puente.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SolicitarAltaPuentePage {
  private readonly api = inject(SolicitudAltaPuenteApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly transloco = inject(TranslocoService);

  readonly justificacion = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(2000)],
  });

  readonly guardando = signal(false);
  readonly erroresCampos = signal<Record<string, string>>({});
  readonly errorSolicitud = signal<string | null>(null);
  readonly errorTerritorio = signal<string | null>(null);
  readonly solicitudCreada = signal<SolicitudAltaPuenteResponse | null>(null);

  solicitar(valores: PuenteFormValores): void {
    if (this.guardando() || this.solicitudCreada()) return;

    if (this.justificacion.invalid) {
      this.justificacion.markAsTouched();
      return;
    }

    this.guardando.set(true);
    this.justificacion.disable({ emitEvent: false });
    this.errorSolicitud.set(null);
    this.erroresCampos.set({});
    this.apiErrors.clear();

    this.api
      .crear({ ...valores, justificacion: this.justificacion.value.trim() || null })
      .pipe(
        finalize(() => {
          this.guardando.set(false);
          this.justificacion.enable({ emitEvent: false });
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (solicitud) => this.solicitudCreada.set(solicitud),
        error: (error: HttpErrorResponse) => this.mostrarError(error),
      });
  }

  alCambiarFormulario(): void {
    this.erroresCampos.set({});
    this.errorSolicitud.set(null);
    this.errorTerritorio.set(null);
  }

  solicitarOtra(): void {
    this.solicitudCreada.set(null);
    this.justificacion.reset('');
    this.alCambiarFormulario();
    this.apiErrors.clear();
  }

  mensajeJustificacion(): string | null {
    const servidor = this.erroresCampos()['justificacion'];
    if (servidor) return servidor;

    return this.justificacion.touched && this.justificacion.invalid
      ? this.transloco.translate('puentes.solicitud.justificationInvalid')
      : null;
  }

  fechaGuatemala(fecha: string): string {
    return new Intl.DateTimeFormat('es-GT', {
      timeZone: 'America/Guatemala',
      dateStyle: 'medium',
      timeStyle: 'medium',
    }).format(new Date(fecha));
  }

  private mostrarError(error: HttpErrorResponse): void {
    const problem = error.error as PuenteProblemDetails | null;
    const detalle: string =
      problem?.detail ?? problem?.title ?? this.transloco.translate('puentes.solicitud.saveFailed');
    const campos: Record<string, string> = {};

    if (error.status === 422) {
      for (const item of problem?.errores ?? []) {
        if (item.campo && item.mensaje) campos[item.campo] = item.mensaje;
      }

      if (CODIGOS_UBICACION_RECHAZADA.includes(problem?.code ?? '')) {
        this.errorTerritorio.set(detalle);
      }
    }

    this.erroresCampos.set(campos);
    this.errorSolicitud.set(detalle);
  }
}
