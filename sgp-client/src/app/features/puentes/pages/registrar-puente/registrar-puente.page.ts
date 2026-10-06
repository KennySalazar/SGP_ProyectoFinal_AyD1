import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { PuntoMapa } from '../../../../shared/utils/ubicacion-guatemala';
import { PuenteFormComponent } from '../../components/puente-form/puente-form.component';
import {
  CrearPuenteRequest,
  PuenteFormValores,
  PuenteProblemDetails,
  PuenteResponse,
} from '../../models/puente.models';
import { PuenteApiService } from '../../services/puente-api.service';

const CODIGOS_UBICACION_RECHAZADA = [
  'ubicacion_fuera_de_guatemala',
  'ubicacion_municipio_incongruente',
  'ubicacion_sin_municipio_activo',
];

@Component({
  selector: 'app-registrar-puente-page',
  standalone: true,
  imports: [TranslocoPipe, DecimalPipe, ButtonModule, PuenteFormComponent],
  templateUrl: './registrar-puente.page.html',
  styleUrl: './registrar-puente.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegistrarPuentePage {
  private readonly api = inject(PuenteApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly transloco = inject(TranslocoService);

  private solicitudPendiente: CrearPuenteRequest | null = null;

  readonly guardando = signal(false);
  readonly erroresCampos = signal<Record<string, string>>({});
  readonly errorRegistro = signal<string | null>(null);
  readonly errorTerritorio = signal<string | null>(null);
  readonly advertencia = signal<PuenteProblemDetails | null>(null);
  readonly puenteRegistrado = signal<PuenteResponse | null>(null);

  readonly puntosCercanos = computed<PuntoMapa[]>(() =>
    (this.advertencia()?.puentesCercanos ?? []).map((puente) => ({
      id: puente.id,
      titulo: `${puente.codigo} — ${puente.nombre}`,
      latitud: puente.latitud,
      longitud: puente.longitud,
    })),
  );

  registrar(valores: PuenteFormValores): void {
    if (this.guardando() || this.puenteRegistrado()) return;

    this.enviar({ ...valores, confirmarCercania: false });
  }

  alCambiarFormulario(): void {
    this.cancelarConfirmacion();
    this.erroresCampos.set({});
    this.errorRegistro.set(null);
    this.errorTerritorio.set(null);
  }

  confirmarRegistro(): void {
    const request = this.solicitudPendiente;

    if (!request || !this.advertencia() || this.guardando() || this.puenteRegistrado()) return;

    this.enviar({ ...request, confirmarCercania: true });
  }

  cancelarConfirmacion(): void {
    this.advertencia.set(null);
    this.solicitudPendiente = null;
  }

  registrarOtro(): void {
    this.puenteRegistrado.set(null);
    this.cancelarConfirmacion();
    this.errorRegistro.set(null);
    this.errorTerritorio.set(null);
    this.erroresCampos.set({});
    this.apiErrors.clear();
  }

  fechaGuatemala(fecha: string): string {
    return new Intl.DateTimeFormat('es-GT', {
      timeZone: 'America/Guatemala',
      dateStyle: 'medium',
      timeStyle: 'medium',
    }).format(new Date(fecha));
  }

  private detalleError(error: HttpErrorResponse, clavePredeterminada: string): string {
    const problem = error.error as PuenteProblemDetails | null;
    return problem?.detail ?? problem?.title ?? this.transloco.translate(clavePredeterminada);
  }

  private enviar(request: CrearPuenteRequest): void {
    this.guardando.set(true);
    this.errorRegistro.set(null);
    this.erroresCampos.set({});
    this.cancelarConfirmacion();
    this.apiErrors.clear();

    this.api
      .registrar(request)
      .pipe(
        finalize(() => this.guardando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (puente) => this.puenteRegistrado.set(puente),
        error: (error: HttpErrorResponse) => {
          const problem = error.error as PuenteProblemDetails | null;

          if (
            error.status === 409 &&
            problem?.code === 'puente_cercano' &&
            problem.requiereConfirmacion === true
          ) {
            this.apiErrors.clear();
            this.solicitudPendiente = {
              ...request,
              confirmarCercania: false,
            };
            this.advertencia.set(problem);
            return;
          }

          const campos: Record<string, string> = {};

          if (error.status === 422) {
            for (const detalle of problem?.errores ?? []) {
              if (detalle.campo && detalle.mensaje) {
                campos[detalle.campo] = detalle.mensaje;
              }
            }
          }

          if (error.status === 422 && CODIGOS_UBICACION_RECHAZADA.includes(problem?.code ?? '')) {
            this.errorTerritorio.set(this.detalleError(error, 'puentes.locationLookupFailed'));
          }

          this.erroresCampos.set(campos);
          this.errorRegistro.set(this.detalleError(error, 'puentes.saveFailed'));
        },
      });
  }
}
