import { mensajeCampo } from '../../utils/nombre-campo';
import { esResultadoIncierto } from '../../services/operacion-idempotente.service';
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
import { DialogModule } from 'primeng/dialog';
import { ProgressBarModule } from 'primeng/progressbar';
import { InputTextModule } from 'primeng/inputtext';
import { FormularioDinamicoComponent } from '../../../formulario/components/formulario-dinamico/formulario-dinamico.component';
import { EsquemaJson } from '../../../formulario/motor/esquema/modelos';
import { FormularioApiService } from '../../../formulario/services/formulario-api.service';
import {
  GuardarBorradorRequest,
  InspeccionBorradorResponse,
  InspeccionResumenResponse,
} from '../../models/inspeccion.models';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { InspeccionApiService } from '../../services/inspeccion-api.service';
import {
  InspeccionesLocalesService,
  BorradorLocalPayload,
} from '../../../../offline/services/inspecciones-locales.service';
import { ConnectivityService } from '../../../../offline/services/connectivity.service';

import { ReporteFotograficoComponent } from '../../components/reporte-fotografico/reporte-fotografico.component';

import { ConfirmacionEstadoIcComponent } from '../../components/confirmacion-estado-ic/confirmacion-estado-ic.component';
type AccionFinal = 'enviar' | 'publicar';
interface ErrorAccion {
  mensaje: string;
  campos: {
    campo: string;
    mensaje: string;
  }[];
  fotografias: string[];
}
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
    DialogModule,
    ProgressBarModule,
    InputTextModule,
    FormularioDinamicoComponent,
    ReporteFotograficoComponent,
    ConfirmacionEstadoIcComponent,
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
  private readonly auth = inject(AuthStore);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly inspeccionesLocales = inject(InspeccionesLocalesService);
  private readonly connectivity = inject(ConnectivityService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cambios = new Subject<void>();
  private revisionDatos = 0;
  private revisionGuardada = 0;
  private ultimaHuellaDatos: string | null = null;
  private guardadoForzadoDuranteSolicitud = false;
  private guardadoPendiente: { request: GuardarBorradorRequest; revision: number } | null = null;

  protected readonly reporteFotos = viewChild(ReporteFotograficoComponent);
  readonly fotografiasOcupadas = computed(() => this.reporteFotos()?.ocupado() ?? false);
  protected readonly formulario = viewChild(FormularioDinamicoComponent);
  readonly revisionIc = signal(0);
  readonly operandoIc = signal(false);
  readonly esAutor = computed(() => this.auth.user()?.id === this.borrador()?.autorId);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly errorCarga = signal<string | null>(null);
  readonly errorGuardado = signal<string | null>(null);
  readonly mensajeExito = signal<string | null>(null);
  readonly estadoAutoguardado = signal<'pendiente' | 'guardando' | 'guardado' | null>(null);
  readonly borrador = signal<InspeccionBorradorResponse | null>(null);
  readonly esquema = signal<EsquemaJson | null>(null);
  readonly fechaInspeccion = signal<string>('');
  readonly horaGuardadoLocal = signal<string | null>(null);
  readonly errorAlmacenamiento = signal<string | null>(null);
  readonly esOffline = computed(() => !this.connectivity.online());
  readonly hoy = new Date().toISOString().slice(0, 10);
  readonly soloLectura = computed(() => {
    const borrador = this.borrador();
    return !!borrador && borrador.autorId !== this.auth.user()?.id;
  });

  readonly confirmandoAccion = signal(false);
  readonly ejecutandoAccion = signal(false);
  readonly errorAccion = signal<ErrorAccion | null>(null);
  readonly resultadoAccion = signal<InspeccionResumenResponse | null>(null);
  readonly accionFinal = computed<AccionFinal | null>(() => {
    const b = this.borrador();
    if (!b || b.estado !== 'BORRADOR' || this.soloLectura()) return null;
    if (b.rolAutor === 'CATEDRATICO' || b.rolAutor === 'PROFESIONAL_EXTERNO') return 'publicar';
    return b.rolAutor === 'ESTUDIANTE' ? 'enviar' : null;
  });
  readonly colegiadoPendiente = computed(
    () => this.accionFinal() === 'publicar' && this.auth.colegiadoPendiente(),
  );
  readonly cambiosSinGuardar = computed(
    () => this.guardando() || this.estadoAutoguardado() === 'pendiente',
  );
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
      nombre_inspector: b.nombreAutor,
      ...(b.identificacionCapturada ?? {}),
      ...idExistente,
      fecha_inspeccion: b.fechaInspeccion,
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
      if (!this.soloLectura() && this.revisionDatos > this.revisionGuardada)
        this.guardarBorrador(false);
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

    // Intentar primero desde Dexie si estamos offline o si existe localmente
    this.inspeccionesLocales.obtenerInspeccionLocal(id).then((local) => {
      if (local && local.payload) {
        const payload = local.payload as BorradorLocalPayload;
        const b = payload.borrador;
        this.borrador.set(b);
        this.datosIniciales.set(b);
        this.fechaInspeccion.set(b.fechaInspeccion || this.hoy);
        if (b.datos) {
          this.datosEnMemoria.set(b.datos);
        }
        if (local.actualizadoEn) {
          const d = new Date(local.actualizadoEn);
          const hh = String(d.getHours()).padStart(2, '0');
          const mm = String(d.getMinutes()).padStart(2, '0');
          this.horaGuardadoLocal.set(`${hh}:${mm}`);
        }
        this.cargarEsquema(b.versionFormularioId);
        return;
      }

      // Si no está local o no hay Dexie disponible, acudir a la API
      this.inspeccionApi
        .obtenerBorrador(id, true)
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
    });
  }
  private cargarEsquema(versionId: string): void {
    // Si no hay red, consultar el esquema guardado en Dexie
    if (!this.connectivity.online()) {
      this.inspeccionesLocales.obtenerEsquemaLocal(versionId).then(
        (localSchema) => {
          if (localSchema && localSchema.schema) {
            this.esquema.set(localSchema.schema as EsquemaJson);
            this.cargando.set(false);
          } else {
            this.inspeccionesLocales.obtenerEsquemaActivoLocal().then((activo) => {
              if (activo && activo.schema) {
                this.esquema.set(activo.schema as EsquemaJson);
              } else {
                this.errorCarga.set(
                  'No fue posible obtener el esquema del formulario para esta inspección.',
                );
              }
              this.cargando.set(false);
            });
          }
        },
        () => {
          this.cargando.set(false);
          this.errorCarga.set(
            'No fue posible obtener el esquema del formulario para esta inspección.',
          );
        },
      );
      return;
    }

    this.formularioApi
      .obtenerEsquemaPublicado(versionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.esquema.set(res.esquema as EsquemaJson);
          this.cargando.set(false);
        },
        error: () => {
          this.inspeccionesLocales.obtenerEsquemaLocal(versionId).then((local) => {
            if (local && local.schema) {
              this.esquema.set(local.schema as EsquemaJson);
              this.cargando.set(false);
              return;
            }
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
          });
        },
      });
  }
  onValorChange(valor: Record<string, unknown>): void {
    const fechaFormulario = (valor['identificacion'] as Record<string, unknown> | undefined)?.[
      'fecha_inspeccion'
    ];
    if (typeof fechaFormulario === 'string' && fechaFormulario !== this.fechaInspeccion()) {
      this.fechaInspeccion.set(fechaFormulario);
    }
    this.datosEnMemoria.set(valor);
    const huella = JSON.stringify(valor);
    if (this.ultimaHuellaDatos === null) {
      this.ultimaHuellaDatos = huella;
      return;
    }
    if (!this.soloLectura() && huella !== this.ultimaHuellaDatos) {
      this.ultimaHuellaDatos = huella;
      this.guardarEnDexieInmediato(valor);
      this.marcarCambio();
    }
  }

  private guardarEnDexieInmediato(valores: Record<string, unknown>): void {
    const b = this.borrador();
    if (!b || this.soloLectura()) return;

    const fecha = this.fechaInspeccion();
    if (fecha > this.hoy) {
      this.errorGuardado.set('La fecha de inspección no puede ser posterior a la fecha actual.');
      return;
    }
    const anioConstruccion = b.datosPermanentes?.anioConstruccion;
    if (anioConstruccion && fecha) {
      const anioInspeccion = parseInt(fecha.substring(0, 4), 10);
      if (anioInspeccion < anioConstruccion) {
        this.errorGuardado.set(
          `La fecha de inspección (${anioInspeccion}) no puede ser anterior al año de construcción registrado (${anioConstruccion}).`,
        );
        return;
      }
    }

    const d = new Date();
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    const ahoraIso = d.toISOString();

    const borradorActualizado: InspeccionBorradorResponse = {
      ...b,
      datos: valores,
      fechaInspeccion: fecha || b.fechaInspeccion,
      actualizadoEn: ahoraIso,
    };

    const payload: BorradorLocalPayload = {
      borrador: borradorActualizado,
      versionFormularioId: b.versionFormularioId,
      dispositivoId: b.dispositivoId,
      autorId: b.autorId,
    };

    this.inspeccionesLocales
      .guardarInspeccionLocal({
        id: b.id,
        puenteId: b.puenteId,
        propietarioId: b.autorId,
        estado: b.estado,
        actualizadoEn: ahoraIso,
        payload: payload,
        sincronizado: false,
      })
      .then(() => {
        this.horaGuardadoLocal.set(`${hh}:${mm}`);
        this.errorAlmacenamiento.set(null);
      })
      .catch((err: Error) => {
        this.errorAlmacenamiento.set(err.message || 'Almacenamiento local insuficiente.');
      });
  }
  onFechaInspeccionChange(fecha: string): void {
    if (this.soloLectura()) return;
    if (fecha === this.fechaInspeccion()) return;
    this.fechaInspeccion.set(fecha);
    this.formulario()
      ?.construido()
      ?.form.get(['identificacion', 'fecha_inspeccion'])
      ?.setValue(fecha, { emitEvent: false });
    this.marcarCambio();
  }
  onSeccionCambiando(): void {
    if (!this.soloLectura()) this.guardarBorrador(false);
  }
  private marcarCambio(): void {
    this.revisionDatos++;
    this.revisionIc.update((revision) => revision + 1);
    this.estadoAutoguardado.set('pendiente');
    this.errorGuardado.set(null);
    this.mensajeExito.set(null);
    this.cambios.next();
  }
  guardarBorrador(forzar = true): void {
    const b = this.borrador();
    if (this.ejecutandoAccion() || this.resultadoAccion()) return;
    if (!b || this.soloLectura() || (!forzar && this.revisionDatos <= this.revisionGuardada))
      return;
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
    const valores = this.formulario()?.valor() ?? this.datosEnMemoria();
    const valorActual = {
      ...valores,
      identificacion: {
        ...((valores['identificacion'] as Record<string, unknown>) ?? {}),
        fecha_inspeccion: fecha || b.fechaInspeccion,
      },
    };
    const revisionSolicitud = this.guardadoPendiente?.revision ?? this.revisionDatos;
    const request: GuardarBorradorRequest = this.guardadoPendiente?.request ?? {
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
    this.guardadoPendiente = { request: structuredClone(request), revision: revisionSolicitud };
    this.guardando.set(true);
    this.estadoAutoguardado.set('guardando');
    // Si no hay red, el guardado local en Dexie ya se realizó inmediatamente
    if (!this.connectivity.online()) {
      this.guardadoPendiente = null;
      this.guardando.set(false);
      this.revisionGuardada = Math.max(this.revisionGuardada, revisionSolicitud);
      this.estadoAutoguardado.set('guardado');
      this.mensajeExito.set('Avance guardado localmente en este dispositivo.');
      return;
    }

    this.inspeccionApi.guardarBorrador(b.id, request).subscribe({
      next: (actualizada) => {
        this.guardadoPendiente = null;
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
        if (!esResultadoIncierto(err)) this.guardadoPendiente = null;
        this.guardando.set(false);
        this.estadoAutoguardado.set(null);
        this.procesarErrorGuardado(err);
      },
    });
  }
  abrirConfirmacion(): void {
    if (
      !this.connectivity.online() ||
      !this.accionFinal() ||
      this.cambiosSinGuardar() ||
      this.errorGuardado() ||
      this.operandoIc() ||
      this.colegiadoPendiente() ||
      this.fotografiasOcupadas()
    )
      return;
    this.errorAccion.set(null);
    this.confirmandoAccion.set(true);
  }
  cancelarConfirmacion(): void {
    if (this.ejecutandoAccion()) return;
    this.confirmandoAccion.set(false);
  }
  confirmarAccion(): void {
    const b = this.borrador();
    const accion = this.accionFinal();
    if (
      !b ||
      !accion ||
      !this.confirmandoAccion() ||
      this.ejecutandoAccion() ||
      this.cambiosSinGuardar() ||
      this.errorGuardado() ||
      this.operandoIc() ||
      this.colegiadoPendiente() ||
      this.fotografiasOcupadas()
    )
      return;
    this.ejecutandoAccion.set(true);
    this.errorAccion.set(null);
    this.apiErrors.clear();
    const peticion =
      accion === 'publicar' ? this.inspeccionApi.publicar(b.id) : this.inspeccionApi.enviar(b.id);
    peticion.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (resultado) => {
        this.ejecutandoAccion.set(false);
        this.confirmandoAccion.set(false);
        this.resultadoAccion.set(resultado);
        this.borrador.update((actual) =>
          actual ? { ...actual, estado: resultado.estado } : actual,
        );
        if (resultado.estado === 'PUBLICADA') {
          void this.router.navigate(['/puentes', resultado.puenteId], { replaceUrl: true });
        }
      },
      error: (err: HttpErrorResponse) => {
        this.ejecutandoAccion.set(false);
        this.errorAccion.set(this.errorDeAccion(err));
        if (
          err.status === 409 &&
          ['confirmacion_ic_desactualizada', 'calculo_ic_desactualizado'].includes(err.error?.code)
        ) {
          this.confirmandoAccion.set(false);
          this.revisionIc.update((revision) => revision + 1);
        }
        this.apiErrors.clear();
      },
    });
  }
  private errorDeAccion(err: HttpErrorResponse): ErrorAccion {
    const problema = err.error as {
      detail?: string;
      errores?: {
        campo?: string;
        mensaje?: string;
      }[];
    } | null;
    const errores = problema?.errores ?? [];
    return {
      mensaje: problema?.detail ?? 'No fue posible completar la acción sobre la inspección.',
      campos: errores
        .filter((e) => e.campo && !e.campo.startsWith('fotografias.'))
        .map((e) => ({
          campo: e.campo ?? '',
          mensaje: mensajeCampo(e.campo ?? '', e.mensaje ?? '', this.esquema()),
        })),
      fotografias: errores
        .filter((e) => e.campo?.startsWith('fotografias.'))
        .map((e) => (e.campo ?? '').replace('fotografias.', '')),
    };
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
