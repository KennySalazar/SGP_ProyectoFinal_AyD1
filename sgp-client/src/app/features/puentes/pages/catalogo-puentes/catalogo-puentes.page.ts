import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { EMPTY, expand, finalize, reduce } from 'rxjs';
import { MapaPuentesComponent } from './mapa-puentes/mapa-puentes.component';
import {
  ConsultaCatalogoPuentes,
  DepartamentoResponse,
  EstadoPuente,
  PaginaResponse,
  PuenteCatalogoResponse,
} from '../../models/puente.models';
import { PuenteApiService } from '../../services/puente-api.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { AppShellComponent } from '../../../../layouts/app-shell/app-shell.component';

@Component({
  selector: 'app-catalogo-puentes-page',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    AppShellComponent,
    TranslocoPipe,
    ButtonModule,
    DialogModule,
    PaginatorModule,
    SelectModule,
    TableModule,
    TagModule,
    TextareaModule,
    RouterLink,
    ReactiveFormsModule,
    MapaPuentesComponent,
  ],
  templateUrl: './catalogo-puentes.page.html',
  styleUrl: './catalogo-puentes.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogoPuentesPage {
  readonly auth = inject(AuthStore);
  private readonly api = inject(PuenteApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  private consultaAplicada: ConsultaCatalogoPuentes = {};
  private listaPendiente = false;
  private mapaCompleto = false;
  readonly puentesMapa = signal<PuenteCatalogoResponse[]>([]);

  readonly filtros = this.fb.group({
    departamentoId: this.fb.control<string | null>(null),
    estado: this.fb.control<EstadoPuente | null>(null),
  });
  readonly filtroActividad = this.fb.control<'activos' | 'inactivos' | 'todos'>('activos');
  readonly opcionesActividad = [
    { etiqueta: 'puentes.catalogo.filterActiveOnly', valor: 'activos' as const },
    { etiqueta: 'puentes.catalogo.filterInactiveOnly', valor: 'inactivos' as const },
    { etiqueta: 'puentes.catalogo.filterAll', valor: 'todos' as const },
  ];
  readonly departamentos = signal<DepartamentoResponse[]>([]);
  readonly cargandoDepartamentos = signal(false);
  readonly errorDepartamentos = signal(false);
  readonly estados: EstadoPuente[] = ['Bueno', 'Regular', 'Malo', 'Sin evaluar'];
  readonly tamaniosPagina = [10, 20, 50, 100];
  readonly tamanioPagina = signal(20);
  readonly selectorTamanio = this.fb.nonNullable.control(20);
  readonly hayFiltrosAplicados = signal(false);

  readonly mostrarModalBaja = signal(false);
  readonly mostrarModalReactivar = signal(false);
  readonly puenteParaAccion = signal<PuenteCatalogoResponse | null>(null);
  readonly procesandoAccion = signal(false);
  readonly accionExitosa = signal<'baja' | 'reactivar' | null>(null);
  readonly errorAccion = signal<'baja' | 'reactivar' | null>(null);
  readonly nombrePuenteAccion = signal<string | null>(null);
  readonly formBaja = this.fb.group({
    motivo: this.fb.control('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(1000)],
    }),
  });
  readonly estiloPanelSelector = {
    // El panel se adjunta al body: garantizar una superficie opaca sin depender del tema.
    backgroundColor: '#ffffff',
    color: '#40546b',
    opacity: '1',
    border: '1px solid #d8e2ec',
    borderRadius: '12px',
    boxShadow: '0 12px 32px rgba(7, 20, 38, 0.16)',
    padding: '0.35rem',
  };
  readonly atributosBotonPagina = {
    style: { minWidth: '44px', minHeight: '44px', margin: '0 0.15rem', borderRadius: '8px' },
  };
  readonly estiloPaginador = {
    first: this.atributosBotonPagina,
    prev: this.atributosBotonPagina,
    page: this.atributosBotonPagina,
    next: this.atributosBotonPagina,
    last: this.atributosBotonPagina,
  };

  readonly vista = signal<'lista' | 'mapa'>('lista');
  readonly pagina = signal<PaginaResponse<PuenteCatalogoResponse> | null>(null);
  readonly puenteSeleccionado = signal<PuenteCatalogoResponse | null>(null);
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
    const esAdmin = this.auth.user()?.role === 'ADMINISTRADOR';
    const actividad = esAdmin ? (this.filtroActividad.value ?? 'activos') : 'activos';
    this.consultaAplicada = {
      ...(departamentoId ? { departamentoId } : {}),
      ...(estado ? { estado } : {}),
      ...(esAdmin && actividad === 'activos' ? { activo: true } : {}),
      ...(esAdmin && actividad === 'inactivos' ? { activo: false } : {}),
      ...(esAdmin && actividad === 'todos' ? { todos: true } : {}),
      pagina: 0,
      tamanio: this.tamanioPagina(),
    };
    this.hayFiltrosAplicados.set(
      !!(departamentoId || estado || (esAdmin && actividad !== 'activos')),
    );
    this.invalidarMapa();
    this.cargarCatalogo();
  }

  limpiarFiltros(): void {
    if (this.cargando()) return;
    this.filtros.reset();
    this.filtroActividad.setValue('activos');
    this.consultaAplicada = { pagina: 0, tamanio: this.tamanioPagina() };
    this.hayFiltrosAplicados.set(false);
    this.invalidarMapa();
    this.cargarCatalogo();
  }

  abrirModalBaja(puente: PuenteCatalogoResponse, evento?: Event): void {
    evento?.stopPropagation();
    this.accionExitosa.set(null);
    this.errorAccion.set(null);
    this.puenteParaAccion.set(puente);
    this.formBaja.reset({ motivo: '' });
    this.mostrarModalBaja.set(true);
  }

  cerrarModalBaja(): void {
    if (this.procesandoAccion()) return;
    this.mostrarModalBaja.set(false);
    this.puenteParaAccion.set(null);
    this.formBaja.reset({ motivo: '' });
  }

  confirmarBaja(): void {
    const puente = this.puenteParaAccion();
    const motivo = this.formBaja.controls.motivo.value.trim();
    if (!puente || !motivo || this.procesandoAccion()) return;

    this.procesandoAccion.set(true);
    this.errorAccion.set(null);

    this.api
      .darDeBaja(puente.id, motivo)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.procesandoAccion.set(false)),
      )
      .subscribe({
        next: () => {
          this.mostrarModalBaja.set(false);
          this.puenteParaAccion.set(null);
          this.formBaja.reset({ motivo: '' });
          this.nombrePuenteAccion.set(puente.nombre);
          this.accionExitosa.set('baja');
          this.invalidarMapa();
          this.cargarCatalogo();
        },
        error: () => {
          this.errorAccion.set('baja');
        },
      });
  }

  abrirModalReactivar(puente: PuenteCatalogoResponse, evento?: Event): void {
    evento?.stopPropagation();
    this.accionExitosa.set(null);
    this.errorAccion.set(null);
    this.puenteParaAccion.set(puente);
    this.mostrarModalReactivar.set(true);
  }

  cerrarModalReactivar(): void {
    if (this.procesandoAccion()) return;
    this.mostrarModalReactivar.set(false);
    this.puenteParaAccion.set(null);
  }

  confirmarReactivar(): void {
    const puente = this.puenteParaAccion();
    if (!puente || this.procesandoAccion()) return;

    this.procesandoAccion.set(true);
    this.errorAccion.set(null);

    this.api
      .reactivar(puente.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.procesandoAccion.set(false)),
      )
      .subscribe({
        next: () => {
          this.mostrarModalReactivar.set(false);
          this.puenteParaAccion.set(null);
          this.nombrePuenteAccion.set(puente.nombre);
          this.accionExitosa.set('reactivar');
          this.invalidarMapa();
          this.cargarCatalogo();
        },
        error: () => {
          this.errorAccion.set('reactivar');
        },
      });
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
    if (this.vista() === 'mapa') {
      this.cargarMapa();
      return;
    }
    this.puenteSeleccionado.set(null);
    this.cargando.set(true);
    this.errorConsulta.set(false);
    this.api
      .listarCatalogo(this.consultaAplicada)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (pagina) => {
          this.selectorTamanio.setValue(pagina.size, { emitEvent: false });
          this.pagina.set(pagina);
          this.listaPendiente = false;
          // Una página que contiene todo el catálogo también sirve para el mapa.
          if (pagina.content.length === pagina.totalElements) {
            this.puentesMapa.set(pagina.content);
            this.mapaCompleto = true;
          }
        },
        error: () => this.errorConsulta.set(true),
      });
  }

  cambiarVista(vista: 'lista' | 'mapa'): void {
    if (this.cargando() || this.vista() === vista) return;
    this.vista.set(vista);
    this.errorConsulta.set(false);
    if (vista === 'mapa') this.cargarMapa();
    else if (this.listaPendiente) {
      if (!this.mapaCompleto) {
        this.cargarCatalogo();
        return;
      }
      const puentes = this.puentesMapa();
      const number = this.consultaAplicada.pagina ?? 0;
      const size = this.tamanioPagina();
      const content = puentes.slice(number * size, (number + 1) * size);
      const totalPages = Math.ceil(puentes.length / size);
      this.pagina.set({
        content,
        number,
        size,
        totalPages,
        totalElements: puentes.length,
        first: number === 0,
        last: number + 1 >= totalPages,
        empty: content.length === 0,
      });
      this.selectorTamanio.setValue(size, { emitEvent: false });
      this.puenteSeleccionado.set(null);
      this.listaPendiente = false;
    }
  }

  private invalidarMapa(): void {
    this.mapaCompleto = false;
    this.puentesMapa.set([]);
    this.listaPendiente = true;
  }

  private cargarMapa(): void {
    if (this.mapaCompleto || this.cargando()) return;
    const consulta = { ...this.consultaAplicada, pagina: 0, tamanio: 100 };
    this.cargando.set(true);
    this.errorConsulta.set(false);
    this.api
      .listarCatalogo(consulta)
      .pipe(
        expand((pagina) =>
          pagina.number + 1 < pagina.totalPages
            ? this.api.listarCatalogo({ ...consulta, pagina: pagina.number + 1 })
            : EMPTY,
        ),
        reduce((puentes, pagina) => puentes.concat(pagina.content), [] as PuenteCatalogoResponse[]),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (puentes) => {
          this.puentesMapa.set(puentes);
          this.mapaCompleto = true;
        },
        error: () => this.errorConsulta.set(true),
      });
  }
}
