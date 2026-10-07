import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AsignacionPuenteResponse } from '../../models/puente.models';
import { PuenteApiService } from '../../services/puente-api.service';

@Component({
  selector: 'app-mis-puentes-asignados-page',
  standalone: true,
  imports: [TranslocoPipe, ButtonModule],
  templateUrl: './mis-puentes-asignados.page.html',
  styleUrl: './mis-puentes-asignados.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MisPuentesAsignadosPage {
  private readonly api = inject(PuenteApiService);
  private readonly apiErrors = inject(ApiErrorService);

  readonly puentes = signal<AsignacionPuenteResponse[]>([]);
  readonly cargando = signal(false);

  constructor() {
    this.cargar();
  }

  cargar(): void {
    if (this.cargando()) return;
    this.cargando.set(true);
    this.api
      .listarMisPuentesAsignados()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (puentes) => this.puentes.set(puentes),
        error: (error) => this.apiErrors.normalize(error),
      });
  }
}
