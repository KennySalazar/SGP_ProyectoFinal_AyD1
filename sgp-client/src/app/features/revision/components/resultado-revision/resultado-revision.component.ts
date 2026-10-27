import { AprobacionCalibracionComponent } from '../../../formulario/components/aprobacion-calibracion/aprobacion-calibracion.component';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
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
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { Subject, finalize, switchMap, takeUntil } from 'rxjs';
import { AuthStore } from '../../../../core/services/auth.store';
import {
  ConfirmacionEstadoIcResponse,
  EstadoCondicionIc,
} from '../../../inspecciones/models/inspeccion.models';
import {
  ListadoObservacionesResponse,
  RevisionResultadoResponse,
  VeredictoRevision,
} from '../../models/revision.models';
import { RevisionApiService } from '../../services/revision-api.service';

@Component({
  selector: 'app-resultado-revision',
  standalone: true,
  imports: [
    AprobacionCalibracionComponent,
    RouterLink,
    DatePipe,
    ReactiveFormsModule,
    TranslocoPipe,
    ButtonModule,
    DialogModule,
    InputTextModule,
    SelectModule,
    TextareaModule,
  ],
  templateUrl: './resultado-revision.component.html',
  styles: `
    .seguimiento {
      padding: 1rem;
      margin-block: 1rem;
      overflow-wrap: anywhere;
    }
    form,
    .campos {
      display: grid;
      gap: 1rem;
    }
    label {
      display: grid;
      gap: 0.4rem;
    }
    input,
    textarea,
    p-select {
      width: 100%;
    }
    .acciones {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      margin-block: 1rem;
    }
    .privado {
      color: var(--sgp-steel-700);
    }
    article {
      padding-block: 1rem;
      border-bottom: 1px solid var(--sgp-border);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResultadoRevisionComponent {
  readonly inspeccionId = input.required<string>();
  readonly revisionId = input<string | null>(null);
  readonly actualizado = output<void>();
  private readonly api = inject(RevisionApiService);
  private readonly auth = inject(AuthStore);
  private readonly i18n = inject(TranslocoService);
  private readonly destruir = inject(DestroyRef);
  private readonly cancelar = new Subject<void>();
  private readonly fb = inject(FormBuilder);
  private readonly respuestas = new Map<string, FormControl<string>>();
  readonly esCatedratico = computed(() => this.auth.user()?.role === 'CATEDRATICO');
  readonly abierto = signal(false);
  readonly historial = signal<RevisionResultadoResponse[]>([]);
  readonly ciclo = signal<RevisionResultadoResponse | null>(null);
  readonly observaciones = signal<ListadoObservacionesResponse | null>(null);
  readonly calculo = signal<ConfirmacionEstadoIcResponse | null>(null);
  readonly ocupado = signal(false);
  readonly confirmando = signal(false);
  readonly error = signal<string | null>(null);
  readonly veredictos: VeredictoRevision[] = ['APROBADA', 'CAMBIOS_SOLICITADOS', 'RECHAZADA'];
  readonly estados: EstadoCondicionIc[] = ['BUENO', 'REGULAR', 'MALO'];
  readonly form = this.fb.group({
    veredicto: this.fb.nonNullable.control<VeredictoRevision>('APROBADA'),
    calificacion: this.fb.control<number | null>(null, [
      Validators.required,
      Validators.min(0),
      Validators.max(100),
      Validators.pattern(/^\d+(\.\d{1,2})?$/),
    ]),
    observacionGeneral: this.fb.nonNullable.control('', Validators.maxLength(2000)),
    estado: this.fb.control<EstadoCondicionIc | null>(null),
    justificacion: this.fb.nonNullable.control('', Validators.maxLength(10000)),
  });
  readonly puedeEmitir = computed(() => {
    const c = this.ciclo(),
      u = this.auth.user();
    return (
      !!c &&
      u?.role === 'CATEDRATICO' &&
      u.id === c.revisorId &&
      c.estadoRevision === 'EN_CURSO' &&
      c.estadoInspeccion === 'EN_REVISION'
    );
  });
  readonly esAutor = computed(
    () => this.auth.user()?.role === 'ESTUDIANTE' && this.auth.user()?.id === this.ciclo()?.autorId,
  );
  readonly ultimoConCambios = computed(() => {
    const c = this.ciclo();
    return (
      !!c &&
      this.esAutor() &&
      c.revisionId === this.historial()[0]?.revisionId &&
      c.veredicto === 'CAMBIOS_SOLICITADOS' &&
      c.estadoRevision === 'RESUELTA'
    );
  });
  readonly puedeResolver = computed(
    () =>
      this.ultimoConCambios() && this.ciclo()?.estadoInspeccion === 'BORRADOR' && !this.ocupado(),
  );
  respuesta(id: string): FormControl<string> {
    let control = this.respuestas.get(id);
    if (!control) {
      control = new FormControl('', {
        nonNullable: true,
        validators: [Validators.maxLength(2000)],
      });
      this.respuestas.set(id, control);
    }
    return control;
  }
  atenderCambios(): void {
    const c = this.ciclo();
    if (
      !c ||
      !this.ultimoConCambios() ||
      c.estadoInspeccion !== 'CAMBIOS_SOLICITADOS' ||
      this.ocupado()
    )
      return;
    this.ocupado.set(true);
    this.error.set(null);
    this.api
      .atenderCambios(c.revisionId)
      .pipe(
        takeUntil(this.cancelar),
        takeUntilDestroyed(this.destruir),
        finalize(() => this.ocupado.set(false)),
      )
      .subscribe({
        next: (r) => {
          this.ciclo.set(r);
          this.historial.update((h) => h.map((x) => (x.revisionId === r.revisionId ? r : x)));
          this.actualizado.emit();
        },
        error: (e) => this.mostrarError(e),
      });
  }
  resolverObservacion(id: string): void {
    const c = this.ciclo(),
      control = this.respuesta(id);
    if (!c || !this.puedeResolver() || control.invalid) return;
    this.ocupado.set(true);
    this.error.set(null);
    this.api
      .resolverObservacion(c.revisionId, id, control.value.trim() || null)
      .pipe(
        switchMap(() => this.api.observaciones(c.revisionId)),
        takeUntil(this.cancelar),
        takeUntilDestroyed(this.destruir),
        finalize(() => this.ocupado.set(false)),
      )
      .subscribe({
        next: (obs) => {
          this.observaciones.set(obs);
          this.actualizado.emit();
        },
        error: (e) => this.mostrarError(e),
      });
  }
  constructor() {
    effect(() => {
      this.auth.user();
      this.inspeccionId();
      this.revisionId();
      this.cancelar.next();
      this.respuestas.clear();
      this.historial.set([]);
      this.ciclo.set(null);
      this.observaciones.set(null);
      this.calculo.set(null);
      this.abierto.set(false);
      this.confirmando.set(false);
      this.ocupado.set(false);
      this.error.set(null);
      this.form.reset();
    });
  }
  abrir(): void {
    if (!['CATEDRATICO', 'ESTUDIANTE'].includes(this.auth.user()?.role ?? '')) return;
    this.abierto.set(true);
    this.cargar();
  }
  cerrar(): void {
    if (this.ocupado()) return;
    this.confirmando.set(false);
    this.abierto.set(false);
  }
  cargar(): void {
    if (this.ocupado()) return;
    this.calculo.set(null);
    this.error.set(null);
    this.ocupado.set(true);
    const actor = this.auth.user()?.id;
    this.api
      .historial(this.inspeccionId())
      .pipe(
        switchMap((historial) => {
          const propios = historial.filter(
            (c) =>
              c.inspeccionId === this.inspeccionId() &&
              (c.autorId === actor || c.revisorId === actor),
          );
          const ciclo = propios.find(
            (c) => c.revisionId === (this.revisionId() ?? propios[0]?.revisionId),
          );
          if (!ciclo) throw new Error('revision_no_encontrada');
          this.historial.set(propios);
          this.ciclo.set(ciclo);
          this.form.controls.observacionGeneral.setValue(ciclo.observacionGeneral ?? '');
          return this.api.observaciones(ciclo.revisionId);
        }),
        takeUntil(this.cancelar),
        takeUntilDestroyed(this.destruir),
        finalize(() => this.ocupado.set(false)),
      )
      .subscribe({
        next: (obs) => {
          this.observaciones.set(obs);
          if (this.puedeEmitir()) this.cargarEstado();
        },
        error: (e) => this.mostrarError(e),
      });
  }
  private cargarEstado(): void {
    const id = this.ciclo()?.revisionId;
    if (!id) return;
    this.api
      .estadoRevision(id)
      .pipe(takeUntil(this.cancelar), takeUntilDestroyed(this.destruir))
      .subscribe({
        next: (c) => {
          this.calculo.set(c);
          this.form.controls.estado.setValue(c.calculo.estadoCalculado);
        },
        error: (e) => this.mostrarError(e),
      });
  }
  valido(): boolean {
    if (!this.puedeEmitir() || this.form.invalid || this.ocupado()) return false;
    const f = this.form.getRawValue();
    if (f.veredicto !== 'APROBADA') return !!f.observacionGeneral.trim();
    return (
      !!this.calculo() &&
      !!f.estado &&
      this.observaciones()?.pendientes === 0 &&
      (f.estado === this.calculo()?.calculo.estadoCalculado || !!f.justificacion.trim())
    );
  }
  preparar(): void {
    if (this.valido()) this.confirmando.set(true);
  }
  emitir(): void {
    const ciclo = this.ciclo();
    if (!this.confirmando() || !ciclo || !this.valido()) return;
    const f = this.form.getRawValue();
    this.ocupado.set(true);
    this.error.set(null);
    this.api
      .emitirVeredicto(ciclo.revisionId, {
        veredicto: f.veredicto,
        calificacion: f.calificacion!,
        observacionGeneral: f.observacionGeneral.trim() || null,
        confirmacionEstado:
          f.veredicto === 'APROBADA'
            ? {
                estadoConfirmado: f.estado!,
                hashEntrada: this.calculo()!.calculo.hashEntrada,
                justificacion: f.justificacion.trim() || null,
              }
            : null,
      })
      .pipe(
        takeUntil(this.cancelar),
        takeUntilDestroyed(this.destruir),
        finalize(() => this.ocupado.set(false)),
      )
      .subscribe({
        next: (r) => {
          this.ciclo.set(r);
          this.historial.update((h) => h.map((c) => (c.revisionId === r.revisionId ? r : c)));
          this.confirmando.set(false);
          this.actualizado.emit();
        },
        error: (e) => {
          this.confirmando.set(false);
          this.mostrarError(e);
        },
      });
  }
  private mostrarError(e: unknown): void {
    const err = e as HttpErrorResponse;
    this.error.set(err.error?.detail ?? this.i18n.translate('revision.resultado.error'));
    if ([401, 403, 404].includes(err.status)) {
      this.historial.set([]);
      this.ciclo.set(null);
      this.observaciones.set(null);
      this.calculo.set(null);
    }
  }
}
