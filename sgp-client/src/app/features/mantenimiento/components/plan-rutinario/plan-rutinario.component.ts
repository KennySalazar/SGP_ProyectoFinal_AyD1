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
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { finalize } from 'rxjs';
import { AuthStore } from '../../../../core/services/auth.store';
import { PuenteResponse } from '../../../puentes/models/puente.models';
import { PlanMantenimiento } from '../../models/plan-mantenimiento.models';
import { PlanMantenimientoApiService } from '../../services/plan-mantenimiento-api.service';

@Component({
  selector: 'app-plan-rutinario',
  standalone: true,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    TranslocoPipe,
    ButtonModule,
    InputNumberModule,
    TextareaModule,
  ],
  templateUrl: './plan-rutinario.component.html',
  styleUrl: './plan-rutinario.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlanRutinarioComponent {
  private readonly api = inject(PlanMantenimientoApiService);
  private readonly auth = inject(AuthStore);
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly destroyRef = inject(DestroyRef);

  readonly puente = input.required<PuenteResponse>();
  readonly plan = signal<PlanMantenimiento | null>(null);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly editando = signal(false);
  readonly guardado = signal(false);
  readonly error = signal(false);
  readonly formulario = this.fb.group({
    periodicidadMeses: [12, [Validators.required, Validators.min(1), Validators.max(12)]],
    actividadesTexto: [''],
  });
  readonly puedeGestionar = computed(() => {
    const rol = this.auth.user()?.role;
    return this.puente().activo && (rol === 'CATEDRATICO' || rol === 'ADMINISTRADOR');
  });

  constructor() {
    effect(() => {
      const puenteId = this.puente().id;
      untracked(() => this.cargar(puenteId));
    });
  }

  cargar(puenteId = this.puente().id): void {
    this.cargando.set(true);
    this.error.set(false);
    this.api
      .obtenerPorPuente(puenteId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({ next: (plan) => this.plan.set(plan), error: () => this.error.set(true) });
  }

  editar(): void {
    const plan = this.plan();
    if (!plan || !this.puedeGestionar()) return;
    this.formulario.setValue({
      periodicidadMeses: plan.periodicidadMeses,
      actividadesTexto: plan.actividades.map((actividad) => actividad.descripcion).join('\n'),
    });
    this.editando.set(true);
    this.guardado.set(false);
  }

  cancelar(): void {
    if (!this.guardando()) this.editando.set(false);
  }

  guardar(): void {
    const plan = this.plan();
    if (!plan || !this.puedeGestionar() || this.formulario.invalid || this.guardando()) return;
    const actividades = this.formulario.controls.actividadesTexto.value
      .split('\n')
      .map((actividad) => actividad.trim())
      .filter(Boolean);
    this.guardando.set(true);
    this.error.set(false);
    this.api
      .actualizar(this.puente().id, {
        periodicidadMeses: this.formulario.controls.periodicidadMeses.value,
        actividades,
        version: plan.version,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardando.set(false)),
      )
      .subscribe({
        next: (actualizado) => {
          this.plan.set(actualizado);
          this.editando.set(false);
          this.guardado.set(true);
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 409) this.cargar();
          else this.error.set(true);
        },
      });
  }
}
