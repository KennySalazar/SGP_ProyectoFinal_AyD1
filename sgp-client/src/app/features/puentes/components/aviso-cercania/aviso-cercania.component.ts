import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { PuntoMapa } from '../../../../shared/utils/ubicacion-guatemala';
import { PuenteCercanoResponse } from '../../models/puente.models';

export type ModoAvisoCercania = 'registro' | 'solicitud' | 'revision';

/** Prefijo de las claves de texto de cada contexto. */
const PREFIJOS: Record<ModoAvisoCercania, string> = {
  registro: 'puentes.',
  solicitud: 'puentes.solicitud.',
  revision: 'puentes.revision.',
};

/** Puntos para el mapa a partir de los puentes cercanos de un aviso (RN-INV-06). */
export function puntosDeCercanos(cercanos: readonly PuenteCercanoResponse[]): PuntoMapa[] {
  return cercanos.map((puente) => ({
    id: puente.id,
    titulo: `${puente.codigo} — ${puente.nombre}`,
    latitud: puente.latitud,
    longitud: puente.longitud,
    inactivo: !puente.activo,
  }));
}

/**
 * Advertencia de posible duplicado (puentes a menos de 100 m). Las acciones de cada pantalla se
 * proyectan al final.
 */
@Component({
  selector: 'app-aviso-cercania',
  standalone: true,
  imports: [TranslocoPipe, DecimalPipe],
  templateUrl: './aviso-cercania.component.html',
  styleUrl: './aviso-cercania.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AvisoCercaniaComponent {
  readonly cercanos = input.required<readonly PuenteCercanoResponse[]>();
  readonly total = input.required<number>();
  readonly modo = input<ModoAvisoCercania>('registro');

  readonly hayInactivos = computed(() => this.cercanos().some((puente) => !puente.activo));

  /** Si todos los cercanos están dados de baja, el aviso cambia de tono. */
  readonly soloInactivos = computed(
    () => this.cercanos().length > 0 && this.cercanos().every((puente) => !puente.activo),
  );

  readonly claveNota = computed(
    () =>
      PREFIJOS[this.modo()] +
      (this.soloInactivos() ? 'proximityOnlyInactiveNote' : 'proximityNote'),
  );

  readonly claveNotaInactivos = computed(() => PREFIJOS[this.modo()] + 'proximityInactiveNote');
}
