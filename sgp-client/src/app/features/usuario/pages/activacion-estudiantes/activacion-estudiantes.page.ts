import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import {
  CursoDisponibleResponse,
  EstudiantePendienteResponse,
  UsuarioApiService,
} from '../../services/usuario-api.service';

@Component({
  selector: 'app-activacion-estudiantes-page',
  standalone: true,
  imports: [ReactiveFormsModule, TranslocoPipe, ButtonModule, InputTextModule, SelectModule],
  templateUrl: './activacion-estudiantes.page.html',
  styleUrl: './activacion-estudiantes.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivacionEstudiantesPage {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(UsuarioApiService);
  private readonly apiErrors = inject(ApiErrorService);

  readonly modo = signal<'activar' | 'vincular'>('activar');
  readonly estudiantes = signal<EstudiantePendienteResponse[]>([]);
  /** Cada estudiante con una etiqueta que lo distingue: su nombre y usuario si los tiene, y su correo. */
  readonly opcionesEstudiantes = computed(() =>
    this.estudiantes().map((estudiante) => ({
      id: estudiante.id,
      etiqueta: estudiante.nombreCompleto
        ? `${estudiante.nombreCompleto} (@${estudiante.nombreUsuario ?? '—'}) · ${estudiante.email}`
        : estudiante.email,
    })),
  );
  readonly cursos = signal<CursoDisponibleResponse[]>([]);
  readonly opcionesCursos = computed(() =>
    this.cursos().map((curso) => ({
      id: curso.id,
      etiqueta: `${curso.nombre} (${curso.periodo})`,
    })),
  );
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly exito = signal<string | null>(null);
  readonly form = this.fb.nonNullable.group({
    estudianteId: ['', Validators.required],
    cursoId: ['', Validators.required],
    seccion: ['', [Validators.required, Validators.maxLength(30)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.exito.set(null);
    (this.modo() === 'activar'
      ? this.api.listarEstudiantesPendientes()
      : this.api.listarEstudiantesActivos()
    )
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (estudiantes) => this.estudiantes.set(estudiantes),
        error: (error) => {
          this.estudiantes.set([]);
          this.apiErrors.normalize(error);
        },
      });
    this.api.listarCursosVigentes().subscribe({ next: (cursos) => this.cursos.set(cursos) });
  }

  cambiarModo(modo: 'activar' | 'vincular'): void {
    if (this.guardando() || this.cargando() || this.modo() === modo) return;
    this.modo.set(modo);
    this.estudiantes.set([]);
    this.form.reset();
    this.cargar();
  }

  activar(): void {
    if (this.form.invalid || !this.form.controls.seccion.value.trim() || this.guardando()) return;
    this.guardando.set(true);
    this.exito.set(null);
    const request = {
      ...this.form.getRawValue(),
      seccion: this.form.controls.seccion.value.trim(),
    };
    (this.modo() === 'activar'
      ? this.api.activarEstudiante(request)
      : this.api.vincularEstudiante(request)
    )
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: (response) => {
          const estudianteId = this.form.controls.estudianteId.value;
          if (this.modo() === 'activar')
            this.estudiantes.update((estudiantes) =>
              estudiantes.filter((estudiante) => estudiante.id !== estudianteId),
            );
          this.form.reset();
          this.exito.set(response.message);
        },
        error: (error) => this.apiErrors.normalize(error),
      });
  }
}
