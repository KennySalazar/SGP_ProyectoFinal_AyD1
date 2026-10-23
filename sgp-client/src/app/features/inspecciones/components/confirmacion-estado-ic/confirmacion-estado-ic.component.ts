import { AprobacionCalibracionComponent } from '../../../formulario/components/aprobacion-calibracion/aprobacion-calibracion.component';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
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
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { ConfirmacionEstadoIcResponse, EstadoCondicionIc } from '../../models/inspeccion.models';
import { InspeccionApiService } from '../../services/inspeccion-api.service';
import { ConnectivityService } from '../../../../offline/services/connectivity.service';

@Component({
  selector: 'app-confirmacion-estado-ic',
  standalone: true,
  imports: [
    AprobacionCalibracionComponent,
    CommonModule,
    ReactiveFormsModule,
    TranslocoPipe,
    ButtonModule,
    SelectModule,
    TextareaModule,
  ],
  templateUrl: './confirmacion-estado-ic.component.html',
  styleUrl: './confirmacion-estado-ic.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmacionEstadoIcComponent {
  readonly inspeccionId = input.required<string>();
  readonly bloqueado = input(false);
  readonly revision = input(0);
  readonly ocupado = output<boolean>();
  private readonly connectivity = inject(ConnectivityService);
  readonly esOffline = computed(() => !this.connectivity.online());
  private readonly api = inject(InspeccionApiService);
  private readonly destroyRef = inject(DestroyRef);
  private solicitud = 0;
  readonly respuesta = signal<ConfirmacionEstadoIcResponse | null>(null);
  readonly procesando = signal(false);
  readonly error = signal<string | null>(null);
  readonly seccionPendiente = signal<string | null>(null);
  readonly exito = signal(false);
  readonly restringido = signal(false);
  readonly estados = [
    { label: 'Bueno', value: 'BUENO' },
    { label: 'Regular', value: 'REGULAR' },
    { label: 'Malo', value: 'MALO' },
  ];
  readonly form = new FormGroup({
    estado: new FormControl<EstadoCondicionIc>('BUENO', {
      nonNullable: true,
      validators: Validators.required,
    }),
    justificacion: new FormControl('', {
      nonNullable: true,
      validators: Validators.maxLength(10000),
    }),
  });
  readonly calculo = computed(() => this.respuesta()?.calculo ?? null);

  constructor() {
    effect(() => {
      this.inspeccionId();
      this.revision();
      this.solicitud++;
      this.respuesta.set(null);
      this.error.set(null);
      this.seccionPendiente.set(null);
      this.exito.set(false);
      this.restringido.set(false);
      this.finalizar();
    });
    this.form.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.exito.set(false));
  }

  consultar(): void {
    if (this.esOffline()) {
      this.error.set('inspecciones.confirmacionIc.icPreliminarOffline');
      return;
    }
    if (this.bloqueado() || this.procesando() || this.restringido()) return;
    const turno = ++this.solicitud;
    this.iniciar();
    this.api
      .obtenerConfirmacionEstado(this.inspeccionId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          if (turno !== this.solicitud) return;
          this.aplicar(res);
          this.finalizar();
        },
        error: (err: HttpErrorResponse) => this.fallar(err, turno),
      });
  }

  guardar(): void {
    const res = this.respuesta();
    if (!res || this.bloqueado() || this.procesando() || this.restringido()) return;
    this.form.markAllAsTouched();
    const { estado, justificacion } = this.form.getRawValue();
    if (this.form.invalid || (estado !== res.calculo.estadoCalculado && !justificacion.trim())) {
      this.error.set('inspecciones.confirmacionIc.justificacionRequerida');
      return;
    }
    const turno = ++this.solicitud;
    this.iniciar();
    this.api
      .confirmarEstado(this.inspeccionId(), {
        estadoConfirmado: estado,
        hashEntrada: res.calculo.hashEntrada,
        justificacion: justificacion.trim() || null,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (actualizada) => {
          if (turno !== this.solicitud) return;
          this.aplicar(actualizada);
          this.exito.set(true);
          this.finalizar();
        },
        error: (err: HttpErrorResponse) => this.fallar(err, turno),
      });
  }

  requiereJustificacion(): boolean {
    return this.form.controls.estado.value !== this.calculo()?.estadoCalculado;
  }

  private aplicar(res: ConfirmacionEstadoIcResponse): void {
    this.respuesta.set(res);
    this.form.reset({
      estado:
        res.confirmacionVigente && res.estadoConfirmado
          ? res.estadoConfirmado
          : res.calculo.estadoCalculado,
      justificacion: res.confirmacionVigente ? (res.justificacion ?? '') : '',
    });
  }

  private iniciar(): void {
    this.procesando.set(true);
    this.ocupado.emit(true);
    this.error.set(null);
    this.seccionPendiente.set(null);
    this.exito.set(false);
  }

  private finalizar(): void {
    this.procesando.set(false);
    this.ocupado.emit(false);
  }

  private fallar(err: HttpErrorResponse, turno: number): void {
    if (turno !== this.solicitud) return;
    const codigo = err.error?.code;
    if (codigo === 'datos_ic_incompletos' && err.status === 422) {
      this.respuesta.set(null);
      const secciones: Record<string, string> = {
        subestructura: 'Subestructura',
        superestructura: 'Superestructura',
        elementos_no_estructurales: 'Elementos no estructurales',
        caracteristicas_cauce: 'Caracteristicas del cauce',
        identificacion: 'Identificacion del puente',
        datos_generales: 'Datos generales',
        tramos: 'Tramos',
        otros: 'Otros',
      };
      const ruta =
        typeof err.error?.detail === 'string'
          ? err.error.detail.match(/\/([a-z_]+)/)?.[1]
          : undefined;
      this.seccionPendiente.set(ruta ? (secciones[ruta] ?? null) : null);
      this.error.set('inspecciones.confirmacionIc.datosPendientes');
    } else if (err.status === 403) {
      this.restringido.set(true);
      this.respuesta.set(null);
      this.error.set('inspecciones.confirmacionIc.noAutorizado');
    } else if (err.status === 409) {
      this.respuesta.set(null);
      const desactualizado =
        codigo === 'calculo_ic_desactualizado' || codigo === 'confirmacion_ic_desactualizada';
      this.restringido.set(!desactualizado);
      this.error.set(
        desactualizado
          ? 'inspecciones.confirmacionIc.desactualizado'
          : 'inspecciones.confirmacionIc.noEditable',
      );
    } else {
      this.error.set(err.error?.detail ?? 'inspecciones.confirmacionIc.error');
    }
    this.finalizar();
  }
}
