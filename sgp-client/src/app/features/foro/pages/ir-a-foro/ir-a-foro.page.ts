import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { map, Observable, of, switchMap } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { HiloForo } from '../../models/foro.models';
import { ForoApiService } from '../../services/foro-api.service';

/**
 * Lleva desde un aviso del foro (HU-066) a donde ocurrió: al hilo de una inspección publicada o al
 * foro del puente, con el hilo abierto y el comentario a la vista. El aviso solo conoce el
 * comentario o el hilo, así que aquí se averigua dónde está y se reemplaza esta ruta por esa.
 */
@Component({
  selector: 'app-ir-a-foro',
  standalone: true,
  imports: [RouterLink, TranslocoPipe, ButtonModule],
  template: `
    @if (error()) {
      <section class="surface-card estado" role="alert">
        <i class="pi pi-exclamation-circle" aria-hidden="true"></i>
        <p>{{ 'foro.irA.error' | transloco }}</p>
        <a pButton severity="secondary" [outlined]="true" routerLink="/avisos">
          <i class="pi pi-bell" aria-hidden="true"></i
          ><span>{{ 'foro.irA.volver' | transloco }}</span>
        </a>
      </section>
    } @else {
      <p class="estado" role="status">{{ 'foro.irA.abriendo' | transloco }}</p>
    }
  `,
  styles: `
    .estado {
      display: grid;
      justify-items: center;
      gap: 0.75rem;
      padding: 2rem 1rem;
      color: var(--sgp-steel-700);
      text-align: center;
    }
    .estado p {
      margin: 0;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrAForoPage {
  private readonly api = inject(ForoApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly router = inject(Router);

  readonly error = signal(false);

  constructor() {
    const ruta = inject(ActivatedRoute).snapshot;
    const id = ruta.paramMap.get('id') ?? '';
    const destino: Observable<{ hilo: HiloForo; comentarioId: string | null }> =
      ruta.data['destino'] === 'comentario'
        ? this.api
            .obtenerComentario(id)
            .pipe(
              switchMap((comentario) =>
                this.api
                  .obtenerHilo(comentario.hiloId)
                  .pipe(map((hilo) => ({ hilo, comentarioId: comentario.id }))),
              ),
            )
        : this.api.obtenerHilo(id).pipe(switchMap((hilo) => of({ hilo, comentarioId: null })));

    destino.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe({
      next: ({ hilo, comentarioId }) => {
        const comentario = comentarioId ? { comentario: comentarioId } : {};
        void (hilo.inspeccionId
          ? this.router.navigate(['/inspecciones', hilo.inspeccionId, 'publicada'], {
              queryParams: comentario,
              replaceUrl: true,
            })
          : this.router.navigate(['/puentes', hilo.puenteId], {
              queryParams: { hilo: hilo.id, ...comentario },
              replaceUrl: true,
            }));
      },
      error: () => {
        // El motivo se explica aquí mismo; no se duplica en el aviso global.
        this.apiErrors.clear();
        this.error.set(true);
      },
    });
  }
}
