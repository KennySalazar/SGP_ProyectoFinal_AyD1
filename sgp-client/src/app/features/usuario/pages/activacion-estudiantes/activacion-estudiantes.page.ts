import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
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

  readonly estudiantes = signal<EstudiantePendienteResponse[]>([]);
  readonly cursos = signal<CursoDisponibleResponse[]>([]);
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
    this.api
      .listarEstudiantesPendientes()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({ next: (estudiantes) => this.estudiantes.set(estudiantes) });
    this.api.listarCursosVigentes().subscribe({ next: (cursos) => this.cursos.set(cursos) });
  }

  activar(): void {
    if (this.form.invalid || this.guardando()) return;
    this.guardando.set(true);
    this.exito.set(null);
    this.api
      .activarEstudiante({
        ...this.form.getRawValue(),
        seccion: this.form.controls.seccion.value.trim(),
      })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: (response) => {
          const estudianteId = this.form.controls.estudianteId.value;
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
