import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import {
  EstadoSolicitudAltaPuente,
  PaginaResponse,
  SolicitudAltaPuenteResponse,
} from '../../models/puente.models';
import { SolicitudAltaPuenteApiService } from '../../services/solicitud-alta-puente-api.service';

const TAMANIO_PAGINA = 10;

@Component({
  selector: 'app-mis-solicitudes-page',
  standalone: true,
  imports: [TranslocoPipe, RouterLink, ButtonModule, PaginatorModule, TableModule, TagModule],
  templateUrl: './mis-solicitudes.page.html',
  styleUrl: './mis-solicitudes.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MisSolicitudesPage {
  private readonly api = inject(SolicitudAltaPuenteApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly tamanioPagina = TAMANIO_PAGINA;
  readonly resultado = signal<PaginaResponse<SolicitudAltaPuenteResponse> | null>(null);
  readonly cargando = signal(false);
  readonly error = signal(false);

  private readonly severidades: Record<
    EstadoSolicitudAltaPuente,
    'success' | 'warn' | 'danger' | 'secondary'
  > = {
    PENDIENTE: 'warn',
    APROBADA: 'success',
    RECHAZADA: 'danger',
    CANCELADA: 'secondary',
  };

  constructor() {
    this.cargar(0);
  }

  cargar(pagina: number): void {
    this.cargando.set(true);
    this.error.set(false);

    this.api
      .listarMias(pagina, TAMANIO_PAGINA)
      .pipe(
        finalize(() => this.cargando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (resultado) => this.resultado.set(resultado),
        error: () => this.error.set(true),
      });
  }

  cambiarPagina(estado: PaginatorState): void {
    this.cargar(estado.page ?? 0);
  }

  severidad(estado: EstadoSolicitudAltaPuente): 'success' | 'warn' | 'danger' | 'secondary' {
    return this.severidades[estado];
  }

  fechaGuatemala(fecha: string): string {
    return new Intl.DateTimeFormat('es-GT', {
      timeZone: 'America/Guatemala',
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(fecha));
  }
}
