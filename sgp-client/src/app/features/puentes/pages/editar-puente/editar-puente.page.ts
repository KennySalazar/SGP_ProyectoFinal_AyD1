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
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { PuntoMapa } from '../../../../shared/utils/ubicacion-guatemala';
import { PuenteFormComponent } from '../../components/puente-form/puente-form.component';
import {
  ActualizarPuenteRequest,
  PuenteFormInicial,
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
  selector: 'app-editar-puente-page',
  standalone: true,
  imports: [TranslocoPipe, ButtonModule, TagModule, RouterLink, PuenteFormComponent],
  templateUrl: './editar-puente.page.html',
  styleUrl: './editar-puente.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditarPuentePage {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(PuenteApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly transloco = inject(TranslocoService);

  private solicitudPendiente: ActualizarPuenteRequest | null = null;

  readonly puenteId = signal<string | null>(null);
  readonly cargandoPuente = signal(true);
  readonly errorCargaPuente = signal(false);
  readonly puente = signal<PuenteResponse | null>(null);
  readonly puenteActualizado = signal<PuenteResponse | null>(null);

  readonly guardando = signal(false);
  readonly erroresCampos = signal<Record<string, string>>({});
  readonly errorRegistro = signal<string | null>(null);
  readonly errorTerritorio = signal<string | null>(null);
  readonly advertencia = signal<PuenteProblemDetails | null>(null);

  readonly valoresIniciales = signal<PuenteFormInicial | null>(null);

  readonly puntosCercanos = computed<PuntoMapa[]>(() =>
    (this.advertencia()?.puentesCercanos ?? []).map((puente) => ({
      id: puente.id,
      titulo: `${puente.codigo} — ${puente.nombre}`,
      latitud: puente.latitud,
      longitud: puente.longitud,
    })),
  );

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = params.get('id');
      if (id) {
        this.puenteId.set(id);
        this.cargarPuente(id);
      } else {
        this.cargandoPuente.set(false);
        this.errorCargaPuente.set(true);
      }
    });
  }

  cargarPuente(id: string): void {
    this.cargandoPuente.set(true);
    this.errorCargaPuente.set(false);

    this.api
      .obtenerPorId(id)
      .pipe(
        finalize(() => this.cargandoPuente.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (puente) => {
          this.puente.set(puente);
          this.valoresIniciales.set({
            nombre: puente.nombre,
            ruta: puente.ruta,
            kilometraje: puente.kilometraje,
            latitud: puente.latitud,
            longitud: puente.longitud,
          });
        },
        error: () => {
          this.errorCargaPuente.set(true);
        },
      });
  }

  actualizar(valores: PuenteFormValores): void {
    if (this.guardando() || !this.puente()) return;

    this.enviar({
      ...valores,
      confirmarCercania: false,
      codigo: this.puente()?.codigo,
    });
  }

  alCambiarFormulario(): void {
    this.cancelarConfirmacion();
    this.erroresCampos.set({});
    this.errorRegistro.set(null);
    this.errorTerritorio.set(null);
    this.puenteActualizado.set(null);
  }

  confirmarActualizacion(): void {
    const request = this.solicitudPendiente;

    if (!request || !this.advertencia() || this.guardando()) return;

    this.enviar({ ...request, confirmarCercania: true });
  }

  cancelarConfirmacion(): void {
    this.advertencia.set(null);
    this.solicitudPendiente = null;
  }

  severidad(estado: string): 'success' | 'warn' | 'danger' | 'info' | 'secondary' {
    switch (estado) {
      case 'Bueno':
        return 'success';
      case 'Regular':
        return 'warn';
      case 'Malo':
        return 'danger';
      default:
        return 'secondary';
    }
  }

  private detalleError(error: HttpErrorResponse, clavePredeterminada: string): string {
    const problem = error.error as PuenteProblemDetails | null;
    return problem?.detail ?? problem?.title ?? this.transloco.translate(clavePredeterminada);
  }

  private enviar(request: ActualizarPuenteRequest): void {
    const id = this.puenteId();
    if (!id) return;

    this.guardando.set(true);
    this.errorRegistro.set(null);
    this.erroresCampos.set({});
    this.cancelarConfirmacion();
    this.puenteActualizado.set(null);
    this.apiErrors.clear();

    this.api
      .actualizar(id, request)
      .pipe(
        finalize(() => this.guardando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (respuesta) => {
          this.puente.set(respuesta);
          this.puenteActualizado.set(respuesta);
        },
        error: (error: HttpErrorResponse) => {
          const problem = error.error as PuenteProblemDetails | null;

          if (
            error.status === 409 &&
            problem?.code === 'puente_cercano' &&
            problem.requiereConfirmacion === true
          ) {
            this.apiErrors.clear();
            this.solicitudPendiente = { ...request, confirmarCercania: false };
            this.advertencia.set(problem);
            return;
          }

          if (
            error.status === 422 &&
            problem?.code &&
            CODIGOS_UBICACION_RECHAZADA.includes(problem.code)
          ) {
            this.apiErrors.clear();
            this.errorTerritorio.set(this.detalleError(error, 'puentes.invalidTerritory'));
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

          this.erroresCampos.set(campos);
          this.errorRegistro.set(this.detalleError(error, 'puentes.updateFailed'));
        },
      });
  }
}
