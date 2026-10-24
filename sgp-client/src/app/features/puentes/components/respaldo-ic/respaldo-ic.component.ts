import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { EstadoCalibracionPuenteApiService } from '../../services/estado-calibracion-puente-api.service';

interface PuenteConIc {
  id: string;
  activo: boolean;
  indiceCondicionActual?: number | null;
  versionFormularioIdIcVigente?: string | null;
}

@Component({
  selector: 'app-respaldo-ic',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './respaldo-ic.component.html',
  styles: [
    ':host { display: block; font-size: 0.85rem; margin-top: 0.4rem; } p { margin: 0; } button { margin-top: 0.25rem; }',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RespaldoIcComponent {
  readonly puente = input<PuenteConIc | null | undefined>();
  private readonly api = inject(EstadoCalibracionPuenteApiService);
  private readonly intento = signal(0);
  readonly tieneIc = computed(() => this.puente()?.indiceCondicionActual != null);
  private readonly consulta = computed(() => ({
    puente: this.puente(),
    intento: this.intento(),
  }));
  readonly resultado = toSignal(
    toObservable(this.consulta).pipe(
      switchMap(({ puente }) => {
        if (puente?.indiceCondicionActual == null) {
          return of({ rotulo: null, mensaje: null });
        }
        if (!puente.activo || !puente.versionFormularioIdIcVigente) {
          return of({ rotulo: null, mensaje: 'puentes.respaldo.unavailable' });
        }
        return this.api.consultar(puente.id).pipe(
          map((estado) =>
            estado !== null &&
            estado.versionFormularioId === puente.versionFormularioIdIcVigente &&
            estado.rotuloCalibracion?.trim()
              ? { rotulo: estado.rotuloCalibracion, mensaje: null }
              : { rotulo: null, mensaje: 'puentes.respaldo.refresh' },
          ),
          startWith({ rotulo: null, mensaje: 'puentes.respaldo.loading' }),
          catchError(() => of({ rotulo: null, mensaje: 'puentes.respaldo.error' })),
        );
      }),
    ),
    { initialValue: { rotulo: null, mensaje: null } },
  );
  readonly reintentable = computed(() => this.resultado().mensaje === 'puentes.respaldo.error');

  reintentar(): void {
    this.intento.update((valor) => valor + 1);
  }
}
