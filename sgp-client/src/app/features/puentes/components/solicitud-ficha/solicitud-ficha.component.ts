import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TagModule } from 'primeng/tag';
import { SelectorUbicacionComponent } from '../../../../shared/components/selector-ubicacion/selector-ubicacion.component';
import { PuntoMapa } from '../../../../shared/utils/ubicacion-guatemala';
import { EstadoSolicitudAltaPuente, SolicitudAltaPuenteResponse } from '../../models/puente.models';

/** Ficha de solo lectura de una solicitud de alta; el contenido proyectado va al final. */
@Component({
  selector: 'app-solicitud-ficha',
  standalone: true,
  imports: [TranslocoPipe, TagModule, SelectorUbicacionComponent],
  templateUrl: './solicitud-ficha.component.html',
  styleUrl: './solicitud-ficha.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SolicitudFichaComponent {
  readonly solicitud = input.required<SolicitudAltaPuenteResponse>();
  readonly mostrarSolicitante = input(false);
  readonly solicitanteEmail = input<string | null>(null);
  readonly mostrarRevisor = input(false);
  readonly revisadoPorEmail = input<string | null>(null);
  readonly cercanos = input<readonly PuntoMapa[]>([]);

  private readonly severidades: Record<
    EstadoSolicitudAltaPuente,
    'success' | 'warn' | 'danger' | 'secondary'
  > = {
    PENDIENTE: 'warn',
    APROBADA: 'success',
    RECHAZADA: 'danger',
    CANCELADA: 'secondary',
  };

  severidad(estado: EstadoSolicitudAltaPuente): 'success' | 'warn' | 'danger' | 'secondary' {
    return this.severidades[estado];
  }

  fechaGuatemala(fecha: string): string {
    return new Intl.DateTimeFormat('es-GT', {
      timeZone: 'America/Guatemala',
      dateStyle: 'medium',
      timeStyle: 'medium',
    }).format(new Date(fecha));
  }
}
