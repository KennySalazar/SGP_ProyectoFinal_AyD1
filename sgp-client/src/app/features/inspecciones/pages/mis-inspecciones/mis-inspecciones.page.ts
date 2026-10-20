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
import {
  InspeccionesLocalesService,
  BorradorLocalPayload,
} from '../../../../offline/services/inspecciones-locales.service';
import { ConnectivityService } from '../../../../offline/services/connectivity.service';

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
  private readonly inspeccionesLocales = inject(InspeccionesLocalesService);
  private readonly connectivity = inject(ConnectivityService);
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
    { label: 'Pendiente de sincronizar', value: 'PENDIENTE_SYNC' as unknown as EstadoInspeccion },
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

    const autorId = this.auth.user()?.id || '';
    const filtro = this.estadoFiltro();

    // Obtener las inspecciones locales pendientes de sincronizar
    this.inspeccionesLocales.listarPendientesPorUsuario(autorId).then((pendientesLocales) => {
      const localesResumen: InspeccionResumenResponse[] = pendientesLocales.map((loc) => {
        const raw = (loc.payload || {}) as BorradorLocalPayload & Record<string, unknown>;
        const payload = (raw.borrador ? raw : { borrador: raw }) as BorradorLocalPayload;
        const b = payload.borrador;
        return {
          id: loc.id,
          puenteId: loc.puenteId,
          codigoPuente: b?.codigoPuente || 'LOCAL',
          nombrePuente: b?.nombrePuente || 'Puente sin sincronizar',
          departamentoNombre: '',
          municipioNombre: null,
          versionFormularioId: b?.versionFormularioId || payload.versionFormularioId || '',
          codigoVersionFormulario: b?.codigoVersionFormulario || payload.versionFormularioId || '',
          fechaInspeccion: b?.fechaInspeccion || loc.actualizadoEn?.slice(0, 10) || '',
          estado: 'PENDIENTE_SYNC',
          tipo: 'INICIAL',
          autorId: loc.propietarioId || autorId,
          rolAutorCreacion: b?.rolAutor || 'PROFESIONAL_EXTERNO',
          nombreAutorCapturado: b?.nombreAutor || 'Inspector',
          superadaPorId: null,
          motivoCorreccion: null,
          porcentajeAvance: b?.progreso?.porcentajeTotal ?? 0,
          creadoEn: b?.creadoEn || loc.actualizadoEn,
        };
      });

      if (!this.connectivity.online()) {
        this.cargando.set(false);
        const filtradas =
          filtro === null || filtro === undefined
            ? localesResumen
            : localesResumen.filter((i) => i.estado === filtro);
        this.pagina.set({
          content: filtradas,
          totalElements: filtradas.length,
          totalPages: 1,
          number: 0,
          size: this.tamanio(),
          first: true,
          last: true,
          empty: filtradas.length === 0,
        });
        return;
      }

      this.inspeccionesApi
        .listarMisInspecciones(
          pagina,
          this.tamanio(),
          (filtro as string) === 'PENDIENTE_SYNC' ? undefined : (filtro ?? undefined),
        )
        .pipe(
          takeUntilDestroyed(this.destroyRef),
          finalize(() => this.cargando.set(false)),
        )
        .subscribe({
          next: (respuesta) => {
            const porId = new Map(
              respuesta.content.map((inspeccion) => [inspeccion.id, inspeccion]),
            );
            for (const local of localesResumen) porId.set(local.id, local);
            let combinadas = [...porId.values()];
            if (filtro) {
              combinadas = combinadas.filter((i) => i.estado === filtro);
            }
            this.pagina.set({
              ...respuesta,
              content: combinadas,
              totalElements: Math.max(respuesta.totalElements, combinadas.length),
              empty: combinadas.length === 0,
            });
          },
          error: () => {
            if (localesResumen.length > 0) {
              this.pagina.set({
                content: localesResumen,
                totalElements: localesResumen.length,
                totalPages: 1,
                number: 0,
                size: this.tamanio(),
                first: true,
                last: true,
                empty: false,
              });
            } else {
              this.error.set('load');
            }
          },
        });
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

    if (!this.connectivity.online()) {
      this.inspeccionesLocales.obtenerPuentesPreparados().then((puentesLocales) => {
        this.buscandoPuentes.set(false);
        const q = busqueda.toLowerCase();
        const filtrados: PuenteCatalogoResponse[] = puentesLocales
          .filter((p) => {
            const codigo = (p.codigo || '').toLowerCase();
            const payload = (p.payload || {}) as { nombre?: string };
            const nombre = (payload.nombre || '').toLowerCase();
            return codigo.includes(q) || nombre.includes(q);
          })
          .map((p) => {
            const payload = (p.payload || {}) as {
              nombre?: string;
              departamento?: { id: string; codigoIne: string; nombre: string };
              municipio?: { id: string; departamentoId: string; codigoIne: string; nombre: string };
              latitud?: number;
              longitud?: number;
              activo?: boolean;
            };
            return {
              id: p.id,
              codigo: p.codigo || '',
              nombre: payload.nombre || '',
              departamento: payload.departamento || { id: '', codigoIne: '', nombre: '' },
              municipio: payload.municipio || {
                id: '',
                departamentoId: '',
                codigoIne: '',
                nombre: '',
              },
              latitud: payload.latitud ?? null,
              longitud: payload.longitud ?? null,
              activo: payload.activo ?? true,
              estadoActual: 'Sin evaluar',
            };
          });
        this.resultadosPuente.set(filtrados);
      });
      return;
    }

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

    if (!this.connectivity.online()) {
      this.inspeccionesLocales.obtenerPuentesPreparados().then((puentes) => {
        this.cargandoPuente.set(false);
        const encontrado = puentes.find((p) => p.id === puente.id);
        if (encontrado) {
          const payload = (encontrado.payload || {}) as unknown as PuenteResponse;
          this.puenteSeleccionado.set({
            id: encontrado.id,
            codigo: encontrado.codigo || puente.codigo,
            nombre: payload.nombre || puente.nombre,
            departamento: payload.departamento || puente.departamento,
            municipio: payload.municipio || puente.municipio,
            ruta: payload.ruta || '',
            kilometraje: payload.kilometraje ?? null,
            latitud: payload.latitud ?? puente.latitud ?? 0,
            longitud: payload.longitud ?? puente.longitud ?? 0,
            utm: payload.utm || { zona: 15, hemisferio: 'Norte', epsg: 32615, este: 0, norte: 0 },
            activo: payload.activo ?? true,
            estadoActual: payload.estadoActual || 'Sin evaluar',
            indiceCondicionActual: payload.indiceCondicionActual ?? null,
            fechaUltimaInspeccion: payload.fechaUltimaInspeccion ?? null,
            creadoEn: payload.creadoEn || new Date().toISOString(),
          });
          this.mostrarInicio.set(true);
        } else {
          this.error.set('bridge');
        }
      });
      return;
    }

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

    // Si es un borrador que solo existe localmente (PENDIENTE_SYNC)
    if ((inspeccion.estado as string) === 'PENDIENTE_SYNC' || !this.connectivity.online()) {
      this.inspeccionesLocales
        .eliminarInspeccionLocal(inspeccion.id)
        .then(() => {
          this.eliminando.set(false);
          this.inspeccionEliminar.set(null);
          this.cargar(this.pagina()?.number ?? 0);
        })
        .catch(() => {
          this.eliminando.set(false);
          this.errorEliminar.set('delete');
        });
      return;
    }

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
    if (estado === 'BORRADOR' || (estado as string) === 'PENDIENTE_SYNC') return 'secondary';
    if (estado === 'RECHAZADA') return 'danger';
    if (estado === 'CAMBIOS_SOLICITADOS') return 'warn';
    return 'info';
  }
}
