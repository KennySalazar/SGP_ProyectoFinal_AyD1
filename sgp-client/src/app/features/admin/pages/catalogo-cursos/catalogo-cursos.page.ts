import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
  viewChild,
  ElementRef,
} from '@angular/core';
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
  private readonly destroyRef = inject(DestroyRef);
  private readonly api = inject(CursoApiService);
  private readonly apiErrors = inject(ApiErrorService);

  readonly formularioCurso = viewChild<ElementRef<HTMLElement>>('formularioCurso');
  readonly hoy = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Guatemala',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());

  vigencia(curso: CursoResponse): string {
    if (curso.estado === 'FINALIZADO') return 'admin.courses.inactive';
    if (this.hoy < curso.fechaInicio) return 'admin.courses.upcoming';
    if (this.hoy > curso.fechaFin) return 'admin.courses.expired';
    return 'admin.courses.inDates';
  }

  readonly anioActual = Number(
    new Intl.DateTimeFormat('en', { year: 'numeric', timeZone: 'America/Guatemala' }).format(
      new Date(),
    ),
  );
  readonly periodos = computed(() => {
    const curso = this.cursoEnEdicion();
    const anio =
      curso?.periodo.match(/^(\d{4})-/)?.[1] ??
      curso?.fechaInicio.match(/^(\d{4})-/)?.[1] ??
      String(this.anioActual);
    return [
      {
        etiqueta: `Periodo 1 (${anio})`,
        valor: `${anio}-1`,
        inicio: `${anio}-01-01`,
        fin: `${anio}-06-30`,
      },
      {
        etiqueta: `Periodo 2 (${anio})`,
        valor: `${anio}-2`,
        inicio: `${anio}-07-01`,
        fin: `${anio}-12-31`,
      },
    ];
  });
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
    this.form.controls.periodo.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((valor) => {
        this.aplicarPeriodo(valor);
      });
    this.cargar();
  }

  private aplicarPeriodo(valor: string): void {
    const periodo = this.periodos().find((item) => item.valor === valor);
    this.form.patchValue(
      { fechaInicio: periodo?.inicio ?? '', fechaFin: periodo?.fin ?? '' },
      { emitEvent: false },
    );
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
    const periodo = this.form.controls.periodo.value;
    if (!this.periodos().some((item) => item.valor === periodo)) {
      this.form.controls.periodo.setErrors({ periodoInvalido: true });
      return;
    }
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
    const periodo = this.periodos().some((item) => item.valor === curso.periodo)
      ? curso.periodo
      : '';
    this.form.setValue(
      {
        nombre: curso.nombre,
        periodo,
        catedraticoId: curso.catedratico.id,
        fechaInicio: curso.fechaInicio,
        fechaFin: curso.fechaFin,
      },
      { emitEvent: false },
    );
    const elemento = this.formularioCurso()?.nativeElement;
    elemento?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    elemento?.focus({ preventScroll: true });
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
