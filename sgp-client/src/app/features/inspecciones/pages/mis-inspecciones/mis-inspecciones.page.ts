import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { finalize } from 'rxjs';
import {
  PaginaResponse,
  PuenteCatalogoResponse,
  PuenteResponse,
} from '../../../puentes/models/puente.models';
import { PuenteApiService } from '../../../puentes/services/puente-api.service';
import { IniciarInspeccionDialogComponent } from '../../components/iniciar-inspeccion-dialog/iniciar-inspeccion-dialog.component';
import { EstadoInspeccion, InspeccionResumenResponse } from '../../models/inspeccion.models';
import { InspeccionApiService } from '../../services/inspeccion-api.service';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';

import { ConfirmacionEstadoIcComponent } from '../../components/confirmacion-estado-ic/confirmacion-estado-ic.component';

@Component({
  selector: 'app-mis-inspecciones-page',
  standalone: true,
  imports: [
    FormsModule,
    ConfirmacionEstadoIcComponent,
    TranslocoPipe,
    ButtonModule,
    DialogModule,
    InputTextModule,
    PaginatorModule,
    SelectModule,
    TagModule,
    TextareaModule,
    IniciarInspeccionDialogComponent,
  ],
  templateUrl: './mis-inspecciones.page.html',
  styleUrl: './mis-inspecciones.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MisInspeccionesPage {
  readonly auth = inject(AuthStore);
  private readonly inspeccionesApi = inject(InspeccionApiService);
  private readonly puentesApi = inject(PuenteApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly apiErrors = inject(ApiErrorService);

  readonly inspeccionIc = signal<InspeccionResumenResponse | null>(null);
  readonly operandoIc = signal(false);
  readonly pagina = signal<PaginaResponse<InspeccionResumenResponse> | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  readonly estadoFiltro = signal<EstadoInspeccion | null>(null);
  readonly tamanio = signal(20);
  readonly busquedaPuente = signal('');
  readonly resultadosPuente = signal<PuenteCatalogoResponse[]>([]);
  readonly buscandoPuentes = signal(false);
  readonly puenteSeleccionado = signal<PuenteResponse | null>(null);
  readonly cargandoPuente = signal(false);
  readonly mostrarInicio = signal(false);
  readonly inspeccionEliminar = signal<InspeccionResumenResponse | null>(null);
  readonly eliminando = signal(false);
  readonly errorEliminar = signal<string | null>(null);
  readonly inspeccionCorregir = signal<InspeccionResumenResponse | null>(null);
  readonly motivoCorreccion = signal('');
  readonly corrigiendo = signal(false);
  readonly errorCorreccion = signal<string | null>(null);

  readonly estados: { label: string; value: EstadoInspeccion | null }[] = [
    { label: 'Todas', value: null },
    { label: 'Borrador', value: 'BORRADOR' },
    { label: 'Enviada', value: 'ENVIADA' },
    { label: 'En revisión', value: 'EN_REVISION' },
    { label: 'Cambios solicitados', value: 'CAMBIOS_SOLICITADOS' },
    { label: 'Publicada', value: 'PUBLICADA' },
    { label: 'Rechazada', value: 'RECHAZADA' },
  ];

  constructor() {
    this.cargar();
  }

  puedeIniciarInspeccion(): boolean {
    const user = this.auth.user();
    return (
      user?.role === 'CATEDRATICO' ||
      (user?.role === 'PROFESIONAL_EXTERNO' && user.colegiadoVerificado === true)
    );
  }

  cargar(pagina = 0): void {
    if (this.cargando()) return;
    this.cargando.set(true);
    this.error.set(null);
    this.inspeccionesApi
      .listarMisInspecciones(pagina, this.tamanio(), this.estadoFiltro() ?? undefined)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (respuesta) => this.pagina.set(respuesta),
        error: () => this.error.set('load'),
      });
  }

  cambiarEstado(estado: EstadoInspeccion | null): void {
    this.estadoFiltro.set(estado);
    this.cargar();
  }

  cambiarPagina(evento: PaginatorState): void {
    if (evento.page === undefined || evento.rows === undefined) return;
    this.tamanio.set(evento.rows);
    this.cargar(evento.page);
  }

  buscarPuentes(): void {
    const busqueda = this.busquedaPuente().trim();
    if (!busqueda || this.buscandoPuentes()) return;
    this.buscandoPuentes.set(true);
    this.puentesApi
      .listarCatalogo({ busqueda, pagina: 0, tamanio: 20 })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.buscandoPuentes.set(false)),
      )
      .subscribe({
        next: (respuesta) => this.resultadosPuente.set(respuesta.content),
        error: () => this.resultadosPuente.set([]),
      });
  }

  seleccionarPuente(puente: PuenteCatalogoResponse): void {
    if (this.cargandoPuente()) return;
    this.cargandoPuente.set(true);
    this.puentesApi
      .obtenerPorId(puente.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargandoPuente.set(false)),
      )
      .subscribe({
        next: (respuesta) => {
          this.puenteSeleccionado.set(respuesta);
          this.mostrarInicio.set(true);
        },
        error: () => this.error.set('bridge'),
      });
  }

  reanudar(inspeccion: InspeccionResumenResponse): void {
    void this.router.navigate(['/inspecciones', inspeccion.id, 'borrador']);
  }

  confirmarEliminar(inspeccion: InspeccionResumenResponse): void {
    this.errorEliminar.set(null);
    this.inspeccionEliminar.set(inspeccion);
  }

  eliminarBorrador(): void {
    const inspeccion = this.inspeccionEliminar();
    if (!inspeccion || this.eliminando()) return;
    this.eliminando.set(true);
    this.errorEliminar.set(null);
    this.inspeccionesApi
      .eliminarBorrador(inspeccion.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.eliminando.set(false)),
      )
      .subscribe({
        next: () => {
          this.inspeccionEliminar.set(null);
          this.cargar(this.pagina()?.number ?? 0);
        },
        error: () => this.errorEliminar.set('delete'),
      });
  }

  puedeCorregir(inspeccion: InspeccionResumenResponse): boolean {
    return inspeccion.estado === 'PUBLICADA' && !inspeccion.superadaPorId;
  }

  abrirCorreccion(inspeccion: InspeccionResumenResponse): void {
    this.motivoCorreccion.set('');
    this.errorCorreccion.set(null);
    this.inspeccionCorregir.set(inspeccion);
  }

  cerrarCorreccion(): void {
    if (this.corrigiendo()) return;
    this.inspeccionCorregir.set(null);
  }

  crearCorreccion(): void {
    const inspeccion = this.inspeccionCorregir();
    const motivo = this.motivoCorreccion().trim();
    if (!inspeccion || !motivo || this.corrigiendo()) return;
    this.corrigiendo.set(true);
    this.errorCorreccion.set(null);
    this.inspeccionesApi
      .crearCorreccion(inspeccion.id, motivo)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.corrigiendo.set(false)),
      )
      .subscribe({
        next: (correccion) => {
          this.inspeccionCorregir.set(null);
          void this.router.navigate(['/inspecciones', correccion.id, 'borrador']);
        },
        error: (err: HttpErrorResponse) => {
          this.apiErrors.clear();
          const problema = err.error as { detail?: string } | null;
          this.errorCorreccion.set(
            problema?.detail ?? 'No fue posible crear la corrección. Intenta nuevamente.',
          );
        },
      });
  }

  etiquetaEstado(estado: EstadoInspeccion): string {
    return `inspecciones.mis.estados.${estado}`;
  }

  severidad(estado: EstadoInspeccion): 'success' | 'warn' | 'danger' | 'info' | 'secondary' {
    if (estado === 'PUBLICADA') return 'success';
    if (estado === 'BORRADOR') return 'secondary';
    if (estado === 'RECHAZADA') return 'danger';
    if (estado === 'CAMBIOS_SOLICITADOS') return 'warn';
    return 'info';
  }
}
