import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { finalize, forkJoin } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { PaginaResponse } from '../../../puentes/models/puente.models';
import {
  AnclaForo,
  ComentarioForo,
  FiltroSeccion,
  HiloForo,
  LIMITE_COMENTARIO,
  LIMITE_MOTIVO_MODERACION,
  OpcionesFiltroForo,
  ROLES_MODERADORES,
} from '../../models/foro.models';
import { ForoApiService } from '../../services/foro-api.service';
import { etiquetaPorOmision } from '../../utils/etiqueta-ancla';
import { leerProblema, severidadCategoria } from '../../utils/errores-foro';
import { ZONA_GUATEMALA } from '../../utils/fechas-foro';

/** Eliminar no pide motivo; ocultar y cerrar sí (RN-FOR-05, RN-FOR-08). */
type AccionPendiente =
  | { tipo: 'eliminar' | 'ocultar'; comentario: ComentarioForo }
  | { tipo: 'cerrar'; comentario: null };

/** Comentarios que se piden por página; es el máximo que admite el servidor. */
const TAMANIO_COMENTARIOS = 100;
/** Comentarios principales que se muestran de una vez; «Mostrar más» enseña otros tantos. */
const COMENTARIOS_POR_VISTA = 10;
/** Un comentario más largo que esto, o con más líneas, se recorta con «Ver más». */
const LARGO_PARA_RECORTAR = 280;
const LINEAS_PARA_RECORTAR = 4;

/**
 * Un hilo con sus comentarios (HU-063): cada comentario principal con sus respuestas, a un solo
 * nivel (RN-FOR-04). Cualquiera lo lee; comentar y responder exige sesión (RN-FOR-09).
 *
 * En el hilo de una inspección (HU-064) puede recibir un `ancla`: entonces lista solo los
 * comentarios de ese campo o elemento y los comentarios nuevos quedan anclados a él (RN-FOR-02).
 * También puede recibir `filtros` para elegir entre todos, los generales o una sección, y poner
 * el cuadro para comentar arriba (`compositorArriba`) para no recorrer la lista antes de escribir.
 *
 * Para que un hilo largo no ocupe la página, muestra los comentarios de a diez («Mostrar más») y
 * recorta los muy largos («Ver más»).
 *
 * El autor edita su comentario dentro del plazo que fija el servidor y lo elimina dejando la marca;
 * el Catedrático y el Administrador ocultan comentarios y cierran el hilo con un motivo (HU-065).
 * Lo irreversible se confirma en un diálogo.
 */
