import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { JsonPipe } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { FormularioApiService } from '../../../formulario/services/formulario-api.service';
import { VersionFormularioResumen } from '../../../formulario/models/formulario.models';
import {
  CondicionRegla,
  CrearReglaRequest,
  EditarReglaRequest,
  PrioridadMantenimiento,
  ReglaMantenimiento,
  ReglaPrioridad,
  ResumenReglasVersion,
  TipoMantenimiento,
} from '../../models/regla-mantenimiento.models';
import { ReglaMantenimientoApiService } from '../../services/regla-mantenimiento-api.service';

const TIPOS_MANTENIMIENTO: TipoMantenimiento[] = [
  'RUTINARIO',
  'PREVENTIVO',
  'CORRECTIVO',
  'EMERGENCIA',
];

const PRIORIDADES: PrioridadMantenimiento[] = ['BAJA', 'MEDIA', 'ALTA', 'CRITICA'];

const OPERADORES = [
  { label: 'Igual (=)', value: 'IGUAL' },
  { label: 'Mayor o igual (>=)', value: 'MAYOR_O_IGUAL' },
  { label: 'Menor o igual (<=)', value: 'MENOR_O_IGUAL' },
  { label: 'Distinto (!=)', value: 'DISTINTO' },
];

@Component({
  selector: 'app-catalogo-reglas-page',
  standalone: true,
  imports: [
    JsonPipe,
    FormsModule,
    TranslocoPipe,
    ReactiveFormsModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    PaginatorModule,
    SelectModule,
    TableModule,
    TagModule,
    TextareaModule,
  ],
  templateUrl: './catalogo-reglas.page.html',
  styleUrl: './catalogo-reglas.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogoReglasPage implements OnInit {
  readonly auth = inject(AuthStore);
  private readonly route = inject(ActivatedRoute);
  private versionSolicitada: string | null = null;
  private versionesCargadas = false;
  private readonly api = inject(ReglaMantenimientoApiService);
  private readonly formularioApi = inject(FormularioApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  // Opciones
  readonly tiposMantenimiento = TIPOS_MANTENIMIENTO;
  readonly prioridades = PRIORIDADES;
  readonly operadores = OPERADORES;
  readonly opcionesTipo = [
    { label: 'Todos los tipos', value: null as TipoMantenimiento | null },
    ...TIPOS_MANTENIMIENTO.map((t) => ({ label: t, value: t })),
  ];
  readonly opcionesEstado = [
    { label: 'Todas', value: null as boolean | null },
    { label: 'Solo activas', value: true },
    { label: 'Solo inactivas', value: false },
  ];

  // Estado
  readonly versiones = signal<VersionFormularioResumen[]>([]);
  readonly versionSeleccionadaId = signal<string | null>(null);
  readonly versionCodigoActual = computed(() => {
    const id = this.versionSeleccionadaId();
    if (!id) return '';
    const v = this.versiones().find((ver) => ver.id === id);
    return v ? v.codigo : '';
  });
  readonly reglas = signal<ReglaMantenimiento[]>([]);
  readonly resumen = signal<ResumenReglasVersion | null>(null);
  readonly totalElementos = signal(0);
  readonly paginaActual = signal(0);
  readonly tamanioPagina = signal(10);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly sembrando = signal(false);
  readonly validando = signal(false);
  readonly errorValidacion = signal<string | null>(null);
  private solicitudReglas = 0;
  private solicitudResumen = 0;
  readonly mensajeExito = signal<string | null>(null);
  readonly errorOperacion = signal<string | null>(null);

  // Filtros
  readonly filtroTipo = signal<TipoMantenimiento | null>(null);
  readonly filtroElemento = signal<string>('');
  readonly filtroActiva = signal<boolean | null>(null);

  // Diálogos
  readonly reglaDetalle = signal<ReglaMantenimiento | null>(null);
  readonly dialogCrearVisible = signal(false);
  readonly dialogEditarVisible = signal(false);
  readonly reglaPorEditar = signal<ReglaMantenimiento | null>(null);
  readonly reglaPorValidar = signal<ReglaMantenimiento | null>(null);
  readonly reglaPorCambiarEstado = signal<ReglaMantenimiento | null>(null);

  // Formulario Crear
  readonly formCrear = this.fb.nonNullable.group({
    codigo: [
      '',
      [Validators.required, Validators.maxLength(80), Validators.pattern('^[A-Za-z0-9_-]+$')],
    ],
    versionFormularioId: ['', Validators.required],
    elementoRef: ['', [Validators.required, Validators.maxLength(300)]],
    indicador: ['', Validators.required],
    operador: ['IGUAL', Validators.required],
    umbral: ['', Validators.required],
    tipoMantenimiento: ['CORRECTIVO' as TipoMantenimiento, Validators.required],
    prioridadBase: ['MEDIA' as PrioridadMantenimiento, Validators.required],
    intervencionSugerida: ['', Validators.required],
    activa: [true],
  });

  // Formulario Editar (crea revisión)
  readonly formEditar = this.fb.nonNullable.group({
    elementoRef: ['', [Validators.required, Validators.maxLength(300)]],
    indicador: ['', Validators.required],
    operador: ['IGUAL', Validators.required],
    umbral: ['', Validators.required],
    tipoMantenimiento: ['CORRECTIVO' as TipoMantenimiento, Validators.required],
    prioridadBase: ['MEDIA' as PrioridadMantenimiento, Validators.required],
    intervencionSugerida: ['', Validators.required],
    activa: [true],
  });

  get esAdmin(): boolean {
    return this.auth.user()?.role === 'ADMINISTRADOR';
  }

  get esCatedratico(): boolean {
    return this.auth.user()?.role === 'CATEDRATICO';
  }

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.versionSolicitada = params.get('versionId');
      if (this.versionesCargadas) this.seleccionarVersionSolicitada();
    });
    this.cargarVersiones();
  }

  cargarVersiones(): void {
    this.formularioApi
      .listarVersiones()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lista) => {
          this.versiones.set(lista);
          this.versionesCargadas = true;
          this.seleccionarVersionSolicitada();
        },
        error: () => {
          if (this.versionSolicitada) {
            this.errorValidacion.set(
              'No se pudo cargar la version del aviso. Recarga para reintentar.',
            );
            return;
          }
          this.cargarReglas();
          this.cargarResumen();
        },
      });
  }

  private seleccionarVersionSolicitada(): void {
    const lista = this.versiones();
    if (this.versionSolicitada && !lista.some((v) => v.id === this.versionSolicitada)) {
      this.versionSeleccionadaId.set(null);
      this.reglas.set([]);
      this.resumen.set(null);
      this.totalElementos.set(0);
      ++this.solicitudReglas;
      ++this.solicitudResumen;
      this.cargando.set(false);
      this.errorValidacion.set('La version del aviso no esta disponible. Selecciona otra version.');
      return;
    }
    const versionId = this.versionSolicitada || lista.find((v) => v.activa)?.id || lista[0]?.id;
    if (versionId) this.cambiarVersion(versionId);
  }

  cambiarVersion(versionId: string | null): void {
    this.errorValidacion.set(null);
    this.reglaDetalle.set(null);
    this.reglaPorValidar.set(null);
    this.filtroTipo.set(null);
    this.filtroElemento.set('');
    this.filtroActiva.set(null);
    this.versionSeleccionadaId.set(versionId);
    if (versionId) {
      this.formCrear.patchValue({ versionFormularioId: versionId });
    }
    this.paginaActual.set(0);
    this.cargarReglas();
    this.cargarResumen();
  }

  cargarResumen(): void {
    const solicitud = ++this.solicitudResumen;
    const vId = this.versionSeleccionadaId();
    this.api
      .obtenerResumen(vId || undefined)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          if (solicitud === this.solicitudResumen) this.resumen.set(data);
        },
        error: () => {
          if (solicitud === this.solicitudResumen) this.resumen.set(null);
        },
      });
  }

  cargarReglas(): void {
    const solicitud = ++this.solicitudReglas;
    this.cargando.set(true);
    this.errorOperacion.set(null);

    this.api
      .listar({
        versionId: this.versionSeleccionadaId() || undefined,
        tipo: this.filtroTipo(),
        elemento: this.filtroElemento(),
        activa: this.filtroActiva(),
        pagina: this.paginaActual(),
        tamanio: this.tamanioPagina(),
      })
      .pipe(
        finalize(() => {
          if (solicitud === this.solicitudReglas) this.cargando.set(false);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (pagina) => {
          if (solicitud !== this.solicitudReglas) return;
          this.reglas.set(pagina.content);
          this.totalElementos.set(pagina.totalElements);
        },
        error: (err) => {
          if (solicitud !== this.solicitudReglas) return;
          this.apiErrors.normalize(err);
        },
      });
  }

  aplicarFiltroTipo(tipo: TipoMantenimiento | null): void {
    this.filtroTipo.set(tipo);
    this.paginaActual.set(0);
    this.cargarReglas();
  }

  aplicarFiltroElemento(elemento: string): void {
    this.filtroElemento.set(elemento);
    this.paginaActual.set(0);
    this.cargarReglas();
  }

  aplicarFiltroActiva(activa: boolean | null): void {
    this.filtroActiva.set(activa);
    this.paginaActual.set(0);
    this.cargarReglas();
  }

  limpiarFiltros(): void {
    this.filtroTipo.set(null);
    this.filtroElemento.set('');
    this.filtroActiva.set(null);
    this.paginaActual.set(0);
    this.cargarReglas();
  }

  cambiarPagina(event: PaginatorState): void {
    this.paginaActual.set(event.page ?? 0);
    this.tamanioPagina.set(event.rows ?? 10);
    this.cargarReglas();
  }

  abrirCrear(): void {
    if (!this.esAdmin || this.guardando()) return;
    this.errorOperacion.set(null);
    this.formCrear.reset({
      codigo: '',
      versionFormularioId: this.versionSeleccionadaId() || '',
      elementoRef: '',
      indicador: '',
      operador: 'IGUAL',
      umbral: '',
      tipoMantenimiento: 'CORRECTIVO',
      prioridadBase: 'MEDIA',
      intervencionSugerida: '',
      activa: true,
    });
    this.dialogCrearVisible.set(true);
  }

  guardarCrear(): void {
    if (!this.esAdmin || this.guardando()) return;
    if (this.formCrear.invalid) {
      this.formCrear.markAllAsTouched();
      return;
    }

    const val = this.formCrear.getRawValue();
    this.guardando.set(true);
    this.errorOperacion.set(null);

    let umbralParsed: unknown = val.umbral;
    if (val.umbral.toLowerCase() === 'true') umbralParsed = true;
    else if (val.umbral.toLowerCase() === 'false') umbralParsed = false;
    else if (!isNaN(Number(val.umbral)) && val.umbral.trim() !== '') {
      umbralParsed = Number(val.umbral);
    }

    const condicion: CondicionRegla = {
      indicador: val.indicador.trim(),
      operador: val.operador,
      umbral: umbralParsed,
    };

    const reglaPrioridad: ReglaPrioridad = {
      prioridad_base: val.prioridadBase,
    };

    const request: CrearReglaRequest = {
      codigo: val.codigo.trim(),
      versionFormularioId: val.versionFormularioId,
      elementoRef: val.elementoRef.trim(),
      condicion,
      tipoMantenimiento: val.tipoMantenimiento,
      intervencionSugerida: val.intervencionSugerida.trim(),
      reglaPrioridad,
      activa: val.activa,
    };

    this.api
      .crear(request)
      .pipe(
        finalize(() => this.guardando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (creada) => {
          this.dialogCrearVisible.set(false);
          this.mensajeExito.set(`Regla ${creada.codigo} creada exitosamente`);
          this.cargarReglas();
          this.cargarResumen();
          setTimeout(() => this.mensajeExito.set(null), 5000);
        },
        error: (err) => {
          this.errorOperacion.set(err.error?.detail || err.message || 'Error al guardar la regla');
        },
      });
  }

  abrirEditar(regla: ReglaMantenimiento): void {
    if (!this.esAdmin || this.guardando()) return;
    this.reglaPorEditar.set(regla);
    this.errorOperacion.set(null);

    const condicion = regla.condicion || {};
    const prioridad = regla.reglaPrioridad || {};

    this.formEditar.reset({
      elementoRef: regla.elementoRef,
      indicador: condicion.indicador || '',
      operador: condicion.operador || 'IGUAL',
      umbral: condicion.umbral !== undefined ? String(condicion.umbral) : '',
      tipoMantenimiento: regla.tipoMantenimiento,
      prioridadBase: (prioridad.prioridad_base as PrioridadMantenimiento) || 'MEDIA',
      intervencionSugerida: regla.intervencionSugerida,
      activa: regla.activa,
    });

    this.dialogEditarVisible.set(true);
  }

  guardarEditar(): void {
    if (!this.esAdmin || this.guardando()) return;
    const regla = this.reglaPorEditar();
    if (!regla || this.formEditar.invalid) {
      this.formEditar.markAllAsTouched();
      return;
    }

    const val = this.formEditar.getRawValue();
    this.guardando.set(true);
    this.errorOperacion.set(null);

    let umbralParsed: unknown = val.umbral;
    if (val.umbral.toLowerCase() === 'true') umbralParsed = true;
    else if (val.umbral.toLowerCase() === 'false') umbralParsed = false;
    else if (!isNaN(Number(val.umbral)) && val.umbral.trim() !== '') {
      umbralParsed = Number(val.umbral);
    }

    const condicion: CondicionRegla = {
      indicador: val.indicador.trim(),
      operador: val.operador,
      umbral: umbralParsed,
    };

    const reglaPrioridad: ReglaPrioridad = {
      prioridad_base: val.prioridadBase,
    };

    const request: EditarReglaRequest = {
      elementoRef: val.elementoRef.trim(),
      condicion,
      tipoMantenimiento: val.tipoMantenimiento,
      intervencionSugerida: val.intervencionSugerida.trim(),
      reglaPrioridad,
      activa: val.activa,
    };

    this.api
      .editar(regla.id, request)
      .pipe(
        finalize(() => this.guardando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (editada) => {
          this.dialogEditarVisible.set(false);
          this.mensajeExito.set(
            `Nueva revisión ${editada.numeroRevision} generada para la regla ${editada.codigo}`,
          );
          this.cargarReglas();
          this.cargarResumen();
          setTimeout(() => this.mensajeExito.set(null), 5000);
        },
        error: (err) => {
          this.errorOperacion.set(
            err.error?.detail || err.message || 'Error al actualizar la regla',
          );
        },
      });
  }

  puedeValidar(regla: ReglaMantenimiento): boolean {
    return this.esCatedratico && regla.activa && regla.provisional;
  }

  abrirValidar(regla: ReglaMantenimiento): void {
    if (!this.puedeValidar(regla) || this.validando()) return;
    this.errorValidacion.set(null);
    this.mensajeExito.set(null);
    this.reglaPorValidar.set(regla);
  }

  cerrarValidar(): void {
    if (!this.validando()) this.reglaPorValidar.set(null);
  }

  confirmarValidar(): void {
    const regla = this.reglaPorValidar();
    if (!regla || !this.puedeValidar(regla) || this.validando()) return;

    this.validando.set(true);
    this.errorValidacion.set(null);
    this.api
      .validar(regla.id)
      .pipe(
        finalize(() => this.validando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (validada) => {
          this.reglas.update((reglas) => reglas.map((r) => (r.id === validada.id ? validada : r)));
          if (this.reglaDetalle()?.id === validada.id) this.reglaDetalle.set(validada);
          this.reglaPorValidar.set(null);
          this.mensajeExito.set(validada.rotuloValidacion || validada.estadoValidacion);
          this.cargarReglas();
          this.cargarResumen();
        },
        error: (err) => {
          this.errorValidacion.set(this.apiErrors.normalize(err));
          if (err.status === 409 || err.status === 404) {
            this.reglaPorValidar.set(null);
            if (this.reglaDetalle()?.id === regla.id) this.reglaDetalle.set(null);
            this.cargarReglas();
            this.cargarResumen();
          }
        },
      });
  }

  abrirCambiarEstado(regla: ReglaMantenimiento): void {
    if (!this.esAdmin || this.guardando()) return;
    this.reglaPorCambiarEstado.set(regla);
  }

  confirmarCambiarEstado(): void {
    if (!this.esAdmin || this.guardando()) return;
    const regla = this.reglaPorCambiarEstado();
    if (!regla) return;

    this.guardando.set(true);
    const nuevoEstado = !regla.activa;

    this.api
      .cambiarEstado(regla.id, nuevoEstado)
      .pipe(
        finalize(() => {
          this.guardando.set(false);
          this.reglaPorCambiarEstado.set(null);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (actualizada) => {
          this.mensajeExito.set(
            `Regla ${actualizada.codigo} ${actualizada.activa ? 'activada' : 'desactivada'} exitosamente`,
          );
          this.cargarReglas();
          this.cargarResumen();
          setTimeout(() => this.mensajeExito.set(null), 5000);
        },
        error: (err) => this.apiErrors.normalize(err),
      });
  }

  sembrarCatalogo(): void {
    if (!this.esAdmin || this.sembrando()) return;
    const vId = this.versionSeleccionadaId();
    if (!vId) return;

    this.sembrando.set(true);
    this.api
      .sembrar(vId)
      .pipe(
        finalize(() => this.sembrando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (res) => {
          this.mensajeExito.set(`Se sembraron ${res.reglasSembradas} reglas provisionales`);
          this.cargarReglas();
          this.cargarResumen();
          setTimeout(() => this.mensajeExito.set(null), 5000);
        },
        error: (err) => this.apiErrors.normalize(err),
      });
  }

  abrirDetalle(regla: ReglaMantenimiento): void {
    this.reglaDetalle.set(regla);
  }

  cerrarDetalle(): void {
    this.reglaDetalle.set(null);
  }

  formatearCondicion(condicion: CondicionRegla): string {
    if (!condicion) return '—';
    const ind = condicion.indicador || '';
    const op = condicion.operador || '=';
    const umb = condicion.umbral !== undefined ? JSON.stringify(condicion.umbral) : '';
    return `${ind} ${op} ${umb}`;
  }

  desglosarRuta(ruta: string): { prefijo: string; nombre: string } {
    if (!ruta) return { prefijo: '', nombre: '—' };
    const lastSlash = ruta.lastIndexOf('/');
    if (lastSlash === -1) return { prefijo: '', nombre: ruta };
    return {
      prefijo: ruta.substring(0, lastSlash + 1),
      nombre: ruta.substring(lastSlash + 1),
    };
  }

  simboloOperador(operador?: string): string {
    switch (operador) {
      case 'MAYOR_O_IGUAL':
        return '≥';
      case 'MENOR_O_IGUAL':
        return '≤';
      case 'DISTINTO':
        return '≠';
      case 'IGUAL':
      default:
        return '=';
    }
  }

  prioridadBaseDe(regla: ReglaMantenimiento): string {
    return regla.reglaPrioridad?.prioridad_base || 'MEDIA';
  }

  severidadTipo(tipo: TipoMantenimiento): 'info' | 'secondary' | 'warn' | 'danger' {
    switch (tipo) {
      case 'RUTINARIO':
        return 'info';
      case 'PREVENTIVO':
        return 'secondary';
      case 'CORRECTIVO':
        return 'warn';
      case 'EMERGENCIA':
        return 'danger';
    }
  }

  severidadPrioridad(prio: string): 'info' | 'secondary' | 'warn' | 'danger' {
    switch (prio) {
      case 'BAJA':
        return 'secondary';
      case 'MEDIA':
        return 'info';
      case 'ALTA':
        return 'warn';
      case 'CRITICA':
        return 'danger';
      default:
        return 'secondary';
    }
  }
}
