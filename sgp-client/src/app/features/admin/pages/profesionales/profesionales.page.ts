import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { PaginaProfesionales, ProfesionalResponse } from '../../models/profesional.models';
import { ProfesionalApiService } from '../../services/profesional-api.service';

const TAMANIO_PAGINA = 10;

export type FiltroVerificacion = 'PENDIENTES' | 'VERIFICADOS' | 'TODOS';

@Component({
  selector: 'app-profesionales-page',
  standalone: true,
  imports: [
    TranslocoPipe,
    RouterLink,
    ButtonModule,
    DialogModule,
    PaginatorModule,
    TableModule,
    TagModule,
  ],
  templateUrl: './profesionales.page.html',
  styleUrl: './profesionales.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfesionalesPage {
  private readonly api = inject(ProfesionalApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formatoFecha = new Intl.DateTimeFormat('es-GT', {
    timeZone: 'America/Guatemala',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  });

  readonly filtros: FiltroVerificacion[] = ['PENDIENTES', 'VERIFICADOS', 'TODOS'];
  readonly filtro = signal<FiltroVerificacion>('PENDIENTES');
  readonly resultado = signal<PaginaProfesionales | null>(null);
  readonly cargando = signal(false);
  readonly errorLista = signal(false);

  readonly porVerificar = signal<ProfesionalResponse | null>(null);
  readonly verificando = signal(false);
  readonly errorVerificacion = signal<string | null>(null);
  readonly verificado = signal<ProfesionalResponse | null>(null);

  constructor() {
    this.cargar(0);
  }

  cargar(pagina: number): void {
    this.cargando.set(true);
    this.errorLista.set(false);

    this.api
      .listar(this.verificadoPorFiltro(), pagina, TAMANIO_PAGINA)
      .pipe(
        finalize(() => this.cargando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (resultado) => this.resultado.set(resultado),
        error: () => this.errorLista.set(true),
      });
  }

  filtrar(filtro: FiltroVerificacion): void {
    if (filtro === this.filtro()) return;
    this.filtro.set(filtro);
    this.resultado.set(null);
    this.cargar(0);
  }

  cambiarPagina(evento: PaginatorState): void {
    this.cargar(evento.page ?? 0);
  }

  /** La verificación no se puede deshacer, por eso se confirma en un diálogo. */
  confirmarVerificacion(profesional: ProfesionalResponse): void {
    this.errorVerificacion.set(null);
    this.verificado.set(null);
    this.porVerificar.set(profesional);
  }

  cancelarVerificacion(): void {
    if (this.verificando()) return;
    this.porVerificar.set(null);
    this.errorVerificacion.set(null);
  }

  verificar(): void {
    const profesional = this.porVerificar();
    if (!profesional || this.verificando()) return;

    this.verificando.set(true);
    this.errorVerificacion.set(null);
    this.apiErrors.clear();

    this.api
      .verificarColegiado(profesional.usuarioId)
      .pipe(
        finalize(() => this.verificando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (actualizado) => {
          this.porVerificar.set(null);
          this.verificado.set(actualizado);
          this.cargar(this.resultado()?.number ?? 0);
        },
        error: (error: HttpErrorResponse) => {
          // El motivo se muestra dentro del diálogo; por ejemplo, otro administrador ya la verificó.
          const problem = error.error as { detail?: string } | null;
          this.errorVerificacion.set(problem?.detail ?? null);
          this.apiErrors.clear();
          if (error.status === 409) this.cargar(this.resultado()?.number ?? 0);
        },
      });
  }

  fechaGuatemala(fecha: string): string {
    return this.formatoFecha.format(new Date(fecha));
  }

  private verificadoPorFiltro(): boolean | null {
    const filtro = this.filtro();
    return filtro === 'TODOS' ? null : filtro === 'VERIFICADOS';
  }
}
