import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { finalize, forkJoin, Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { PuenteResponse } from '../../../puentes/models/puente.models';
import { PuenteApiService } from '../../../puentes/services/puente-api.service';
import {
  EjecucionOrden,
  EvidenciaMantenimiento,
  HistorialEstadoOrden,
  OrdenMantenimiento,
  ResponsableDisponible,
} from '../../models/orden-mantenimiento.models';
import { PrioridadMantenimiento } from '../../models/regla-mantenimiento.models';
import { OrdenMantenimientoApiService } from '../../services/orden-mantenimiento-api.service';
import { comprimirImagen } from '../../utils/comprimir-imagen';
import { aFechaIso, severidadEstadoOrden, ZONA_GUATEMALA } from '../../utils/estado-orden';

export type AccionOrden = 'programar' | 'descartar' | 'iniciar' | 'cancelar' | 'cerrar';

interface ErrorAccion {
  mensaje: string;
  campos: { campo: string; mensaje: string }[];
}

type ModoResponsable = 'usuario' | 'externo';

const TIPOS_ACEPTADOS = ['image/jpeg', 'image/webp'];

@Component({
  selector: 'app-detalle-orden-page',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    TranslocoPipe,
    ButtonModule,
    DialogModule,
    InputTextModule,
    DatePickerModule,
    SelectModule,
    SkeletonModule,
    TagModule,
    TextareaModule,
  ],
  templateUrl: './detalle-orden.page.html',
  styleUrl: './detalle-orden.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleOrdenPage {
  private readonly api = inject(OrdenMantenimientoApiService);
  private readonly puentesApi = inject(PuenteApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly auth = inject(AuthStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly ordenId = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';

  readonly orden = signal<OrdenMantenimiento | null>(null);
  readonly puente = signal<PuenteResponse | null>(null);
  readonly historial = signal<HistorialEstadoOrden[]>([]);
  readonly ejecucion = signal<EjecucionOrden | null>(null);
  readonly evidencias = signal<EvidenciaMantenimiento[]>([]);
  readonly miniaturas = signal<Record<string, string>>({});
  readonly responsables = signal<ResponsableDisponible[]>([]);
  readonly cargando = signal(true);
  readonly errorCarga = signal(false);

  readonly accion = signal<AccionOrden | null>(null);
  readonly fechaProgramada = signal<Date | null>(null);
  readonly motivo = signal('');
  readonly procesando = signal(false);
  readonly errorAccion = signal<ErrorAccion | null>(null);
  readonly editandoPrioridad = signal(false);
  readonly prioridadNueva = signal<PrioridadMantenimiento>('MEDIA');
  readonly justificacionNueva = signal('');
  readonly guardandoPrioridad = signal(false);
  readonly errorPrioridad = signal<string | null>(null);
  readonly puedeCambiarPrioridad = computed(() => {
    const orden = this.orden();
    const rol = this.auth.user()?.role;
    return (
      (rol === 'CATEDRATICO' || rol === 'ADMINISTRADOR') &&
      !!orden &&
      !['EJECUTADA', 'CANCELADA', 'DESCARTADA'].includes(orden.estado)
    );
  });
  readonly opcionesPrioridad: PrioridadMantenimiento[] = ['BAJA', 'MEDIA', 'ALTA', 'CRITICA'];

  readonly modoResponsable = signal<ModoResponsable>('usuario');
  readonly responsableUsuarioId = signal<string | null>(null);
  readonly responsableNombre = signal('');
  readonly fechaEjecucion = signal<Date | null>(null);
  readonly guardandoEjecucion = signal(false);
  readonly errorEjecucion = signal<ErrorAccion | null>(null);
  readonly ejecucionGuardada = signal(false);

  readonly elementoRef = signal('');
  readonly subiendo = signal(false);
  readonly errorEvidencia = signal<string | null>(null);
  readonly evidenciaRetirar = signal<EvidenciaMantenimiento | null>(null);
  readonly motivoRetiro = signal('');
  readonly retirando = signal(false);
  readonly errorRetiro = signal<string | null>(null);
  readonly evidenciaAmpliada = signal<string | null>(null);

  readonly hoy = inicioDelDia(new Date());
  readonly zona = ZONA_GUATEMALA;
  readonly severidad = severidadEstadoOrden;
  /** Campos de los diálogos cuyo error del servidor se muestra junto al control. */
  private readonly camposDialogo = ['fechaProgramada', 'motivo'];

  readonly enEjecucion = computed(() => this.orden()?.estado === 'EN_EJECUCION');
  readonly fechaMinimaEjecucion = computed(() => {
    const iniciado = this.ejecucion()?.iniciadoEn;
    return iniciado ? inicioDelDia(new Date(iniciado)) : this.hoy;
  });
  /** Fundamento de una propuesta generada, como pares clave-valor legibles. */
  readonly fundamento = computed(() => {
    const datos = this.orden()?.fundamentoGeneracion;
    if (!datos || typeof datos !== 'object') return [];
    return Object.entries(datos as Record<string, unknown>).map(([clave, valor]) => ({
      clave,
      valor: typeof valor === 'object' && valor !== null ? JSON.stringify(valor) : String(valor),
    }));
  });
  readonly erroresGenerales = computed(
    () => this.errorAccion()?.campos.filter((c) => !this.camposDialogo.includes(c.campo)) ?? [],
  );
  readonly requisitosCierre = computed(() => {
    const ejecucion = this.ejecucion();
    return {
      fecha: !!ejecucion?.fechaEjecucion,
      responsable: !!(ejecucion?.responsableUsuarioId || ejecucion?.responsableNombre),
      evidencias: this.evidencias().length,
    };
  });
  readonly puedeCerrar = computed(() => {
    const requisitos = this.requisitosCierre();
    return requisitos.fecha && requisitos.responsable && requisitos.evidencias > 0;
  });
  readonly responsableValido = computed(() =>
    this.modoResponsable() === 'usuario'
      ? !!this.responsableUsuarioId()
      : this.responsableNombre().trim().length > 0,
  );

  constructor() {
    this.destroyRef.onDestroy(() => this.liberarMiniaturas());
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.errorCarga.set(false);
    forkJoin({
      orden: this.api.obtener(this.ordenId),
      historial: this.api.historial(this.ordenId),
    })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: ({ orden, historial }) => {
          this.orden.set(orden);
          this.historial.set(historial);
          this.cargarPuente(orden.puenteId);
          this.cargarEjecucion(orden);
        },
        error: () => this.errorCarga.set(true),
      });
  }

  abrirAccion(accion: AccionOrden): void {
    this.errorAccion.set(null);
    this.motivo.set('');
    this.fechaProgramada.set(this.hoy);
    this.accion.set(accion);
  }

  abrirEdicionPrioridad(): void {
    const orden = this.orden();
    if (!orden || !this.puedeCambiarPrioridad()) return;
    this.prioridadNueva.set(orden.prioridadConfirmada);
    this.justificacionNueva.set('');
    this.errorPrioridad.set(null);
    this.editandoPrioridad.set(true);
  }

  guardarPrioridad(): void {
    const justificacion = this.justificacionNueva().trim();
    if (
      !this.orden() ||
      !this.puedeCambiarPrioridad() ||
      !justificacion ||
      this.guardandoPrioridad()
    )
      return;
    this.guardandoPrioridad.set(true);
    this.errorPrioridad.set(null);
    this.api
      .cambiarPrioridad(this.ordenId, this.prioridadNueva(), justificacion)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardandoPrioridad.set(false)),
      )
      .subscribe({
        next: () => {
          this.editandoPrioridad.set(false);
          this.cargar();
        },
        error: (err: HttpErrorResponse) => {
          this.errorPrioridad.set(errorDe(err, 'No fue posible cambiar la prioridad.').mensaje);
        },
      });
  }

  cerrarDialogo(): void {
    if (this.procesando()) return;
    this.accion.set(null);
  }

  accionValida(): boolean {
    const accion = this.accion();
    if (accion === 'programar') return !!this.fechaProgramada();
    if (accion === 'descartar' || accion === 'cancelar') return !!this.motivo().trim();
    return true;
  }

  confirmarAccion(): void {
    const orden = this.orden();
    const accion = this.accion();
    if (!orden || !accion || !this.accionValida() || this.procesando()) return;
    this.procesando.set(true);
    this.errorAccion.set(null);
    this.ejecutar(orden, accion)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.procesando.set(false)),
      )
      .subscribe({
        next: () => {
          this.accion.set(null);
          this.cargar();
        },
        error: (err: HttpErrorResponse) => {
          // El error se muestra dentro del diálogo; se evita duplicarlo en el aviso global.
          this.apiErrors.clear();
          this.errorAccion.set(errorDe(err, 'No fue posible completar la acción sobre la orden.'));
        },
      });
  }

  cambiarModoResponsable(modo: ModoResponsable): void {
    this.modoResponsable.set(modo);
    this.ejecucionGuardada.set(false);
  }

  guardarEjecucion(): void {
    if (!this.responsableValido() || this.guardandoEjecucion()) return;
    const externo = this.modoResponsable() === 'externo';
    this.guardandoEjecucion.set(true);
    this.errorEjecucion.set(null);
    this.ejecucionGuardada.set(false);
    this.api
      .registrarEjecucion(this.ordenId, {
        fechaEjecucion: aFechaIso(this.fechaEjecucion()),
        responsableUsuarioId: externo ? null : this.responsableUsuarioId(),
        responsableNombre: externo ? this.responsableNombre().trim() : null,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardandoEjecucion.set(false)),
      )
      .subscribe({
        next: (ejecucion) => {
          this.ejecucion.set(ejecucion);
          this.ejecucionGuardada.set(true);
        },
        error: (err: HttpErrorResponse) => {
          this.apiErrors.clear();
          this.errorEjecucion.set(errorDe(err, 'No fue posible guardar la ejecución.'));
        },
      });
  }

  async seleccionarEvidencia(evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    entrada.value = '';
    if (!archivo || this.subiendo()) return;
    this.errorEvidencia.set(null);
    if (!TIPOS_ACEPTADOS.includes(archivo.type)) {
      this.errorEvidencia.set('Seleccione una fotografía JPEG o WebP.');
      return;
    }
    this.subiendo.set(true);
    let imagen: Blob;
    try {
      imagen = await comprimirImagen(archivo);
    } catch {
      this.subiendo.set(false);
      this.errorEvidencia.set('No fue posible procesar la fotografía en este dispositivo.');
      return;
    }
    this.api
      .adjuntarEvidencia(this.ordenId, imagen, this.elementoRef())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.subiendo.set(false)),
      )
      .subscribe({
        next: () => {
          this.elementoRef.set('');
          this.cargarEvidencias();
        },
        error: (err: HttpErrorResponse) => {
          this.apiErrors.clear();
          this.errorEvidencia.set(
            errorDe(err, 'No fue posible subir la fotografía de evidencia.').mensaje,
          );
        },
      });
  }

  abrirRetiro(evidencia: EvidenciaMantenimiento): void {
    this.motivoRetiro.set('');
    this.errorRetiro.set(null);
    this.evidenciaRetirar.set(evidencia);
  }

  cerrarRetiro(): void {
    if (this.retirando()) return;
    this.evidenciaRetirar.set(null);
  }

  retirarEvidencia(): void {
    const evidencia = this.evidenciaRetirar();
    const motivo = this.motivoRetiro().trim();
    if (!evidencia || !motivo || this.retirando()) return;
    this.retirando.set(true);
    this.errorRetiro.set(null);
    this.api
      .retirarEvidencia(this.ordenId, evidencia.id, motivo)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.retirando.set(false)),
      )
      .subscribe({
        next: () => {
          this.evidenciaRetirar.set(null);
          this.cargarEvidencias();
        },
        error: (err: HttpErrorResponse) => {
          this.apiErrors.clear();
          this.errorRetiro.set(errorDe(err, 'No fue posible retirar la evidencia.').mensaje);
        },
      });
  }

  ampliar(evidencia: EvidenciaMantenimiento): void {
    this.api
      .imagenEvidencia(this.ordenId, evidencia.id, false)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (imagen) => {
          this.cerrarAmpliada();
          this.evidenciaAmpliada.set(URL.createObjectURL(imagen));
        },
      });
  }

  cerrarAmpliada(): void {
    const url = this.evidenciaAmpliada();
    if (url) URL.revokeObjectURL(url);
    this.evidenciaAmpliada.set(null);
  }

  /** Error del servidor para un campo del diálogo, mostrado junto a ese campo. */
  errorCampo(campo: string): string | null {
    return this.errorAccion()?.campos.find((c) => c.campo === campo)?.mensaje ?? null;
  }

  etiquetaResponsable(ejecucion: EjecucionOrden): string {
    return [ejecucion.responsableUsuarioEmail, ejecucion.responsableNombre]
      .filter((valor) => !!valor)
      .join(' · ');
  }

  private ejecutar(orden: OrdenMantenimiento, accion: AccionOrden): Observable<unknown> {
    switch (accion) {
      case 'programar':
        return this.api.programar(orden.id, aFechaIso(this.fechaProgramada()) ?? '', orden.version);
      case 'descartar':
        return this.api.descartar(orden.id, this.motivo().trim(), orden.version);
      case 'cancelar':
        return this.api.cancelar(orden.id, this.motivo().trim(), orden.version);
      case 'iniciar':
        return this.api.iniciar(orden.id, orden.version);
      case 'cerrar':
        return this.api.cerrar(orden.id, orden.version);
    }
  }

  private cargarPuente(puenteId: string): void {
    if (this.puente()?.id === puenteId) return;
    this.puentesApi
      .obtenerPorId(puenteId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (puente) => this.puente.set(puente), error: () => this.puente.set(null) });
  }

  private cargarEjecucion(orden: OrdenMantenimiento): void {
    if (!['EN_EJECUCION', 'EJECUTADA'].includes(orden.estado)) {
      this.ejecucion.set(null);
      this.evidencias.set([]);
      return;
    }
    this.api
      .obtenerEjecucion(orden.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        catchError(() => of(null)),
      )
      .subscribe((ejecucion) => {
        this.ejecucion.set(ejecucion);
        this.prepararFormulario(ejecucion);
      });
    this.cargarEvidencias();
    if (orden.estado === 'EN_EJECUCION' && this.responsables().length === 0) {
      this.api
        .responsables()
        .pipe(
          takeUntilDestroyed(this.destroyRef),
          catchError(() => of([])),
        )
        .subscribe((responsables) => this.responsables.set(responsables));
    }
  }

  private prepararFormulario(ejecucion: EjecucionOrden | null): void {
    this.fechaEjecucion.set(deFechaIso(ejecucion?.fechaEjecucion));
    this.responsableUsuarioId.set(ejecucion?.responsableUsuarioId ?? null);
    this.responsableNombre.set(ejecucion?.responsableNombre ?? '');
    this.modoResponsable.set(
      ejecucion?.responsableNombre && !ejecucion.responsableUsuarioId ? 'externo' : 'usuario',
    );
  }

  private cargarEvidencias(): void {
    this.api
      .evidencias(this.ordenId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (evidencias) => {
          this.evidencias.set(evidencias);
          this.cargarMiniaturas(evidencias);
        },
      });
  }

  private cargarMiniaturas(evidencias: EvidenciaMantenimiento[]): void {
    this.liberarMiniaturas();
    if (evidencias.length === 0) return;
    forkJoin(
      evidencias.map((evidencia) =>
        this.api.imagenEvidencia(this.ordenId, evidencia.id, true).pipe(catchError(() => of(null))),
      ),
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((imagenes) => {
        const urls: Record<string, string> = {};
        imagenes.forEach((imagen, indice) => {
          if (imagen) urls[evidencias[indice].id] = URL.createObjectURL(imagen);
        });
        this.miniaturas.set(urls);
      });
  }

  private liberarMiniaturas(): void {
    Object.values(this.miniaturas()).forEach((url) => URL.revokeObjectURL(url));
    this.miniaturas.set({});
  }
}

function inicioDelDia(fecha: Date): Date {
  return new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate());
}

/** Interpreta yyyy-MM-dd como día del calendario local, sin desplazarlo por la zona. */
function deFechaIso(fecha: string | null | undefined): Date | null {
  if (!fecha) return null;
  const [anio, mes, dia] = fecha.split('-').map(Number);
  return new Date(anio, mes - 1, dia);
}

function errorDe(err: HttpErrorResponse, porDefecto: string): ErrorAccion {
  const problema = err.error as {
    detail?: string;
    errores?: { campo: string; mensaje: string }[];
  } | null;
  return {
    mensaje: problema?.detail ?? porDefecto,
    campos: problema?.errores ?? [],
  };
}
