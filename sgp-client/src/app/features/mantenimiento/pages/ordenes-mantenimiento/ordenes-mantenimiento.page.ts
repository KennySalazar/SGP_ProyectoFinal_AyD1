import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { SkeletonModule } from 'primeng/skeleton';
import { TabsModule } from 'primeng/tabs';
import { TagModule } from 'primeng/tag';
import { catchError, finalize, of } from 'rxjs';
import { PuenteCatalogoResponse } from '../../../puentes/models/puente.models';
import { PuenteApiService } from '../../../puentes/services/puente-api.service';
import { PlanMantenimiento } from '../../models/plan-mantenimiento.models';
import {
  FiltrosOrdenes,
  PaginaOrdenesMantenimiento,
  TotalesOrdenes,
} from '../../models/orden-mantenimiento.models';
import { PrioridadMantenimiento, TipoMantenimiento } from '../../models/regla-mantenimiento.models';
import { OrdenMantenimientoApiService } from '../../services/orden-mantenimiento-api.service';
import { PlanMantenimientoApiService } from '../../services/plan-mantenimiento-api.service';
import {
  aFechaIso,
  PESTANAS_ORDENES,
  severidadEstadoOrden,
  ZONA_GUATEMALA,
} from '../../utils/estado-orden';

const TIPOS: TipoMantenimiento[] = ['RUTINARIO', 'PREVENTIVO', 'CORRECTIVO', 'EMERGENCIA'];
const PRIORIDADES: PrioridadMantenimiento[] = ['BAJA', 'MEDIA', 'ALTA', 'CRITICA'];

/** Bandeja general de mantenimiento (HU-049): pestañas por estado y filtros combinados. */
@Component({
  selector: 'app-ordenes-mantenimiento-page',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    TranslocoPipe,
    ButtonModule,
    DatePickerModule,
    PaginatorModule,
    SelectModule,
    SkeletonModule,
    TabsModule,
    TagModule,
  ],
  templateUrl: './ordenes-mantenimiento.page.html',
  styleUrl: './ordenes-mantenimiento.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrdenesMantenimientoPage {
  private readonly api = inject(OrdenMantenimientoApiService);
  private readonly puentesApi = inject(PuenteApiService);
  private readonly planesApi = inject(PlanMantenimientoApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly pestanas = PESTANAS_ORDENES;
  readonly tipos = TIPOS;
  readonly prioridades = PRIORIDADES;
  readonly zona = ZONA_GUATEMALA;
  readonly severidad = severidadEstadoOrden;

  readonly pestana = signal(PESTANAS_ORDENES[0].clave);
  readonly pagina = signal<PaginaOrdenesMantenimiento | null>(null);
  readonly totales = signal<TotalesOrdenes | null>(null);
  readonly cargando = signal(false);
  readonly error = signal(false);
  readonly planes = signal<PlanMantenimiento[]>([]);
  readonly cargandoPlanes = signal(true);
  readonly errorPlanes = signal(false);
  readonly tamanio = signal(20);

  readonly tipo = signal<TipoMantenimiento | null>(null);
  readonly prioridad = signal<PrioridadMantenimiento | null>(null);
  readonly puenteId = signal<string | null>(null);
  readonly desde = signal<Date | null>(null);
  readonly hasta = signal<Date | null>(null);

  readonly puentes = signal<PuenteCatalogoResponse[]>([]);
  readonly nombresPuente = computed(() => {
    const nombres: Record<string, string> = {};
    for (const puente of this.puentes()) {
      nombres[puente.id] = `${puente.codigo} · ${puente.nombre}`;
    }
    return nombres;
  });
  readonly hayFiltros = computed(
    () => !!(this.tipo() || this.prioridad() || this.puenteId() || this.desde() || this.hasta()),
  );

  constructor() {
    this.puentesApi
      .listarCatalogo({ pagina: 0, tamanio: 100 })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        catchError(() => of(null)),
      )
      .subscribe((respuesta) => this.puentes.set(respuesta?.content ?? []));
    this.cargar();
    this.cargarPlanes();
  }

  cargarPlanes(): void {
    this.cargandoPlanes.set(true);
    this.errorPlanes.set(false);
    this.planesApi
      .listar()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargandoPlanes.set(false)),
      )
      .subscribe({
        next: (planes) => this.planes.set(planes),
        error: () => this.errorPlanes.set(true),
      });
  }

  total(clave: string): number | null {
    const totales = this.totales();
    if (!totales) return null;
    const pestana = this.pestanas.find((p) => p.clave === clave);
    return (pestana?.estados ?? []).reduce((suma, estado) => suma + totales.porEstado[estado], 0);
  }

  cambiarPestana(clave: string | number | undefined): void {
    if (typeof clave !== 'string' || clave === this.pestana()) return;
    this.pestana.set(clave);
    this.cargarLista(0);
  }

  aplicarFiltros(): void {
    this.cargar();
  }

  limpiarFiltros(): void {
    this.tipo.set(null);
    this.prioridad.set(null);
    this.puenteId.set(null);
    this.desde.set(null);
    this.hasta.set(null);
    this.cargar();
  }

  cambiarPagina(evento: PaginatorState): void {
    if (evento.page === undefined || evento.rows === undefined) return;
    this.tamanio.set(evento.rows);
    this.cargarLista(evento.page);
  }

  /** Recarga la pestaña actual y los totales de todas las pestañas. */
  cargar(): void {
    this.cargarLista(0);
    this.api
      .totales(this.filtros())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        catchError(() => of(null)),
      )
      .subscribe((totales) => this.totales.set(totales));
  }

  cargarLista(pagina = this.pagina()?.number ?? 0): void {
    const estados = this.pestanas.find((p) => p.clave === this.pestana())?.estados ?? [];
    this.cargando.set(true);
    this.error.set(false);
    this.api
      .listar({ ...this.filtros(), estados }, pagina, this.tamanio())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (respuesta) => this.pagina.set(respuesta),
        error: () => {
          this.pagina.set(null);
          this.error.set(true);
        },
      });
  }

  private filtros(): FiltrosOrdenes {
    return {
      puenteId: this.puenteId(),
      tipo: this.tipo(),
      prioridad: this.prioridad(),
      desde: aFechaIso(this.desde()),
      hasta: aFechaIso(this.hasta()),
    };
  }
}
