import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { finalize } from 'rxjs';
import { SolicitudFichaComponent } from '../../components/solicitud-ficha/solicitud-ficha.component';
import { SolicitudAltaPuenteResponse } from '../../models/puente.models';
import { SolicitudAltaPuenteApiService } from '../../services/solicitud-alta-puente-api.service';

/** Detalle de una solicitud propia del Catedrático, con la misma ficha que usa el Administrador. */
@Component({
  selector: 'app-detalle-solicitud-page',
  standalone: true,
  imports: [TranslocoPipe, RouterLink, ButtonModule, SolicitudFichaComponent],
  templateUrl: './detalle-solicitud.page.html',
  styleUrl: './detalle-solicitud.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleSolicitudPage {
  private readonly api = inject(SolicitudAltaPuenteApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly transloco = inject(TranslocoService);

  /** Identificador de la ruta (`withComponentInputBinding`). */
  readonly id = input.required<string>();

  readonly solicitud = signal<SolicitudAltaPuenteResponse | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      this.id();
      this.cargar();
    });
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);

    this.api
      .obtenerMia(this.id())
      .pipe(
        finalize(() => this.cargando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (solicitud) => this.solicitud.set(solicitud),
        error: (error: HttpErrorResponse) => {
          this.solicitud.set(null);
          this.error.set(
            this.transloco.translate(
              error.status === 404
                ? 'puentes.solicitud.notFound'
                : 'puentes.solicitud.detailFailed',
            ),
          );
        },
      });
  }
}
