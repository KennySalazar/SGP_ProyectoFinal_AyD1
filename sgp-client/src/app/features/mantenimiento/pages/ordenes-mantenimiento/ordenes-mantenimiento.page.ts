import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import { EstadoOrden, PaginaOrdenesMantenimiento } from '../../models/orden-mantenimiento.models';
import { OrdenMantenimientoApiService } from '../../services/orden-mantenimiento-api.service';
import { severidadEstadoOrden } from '../../utils/estado-orden';

@Component({
  selector: 'app-ordenes-mantenimiento-page',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    TranslocoPipe,
    ButtonModule,
    PaginatorModule,
    SelectModule,
    TagModule,
  ],
  templateUrl: './ordenes-mantenimiento.page.html',
  styleUrl: './ordenes-mantenimiento.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrdenesMantenimientoPage {
  private readonly api = inject(OrdenMantenimientoApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly pagina = signal<PaginaOrdenesMantenimiento | null>(null);
  readonly cargando = signal(false);
  readonly error = signal(false);
  readonly estadoFiltro = signal<EstadoOrden | null>(null);
  readonly tamanio = signal(20);

  readonly estados: { label: string; value: EstadoOrden | null }[] = [
    { label: 'Todas', value: null },
    { label: 'Propuesta', value: 'PROPUESTA' },
    { label: 'Programada', value: 'PROGRAMADA' },
    { label: 'En ejecución', value: 'EN_EJECUCION' },
    { label: 'Ejecutada', value: 'EJECUTADA' },
    { label: 'Descartada', value: 'DESCARTADA' },
    { label: 'Cancelada', value: 'CANCELADA' },
  ];

  readonly severidad = severidadEstadoOrden;

  constructor() {
    this.cargar();
  }

  cargar(pagina = 0): void {
    this.cargando.set(true);
    this.error.set(false);
    this.api
      .listar(pagina, this.tamanio(), this.estadoFiltro())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (respuesta) => this.pagina.set(respuesta),
        error: () => this.error.set(true),
      });
  }

  cambiarEstado(estado: EstadoOrden | null): void {
    this.estadoFiltro.set(estado);
    this.cargar();
  }

  cambiarPagina(evento: PaginatorState): void {
    if (evento.page === undefined || evento.rows === undefined) return;
    this.tamanio.set(evento.rows);
    this.cargar(evento.page);
  }
}
