import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import {
  AnclaConTotal,
  AnclaForo,
  HiloInspeccionForo,
  OpcionesFiltroForo,
} from '../../models/foro.models';
import { ForoApiService } from '../../services/foro-api.service';
import { etiquetaPorOmision } from '../../utils/etiqueta-ancla';
import { HiloForoComponent } from '../hilo-foro/hilo-foro.component';

type Estado = 'cargando' | 'listo' | 'sinHilo' | 'error';

/**
 * Foro de una inspección publicada (HU-064, RN-FOR-02, RN-FOR-03). Quien tiene sesión abre el hilo
 * si todavía no existe (las inspecciones publicadas antes de esta función no lo tienen); un
 * visitante solo lo consulta y nunca lo crea (RN-FOR-09). Informa los totales por elemento para
 * que el formulario dibuje sus marcadores, y con esos mismos totales ofrece filtrar los comentarios
 * por sección o ver solo los generales, que es lo que hace manejable un hilo largo.
 */
@Component({
  selector: 'app-foro-inspeccion',
  standalone: true,
  imports: [HiloForoComponent, RouterLink, TranslocoPipe, ButtonModule, SkeletonModule],
  templateUrl: './foro-inspeccion.component.html',
  styleUrl: './foro-inspeccion.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForoInspeccionComponent {
  private readonly api = inject(ForoApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly auth = inject(AuthStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly hilo = viewChild<{ enfocarFormulario(): void }>('hilo');

  readonly inspeccionId = input.required<string>();
  /** Elemento sobre el que se ven y se dejan los comentarios; nulo para todo el hilo. */
  readonly ancla = input<AnclaForo | null>(null);
  /** Comentario que se deja a la vista, por ejemplo al llegar desde un aviso (HU-066). */
  readonly comentarioDestacado = input<string | null>(null);
  readonly etiquetaDeAncla = input<(ancla: AnclaForo) => string>(etiquetaPorOmision);
  /** Quien lo usa decide qué hacer al dejar de filtrar por un elemento. */
  readonly anclaQuitada = output<void>();
  /** Quien lo usa lleva la vista al campo o elemento del formulario al que se refiere el ancla. */
  readonly irAElemento = output<AnclaForo>();
  /** Cuántos comentarios tiene cada elemento; cambia al abrir el foro y al comentar. */
  readonly anclasCambian = output<AnclaConTotal[]>();

  readonly autenticado = this.auth.authenticated;
  readonly hiloId = signal<string | null>(null);
  readonly estado = signal<Estado>('cargando');
  private readonly anclas = signal<readonly AnclaConTotal[]>([]);
  private readonly totalComentarios = signal(0);

  /** Cuántos comentarios tiene cada sección del formulario, y cuántos son generales. */
  readonly filtros = computed<OpcionesFiltroForo>(() => {
    const porSeccion = new Map<string, number>();
    let anclados = 0;
    for (const ancla of this.anclas()) {
      const clave = ancla.elementoRef.split('/')[1];
      if (!clave) continue;
      porSeccion.set(clave, (porSeccion.get(clave) ?? 0) + ancla.total);
      anclados += ancla.total;
    }
    const etiqueta = this.etiquetaDeAncla();
    const secciones = [...porSeccion].map(([clave, total]) => ({
      clave,
      total,
      titulo: etiqueta({ elementoRef: '/' + clave, instanciaElementoId: null }),
    }));
    // «1. …», «2. …», «10. …»: por su número, como están en el formulario.
    secciones.sort((a, b) => a.titulo.localeCompare(b.titulo, 'es', { numeric: true }));
    const todos = this.totalComentarios();
    return { todos, generales: Math.max(0, todos - anclados), secciones };
  });

  constructor() {
    effect(() => {
      this.inspeccionId();
      untracked(() => this.cargar());
    });
  }

  cargar(): void {
    this.estado.set('cargando');
    const id = this.inspeccionId();
    const peticion = this.autenticado()
      ? this.api.abrirHiloDeInspeccion(id)
      : this.api.obtenerHiloDeInspeccion(id);
    peticion.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (respuesta) => {
        this.hiloId.set(respuesta.hilo.id);
        this.recordar(respuesta);
        this.anclasCambian.emit(respuesta.anclas);
        this.estado.set('listo');
      },
      error: (err: HttpErrorResponse) => {
        // El error se explica aquí mismo; se evita duplicarlo en el aviso global.
        this.apiErrors.clear();
        this.estado.set(err.status === 404 && !this.autenticado() ? 'sinHilo' : 'error');
      },
    });
  }

  /** Tras comentar cambian los totales de los marcadores; si no se pueden leer, se conservan. */
  actualizarAnclas(): void {
    this.api
      .obtenerHiloDeInspeccion(this.inspeccionId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (respuesta) => {
          this.recordar(respuesta);
          this.anclasCambian.emit(respuesta.anclas);
        },
        error: () => this.apiErrors.clear(),
      });
  }

  private recordar(respuesta: HiloInspeccionForo): void {
    this.anclas.set(respuesta.anclas);
    this.totalComentarios.set(respuesta.hilo.totalComentarios);
  }

  /** Lleva el foco al formulario de comentario, por ejemplo tras elegir un elemento. */
  enfocarFormulario(): void {
    this.hilo()?.enfocarFormulario();
  }
}
