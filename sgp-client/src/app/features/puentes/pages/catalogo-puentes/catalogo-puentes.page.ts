import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { finalize } from 'rxjs';
import { PaginaResponse, PuenteCatalogoResponse } from '../../models/puente.models';
import { PuenteApiService } from '../../services/puente-api.service';

@Component({
  selector: 'app-catalogo-puentes-page',
  standalone: true,
  imports: [TranslocoPipe, ButtonModule, RouterLink],
  templateUrl: './catalogo-puentes.page.html',
  styleUrl: './catalogo-puentes.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogoPuentesPage {
  private readonly api = inject(PuenteApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly vista = signal<'lista' | 'mapa'>('lista');
  readonly pagina = signal<PaginaResponse<PuenteCatalogoResponse> | null>(null);
  readonly cargando = signal(false);
  readonly errorConsulta = signal(false);

  constructor() {
    this.cargarCatalogo();
  }

  cargarCatalogo(): void {
    if (this.cargando()) return;
    this.cargando.set(true);
    this.errorConsulta.set(false);
    this.api
      .listarCatalogo()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.cargando.set(false)),
      )
      .subscribe({
        next: (pagina) => this.pagina.set(pagina),
        error: () => this.errorConsulta.set(true),
      });
  }
}
