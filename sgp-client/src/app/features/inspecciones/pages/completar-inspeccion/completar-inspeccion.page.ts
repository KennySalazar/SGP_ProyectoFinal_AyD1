import { LocalInspection } from '../../../../offline/db/sgp-db';
import { SincronizacionService } from '../../../../offline/services/sincronizacion.service';
import { TranslocoService } from '@jsverse/transloco';
import { AprobacionCalibracionComponent } from '../../../formulario/components/aprobacion-calibracion/aprobacion-calibracion.component';
import { esResultadoIncierto } from '../../services/operacion-idempotente.service';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  effect,
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
import { mensajeCampo } from '../../utils/nombre-campo';

import { ReporteFotograficoComponent } from '../../components/reporte-fotografico/reporte-fotografico.component';
import { DocumentosInspeccionComponent } from '../../components/documentos-inspeccion/documentos-inspeccion.component';

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
import { ResultadoRevisionComponent } from '../../../revision/components/resultado-revision/resultado-revision.component';

@Component({
  selector: 'app-completar-inspeccion-page',
  standalone: true,
  imports: [
    AprobacionCalibracionComponent,
    ResultadoRevisionComponent,
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
    DocumentosInspeccionComponent,
    ConfirmacionEstadoIcComponent,
  ],
  templateUrl: './completar-inspeccion.page.html',
  styleUrl: './completar-inspeccion.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CompletarInspeccionPage implements OnInit {
  private readonly sync = inject(SincronizacionService);
  private readonly i18n = inject(TranslocoService);
  readonly intencionFinal = signal<LocalInspection['intencionFinal']>(undefined);
  private escrituraLocal: Promise<unknown> = Promise.resolve();
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
  private publicarTrasGuardar = false;

  protected readonly reporteFotos = viewChild(ReporteFotograficoComponent);
  readonly fotografiasOcupadas = computed(() => this.reporteFotos()?.ocupado() ?? false);
  protected readonly formulario = viewChild(FormularioDinamicoComponent);
  readonly valoresInicialesIncompatibles = computed(
    () => this.formulario()?.valoresInicialesIncompatibles() ?? [],
  );
  readonly revisionInicialConfirmada = signal(false);
  readonly requiereRevisionInicial = computed(
    () => this.valoresInicialesIncompatibles().length > 0 && !this.revisionInicialConfirmada(),
  );
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
  readonly esSoloLocal = signal(false);
  readonly errorAlmacenamiento = signal<string | null>(null);
  readonly icVisible = computed(() => (this.esOffline() ? null : this.borrador()?.icPreliminar));
  readonly esOffline = computed(() => !this.connectivity.online());
  readonly hoy = new Date().toISOString().slice(0, 10);
  readonly soloLectura = computed(() => {
    const borrador = this.borrador();
    return (
      !!borrador &&
      (borrador.autorId !== this.auth.user()?.id ||
        borrador.estado !== 'BORRADOR' ||
        this.intencionFinal()?.estado === 'PENDIENTE' ||
        this.intencionFinal()?.estado === 'CONFIRMADA')
    );
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
  readonly datosIniciales = signal<InspeccionBorradorResponse | null>(null);
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
    effect(() => {
      const usuario = this.auth.user()?.id;
      if (this.borrador() && this.borrador()?.autorId !== usuario) {
        this.borrador.set(null);
        this.datosIniciales.set(null);
        this.esquema.set(null);
        this.intencionFinal.set(undefined);
        this.resultadoAccion.set(null);
        this.errorAccion.set(null);
      }
    });
    this.inspeccionesLocales
      .observarInspeccion(this.route.snapshot.paramMap.get('id') ?? '')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((local) => {
        if (local?.propietarioId !== this.auth.user()?.id) return;
        const confirmacionAnterior =
          local?.intencionFinal?.estado === 'CONFIRMADA' &&
          (local.estado === 'BORRADOR' || local.estado === 'CAMBIOS_SOLICITADOS');
        this.intencionFinal.set(confirmacionAnterior ? undefined : local?.intencionFinal);
        if (confirmacionAnterior) this.resultadoAccion.set(null);
        if (
          local?.intencionFinal?.estado === 'CONFIRMADA' &&
          local.estado !== 'BORRADOR' &&
          local.estado !== 'CAMBIOS_SOLICITADOS'
        ) {
          this.resultadoAccion.set(local.intencionFinal.resultado ?? null);
          this.borrador.update((b) => (b ? { ...b, estado: local.estado } : b));
        } else if (local?.estado === 'BORRADOR' && !local.intencionFinal) {
          this.resultadoAccion.set(null);
        }
      });
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
  actualizarRevision(): void {
    const actual = this.borrador();
    if (!actual || actual.estado !== 'CAMBIOS_SOLICITADOS' || !this.connectivity.online()) return;
    this.inspeccionApi
      .obtenerBorrador(actual.id, true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (borrador) => {
          void this.inspeccionesLocales
            .actualizarBorradorCanonicoSiSinPendientes(borrador, borrador.autorId)
            .then((actualizado) => {
              if (!actualizado) {
                this.errorGuardado.set(
                  'Hay cambios locales pendientes. Sincronice antes de abrir la correccion.',
                );
                return;
              }
              this.intencionFinal.set(undefined);
              this.aplicarBorrador(borrador);
              this.cargarEsquema(borrador.versionFormularioId);
            })
            .catch(() =>
              this.errorGuardado.set('No fue posible preparar la correccion en este dispositivo.'),
            );
        },
        error: (err: HttpErrorResponse) => this.procesarErrorCarga(err),
      });
  }

  private cargarBorrador(id: string): void {
    this.cargando.set(true);
    this.errorCarga.set(null);
    void this.inspeccionesLocales
      .tieneConflictoConcurrencia(id, this.auth.user()?.id)
      .then((conflicto) => {
        if (conflicto && this.connectivity.online()) {
          this.cargarBorradorCanonico(id);
          return;
        }
        // En operación normal y sin conexión, Dexie conserva la precedencia existente.
        void this.inspeccionesLocales.obtenerInspeccionLocal(id).then((local) => {
          this.cargarBorradorDesdeLocal(id, local);
        });
      });
  }

  private cargarBorradorDesdeLocal(
    id: string,
    local: Awaited<ReturnType<InspeccionesLocalesService['obtenerInspeccionLocal']>>,
  ): void {
    if (local && local.payload) {
      this.intencionFinal.set(local.intencionFinal);
      if (!local.sincronizado) {
        this.esSoloLocal.set(true);
      }
      const rawPayload = local.payload as Record<string, unknown>;
      const b = (
        rawPayload &&
        typeof rawPayload === 'object' &&
        'borrador' in rawPayload &&
        rawPayload['borrador']
          ? rawPayload['borrador']
          : rawPayload
      ) as InspeccionBorradorResponse;

      if (b && typeof b === 'object' && (b.fechaInspeccion || b.id || b.versionFormularioId)) {
        if ((local.sincronizado || b.version > 0) && this.connectivity.online()) {
          this.inspeccionApi
            .obtenerBorrador(id, true)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
              next: (canonico) => {
                this.esSoloLocal.set(false);
                void this.inspeccionesLocales
                  .actualizarBorradorCanonicoSiSinPendientes(canonico, canonico.autorId)
                  .then(async (actualizado) => {
                    let borrador = canonico;
                    let sincronizado = actualizado;
                    if (!actualizado && canonico.estado === 'BORRADOR') {
                      const copiaLocal = await this.inspeccionesLocales.obtenerInspeccionLocal(id);
                      const payload = copiaLocal?.payload as BorradorLocalPayload | undefined;
                      borrador = payload?.borrador ?? b;
                      sincronizado = copiaLocal?.sincronizado ?? false;
                    }
                    this.aplicarBorradorLocal(borrador, sincronizado);
                  })
                  .catch(() => this.aplicarBorradorLocal(canonico, true));
              },
              error: (err: HttpErrorResponse) => {
                if (err.status === 403 || err.status === 404) {
                  this.cargando.set(false);
                  this.procesarErrorCarga(err);
                } else {
                  this.aplicarBorradorLocal(b, local.sincronizado);
                }
              },
            });
        } else {
          this.aplicarBorradorLocal(b, local.sincronizado && !local.actualizacionLocalPendiente);
        }
        return;
      }
    }

    if (!this.connectivity.online()) {
      this.cargando.set(false);
      this.errorCarga.set(this.i18n.translate('offline.hu061.noLocal'));
      return;
    }
    // Si no está local o no hay Dexie disponible, acudir a la API
    this.inspeccionApi
      .obtenerBorrador(id, true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (b) => {
          this.aplicarBorrador(b);
          this.cargarEsquema(b.versionFormularioId);
        },
        error: (err: HttpErrorResponse) => {
          this.cargando.set(false);
          this.procesarErrorCarga(err);
        },
      });
  }

  private aplicarBorradorLocal(borrador: InspeccionBorradorResponse, sincronizado: boolean): void {
    this.aplicarBorrador(borrador);
    this.estadoAutoguardado.set('guardado');
    this.mensajeExito.set(
      sincronizado
        ? 'Borrador guardado en el servidor.'
        : this.mensajeGuardadoEnDispositivo(new Date(borrador.actualizadoEn ?? Date.now())),
    );
    this.cargarEsquema(borrador.versionFormularioId);
  }

  private cargarBorradorCanonico(id: string): void {
    this.inspeccionApi
      .obtenerBorrador(id, true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (borrador) => {
          void this.inspeccionesLocales
            .cachearBorradorCanonico(borrador, borrador.autorId)
            .catch((error: Error) => {
              this.errorAlmacenamiento.set(
                error.message || 'No fue posible actualizar la copia local del borrador.',
              );
            })
            .finally(() => {
              this.esSoloLocal.set(false);
              this.aplicarBorrador(borrador);
              this.cargarEsquema(borrador.versionFormularioId);
            });
        },
        error: (err: HttpErrorResponse) => {
          this.inspeccionesLocales.obtenerInspeccionLocal(id).then((local) => {
            this.cargarBorradorDesdeLocal(id, local);
            if (local)
              this.errorGuardado.set(
                'No se pudo actualizar el borrador; se muestra la copia local disponible.',
              );
            else this.procesarErrorCarga(err);
          });
        },
      });
  }

  private aplicarBorrador(borrador: InspeccionBorradorResponse): void {
    this.ultimaHuellaDatos = null;
    this.errorGuardado.set(null);
    if (borrador.estado === 'BORRADOR' || borrador.estado === 'CAMBIOS_SOLICITADOS') {
      this.resultadoAccion.set(null);
      if (this.intencionFinal()?.estado === 'CONFIRMADA') this.intencionFinal.set(undefined);
    }
    this.revisionInicialConfirmada.set(false);
    this.borrador.set(this.conProgreso(borrador));
    this.datosIniciales.set(borrador);
    this.fechaInspeccion.set(borrador.fechaInspeccion || this.hoy);
    this.datosEnMemoria.set(borrador.datos ?? {});
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
            this.errorCarga.set(this.i18n.translate('offline.hu061.noSchema'));
            this.cargando.set(false);
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
          void this.inspeccionesLocales.guardarEsquemaLocal({
            id: res.id,
            version: String(res.version),
            schema: res.esquema,
            activo: false,
          });
          this.cargando.set(false);
        },
        error: () => {
          this.inspeccionesLocales.obtenerEsquemaLocal(versionId).then((local) => {
            if (local && local.schema) {
              this.esquema.set(local.schema as EsquemaJson);
              this.cargando.set(false);
              return;
            }
            this.errorCarga.set(this.i18n.translate('offline.hu061.noSchema'));
            this.cargando.set(false);
          });
        },
      });
  }

  private conProgreso(borrador: InspeccionBorradorResponse): InspeccionBorradorResponse {
    return {
      ...borrador,
      progreso: borrador.progreso ?? {
        totalCamposRequeridos: 0,
        totalCamposCompletados: 0,
        porcentajeTotal: 0,
        secciones: [],
      },
    };
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
    const request: GuardarBorradorRequest = {
      version: b.version,
      datos: valores,
      identificacionCapturada:
        (valores['identificacion'] as Record<string, unknown>) ?? b.identificacionCapturada ?? null,
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

    const local = {
      id: b.id,
      puenteId: b.puenteId,
      propietarioId: b.autorId,
      estado: b.estado,
      actualizadoEn: ahoraIso,
      payload: payload,
      sincronizado: false,
      actualizacionLocalPendiente: true,
    };
    const guardadoLocal =
      this.connectivity.online() && !this.esSoloLocal()
        ? this.inspeccionesLocales.guardarBorradorEnCache(local)
        : this.inspeccionesLocales.guardarBorradorLocal(local, request);
    this.escrituraLocal = guardadoLocal
      .then(() => {
        this.errorAlmacenamiento.set(null);
        if (!this.connectivity.online() || this.esSoloLocal()) {
          this.estadoAutoguardado.set('guardado');
          this.mensajeExito.set(this.mensajeGuardadoEnDispositivo(d));
        }
      })
      .catch((err: Error) => {
        this.errorAlmacenamiento.set(
          err.message?.startsWith('offline.')
            ? this.i18n.translate(err.message)
            : err.message || 'Almacenamiento local insuficiente.',
        );
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
    if ((this.ejecutandoAccion() && !this.publicarTrasGuardar) || this.resultadoAccion()) return;
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
    const formulario = this.formulario();
    if (!formulario?.disponible()) {
      const error = new HttpErrorResponse({
        status: 0,
        error: {
          detail:
            'El formulario de inspección todavía no está listo. Espere a que cargue para guardar.',
        },
      });
      if (this.publicarTrasGuardar) this.abortarPublicacionPorGuardado(error);
      else {
        this.errorGuardado.set(error.error.detail);
        this.estadoAutoguardado.set('pendiente');
      }
      return;
    }
    const valores = formulario.valor();
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
    this.guardadoPendiente = {
      request: structuredClone(request),
      revision: revisionSolicitud,
    };
    this.guardando.set(true);
    this.estadoAutoguardado.set('guardando');
    // Si no hay red o es un borrador que solo existe localmente (no sincronizado en servidor)
    if (!this.connectivity.online() || this.esSoloLocal()) {
      if (this.publicarTrasGuardar) {
        this.abortarPublicacionPorGuardado(
          new HttpErrorResponse({
            status: 0,
            error: { detail: 'Se requiere conexión para guardar el borrador antes de publicar.' },
          }),
        );
        return;
      }
      void this.inspeccionesLocales
        .guardarBorradorLocal(
          {
            id: b.id,
            puenteId: b.puenteId,
            propietarioId: b.autorId,
            estado: b.estado,
            actualizadoEn: new Date().toISOString(),
            payload: {
              borrador: {
                ...b,
                datos: valorActual,
                fechaInspeccion: fecha || b.fechaInspeccion,
              },
              versionFormularioId: b.versionFormularioId,
              dispositivoId: b.dispositivoId,
              autorId: b.autorId,
            },
            sincronizado: false,
          },
          request,
        )
        .then(() => {
          this.guardadoPendiente = null;
          this.guardando.set(false);
          this.revisionGuardada = Math.max(this.revisionGuardada, revisionSolicitud);
          this.estadoAutoguardado.set('guardado');
          this.mensajeExito.set(this.mensajeGuardadoEnDispositivo(new Date()));
        })
        .catch((err: Error) => {
          this.guardando.set(false);
          this.estadoAutoguardado.set(null);
          this.errorAlmacenamiento.set(
            err.message?.startsWith('offline.')
              ? this.i18n.translate(err.message)
              : err.message || 'Almacenamiento local insuficiente.',
          );
        });
      return;
    }

    void this.inspeccionesLocales
      .guardarBorradorLocal(
        {
          id: b.id,
          puenteId: b.puenteId,
          propietarioId: b.autorId,
          estado: b.estado,
          actualizadoEn: new Date().toISOString(),
          payload: {
            borrador: {
              ...b,
              datos: valorActual,
              fechaInspeccion: fecha || b.fechaInspeccion,
            },
            versionFormularioId: b.versionFormularioId,
            dispositivoId: b.dispositivoId,
            autorId: b.autorId,
          },
          sincronizado: false,
        },
        request,
        'ENVIANDO',
      )
      .then((operacion) => {
        if (this.auth.user()?.id !== b.autorId) {
          void this.inspeccionesLocales.marcarOperacionPendiente(
            operacion.id,
            'La sesión actual no corresponde al propietario de la inspección.',
          );
          this.guardando.set(false);
          this.estadoAutoguardado.set('guardado');
          this.mensajeExito.set(this.mensajeGuardadoEnDispositivo(new Date()));
          if (this.publicarTrasGuardar) {
            this.abortarPublicacionPorGuardado(
              new HttpErrorResponse({
                status: 403,
                error: { detail: 'La sesión actual no corresponde al propietario del borrador.' },
              }),
            );
          }
          return;
        }
        this.inspeccionApi.guardarBorrador(b.id, request, operacion.idempotencyKey).subscribe({
          next: (actualizada) => {
            void this.inspeccionesLocales.confirmarGuardado(
              operacion.id,
              b.id,
              b.autorId,
              actualizada,
            );
            if (this.auth.user()?.id !== b.autorId) return;
            this.guardadoPendiente = null;
            this.guardando.set(false);
            this.borrador.set(this.conProgreso(actualizada));
            this.revisionGuardada = Math.max(this.revisionGuardada, revisionSolicitud);
            const hayCambiosNuevos = this.revisionDatos > revisionSolicitud;
            if (!hayCambiosNuevos && actualizada.datos) {
              this.datosEnMemoria.set(actualizada.datos);
            }
            if (hayCambiosNuevos) {
              this.estadoAutoguardado.set('pendiente');
            } else {
              this.estadoAutoguardado.set('guardado');
              this.mensajeExito.set('Borrador guardado en el servidor.');
            }
            const guardarCambiosPendientes =
              this.guardadoForzadoDuranteSolicitud && hayCambiosNuevos;
            this.guardadoForzadoDuranteSolicitud = false;
            const publicar = this.publicarTrasGuardar;
            if (publicar && hayCambiosNuevos) {
              this.guardarBorrador(false);
            } else if (guardarCambiosPendientes) {
              this.publicarTrasGuardar = false;
              this.guardarBorrador(false);
            } else if (publicar) {
              this.publicarTrasGuardar = false;
              this.datosIniciales.set(actualizada);
              this.ejecutarAccionFinal();
            } else {
              this.publicarTrasGuardar = false;
            }
          },
          error: (err: HttpErrorResponse) => {
            if (err.status !== 0 && err.status < 500) {
              const codigoError = typeof err.error?.code === 'string' ? err.error.code : undefined;
              void this.inspeccionesLocales
                .marcarOperacionParaIntervencion(
                  operacion.id,
                  String(err.error?.detail ?? err.error?.message ?? err.message),
                  codigoError,
                )
                .then(() => {
                  if (codigoError === 'concurrencia_conflicto' && this.connectivity.online()) {
                    this.cargarBorradorCanonico(b.id);
                  }
                });
            } else {
              void this.inspeccionesLocales.marcarOperacionPendiente(
                operacion.id,
                String(err.message ?? 'Error de conexión'),
              );
            }
            if (!esResultadoIncierto(err)) this.guardadoPendiente = null;
            this.guardando.set(false);
            if (operacion) {
              this.estadoAutoguardado.set('guardado');
              this.mensajeExito.set(this.mensajeGuardadoEnDispositivo(new Date()));
            } else {
              this.estadoAutoguardado.set(null);
            }
            this.procesarErrorGuardado(err);
            if (this.publicarTrasGuardar) this.abortarPublicacionPorGuardado(err);
          },
        });
      })
      .catch((err: unknown) => {
        if (this.publicarTrasGuardar) {
          this.abortarPublicacionPorGuardado(
            err instanceof HttpErrorResponse
              ? err
              : new HttpErrorResponse({
                  status: 0,
                  error: { detail: 'No fue posible preparar el guardado.' },
                }),
          );
        }
      });
  }
  confirmarRevisionInicial(): void {
    if (this.valoresInicialesIncompatibles().length > 0) this.revisionInicialConfirmada.set(true);
  }
  abrirConfirmacion(): void {
    if (
      !this.accionFinal() ||
      this.requiereRevisionInicial() ||
      this.errorGuardado() ||
      this.guardando() ||
      this.operandoIc() ||
      this.colegiadoPendiente() ||
      this.fotografiasOcupadas()
    )
      return;
    this.errorAccion.set(null);
    if (!this.formulario()?.revisar()) {
      this.errorAccion.set({
        mensaje:
          this.formulario()?.problemasValidacion().join(' · ') ||
          this.i18n.translate('offline.hu061.incomplete'),
        campos: [],
        fotografias: [],
      });
      return;
    }
    const faltantes = this.reporteFotos()?.faltantes() ?? [
      'ACCESO',
      'SUPERESTRUCTURA',
      'SUBESTRUCTURA',
      'CAUCE',
    ];
    if (faltantes.length) {
      this.errorAccion.set({
        mensaje: this.i18n.translate('offline.hu061.missingPhotos'),
        campos: [],
        fotografias: faltantes,
      });
      return;
    }
    this.confirmandoAccion.set(true);
  }
  cancelarConfirmacion(): void {
    if (!this.ejecutandoAccion()) this.confirmandoAccion.set(false);
  }
  async confirmarAccion(): Promise<void> {
    const b = this.borrador();
    const accion = this.accionFinal();
    if (
      !b ||
      !accion ||
      this.requiereRevisionInicial() ||
      !this.confirmandoAccion() ||
      this.ejecutandoAccion() ||
      this.guardando() ||
      this.operandoIc() ||
      this.colegiadoPendiente() ||
      this.fotografiasOcupadas()
    )
      return;
    if (!this.formulario()?.revisar()) {
      this.confirmandoAccion.set(false);
      return;
    }
    this.ejecutandoAccion.set(true);
    this.errorAccion.set(null);
    if (accion === 'publicar' && this.connectivity.online() && !this.esSoloLocal()) {
      this.publicarTrasGuardar = true;
      this.guardarBorrador(true);
      return;
    }
    try {
      await this.escrituraLocal;
      if (this.auth.user()?.id !== b.autorId || this.errorAlmacenamiento())
        throw new Error(this.i18n.translate('offline.hu061.storageError'));
      const formulario = this.formulario();
      if (!formulario?.disponible() || !formulario.construido())
        throw new Error(this.i18n.translate('offline.hu061.incomplete'));
      const datos = formulario.valor();
      const fecha = this.fechaInspeccion() || b.fechaInspeccion;
      if (
        fecha > this.hoy ||
        (b.datosPermanentes?.anioConstruccion &&
          Number(fecha.slice(0, 4)) < b.datosPermanentes.anioConstruccion)
      )
        throw new Error(this.i18n.translate('offline.hu061.invalidDate'));
      // El borrador remoto también necesita una copia local antes de encolar su intención.
      await this.inspeccionesLocales.actualizarBorradorCanonicoSiSinPendientes(b, b.autorId);
      await this.inspeccionesLocales.encolarAccionFinal(b.id, accion, {
        version: b.version,
        datos,
        fechaInspeccion: fecha,
        identificacionCapturada:
          (datos['identificacion'] as Record<string, unknown>) ?? b.identificacionCapturada,
        acompanantes: b.acompanantes,
      });
      this.intencionFinal.set({ accion, estado: 'PENDIENTE' });
      this.confirmandoAccion.set(false);
      this.estadoAutoguardado.set('guardado');
      this.revisionGuardada = this.revisionDatos;
      void this.sync.sincronizarAhora();
    } catch (error) {
      this.errorAccion.set({
        mensaje:
          error instanceof Error
            ? error.message.startsWith('offline.')
              ? this.i18n.translate(error.message)
              : error.message
            : this.i18n.translate('offline.hu061.storageError'),
        campos: [],
        fotografias: [],
      });
    } finally {
      this.ejecutandoAccion.set(false);
    }
  }
  private ejecutarAccionFinal(): void {
    const b = this.borrador();
    if (!b) return;
    const peticion = this.inspeccionApi.publicar(b.id);
    peticion.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (resultado) => {
        this.ejecutandoAccion.set(false);
        this.confirmandoAccion.set(false);
        this.resultadoAccion.set(resultado);
        this.borrador.update((actual) =>
          actual ? { ...actual, estado: resultado.estado } : actual,
        );
        if (resultado.estado === 'PUBLICADA')
          void this.router.navigate(['/puentes', resultado.puenteId], { replaceUrl: true });
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
  private abortarPublicacionPorGuardado(err: HttpErrorResponse): void {
    this.publicarTrasGuardar = false;
    this.guardando.set(false);
    this.estadoAutoguardado.set(this.revisionDatos > this.revisionGuardada ? 'pendiente' : null);
    this.ejecutandoAccion.set(false);
    this.confirmandoAccion.set(false);
    this.errorAccion.set(this.errorDeAccion(err));
  }
  private errorDeAccion(err: HttpErrorResponse): ErrorAccion {
    const problema = err.error as {
      detail?: string;
      errores?: { campo?: string; mensaje?: string }[];
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
        const id = this.borrador()?.id;
        if (id) {
          this.inspeccionApi
            .obtenerBorrador(id, true)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
              next: (actual) => {
                this.borrador.set(this.conProgreso(actual));
                this.confirmandoAccion.set(false);
                this.guardadoPendiente = null;
                if (actual.estado === 'BORRADOR') this.errorGuardado.set(null);
              },
              error: (error: HttpErrorResponse) => {
                if (error.status === 403 || error.status === 404) this.procesarErrorCarga(error);
              },
            });
        }
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

  private mensajeGuardadoEnDispositivo(fecha: Date): string {
    const hora = `${String(fecha.getHours()).padStart(2, '0')}:${String(fecha.getMinutes()).padStart(2, '0')}`;
    return `Borrador guardado en este dispositivo a las ${hora}.`;
  }
}
