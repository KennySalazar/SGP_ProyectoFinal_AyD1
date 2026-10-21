import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
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
import { AnclaConTotal, AnclaForo } from '../../models/foro.models';
import { ForoApiService } from '../../services/foro-api.service';
import { etiquetaPorOmision } from '../../utils/etiqueta-ancla';
import { HiloForoComponent } from '../hilo-foro/hilo-foro.component';

type Estado = 'cargando' | 'listo' | 'sinHilo' | 'error';

/**
 * Foro de una inspección publicada (HU-064, RN-FOR-02, RN-FOR-03). Quien tiene sesión abre el hilo
 * si todavía no existe (las inspecciones publicadas antes de esta función no lo tienen); un
 * visitante solo lo consulta y nunca lo crea (RN-FOR-09). Informa los totales por elemento para
 * que el formulario dibuje sus marcadores.
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
        next: (respuesta) => this.anclasCambian.emit(respuesta.anclas),
        error: () => this.apiErrors.clear(),
      });
  }

  /** Lleva el foco al formulario de comentario, por ejemplo tras elegir un elemento. */
  enfocarFormulario(): void {
    this.hilo()?.enfocarFormulario();
  }
}