@Component({
  selector: 'app-hilo-foro',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    NgTemplateOutlet,
    RouterLink,
    TranslocoPipe,
    ButtonModule,
    DialogModule,
    SkeletonModule,
    TagModule,
    TextareaModule,
  ],
  templateUrl: './hilo-foro.component.html',
  styleUrl: './hilo-foro.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HiloForoComponent {
  private readonly api = inject(ForoApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly transloco = inject(TranslocoService);
  private readonly auth = inject(AuthStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly hiloId = input.required<string>();
  readonly ancla = input<AnclaForo | null>(null);
  readonly etiquetaDeAncla = input<(ancla: AnclaForo) => string>(etiquetaPorOmision);
  readonly mostrarVolver = input(true);
  /** Cuántos comentarios tiene cada sección; con ello se ofrece el selector de sección. */
  readonly filtros = input<OpcionesFiltroForo | null>(null);
  /**
   * Comentario que se deja a la vista y con el foco, por ejemplo al llegar desde el aviso de una
   * mención (HU-066). Si está más allá de lo que se muestra o de lo cargado, se busca.
   */
  readonly comentarioDestacado = input<string | null>(null);
  /** Pone el cuadro para comentar antes de la lista en lugar de después. */
  readonly compositorArriba = input(false);
  readonly volver = output<void>();
  /** Quien lo usa decide qué hacer al dejar de filtrar por un elemento. */
  readonly anclaQuitada = output<void>();
  /** Se publicó un comentario o una respuesta: cambian los totales de los marcadores. */
  readonly comentarioPublicado = output<void>();
  /** Quien lo usa lleva la vista al campo o elemento del formulario al que se refiere el ancla. */
  readonly irAElemento = output<AnclaForo>();

  readonly zona = ZONA_GUATEMALA;
  readonly limite = LIMITE_COMENTARIO;
  readonly limiteMotivo = LIMITE_MOTIVO_MODERACION;
  readonly severidad = severidadCategoria;
  readonly autenticado = this.auth.authenticated;
  private readonly usuarioId = computed(() => this.auth.user()?.id ?? null);
  readonly esModerador = computed(() => ROLES_MODERADORES.includes(this.auth.user()?.role ?? ''));

  readonly hilo = signal<HiloForo | null>(null);
  readonly comentarios = signal<ComentarioForo[]>([]);
  readonly ultimaPagina = signal<PaginaResponse<ComentarioForo> | null>(null);
  readonly cargando = signal(false);
  readonly cargandoMas = signal(false);
  readonly error = signal(false);

  /** `todos`, `generales` o `seccion:<clave>`. */
  readonly filtro = signal('todos');
  /** Comentarios principales a la vista; el resto espera a «Mostrar más». */
  readonly visibles = signal(COMENTARIOS_POR_VISTA);
  private readonly textosAbiertos = signal<ReadonlySet<string>>(new Set());
  /** El comentario destacado una vez encontrado; se resalta en la lista. */
  readonly destacadoId = signal<string | null>(null);
  /** El comentario destacado que todavía no se encontró en lo cargado. */
  private destacadoPendiente: string | null = null;
  /** El último comentario principal que publicó quien lo usa, para llevarlo a verlo. */
  readonly ultimoPublicadoId = signal<string | null>(null);

  readonly texto = signal('');
  readonly publicando = signal(false);
  readonly errorPublicar = signal<string | null>(null);
  readonly publicado = signal(false);

  readonly respondiendoA = signal<string | null>(null);
  readonly textoRespuesta = signal('');
  readonly publicandoRespuesta = signal(false);
  readonly errorRespuesta = signal<string | null>(null);

  readonly editandoId = signal<string | null>(null);
  readonly textoEdicion = signal('');
  readonly guardandoEdicion = signal(false);
  readonly errorEdicion = signal<string | null>(null);

  /** La acción irreversible que espera confirmación en el diálogo. */
  readonly accion = signal<AccionPendiente | null>(null);
  readonly motivo = signal('');
  readonly procesandoAccion = signal(false);
  readonly errorAccion = signal<string | null>(null);
  readonly pideMotivo = computed(() => this.accion()?.tipo !== 'eliminar');

  readonly principales = computed(() => this.comentarios().filter((c) => c.padreId === null));
  readonly respuestasPorPadre = computed(() => {
    const respuestas = new Map<string, ComentarioForo[]>();
    for (const comentario of this.comentarios()) {
      if (comentario.padreId === null) continue;
      const lista = respuestas.get(comentario.padreId) ?? [];
      lista.push(comentario);
      respuestas.set(comentario.padreId, lista);
    }
    return respuestas;
  });
  readonly hayMas = computed(() => {
    const pagina = this.ultimaPagina();
    return pagina !== null && !pagina.last;
  });
  readonly principalesVisibles = computed(() => this.principales().slice(0, this.visibles()));
  readonly restantes = computed(() => Math.max(0, this.principales().length - this.visibles()));
  readonly hayMasPorMostrar = computed(() => this.restantes() > 0 || this.hayMas());
  /** Comentarios a la vista contando las respuestas de los principales que se muestran. */
  readonly comentariosMostrados = computed(() => {
    const respuestas = this.respuestasPorPadre();
    return this.principalesVisibles().reduce(
      (total, c) => total + 1 + (respuestas.get(c.id)?.length ?? 0),
      0,
    );
  });
  /** El total solo se sabe cuando ya se cargó todo; si faltan páginas, no se inventa. */
  readonly totalConocido = computed(() => (this.hayMas() ? null : this.comentarios().length));
  readonly mostrarFiltro = computed(() => {
    const filtros = this.filtros();
    if (this.ancla() || filtros === null) return false;
    return filtros.secciones.length + (filtros.generales > 0 ? 1 : 0) >= 2;
  });
  readonly miComentarioOculto = computed(() => {
    const id = this.ultimoPublicadoId();
    return id !== null && !this.principalesVisibles().some((c) => c.id === id);
  });
  /** Replica la autorización del backend para mostrar los formularios; el servidor decide igual. */
  readonly puedeParticipar = computed(() => this.autenticado() && this.hilo()?.cerrado === false);

  constructor() {
    effect(() => {
      this.destacadoPendiente = this.comentarioDestacado();
    });
    effect(() => {
      this.hiloId();
      this.ancla();
      this.filtro();
      untracked(() => {
        this.visibles.set(COMENTARIOS_POR_VISTA);
        this.cargar();
      });
    });
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(false);
    forkJoin({
      hilo: this.api.obtenerHilo(this.hiloId()),
      pagina: this.api.listarComentarios(
        this.hiloId(),
        0,
        TAMANIO_COMENTARIOS,
        this.ancla(),
        this.filtroApi(),
      ),
    })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: ({ hilo, pagina }) => {
          this.hilo.set(hilo);
          this.comentarios.set(pagina.content);
          this.ultimaPagina.set(pagina);
          this.buscarDestacado();
        },
        error: () => this.error.set(true),
      });
  }

  cargarMas(): void {
    const actual = this.ultimaPagina();
    if (!actual || actual.last || this.cargandoMas()) return;
    this.cargandoMas.set(true);
    this.api
      .listarComentarios(
        this.hiloId(),
        actual.number + 1,
        TAMANIO_COMENTARIOS,
        this.ancla(),
        this.filtroApi(),
      )
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargandoMas.set(false)),
      )
      .subscribe((pagina) => {
        this.comentarios.update((lista) => [...lista, ...pagina.content]);
        this.ultimaPagina.set(pagina);
        this.visibles.update((visibles) => visibles + COMENTARIOS_POR_VISTA);
        this.buscarDestacado();
      });
  }

  publicar(): void {
    const contenido = this.texto().trim();
    if (!contenido || this.publicando()) return;
    this.publicando.set(true);
    this.errorPublicar.set(null);
    this.publicado.set(false);
    const ancla = this.ancla();
    this.api
      .crearComentario(this.hiloId(), {
        contenido,
        padreId: null,
        elementoRef: ancla?.elementoRef ?? null,
        instanciaElementoId: ancla?.instanciaElementoId ?? null,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.publicando.set(false)),
      )
      .subscribe({
        next: (nuevo) => {
          this.texto.set('');
          this.publicado.set(true);
          this.ultimoPublicadoId.set(nuevo.id);
          // Un comentario general no entra en el filtro de una sección: se muestra todo. Al cambiar
          // el filtro, la lista se recarga sola.
          if (!ancla && this.filtro() !== 'todos') this.filtro.set('todos');
          else this.cargar();
          this.comentarioPublicado.emit();
        },
        error: (err: HttpErrorResponse) => this.errorPublicar.set(this.mensajeDe(err)),
      });
  }

  /** El filtro de sección elegido, en la forma que entiende el servidor; con un ancla no aplica. */
  private filtroApi(): FiltroSeccion | null {
    if (this.ancla()) return null;
    const filtro = this.filtro();
    if (filtro === 'generales') return { soloGenerales: true };
    return filtro.startsWith('seccion:') ? { seccion: filtro.slice('seccion:'.length) } : null;
  }

  /** Enseña otros diez comentarios; si ya se ven todos los cargados, pide la página siguiente. */
  mostrarMas(): void {
    if (this.restantes() > 0) {
      const primero = this.visibles();
      this.visibles.update((visibles) => visibles + COMENTARIOS_POR_VISTA);
      // El foco pasa al primer comentario nuevo para quien navega con teclado.
      this.enfocar(`article.comentario[data-posicion="${primero}"]`);
    } else {
      this.cargarMas();
    }
  }

  /**
   * Deja a la vista el comentario destacado: muestra su comentario principal aunque la paginación
   * lo esconda y le pasa el foco. Si aún no está cargado, pide la página siguiente.
   */
  private buscarDestacado(): void {
    const id = this.destacadoPendiente;
    if (id === null) return;
    const comentario = this.comentarios().find((c) => c.id === id);
    if (!comentario) {
      if (this.hayMas()) this.cargarMas();
      else this.destacadoPendiente = null;
      return;
    }
    this.destacadoPendiente = null;
    const principalId = comentario.padreId ?? comentario.id;
    const posicion = this.principales().findIndex((c) => c.id === principalId);
    if (posicion >= this.visibles()) this.visibles.set(posicion + 1);
    this.destacadoId.set(id);
    this.enfocar(`article[data-comentario-id="${id}"]`);
  }

  /** Lleva a quien acaba de comentar a su comentario, aunque la paginación lo esconda. */
  verMiComentario(): void {
    const id = this.ultimoPublicadoId();
    if (id === null) return;
    this.visibles.set(Math.max(this.visibles(), this.principales().length));
    this.enfocar(`article.comentario[data-comentario-id="${id}"]`);
  }

  /** Quita el filtro por elemento y por sección: vuelve a todos los comentarios. */
  verTodos(): void {
    this.filtro.set('todos');
    this.anclaQuitada.emit();
  }

  esLargo(comentario: ComentarioForo): boolean {
    const contenido = comentario.contenido ?? '';
    return (
      contenido.length > LARGO_PARA_RECORTAR || contenido.split('\n').length > LINEAS_PARA_RECORTAR
    );
  }

  estaRecortado(comentario: ComentarioForo): boolean {
    return this.esLargo(comentario) && !this.textosAbiertos().has(comentario.id);
  }

  alternarTexto(comentarioId: string): void {
    this.textosAbiertos.update((abiertos) => {
      const siguientes = new Set(abiertos);
      if (!siguientes.delete(comentarioId)) siguientes.add(comentarioId);
      return siguientes;
    });
  }

  /** El ancla de un comentario anclado, para llevar la vista al elemento del formulario. */
  anclaDe(comentario: ComentarioForo): AnclaForo {
    return {
      elementoRef: comentario.elementoRef ?? '',
      instanciaElementoId: comentario.instanciaElementoId,
    };
  }

  /**
   * Abandona el comentario sobre el elemento elegido: descarta lo escrito, deja de filtrar por el
   * elemento y deja el foco en el cuadro, que sigue disponible para un comentario general.
   */
  cancelarComentarioAnclado(): void {
    if (this.publicando()) return;
    this.texto.set('');
    this.errorPublicar.set(null);
    this.publicado.set(false);
    this.anclaQuitada.emit();
    this.enfocarFormulario();
  }

  /** Nombre legible del elemento al que está anclado un comentario. */
  etiquetaDe(comentario: ComentarioForo): string {
    return this.etiquetaDeAncla()({
      elementoRef: comentario.elementoRef ?? '',
      instanciaElementoId: comentario.instanciaElementoId,
    });
  }

  /** Lleva el foco al formulario de comentario nuevo, por ejemplo tras elegir un elemento. */
  enfocarFormulario(): void {
    this.enfocar('form:not(.respuesta-formulario) textarea');
  }

  abrirRespuesta(comentarioId: string): void {
    this.respondiendoA.set(comentarioId);
    this.textoRespuesta.set('');
    this.errorRespuesta.set(null);
    this.enfocar('.respuesta-formulario textarea');
  }

  cancelarRespuesta(): void {
    if (this.publicandoRespuesta()) return;
    const comentarioId = this.respondiendoA();
    this.respondiendoA.set(null);
    this.enfocar(`button[data-comentario="${comentarioId}"]`);
  }

  responder(): void {
    const padreId = this.respondiendoA();
    const contenido = this.textoRespuesta().trim();
    if (!padreId || !contenido || this.publicandoRespuesta()) return;
    this.publicandoRespuesta.set(true);
    this.errorRespuesta.set(null);
    this.api
      .crearComentario(this.hiloId(), { contenido, padreId })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.publicandoRespuesta.set(false)),
      )
      .subscribe({
        next: () => {
          this.respondiendoA.set(null);
          this.textoRespuesta.set('');
          this.cargar();
          this.comentarioPublicado.emit();
        },
        error: (err: HttpErrorResponse) => this.errorRespuesta.set(this.mensajeDe(err)),
      });
  }

  private esAutor(comentario: ComentarioForo): boolean {
    return this.autenticado() && comentario.autorId === this.usuarioId();
  }

  /** Modificable por su autor: no eliminado, no oculto y en un hilo abierto. */
  private modificable(comentario: ComentarioForo): boolean {
    return (
      this.esAutor(comentario) &&
      !comentario.eliminado &&
      !comentario.oculto &&
      this.hilo()?.cerrado === false
    );
  }

  /** El plazo lo decide el servidor; aquí solo se evita ofrecer lo que ya venció. */
  puedeEditar(comentario: ComentarioForo): boolean {
    return this.modificable(comentario) && Date.now() <= Date.parse(comentario.editableHasta);
  }

  puedeEliminar(comentario: ComentarioForo): boolean {
    return this.modificable(comentario);
  }

  puedeOcultar(comentario: ComentarioForo): boolean {
    return this.esModerador() && !comentario.eliminado && !comentario.oculto;
  }

  abrirEdicion(comentario: ComentarioForo): void {
    this.editandoId.set(comentario.id);
    this.textoEdicion.set(comentario.contenido ?? '');
    this.errorEdicion.set(null);
    this.enfocar('.edicion-formulario textarea');
  }

  cancelarEdicion(): void {
    if (this.guardandoEdicion()) return;
    const comentarioId = this.editandoId();
    this.editandoId.set(null);
    this.enfocar(`article[data-comentario-id="${comentarioId}"]`);
  }

  guardarEdicion(): void {
    const comentarioId = this.editandoId();
    const contenido = this.textoEdicion().trim();
    if (!comentarioId || !contenido || this.guardandoEdicion()) return;
    this.guardandoEdicion.set(true);
    this.errorEdicion.set(null);
    this.api
      .editarComentario(comentarioId, contenido)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardandoEdicion.set(false)),
      )
      .subscribe({
        next: (editado) => {
          this.reemplazar(editado);
          this.editandoId.set(null);
        },
        error: (err: HttpErrorResponse) => this.errorEdicion.set(this.mensajeDe(err)),
      });
  }

  pedirEliminar(comentario: ComentarioForo): void {
    this.abrirAccion({ tipo: 'eliminar', comentario });
  }

  pedirOcultar(comentario: ComentarioForo): void {
    this.abrirAccion({ tipo: 'ocultar', comentario });
  }

  pedirCerrar(): void {
    this.abrirAccion({ tipo: 'cerrar', comentario: null });
  }

  cancelarAccion(): void {
    if (this.procesandoAccion()) return;
    this.accion.set(null);
  }

  confirmarAccion(): void {
    const accion = this.accion();
    const motivo = this.motivo().trim();
    if (!accion || this.procesandoAccion() || (this.pideMotivo() && !motivo)) return;
    this.procesandoAccion.set(true);
    this.errorAccion.set(null);
    const error = (err: HttpErrorResponse) => {
      this.apiErrors.clear();
      const problema = leerProblema(err);
      this.errorAccion.set(
        problema.campos['motivo'] ??
          problema.mensaje ??
          this.transloco.translate('foro.moderacion.error'),
      );
    };
    if (accion.tipo === 'cerrar') {
      this.api
        .cerrarHilo(this.hiloId(), motivo)
        .pipe(
          takeUntilDestroyed(this.destroyRef),
          finalize(() => this.procesandoAccion.set(false)),
        )
        .subscribe({
          next: (hilo) => {
            this.hilo.set(hilo);
            this.accion.set(null);
          },
          error,
        });
      return;
    }
    const comentarioId = accion.comentario.id;
    const solicitud =
      accion.tipo === 'eliminar'
        ? this.api.eliminarComentario(comentarioId)
        : this.api.ocultarComentario(comentarioId, motivo);
    solicitud
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.procesandoAccion.set(false)),
      )
      .subscribe({
        next: (actualizado) => {
          this.reemplazar(actualizado);
          this.accion.set(null);
        },
        error,
      });
  }

  private abrirAccion(accion: AccionPendiente): void {
    this.accion.set(accion);
    this.motivo.set('');
    this.errorAccion.set(null);
  }

  /** Actualiza un comentario en la lista sin recargarla: no se pierde lo que ya se mostraba. */
  private reemplazar(actualizado: ComentarioForo): void {
    this.comentarios.update((lista) =>
      lista.map((c) => (c.id === actualizado.id ? actualizado : c)),
    );
  }

  /** Lleva el foco al control que acaba de aparecer, para quien navega con teclado. */
  private enfocar(selector: string): void {
    afterNextRender(
      () => this.anfitrion.nativeElement.querySelector<HTMLElement>(selector)?.focus(),
      { injector: this.injector },
    );
  }

  /** El error se muestra junto al formulario; se evita duplicarlo en el aviso global. */
  private mensajeDe(err: HttpErrorResponse): string {
    this.apiErrors.clear();
    const problema = leerProblema(err);
    if (problema.codigo === 'hilo_cerrado') {
      this.hilo.update((h) => (h ? { ...h, cerrado: true } : h));
    }
    if (problema.codigo === 'plazo_edicion_vencido') {
      // Pasado el plazo el comentario queda fijo: se deja de ofrecer la edición.
      const vencido = new Date(0).toISOString();
      this.comentarios.update((lista) =>
        lista.map((c) => (c.id === this.editandoId() ? { ...c, editableHasta: vencido } : c)),
      );
    }
    return (
      problema.campos['contenido'] ??
      problema.campos['padreId'] ??
      problema.campos['elementoRef'] ??
      problema.campos['instanciaElementoId'] ??
      problema.mensaje ??
      this.transloco.translate('foro.hilo.errorPublicar')
    );
  }
}
