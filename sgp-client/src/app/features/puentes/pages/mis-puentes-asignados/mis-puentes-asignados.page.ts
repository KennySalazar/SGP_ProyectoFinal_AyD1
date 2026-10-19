import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { IniciarInspeccionDialogComponent } from '../../../inspecciones/components/iniciar-inspeccion-dialog/iniciar-inspeccion-dialog.component';
import { AsignacionPuenteResponse, PuenteResponse } from '../../models/puente.models';
import { PuenteApiService } from '../../services/puente-api.service';

@Component({
  selector: 'app-mis-puentes-asignados-page',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    TranslocoPipe,
    ButtonModule,
    InputTextModule,
    TagModule,
    IniciarInspeccionDialogComponent,
  ],
  templateUrl: './mis-puentes-asignados.page.html',
  styleUrl: './mis-puentes-asignados.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MisPuentesAsignadosPage {
  private readonly api = inject(PuenteApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly router = inject(Router);

  readonly puentes = signal<AsignacionPuenteResponse[]>([]);
  readonly busqueda = signal('');
  readonly puentesFiltrados = computed(() => {
    const termino = this.busqueda().trim().toLocaleLowerCase('es');
    if (!termino) return this.puentes();
    return this.puentes().filter((asignacion) =>
      `${asignacion.puenteCodigo} ${asignacion.puenteNombre}`
        .toLocaleLowerCase('es')
        .includes(termino),
    );
  });
  readonly cargando = signal(false);
  readonly cargandoPuente = signal<string | null>(null);
  readonly puenteSeleccionado = signal<PuenteResponse | null>(null);
  readonly mostrarInicio = signal(false);

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

  iniciar(asignacion: AsignacionPuenteResponse): void {
    if (this.cargandoPuente()) return;
    this.cargandoPuente.set(asignacion.puenteId);
    this.api
      .obtenerPorId(asignacion.puenteId)
      .pipe(finalize(() => this.cargandoPuente.set(null)))
      .subscribe({
        next: (puente) => {
          this.puenteSeleccionado.set(puente);
          this.mostrarInicio.set(true);
        },
        error: (error) => this.apiErrors.normalize(error),
      });
  }

  continuar(asignacion: AsignacionPuenteResponse): void {
    if (asignacion.inspeccionId) {
      void this.router.navigate(['/inspecciones', asignacion.inspeccionId, 'borrador']);
    }
  }

  estado(asignacion: AsignacionPuenteResponse): string {
    return asignacion.estadoInspeccion ?? 'SIN_INICIAR';
  }

  severidad(
    asignacion: AsignacionPuenteResponse,
  ): 'success' | 'warn' | 'danger' | 'info' | 'secondary' {
    const estado = this.estado(asignacion);
    if (estado === 'PUBLICADA') return 'success';
    if (estado === 'RECHAZADA') return 'danger';
    if (estado === 'CAMBIOS_SOLICITADOS') return 'warn';
    if (estado === 'SIN_INICIAR' || estado === 'BORRADOR') return 'secondary';
    return 'info';
  }
}
