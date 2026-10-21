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
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { finalize, forkJoin } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { PaginaResponse } from '../../../puentes/models/puente.models';
import { AnclaForo, ComentarioForo, HiloForo, LIMITE_COMENTARIO } from '../../models/foro.models';
import { ForoApiService } from '../../services/foro-api.service';
import { etiquetaPorOmision } from '../../utils/etiqueta-ancla';
import { leerProblema, severidadCategoria } from '../../utils/errores-foro';
import { ZONA_GUATEMALA } from '../../utils/fechas-foro';

/** Comentarios que se piden por página; es el máximo que admite el servidor. */
const TAMANIO_COMENTARIOS = 100;

/**
 * Un hilo con sus comentarios (HU-063): cada comentario principal con sus respuestas, a un solo
 * nivel (RN-FOR-04). Cualquiera lo lee; comentar y responder exige sesión (RN-FOR-09).
 *
 * En el hilo de una inspección (HU-064) puede recibir un `ancla`: entonces lista solo los
 * comentarios de ese campo o elemento y los comentarios nuevos quedan anclados a él (RN-FOR-02).
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
  readonly volver = output<void>();
  /** Quien lo usa decide qué hacer al dejar de filtrar por un elemento. */
  readonly anclaQuitada = output<void>();
  /** Se publicó un comentario o una respuesta: cambian los totales de los marcadores. */
  readonly comentarioPublicado = output<void>();
  /** Quien lo usa lleva la vista al campo o elemento del formulario al que se refiere el ancla. */
  readonly irAElemento = output<AnclaForo>();

  readonly zona = ZONA_GUATEMALA;
  readonly limite = LIMITE_COMENTARIO;
  readonly severidad = severidadCategoria;
  readonly autenticado = this.auth.authenticated;

  readonly hilo = signal<HiloForo | null>(null);
  readonly comentarios = signal<ComentarioForo[]>([]);
  readonly ultimaPagina = signal<PaginaResponse<ComentarioForo> | null>(null);
  readonly cargando = signal(false);
  readonly cargandoMas = signal(false);
  readonly error = signal(false);

  readonly texto = signal('');
  readonly publicando = signal(false);
  readonly errorPublicar = signal<string | null>(null);
  readonly publicado = signal(false);

  readonly respondiendoA = signal<string | null>(null);
  readonly textoRespuesta = signal('');
  readonly publicandoRespuesta = signal(false);
  readonly errorRespuesta = signal<string | null>(null);

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
  /** Replica la autorización del backend para mostrar los formularios; el servidor decide igual. */
  readonly puedeParticipar = computed(() => this.autenticado() && this.hilo()?.cerrado === false);

  constructor() {
    effect(() => {
      this.hiloId();
      this.ancla();
      untracked(() => this.cargar());
    });
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(false);
    forkJoin({
      hilo: this.api.obtenerHilo(this.hiloId()),
      pagina: this.api.listarComentarios(this.hiloId(), 0, TAMANIO_COMENTARIOS, this.ancla()),
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
        },
        error: () => this.error.set(true),
      });
  }

  cargarMas(): void {
    const actual = this.ultimaPagina();
    if (!actual || actual.last || this.cargandoMas()) return;
    this.cargandoMas.set(true);
    this.api
      .listarComentarios(this.hiloId(), actual.number + 1, TAMANIO_COMENTARIOS, this.ancla())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargandoMas.set(false)),
      )
      .subscribe((pagina) => {
        this.comentarios.update((lista) => [...lista, ...pagina.content]);
        this.ultimaPagina.set(pagina);
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
        next: () => {
          this.texto.set('');
          this.publicado.set(true);
          this.cargar();
          this.comentarioPublicado.emit();
        },
        error: (err: HttpErrorResponse) => this.errorPublicar.set(this.mensajeDe(err)),
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
