import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SkeletonModule } from 'primeng/skeleton';
import { finalize } from 'rxjs';
import { PaginaResponse } from '../../../puentes/models/puente.models';
import { InspeccionSinRevision } from '../../models/foro.models';
import { ForoApiService } from '../../services/foro-api.service';
import { ZONA_GUATEMALA } from '../../utils/fechas-foro';

/**
 * Moderación posterior (HU-065, RN-REV-02): las inspecciones que un Catedrático o un Profesional
 * externo publicó sin revisión previa, de la más reciente a la más antigua, con acceso directo a la
 * inspección publicada y su foro. Solo para el Catedrático y el Administrador.
 */
@Component({
  selector: 'app-moderacion-foro',
  standalone: true,
  imports: [DatePipe, RouterLink, TranslocoPipe, ButtonModule, PaginatorModule, SkeletonModule],
  templateUrl: './moderacion.page.html',
  styleUrl: './moderacion.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModeracionForoPage {
  private readonly api = inject(ForoApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly zona = ZONA_GUATEMALA;
  readonly pagina = signal<PaginaResponse<InspeccionSinRevision> | null>(null);
  readonly cargando = signal(false);
  readonly error = signal(false);
  private readonly tamanio = signal(20);

  constructor() {
    this.cargar(0);
  }

  cargar(numero = this.pagina()?.number ?? 0): void {
    this.cargando.set(true);
    this.error.set(false);
    this.api
      .listarInspeccionesSinRevision(numero, this.tamanio())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (pagina) => this.pagina.set(pagina),
        error: () => this.error.set(true),
      });
  }

  cambiarPagina(evento: PaginatorState): void {
    if (evento.page === undefined || evento.rows === undefined) return;
    this.tamanio.set(evento.rows);
    this.cargar(evento.page);
  }
}
