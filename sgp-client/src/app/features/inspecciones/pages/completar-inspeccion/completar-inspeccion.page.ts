import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, Subject } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { ProgressBarModule } from 'primeng/progressbar';
import { InputTextModule } from 'primeng/inputtext';
import { FormularioDinamicoComponent } from '../../../formulario/components/formulario-dinamico/formulario-dinamico.component';
import { EsquemaJson } from '../../../formulario/motor/esquema/modelos';
import { FormularioApiService } from '../../../formulario/services/formulario-api.service';
import { GuardarBorradorRequest, InspeccionBorradorResponse } from '../../models/inspeccion.models';
import { InspeccionApiService } from '../../services/inspeccion-api.service';

@Component({
  selector: 'app-completar-inspeccion-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    TranslocoPipe,
    ButtonModule,
    TagModule,
    ProgressBarModule,
    InputTextModule,
    FormularioDinamicoComponent,
  ],
  templateUrl: './completar-inspeccion.page.html',
  styleUrl: './completar-inspeccion.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CompletarInspeccionPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly inspeccionApi = inject(InspeccionApiService);
  private readonly formularioApi = inject(FormularioApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cambios = new Subject<void>();
  private revisionDatos = 0;
  private revisionGuardada = 0;
  private ultimaHuellaDatos: string | null = null;
  private guardadoForzadoDuranteSolicitud = false;

  protected readonly formulario = viewChild(FormularioDinamicoComponent);

  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly errorCarga = signal<string | null>(null);
  readonly errorGuardado = signal<string | null>(null);
  readonly mensajeExito = signal<string | null>(null);
  readonly estadoAutoguardado = signal<'pendiente' | 'guardando' | 'guardado' | null>(null);
  readonly borrador = signal<InspeccionBorradorResponse | null>(null);
  readonly esquema = signal<EsquemaJson | null>(null);
  readonly fechaInspeccion = signal<string>('');
  readonly hoy = new Date().toISOString().slice(0, 10);

  private readonly datosEnMemoria = signal<Record<string, unknown>>({});
  private readonly datosIniciales = signal<InspeccionBorradorResponse | null>(null);

  readonly valoresIniciales = computed<Record<string, unknown> | null>(() => {
    const b = this.datosIniciales();
    if (!b) return null;

    const iniciales: Record<string, unknown> = {
      ...(b.datos ?? {}),
    };

    const idExistente = (iniciales['identificacion'] as Record<string, unknown>) ?? {};
    iniciales['identificacion'] = {
      nombre_puente: b.nombrePuente,
      codigo: b.codigoPuente,
      fecha_inspeccion: b.fechaInspeccion,
      nombre_inspector: b.nombreAutor,
      ...(b.identificacionCapturada ?? {}),
      ...idExistente,
    };

    if (b.datosPermanentes) {
      const dgExistente = (iniciales['datos_generales'] as Record<string, unknown>) ?? {};
      iniciales['datos_generales'] = {
        longitud_m: b.datosPermanentes.longitudM,
        numero_tramos: b.datosPermanentes.numeroTramos,
        ancho_rodadura_m: b.datosPermanentes.anchoRodaduraM,
        ancho_acera_derecha_m: b.datosPermanentes.anchoAceraDerechaM,
        ancho_acera_izquierda_m: b.datosPermanentes.anchoAceraIzquierdaM,
        tipologia_puente: b.datosPermanentes.tipologiaPuente,
        tipo_cruce: b.datosPermanentes.tipoCruce,
        numero_vias_por_sentido: b.datosPermanentes.numeroViasPorSentido,
        material_superestructura: b.datosPermanentes.materialSuperestructura,
        material_subestructura: b.datosPermanentes.materialSubestructura,
        altura_libre_sobre_cauce_m: b.datosPermanentes.alturaLibreSobreCauceM,
        ...dgExistente,
      };
    }

    return iniciales;
  });

  constructor() {
    this.cambios.pipe(debounceTime(1200), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.guardarBorrador(false);
    });
    this.destroyRef.onDestroy(() => {
      if (this.revisionDatos > this.revisionGuardada) this.guardarBorrador(false);
    });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorCarga.set('Identificador de inspección no válido.');
      this.cargando.set(false);
      return;
    }

    this.cargarBorrador(id);
  }

  private cargarBorrador(id: string): void {
    this.cargando.set(true);
    this.errorCarga.set(null);

    this.inspeccionApi
      .obtenerBorrador(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (b) => {
          this.borrador.set(b);
          this.datosIniciales.set(b);
          this.fechaInspeccion.set(b.fechaInspeccion || this.hoy);
          if (b.datos) {
            this.datosEnMemoria.set(b.datos);
          }
          this.cargarEsquema(b.versionFormularioId);
        },
        error: (err: HttpErrorResponse) => {
          this.cargando.set(false);
          this.procesarErrorCarga(err);
        },
      });
  }

  private cargarEsquema(versionId: string): void {
    this.formularioApi
      .obtenerEsquemaPublicado(versionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.esquema.set(res.esquema as EsquemaJson);
          this.cargando.set(false);
        },
        error: () => {
          // Fallback a versión activa si la versión no se encuentra como publicada directamente
          this.formularioApi
            .obtenerActiva()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
              next: (activa) => {
                this.esquema.set(activa.esquema as EsquemaJson);
                this.cargando.set(false);
              },
              error: () => {
                this.cargando.set(false);
                this.errorCarga.set(
                  'No fue posible obtener el esquema del formulario para esta inspección.',
                );
              },
            });
        },
      });
  }

  onValorChange(valor: Record<string, unknown>): void {
    this.datosEnMemoria.set(valor);
    const huella = JSON.stringify(valor);
    if (this.ultimaHuellaDatos === null) {
      this.ultimaHuellaDatos = huella;
      return;
    }
    if (huella !== this.ultimaHuellaDatos) {
      this.ultimaHuellaDatos = huella;
      this.marcarCambio();
    }
  }

  onFechaInspeccionChange(fecha: string): void {
    if (fecha === this.fechaInspeccion()) return;
    this.fechaInspeccion.set(fecha);
    this.marcarCambio();
  }

  onSeccionCambiando(): void {
    this.guardarBorrador(false);
  }

  private marcarCambio(): void {
    this.revisionDatos++;
    this.estadoAutoguardado.set('pendiente');
    this.errorGuardado.set(null);
    this.mensajeExito.set(null);
    this.cambios.next();
  }

  guardarBorrador(forzar = true): void {
    const b = this.borrador();
    if (!b || (!forzar && this.revisionDatos <= this.revisionGuardada)) return;
    if (this.guardando()) {
      this.guardadoForzadoDuranteSolicitud = true;
      return;
    }

    this.errorGuardado.set(null);
    this.mensajeExito.set(null);

    const fecha = this.fechaInspeccion();
    if (fecha > this.hoy) {
      this.errorGuardado.set('La fecha de inspección no puede ser posterior a la fecha actual.');
      this.estadoAutoguardado.set('pendiente');
      return;
    }

    const anioConstruccion = b.datosPermanentes?.anioConstruccion;
    if (anioConstruccion && fecha) {
      const anioInspeccion = parseInt(fecha.substring(0, 4), 10);
      if (anioInspeccion < anioConstruccion) {
        this.errorGuardado.set(
          `La fecha de inspección (${anioInspeccion}) no puede ser anterior al año de construcción registrado (${anioConstruccion}).`,
        );
        this.estadoAutoguardado.set('pendiente');
        return;
      }
    }

    const valorActual = this.formulario()?.valor() ?? this.datosEnMemoria();
    const revisionSolicitud = this.revisionDatos;

    const request: GuardarBorradorRequest = {
      version: b.version,
      datos: valorActual,
      identificacionCapturada:
        (valorActual?.['identificacion'] as Record<string, unknown>) ??
        b.identificacionCapturada ??
        null,
      fechaInspeccion: fecha || b.fechaInspeccion,
      acompanantes:
        b.acompanantes?.map((a) => ({
          id: a.id,
          usuarioId: a.usuarioId,
          nombre: a.nombre,
          colegiado: a.colegiado ?? a.numeroColegiado,
          numeroColegiado: a.numeroColegiado ?? a.colegiado,
        })) ?? null,
    };

    this.guardando.set(true);
    this.estadoAutoguardado.set('guardando');

    this.inspeccionApi.guardarBorrador(b.id, request).subscribe({
      next: (actualizada) => {
        this.guardando.set(false);
        this.borrador.set(actualizada);
        this.revisionGuardada = Math.max(this.revisionGuardada, revisionSolicitud);
        const hayCambiosNuevos = this.revisionDatos > revisionSolicitud;
        if (!hayCambiosNuevos && actualizada.datos) {
          this.datosEnMemoria.set(actualizada.datos);
        }
        if (hayCambiosNuevos) {
          this.estadoAutoguardado.set('pendiente');
        } else {
          this.estadoAutoguardado.set('guardado');
          this.mensajeExito.set('Avance de borrador guardado exitosamente.');
        }
        const guardarCambiosPendientes = this.guardadoForzadoDuranteSolicitud && hayCambiosNuevos;
        this.guardadoForzadoDuranteSolicitud = false;
        if (guardarCambiosPendientes) {
          this.guardarBorrador(false);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.estadoAutoguardado.set(null);
        this.procesarErrorGuardado(err);
      },
    });
  }

  severidadIc(estado: string | null | undefined): 'success' | 'warn' | 'danger' | 'secondary' {
    switch (estado?.toUpperCase()) {
      case 'BUENO':
        return 'success';
      case 'REGULAR':
        return 'warn';
      case 'MALO':
      case 'CRITICO':
        return 'danger';
      default:
        return 'secondary';
    }
  }

  private procesarErrorCarga(err: HttpErrorResponse): void {
    if (err.status === 404) {
      this.errorCarga.set(
        'Inspección no encontrada o no tiene permisos para acceder a este borrador.',
      );
    } else if (err.status === 403) {
      this.errorCarga.set(
        'Acceso denegado: Solo el autor de la inspección puede modificar este borrador.',
      );
    } else {
      this.errorCarga.set(
        err.error?.detail ||
          err.error?.message ||
          'Ocurrió un error al cargar la inspección técnica.',
      );
    }
  }

  private procesarErrorGuardado(err: HttpErrorResponse): void {
    if (err.status === 403) {
      this.errorGuardado.set(
        'Acceso denegado: Solo el autor de la inspección puede modificar este borrador.',
      );
    } else if (err.status === 404) {
      this.errorGuardado.set(
        'Inspección no encontrada o no tiene permisos para acceder a este borrador.',
      );
    } else if (err.status === 409) {
      if (err.error?.code === 'concurrencia_conflicto') {
        this.errorGuardado.set(
          'Conflicto de concurrencia: El borrador fue modificado concurrentemente por otra sesión o dispositivo.',
        );
      } else if (err.error?.code === 'inspeccion_no_editable') {
        this.errorGuardado.set(
          'La inspección no es editable porque ya no se encuentra en estado Borrador.',
        );
      } else {
        this.errorGuardado.set(
          err.error?.detail ||
            err.error?.message ||
            'Conflicto al guardar el borrador de inspección.',
        );
      }
    } else if (err.status === 422) {
      this.errorGuardado.set(
        err.error?.detail ||
          err.error?.message ||
          'Error de validación al guardar los datos del borrador.',
      );
    } else {
      this.errorGuardado.set(
        err.error?.detail || err.error?.message || 'Ocurrió un error al guardar el borrador.',
      );
    }
  }
}
