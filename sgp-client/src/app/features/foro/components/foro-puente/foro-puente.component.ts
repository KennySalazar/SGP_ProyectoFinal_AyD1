import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { finalize, Subscription } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { PaginaResponse, PuenteResponse } from '../../../puentes/models/puente.models';
import {
  CATEGORIAS_HILO,
  CategoriaHilo,
  HiloForo,
  LIMITE_COMENTARIO,
  LIMITE_TITULO_HILO,
} from '../../models/foro.models';
import { ForoApiService } from '../../services/foro-api.service';
import { leerProblema, severidadCategoria } from '../../utils/errores-foro';
import { ZONA_GUATEMALA } from '../../utils/fechas-foro';
import { HiloForoComponent } from '../hilo-foro/hilo-foro.component';

const TAMANIO = 10;

/**
 * Foro permanente de un puente (HU-063, RN-FOR-01): hilos clasificados en cinco categorías, del de
 * actividad más reciente al más antiguo. Cualquiera puede leer (RN-FOR-09); abrir un hilo exige
 * sesión.
 */
@Component({
  selector: 'app-foro-puente',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    TranslocoPipe,
    ButtonModule,
    DialogModule,
    InputTextModule,
    PaginatorModule,
    SelectModule,
    SkeletonModule,
    TagModule,
    TextareaModule,
    HiloForoComponent,
  ],
  templateUrl: './foro-puente.component.html',
  styleUrl: './foro-puente.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForoPuenteComponent {
  private readonly api = inject(ForoApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly auth = inject(AuthStore);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private solicitud: Subscription | null = null;

  readonly puente = input.required<PuenteResponse>();

  readonly categorias = CATEGORIAS_HILO;
  readonly tamanio = TAMANIO;
  readonly zona = ZONA_GUATEMALA;
  readonly limiteTitulo = LIMITE_TITULO_HILO;
  readonly limiteComentario = LIMITE_COMENTARIO;
  readonly severidad = severidadCategoria;
  readonly autenticado = this.auth.authenticated;

  readonly pagina = signal<PaginaResponse<HiloForo> | null>(null);
  readonly cargando = signal(false);
  readonly error = signal(false);
  /** Hilo cuya conversación está abierta; si es nulo se muestra la lista. */
  readonly hiloAbierto = signal<string | null>(null);

  readonly creando = signal(false);
  readonly guardando = signal(false);
  readonly errorCrear = signal<string | null>(null);
  readonly erroresCampo = signal<Record<string, string>>({});
  readonly hiloCreado = signal(false);
  readonly nuevoTitulo = signal('');
  readonly nuevaCategoria = signal<CategoriaHilo | null>(null);
  readonly nuevoComentario = signal('');

  readonly formularioValido = computed(
    () =>
      !!this.nuevoTitulo().trim() &&
      this.nuevoTitulo().length <= this.limiteTitulo &&
      !!this.nuevaCategoria() &&
      !!this.nuevoComentario().trim(),
  );

  constructor() {
    effect(() => {
      this.puente();
      untracked(() => {
        this.hiloAbierto.set(null);
        this.cargar(0);
      });
    });
  }

  cargar(pagina: number): void {
    this.solicitud?.unsubscribe();
    this.cargando.set(true);
    this.error.set(false);
    this.solicitud = this.api
      .listarHilos(this.puente().id, pagina, TAMANIO)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (respuesta) => this.pagina.set(respuesta),
        error: () => this.error.set(true),
      });
  }

  cambiarPagina(evento: PaginatorState): void {
    if (evento.page !== undefined) this.cargar(evento.page);
  }

  abrirHilo(hiloId: string): void {
    this.hiloCreado.set(false);
    this.hiloAbierto.set(hiloId);
  }

  /** Al volver a la lista se recarga: la conversación pudo cambiar el total y el orden. */
  volverALista(): void {
    this.hiloAbierto.set(null);
    this.cargar(this.pagina()?.number ?? 0);
  }

  abrirNuevoHilo(): void {
    this.nuevoTitulo.set('');
    this.nuevaCategoria.set(null);
    this.nuevoComentario.set('');
    this.errorCrear.set(null);
    this.erroresCampo.set({});
    this.hiloCreado.set(false);
    this.creando.set(true);
  }

  cerrarNuevoHilo(): void {
    if (this.guardando()) return;
    this.creando.set(false);
  }

  crearHilo(): void {
    const categoria = this.nuevaCategoria();
    if (!categoria || !this.formularioValido() || this.guardando()) return;
    this.guardando.set(true);
    this.errorCrear.set(null);
    this.erroresCampo.set({});
    this.api
      .crearHilo(this.puente().id, {
        titulo: this.nuevoTitulo().trim(),
        categoria,
        comentario: this.nuevoComentario().trim(),
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.guardando.set(false)),
      )
      .subscribe({
        next: () => {
          this.creando.set(false);
          this.hiloCreado.set(true);
          this.cargar(0);
        },
        error: (err: HttpErrorResponse) => {
          // El error se muestra dentro del diálogo; se evita duplicarlo en el aviso global.
          this.apiErrors.clear();
          const problema = leerProblema(err);
          this.erroresCampo.set(problema.campos);
          this.errorCrear.set(
            problema.mensaje ??
              (Object.keys(problema.campos).length > 0
                ? null
                : this.transloco.translate('foro.nuevoHilo.error')),
          );
        },
      });
  }
}
