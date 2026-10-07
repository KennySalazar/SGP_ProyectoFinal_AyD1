import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import {
  AsignacionPuenteResponse,
  EstudianteAsignableResponse,
  PuenteAsignableResponse,
} from '../../models/puente.models';
import { PuenteApiService } from '../../services/puente-api.service';

interface OpcionEstudiante extends EstudianteAsignableResponse {
  etiqueta: string;
}

interface OpcionPuente extends PuenteAsignableResponse {
  etiqueta: string;
}

@Component({
  selector: 'app-asignaciones-puentes-page',
  standalone: true,
  imports: [ReactiveFormsModule, TranslocoPipe, ButtonModule, InputTextModule, SelectModule],
  templateUrl: './asignaciones-puentes.page.html',
  styleUrl: './asignaciones-puentes.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AsignacionesPuentesPage {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(PuenteApiService);
  private readonly apiErrors = inject(ApiErrorService);

  readonly estudiantes = signal<OpcionEstudiante[]>([]);
  readonly puentes = signal<OpcionPuente[]>([]);
  readonly asignaciones = signal<AsignacionPuenteResponse[]>([]);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly exito = signal<string | null>(null);
  readonly asignacionARevocar = signal<AsignacionPuenteResponse | null>(null);
  readonly form = this.fb.nonNullable.group({
    cursoEstudianteId: ['', Validators.required],
    puenteId: ['', Validators.required],
  });
  readonly revocacionForm = this.fb.nonNullable.group({
    motivo: ['', [Validators.required, Validators.maxLength(1000)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    if (this.cargando()) return;
    this.cargando.set(true);
    this.exito.set(null);
    this.api
      .listarEstudiantesAsignables()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (estudiantes) =>
          this.estudiantes.set(
            estudiantes.map((estudiante) => ({
              ...estudiante,
              etiqueta: `${estudiante.estudianteEmail} — ${estudiante.cursoNombre} (${estudiante.periodo})`,
            })),
          ),
        error: (error) => this.apiErrors.normalize(error),
      });
    this.api.listarPuentesAsignables().subscribe({
      next: (puentes) =>
        this.puentes.set(
          puentes.map((puente) => ({ ...puente, etiqueta: `${puente.codigo} — ${puente.nombre}` })),
        ),
      error: (error) => this.apiErrors.normalize(error),
    });
    this.api.listarAsignaciones().subscribe({
      next: (asignaciones) => this.asignaciones.set(asignaciones),
      error: (error) => this.apiErrors.normalize(error),
    });
  }

  asignar(): void {
    if (this.form.invalid || this.guardando()) return;
    this.guardando.set(true);
    this.exito.set(null);
    this.api
      .asignarPuente(this.form.getRawValue())
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: (response) => {
          this.form.reset();
          this.exito.set(response.message);
          this.cargar();
        },
        error: (error) => this.apiErrors.normalize(error),
      });
  }

  seleccionarRevocacion(asignacion: AsignacionPuenteResponse): void {
    this.asignacionARevocar.set(asignacion);
    this.revocacionForm.reset();
  }

  cancelarRevocacion(): void {
    this.asignacionARevocar.set(null);
    this.revocacionForm.reset();
  }

  revocar(): void {
    const asignacion = this.asignacionARevocar();
    if (!asignacion || this.revocacionForm.invalid || this.guardando()) return;
    this.guardando.set(true);
    this.exito.set(null);
    this.api
      .revocarAsignacion(asignacion.id, this.revocacionForm.controls.motivo.value.trim())
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: (response) => {
          this.asignaciones.update((items) => items.filter((item) => item.id !== asignacion.id));
          this.cancelarRevocacion();
          this.exito.set(response.message);
        },
        error: (error) => this.apiErrors.normalize(error),
      });
  }
}
