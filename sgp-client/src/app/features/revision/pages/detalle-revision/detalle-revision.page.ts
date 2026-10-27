import { AprobacionCalibracionComponent } from '../../../formulario/components/aprobacion-calibracion/aprobacion-calibracion.component';
import { DatePipe, JsonPipe, KeyValuePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  DestroyRef,
  ElementRef,
  forwardRef,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import {
  catchError,
  distinctUntilChanged,
  finalize,
  forkJoin,
  map,
  merge,
  Observable,
  of,
  Subject,
  switchMap,
  takeUntil,
} from 'rxjs';
import { ProblemDetails } from '../../../../core/models/problem-details';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { FormularioDinamicoComponent } from '../../../formulario/components/formulario-dinamico/formulario-dinamico.component';
import {
  CONTEXTO_OBSERVACIONES,
  ContextoObservacionesFormulario,
} from '../../../formulario/components/observaciones/contexto-observaciones';
import { EditorObservacionesComponent } from '../../../formulario/components/observaciones/editor-observaciones.component';
import { PanelObservacionesComponent } from '../../../formulario/components/observaciones/panel-observaciones.component';
import {
  ContextoEditorObservacion,
  DestinoElementoObservacion,
  DestinoNavegableObservacion,
  ObservacionPresentacion,
  PreparacionObservacion,
} from '../../../formulario/models/observacion.models';
import {
  AnclajeFormulario,
  crearAnclajeFormulario,
} from '../../../formulario/utils/anclaje-formulario';
import { ReporteFotograficoComponent } from '../../../inspecciones/components/reporte-fotografico/reporte-fotografico.component';
import { CategoriaFoto } from '../../../inspecciones/models/fotografia.models';
import {
  ListadoObservacionesResponse,
  ObservacionResponse,
  RevisionDetalleResponse,
} from '../../models/revision.models';
import { RevisionApiService } from '../../services/revision-api.service';

import { ResultadoRevisionComponent } from '../../components/resultado-revision/resultado-revision.component';

@Component({
  selector: 'app-detalle-revision-page',
  standalone: true,
  imports: [
    AprobacionCalibracionComponent,
    ResultadoRevisionComponent,
    DatePipe,
    JsonPipe,
    KeyValuePipe,
    RouterLink,
    TranslocoPipe,
    ButtonModule,
    DialogModule,
    FormularioDinamicoComponent,
    ReporteFotograficoComponent,
    EditorObservacionesComponent,
    PanelObservacionesComponent,
  ],
  providers: [
    { provide: CONTEXTO_OBSERVACIONES, useExisting: forwardRef(() => DetalleRevisionPage) },
  ],
  templateUrl: './detalle-revision.page.html',
  styleUrl: './detalle-revision.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleRevisionPage implements ContextoObservacionesFormulario {
  private readonly api = inject(RevisionApiService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly auth = inject(AuthStore);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly i18n = inject(TranslocoService);
  private readonly destruir = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly formulario = viewChild(FormularioDinamicoComponent);
  private readonly refrescar = new Subject<void>();
  private readonly cancelarEscritura = new Subject<void>();
  private id = '';
  private navegacionPendiente: DestinoNavegableObservacion | null = null;
  readonly detalle = signal<RevisionDetalleResponse | null>(null);
  readonly listado = signal<ListadoObservacionesResponse | null>(null);
  readonly cargando = signal(false);
  readonly ocupado = signal(false);
  readonly bloqueada = signal(false);
  readonly error = signal<string | null>(null);
  readonly errorEditor = signal<string | null>(null);
  readonly estadoHttp = signal<number | null>(null);
  readonly panelAbierto = signal(false);
  readonly editor = signal<ContextoEditorObservacion | null>(null);
  readonly aEliminar = signal<ObservacionPresentacion | 'GENERAL' | null>(null);
  readonly destinoNavegacion = signal<DestinoElementoObservacion | null>(null);
  readonly consultaAutor = computed(() => this.auth.user()?.role === 'ESTUDIANTE');
  private puedeLeer(d: RevisionDetalleResponse): boolean {
    const u = this.auth.user();
    return (
      (u?.role === 'CATEDRATICO' && u.id === d.revisorId) ||
      (u?.role === 'ESTUDIANTE' && u.id === d.autorId)
    );
  }
  readonly puedeEscribir = computed(() => {
    const d = this.detalle();
    const u = this.auth.user();
    return (
      !!d &&
      u?.role === 'CATEDRATICO' &&
      u.id === d.revisorId &&
      d.estadoRevision === 'EN_CURSO' &&
      !this.bloqueada() &&
      !this.ocupado() &&
      !this.cargando()
    );
  });
  readonly valores = computed(() => {
    const detalle = this.detalle();
    const datos = structuredClone(detalle?.datos ?? {});
    if (detalle?.identificacionCapturada) {
      datos['identificacion'] = {
        ...structuredClone(detalle.identificacionCapturada),
        ...(typeof datos['identificacion'] === 'object' && datos['identificacion'] !== null
          ? datos['identificacion']
          : {}),
      };
    }
    return datos;
  });
  readonly contextoFotos = computed(() => {
    const d = this.detalle();
    return d ? { id: d.inspeccionId, autorId: d.autorId, puenteId: d.puenteId } : null;
  });
  readonly observacionesPresentacion = computed<readonly ObservacionPresentacion[]>(() =>
    (this.listado()?.observaciones ?? []).map((o) => this.presentar(o)),
  );
  readonly conteosCategoria = computed(() => {
    const conteos: Partial<Record<CategoriaFoto, number>> = {};
    for (const o of this.listado()?.observaciones ?? [])
      if (o.destino?.tipo === 'CATEGORIA_FOTOGRAFICA') {
        const categoria = o.destino.categoriaFotografica;
        conteos[categoria] = (conteos[categoria] ?? 0) + 1;
      }
    return conteos;
  });

  constructor() {
    effect(() => {
      const detalle = this.detalle();
      if (detalle && !this.puedeLeer(detalle)) {
        this.cancelarEscritura.next();
        this.limpiarPrivados();
        this.bloqueada.set(true);
      }
    });
    this.ruta.paramMap
      .pipe(
        map((p) => p.get('revisionId') ?? ''),
        distinctUntilChanged(),
        switchMap((id) => {
          this.cancelarEscritura.next();
          this.id = id;
          this.limpiarPrivados();
          this.error.set(null);
          this.estadoHttp.set(null);
          this.bloqueada.set(false);
          return merge(of(undefined), this.refrescar).pipe(
            switchMap(() => {
              this.cargando.set(true);
              return forkJoin({
                detalle: this.api.detalle(id),
                listado: this.api.observaciones(id),
              }).pipe(
                catchError((e: HttpErrorResponse) => {
                  this.manejarError(e);
                  return of(null);
                }),
                finalize(() => this.cargando.set(false)),
              );
            }),
          );
        }),
        takeUntilDestroyed(this.destruir),
      )
      .subscribe((respuesta) => {
        if (!respuesta) return;
        if (
          !this.puedeLeer(respuesta.detalle) ||
          respuesta.detalle.revisionId !== this.id ||
          respuesta.listado.revisionId !== this.id ||
          respuesta.listado.observaciones.some((o) => o.revisionId !== this.id)
        ) {
          this.limpiarPrivados();
          this.estadoHttp.set(404);
          this.error.set(this.i18n.translate('revision.detalle.errores.404'));
          return;
        }
        this.detalle.set(respuesta.detalle);
        this.listado.set(respuesta.listado);
      });
  }
  actualizarRevision(): void {
    this.editor.set(null);
    this.aEliminar.set(null);
    this.refrescar.next();
  }
  anclajeDisponible(anclaje: AnclajeFormulario): boolean {
    if (!anclaje.instanciaElementoId) return true;
    let dato: unknown = this.detalle()?.datos;
    const segmentos = anclaje.elementoRef
      .slice(1)
      .split('/')
      .map((s) => s.replace(/~1/g, '/').replace(/~0/g, '~'));
    let encontrada = false;
    const resolverInstancia = (lista: unknown[]): unknown => {
      if (encontrada) return undefined;
      const coincidencias = lista.filter(
        (item) =>
          typeof item === 'object' &&
          item !== null &&
          (item as Record<string, unknown>)['instancia_id'] === anclaje.instanciaElementoId,
      );
      if (coincidencias.length !== 1) return undefined;
      encontrada = true;
      return coincidencias[0];
    };
    for (const segmento of segmentos) {
      if (Array.isArray(dato)) dato = resolverInstancia(dato);
      if (!dato || typeof dato !== 'object') return false;
      dato = (dato as Record<string, unknown>)[segmento];
    }
    if (Array.isArray(dato) && !encontrada) resolverInstancia(dato);
    return encontrada;
  }
  abrirPanel(): void {
    this.panelAbierto.set(true);
  }
  seleccionarElemento(destino: DestinoElementoObservacion): void {
    this.abrirEditor({ modo: 'CREAR', destino });
  }
  observarCategoria(categoriaFotografica: CategoriaFoto): void {
    this.abrirEditor({
      modo: 'CREAR',
      destino: {
        tipo: 'CATEGORIA_FOTOGRAFICA',
        categoriaFotografica,
        etiqueta: this.i18n.translate('inspecciones.fotos.categorias.' + categoriaFotografica),
      },
    });
  }
  editar(observacion: ObservacionPresentacion): void {
    this.abrirEditor({ modo: 'EDITAR', observacion });
  }
  editarGeneral(): void {
    const texto = this.listado()?.general.texto;
    this.abrirEditor(
      texto === null || texto === undefined
        ? { modo: 'CREAR', destino: { tipo: 'GENERAL' } }
        : {
            modo: 'EDITAR',
            observacion: { id: this.id, texto, destino: { tipo: 'GENERAL' }, estado: null },
          },
    );
  }
  private abrirEditor(contexto: ContextoEditorObservacion): void {
    if (!this.puedeEscribir()) return;
    this.errorEditor.set(null);
    this.error.set(null);
    this.editor.set(contexto);
  }
  guardar(intencion: PreparacionObservacion): void {
    if (!this.puedeEscribir() || !this.editor()) return;
    const d = intencion.destino;
    let operacion: Observable<unknown>;
    if (d.tipo === 'GENERAL') operacion = this.api.guardarGeneral(this.id, intencion.texto);
    else if (intencion.modo === 'EDITAR')
      operacion = this.api.editarObservacion(this.id, intencion.id, intencion.texto);
    else if (d.tipo === 'ELEMENTO') {
      const a = crearAnclajeFormulario(d.anclaje.elementoRef, d.anclaje.instanciaElementoId);
      operacion = this.api.crearObservacion(this.id, {
        texto: intencion.texto,
        destino: {
          tipo: 'ELEMENTO',
          elementoRef: a.elementoRef,
          instanciaElementoId: a.instanciaElementoId ?? null,
          categoriaFotografica: null,
        },
      });
    } else if (d.tipo === 'CATEGORIA_FOTOGRAFICA')
      operacion = this.api.crearObservacion(this.id, {
        texto: intencion.texto,
        destino: {
          tipo: d.tipo,
          elementoRef: null,
          instanciaElementoId: null,
          categoriaFotografica: d.categoriaFotografica,
        },
      });
    else return;
    this.escribir(operacion);
  }
  solicitarEliminacion(observacion: ObservacionPresentacion | 'GENERAL'): void {
    if (!this.puedeEscribir()) return;
    this.error.set(null);
    this.aEliminar.set(observacion);
  }
  eliminar(): void {
    const o = this.aEliminar();
    if (!o || !this.puedeEscribir()) return;
    this.escribir(
      o === 'GENERAL'
        ? this.api.guardarGeneral(this.id, null)
        : this.api.eliminarObservacion(this.id, o.id),
    );
  }
  private escribir(operacion: Observable<unknown>): void {
    this.ocupado.set(true);
    this.errorEditor.set(null);
    this.error.set(null);
    operacion
      .pipe(
        switchMap(() => this.api.observaciones(this.id)),
        takeUntil(this.cancelarEscritura),
        takeUntilDestroyed(this.destruir),
        finalize(() => this.ocupado.set(false)),
      )
      .subscribe({
        next: (listado) => {
          this.listado.set(listado);
          this.editor.set(null);
          this.aEliminar.set(null);
        },
        error: (e: HttpErrorResponse) => {
          this.manejarError(e);
          if (e.status === 409) {
            this.bloqueada.set(true);
            this.editor.set(null);
            this.aEliminar.set(null);
            this.refrescar.next();
          }
        },
      });
  }
  private manejarError(e: HttpErrorResponse): void {
    this.estadoHttp.set(e.status);
    const detalle = (e.error as ProblemDetails | null)?.detail;
    const campos = (e.error as ProblemDetails | null)?.errores ?? [];
    this.error.set(
      detalle ??
        this.i18n.translate(
          'revision.detalle.errores.' +
            ([401, 403, 404, 409, 422].includes(e.status) ? e.status : 'otro'),
        ),
    );
    this.errorEditor.set(
      campos.map((c) => `${c.campo ?? ''}: ${c.mensaje ?? ''}`).join(' · ') || this.error(),
    );
    if ([401, 403, 404].includes(e.status)) {
      this.limpiarPrivados();
      this.bloqueada.set(true);
    }
    if (e.status !== 422) this.apiErrors.normalize(e);
  }
  private limpiarPrivados(): void {
    this.detalle.set(null);
    this.listado.set(null);
    this.editor.set(null);
    this.aEliminar.set(null);
    this.panelAbierto.set(false);
    this.destinoNavegacion.set(null);
    this.navegacionPendiente = null;
  }
  private presentar(o: ObservacionResponse): ObservacionPresentacion {
    const d = o.destino;
    const destino =
      d?.tipo === 'ELEMENTO'
        ? {
            tipo: 'ELEMENTO' as const,
            etiqueta: d.elementoRef + (d.instanciaElementoId ? ' · ' + d.instanciaElementoId : ''),
            anclaje: crearAnclajeFormulario(d.elementoRef, d.instanciaElementoId ?? undefined),
          }
        : d?.tipo === 'CATEGORIA_FOTOGRAFICA'
          ? {
              tipo: 'CATEGORIA_FOTOGRAFICA' as const,
              categoriaFotografica: d.categoriaFotografica,
              etiqueta: this.i18n.translate(
                'inspecciones.fotos.categorias.' + d.categoriaFotografica,
              ),
            }
          : {
              tipo: 'SIN_ANCLAJE' as const,
              etiqueta: this.i18n.translate('revision.detalle.sinAnclaje'),
            };
    return { id: o.id, texto: o.texto, estado: o.estado, destino };
  }
  navegar(destino: DestinoNavegableObservacion): void {
    if (this.panelAbierto()) {
      this.navegacionPendiente = destino;
      this.panelAbierto.set(false);
    } else this.enfocarDestino(destino);
  }
  panelCerrado(): void {
    const destino = this.navegacionPendiente;
    this.navegacionPendiente = null;
    if (destino) this.enfocarDestino(destino);
  }
  private enfocarDestino(destino: DestinoNavegableObservacion): void {
    if (destino.tipo === 'ELEMENTO') {
      this.destinoNavegacion.set({ ...destino });
      if (!this.formulario()?.mostrarAnclaje(destino.anclaje.elementoRef)) return;
    }
    afterNextRender(
      () => {
        const elementos = this.host.nativeElement.querySelectorAll<HTMLElement>(
          destino.tipo === 'ELEMENTO' ? '[data-elemento-ref]' : '[data-categoria-fotografica]',
        );
        const objetivo = Array.from(elementos).find((el) =>
          destino.tipo === 'ELEMENTO'
            ? el.dataset['elementoRef'] === destino.anclaje.elementoRef &&
              el.dataset['instanciaId'] === (destino.anclaje.instanciaElementoId ?? '')
            : el.dataset['categoriaFotografica'] === destino.categoriaFotografica,
        );
        if (!objetivo) return;
        let padre = objetivo.parentElement;
        while (padre && padre !== this.host.nativeElement) {
          if (padre instanceof HTMLDetailsElement) padre.open = true;
          padre = padre.parentElement;
        }
        objetivo.scrollIntoView?.({ block: 'center' });
        objetivo.focus();
      },
      { injector: this.injector },
    );
  }
}
