import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, Validators } from '@angular/forms';
import { FormBuilder } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { CatedraticoResponse, CursoResponse } from '../../models/curso.models';
import { CursoApiService } from '../../services/curso-api.service';

@Component({
  selector: 'app-catalogo-cursos-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    ButtonModule,
    InputTextModule,
    SelectModule,
    TagModule,
  ],
  templateUrl: './catalogo-cursos.page.html',
  styleUrl: './catalogo-cursos.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogoCursosPage {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(CursoApiService);
  private readonly apiErrors = inject(ApiErrorService);

  readonly cursos = signal<CursoResponse[]>([]);
  readonly catedraticos = signal<CatedraticoResponse[]>([]);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly finalizandoId = signal<string | null>(null);
  readonly cursoEnEdicion = signal<CursoResponse | null>(null);
  readonly exito = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(150)]],
    periodo: ['', [Validators.required, Validators.maxLength(50)]],
    catedraticoId: ['', Validators.required],
    fechaInicio: ['', Validators.required],
    fechaFin: ['', Validators.required],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    if (this.cargando()) return;
    this.cargando.set(true);
    this.apiErrors.clear();
    this.api
      .listar()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({ next: (pagina) => this.cursos.set(pagina.content) });
    this.api
      .listarCatedraticos()
      .subscribe({ next: (catedraticos) => this.catedraticos.set(catedraticos) });
  }

  guardar(): void {
    this.form.markAllAsTouched();
    if (this.guardando() || this.form.invalid) return;
    const values = this.form.getRawValue();
    if (values.fechaFin < values.fechaInicio) {
      this.form.controls.fechaFin.setErrors({ rango: true });
      return;
    }
    this.guardando.set(true);
    this.exito.set(null);
    const request = {
      nombre: values.nombre.trim(),
      periodo: values.periodo.trim(),
      catedraticoId: values.catedraticoId,
      fechaInicio: values.fechaInicio,
      fechaFin: values.fechaFin,
    };
    const cursoEnEdicion = this.cursoEnEdicion();
    const operacion = cursoEnEdicion
      ? this.api.actualizar(cursoEnEdicion.id, request)
      : this.api.crear(request);
    operacion.pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: (curso) => {
        this.cursos.update((cursos) =>
          cursoEnEdicion
            ? cursos.map((item) => (item.id === curso.id ? curso : item))
            : [curso, ...cursos],
        );
        this.form.reset();
        this.cursoEnEdicion.set(null);
        this.exito.set(cursoEnEdicion ? 'admin.courses.updated' : 'admin.courses.created');
      },
    });
  }

  editar(curso: CursoResponse): void {
    this.cursoEnEdicion.set(curso);
    this.exito.set(null);
    this.form.setValue({
      nombre: curso.nombre,
      periodo: curso.periodo,
      catedraticoId: curso.catedratico.id,
      fechaInicio: curso.fechaInicio,
      fechaFin: curso.fechaFin,
    });
  }

  cancelarEdicion(): void {
    this.cursoEnEdicion.set(null);
    this.form.reset();
  }

  finalizar(curso: CursoResponse): void {
    if (this.finalizandoId() || curso.estado !== 'VIGENTE') return;
    this.finalizandoId.set(curso.id);
    this.exito.set(null);
    this.api
      .finalizar(curso.id)
      .pipe(finalize(() => this.finalizandoId.set(null)))
      .subscribe({
        next: (actualizado) => {
          this.cursos.update((cursos) =>
            cursos.map((item) => (item.id === actualizado.id ? actualizado : item)),
          );
          this.exito.set('admin.courses.finalized');
        },
      });
  }
}
