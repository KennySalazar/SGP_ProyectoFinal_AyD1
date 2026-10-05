import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import {
  ConsultaCatalogoPuentes,
  DepartamentoResponse,
  EstadoPuente,
  PaginaResponse,
  PuenteCatalogoResponse,
} from '../../models/puente.models';
import { PuenteApiService } from '../../services/puente-api.service';

@Component({
  selector: 'app-catalogo-puentes-page',
  standalone: true,
  imports: [
    TranslocoPipe,
    ButtonModule,
    PaginatorModule,
    SelectModule,
    TableModule,
    TagModule,
    RouterLink,
    ReactiveFormsModule,
  ],
  templateUrl: './catalogo-puentes.page.html',
  styleUrl: './catalogo-puentes.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogoPuentesPage {
  private readonly api = inject(PuenteApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  private consultaAplicada: ConsultaCatalogoPuentes = {};

  readonly filtros = this.fb.group({
    departamentoId: this.fb.control<string | null>(null),
    estado: this.fb.control<EstadoPuente | null>(null),
  });
  readonly departamentos = signal<DepartamentoResponse[]>([]);
  readonly cargandoDepartamentos = signal(false);
  readonly errorDepartamentos = signal(false);
  readonly estados: EstadoPuente[] = ['Bueno', 'Regular', 'Malo', 'Sin evaluar'];
  readonly tamaniosPagina = [10, 20, 50, 100];
  readonly tamanioPagina = signal(20);

  readonly vista = signal<'lista' | 'mapa'>('lista');
  readonly pagina = signal<PaginaResponse<PuenteCatalogoResponse> | null>(null);
  readonly cargando = signal(false);
  readonly errorConsulta = signal(false);
  private readonly severidadEstado: Record<
    EstadoPuente,
    'success' | 'warn' | 'danger' | 'secondary'
  > = {
    Bueno: 'success',
    Regular: 'warn',
    Malo: 'danger',
    'Sin evaluar': 'secondary',
  };

  constructor() {
    this.cargarDepartamentos();
    this.cargarCatalogo();
  }

  cargarDepartamentos(): void {
    if (this.cargandoDepartamentos()) return;
    this.cargandoDepartamentos.set(true);
    this.errorDepartamentos.set(false);
    this.api
      .listarDepartamentos()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargandoDepartamentos.set(false)),
      )
      .subscribe({
        next: (pagina) => this.departamentos.set(pagina.content),
        error: () => this.errorDepartamentos.set(true),
      });
  }

  aplicarFiltros(): void {
    if (this.cargando()) return;
    const { departamentoId, estado } = this.filtros.getRawValue();
    this.consultaAplicada = {
      ...(departamentoId ? { departamentoId } : {}),
      ...(estado ? { estado } : {}),
      pagina: 0,
      tamanio: this.tamanioPagina(),
    };
    this.cargarCatalogo();
  }

  limpiarFiltros(): void {
    if (this.cargando()) return;
    this.filtros.reset();
    this.consultaAplicada = { pagina: 0, tamanio: this.tamanioPagina() };
    this.cargarCatalogo();
  }

  cambiarPagina(evento: PaginatorState): void {
    if (this.cargando()) return;
    const tamanio = evento.rows;
    const pagina = evento.page;
    if (
      tamanio === undefined ||
      pagina === undefined ||
      !Number.isInteger(tamanio) ||
      tamanio < 1 ||
      tamanio > 100 ||
      !Number.isInteger(pagina) ||
      pagina < 0
    )
      return;

    const cambioTamanio = tamanio !== this.tamanioPagina();
    const nuevaPagina = cambioTamanio ? 0 : pagina;
    if (!cambioTamanio && nuevaPagina === (this.consultaAplicada.pagina ?? 0)) return;

    this.tamanioPagina.set(tamanio);
    this.consultaAplicada = { ...this.consultaAplicada, pagina: nuevaPagina, tamanio };
    this.cargarCatalogo();
  }

  severidad(estado: EstadoPuente): 'success' | 'warn' | 'danger' | 'secondary' {
    return this.severidadEstado[estado];
  }

  cargarCatalogo(): void {
    if (this.cargando()) return;
    this.cargando.set(true);
    this.errorConsulta.set(false);
    this.api
      .listarCatalogo(this.consultaAplicada)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (pagina) => this.pagina.set(pagina),
        error: () => this.errorConsulta.set(true),
      });
  }
}
