import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { SkeletonModule } from 'primeng/skeleton';
import { TabsModule } from 'primeng/tabs';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { catchError, finalize, of } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { InspeccionPublicadaResumenResponse } from '../../../inspecciones/models/inspeccion.models';
import { InspeccionApiService } from '../../../inspecciones/services/inspeccion-api.service';
import { PuenteResponse } from '../../../puentes/models/puente.models';
import { EstadoOrden, PaginaOrdenesMantenimiento } from '../../models/orden-mantenimiento.models';
import { PrioridadMantenimiento, TipoMantenimiento } from '../../models/regla-mantenimiento.models';
import { OrdenMantenimientoApiService } from '../../services/orden-mantenimiento-api.service';
import { PlanRutinarioComponent } from '../plan-rutinario/plan-rutinario.component';
import { severidadEstadoOrden, ZONA_GUATEMALA } from '../../utils/estado-orden';

type Grupo = 'abiertas' | 'cerradas';

const ESTADOS: Record<Grupo, EstadoOrden[]> = {
  abiertas: ['PROPUESTA', 'PROGRAMADA', 'EN_EJECUCION'],
  cerradas: ['EJECUTADA', 'DESCARTADA', 'CANCELADA'],
};
const TAMANIO = 10;

/**
 * Pestaña «Mantenimiento» de la ficha de un puente (HU-049): órdenes abiertas e historial de las
 * cerradas para todo usuario autenticado; acciones solo para Catedrático y Administrador.
 */
@Component({
  selector: 'app-mantenimiento-puente',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    TranslocoPipe,
    ButtonModule,
    DialogModule,
    InputTextModule,
    PaginatorModule,
    SelectModule,
    SkeletonModule,
    TabsModule,
    TagModule,
    TextareaModule,
    PlanRutinarioComponent,
  ],
  templateUrl: './mantenimiento-puente.component.html',
  styleUrl: './mantenimiento-puente.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MantenimientoPuenteComponent {
  private readonly api = inject(OrdenMantenimientoApiService);
  private readonly inspeccionesApi = inject(InspeccionApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly auth = inject(AuthStore);
  private readonly destroyRef = inject(DestroyRef);

  readonly puente = input.required<PuenteResponse>();

  readonly tipos: TipoMantenimiento[] = ['RUTINARIO', 'PREVENTIVO', 'CORRECTIVO', 'EMERGENCIA'];
  readonly prioridades: PrioridadMantenimiento[] = ['BAJA', 'MEDIA', 'ALTA', 'CRITICA'];
  readonly tamanio = TAMANIO;
  readonly zona = ZONA_GUATEMALA;
  readonly severidad = severidadEstadoOrden;

  readonly grupo = signal<Grupo>('abiertas');
  readonly paginas = signal<Record<Grupo, PaginaOrdenesMantenimiento | null>>({
    abiertas: null,
    cerradas: null,
  });
  readonly cargando = signal(false);
  readonly error = signal(false);

  readonly creando = signal(false);
  readonly guardando = signal(false);
  readonly errorCrear = signal<string | null>(null);
  readonly erroresCampo = signal<Record<string, string>>({});
  readonly ordenCreada = signal(false);
  readonly inspecciones = signal<InspeccionPublicadaResumenResponse[]>([]);
  readonly nuevoTipo = signal<TipoMantenimiento | null>(null);
  readonly nuevaDescripcion = signal('');
  readonly nuevaPrioridad = signal<PrioridadMantenimiento | null>(null);
  readonly nuevoElemento = signal('');
  readonly nuevaInspeccion = signal<string | null>(null);

  /** Replica la autorización del backend para mostrar acciones; el servidor decide igual. */
  readonly puedeGestionar = computed(() => {
    const rol = this.auth.user()?.role;
    return rol === 'CATEDRATICO' || rol === 'ADMINISTRADOR';
  });
  readonly formularioValido = computed(
    () => !!this.nuevoTipo() && !!this.nuevaPrioridad() && !!this.nuevaDescripcion().trim(),
  );

  constructor() {
    effect(() => {
      this.puente();
      untracked(() => {
        this.cargar('abiertas', 0);
        this.cargar('cerradas', 0);
      });
    });
  }

  cambiarGrupo(valor: string | number | undefined): void {
    if (valor === 'abiertas' || valor === 'cerradas') this.grupo.set(valor);
  }

  cambiarPagina(grupo: Grupo, evento: PaginatorState): void {
    if (evento.page !== undefined) this.cargar(grupo, evento.page);
  }

  reintentar(): void {
    this.cargar('abiertas', 0);
    this.cargar('cerradas', 0);
  }

  cargar(grupo: Grupo, pagina: number): void {
    this.cargando.set(true);
    this.error.set(false);
    this.api
      .listar({ puenteId: this.puente().id, estados: ESTADOS[grupo] }, pagina, TAMANIO)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (respuesta) => this.paginas.update((actual) => ({ ...actual, [grupo]: respuesta })),
        error: () => this.error.set(true),
      });
  }

  abrirNuevaOrden(): void {
    this.nuevoTipo.set(null);
    this.nuevaDescripcion.set('');
    this.nuevaPrioridad.set(null);
    this.nuevoElemento.set('');
    this.nuevaInspeccion.set(null);
    this.errorCrear.set(null);
    this.erroresCampo.set({});
    this.ordenCreada.set(false);
    this.creando.set(true);
    this.inspeccionesApi
      .listarPublicadasPorPuente(this.puente().id, 0, 100)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        catchError(() => of(null)),
      )
      .subscribe((respuesta) => this.inspecciones.set(respuesta?.content ?? []));
  }

  cerrarNuevaOrden(): void {
    if (this.guardando()) return;
    this.creando.set(false);
  }

  crearOrden(): void {
    const tipo = this.nuevoTipo();
    const prioridad = this.nuevaPrioridad();
    if (!tipo || !prioridad || !this.formularioValido() || this.guardando()) return;
    this.guardando.set(true);
    this.errorCrear.set(null);
    this.erroresCampo.set({});
    this.api
      .crear({
        puenteId: this.puente().id,
        tipo,
        prioridad,
        descripcion: this.nuevaDescripcion().trim(),
        elementoRef: this.nuevoElemento().trim() || null,
        inspeccionOrigenId: this.nuevaInspeccion(),
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardando.set(false)),
      )
      .subscribe({
        next: () => {
          this.creando.set(false);
          this.ordenCreada.set(true);
          this.grupo.set('abiertas');
          this.cargar('abiertas', 0);
        },
        error: (err: HttpErrorResponse) => {
          // El error se muestra dentro del diálogo; se evita duplicarlo en el aviso global.
          this.apiErrors.clear();
          const problema = err.error as {
            detail?: string;
            errores?: { campo: string; mensaje: string }[];
          } | null;
          const campos: Record<string, string> = {};
          for (const error of problema?.errores ?? []) campos[error.campo] = error.mensaje;
          this.erroresCampo.set(campos);
          this.errorCrear.set(
            Object.keys(campos).length > 0
              ? null
              : (problema?.detail ?? 'No fue posible crear la orden.'),
          );
        },
      });
  }
}
