import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { Subscription } from 'rxjs';
import { ApiErrorService } from '../../../../core/services/api-error.service';
import { AuthStore } from '../../../../core/services/auth.store';
import { ComparacionIcResponse } from '../../models/recalculo-ic.models';
import { RecalculoIcApiService } from '../../services/recalculo-ic-api.service';

@Component({
  selector: 'app-recalculo-ic',
  standalone: true,
  imports: [DatePipe, DecimalPipe, TranslocoPipe, ButtonModule],
  templateUrl: './recalculo-ic.component.html',
  styleUrl: './recalculo-ic.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecalculoIcComponent {
  readonly inspeccionId = input.required<string>();
  private readonly api = inject(RecalculoIcApiService);
  private readonly auth = inject(AuthStore);
  private readonly errores = inject(ApiErrorService);
  private solicitudes = new Subscription();
  readonly historial = signal<ComparacionIcResponse | null>(null);
  readonly cargando = signal(false);
  readonly recalculando = signal(false);
  readonly error = signal<string | null>(null);
  readonly mensaje = signal<'creado' | 'existente' | null>(null);
  readonly puedeRecalcular = computed(() =>
    ['ADMINISTRADOR', 'CATEDRATICO'].includes(this.auth.user()?.role ?? ''),
  );
  readonly resultados = computed(() => {
    const h = this.historial();
    return h ? [h.original, ...h.recalculos] : [];
  });

  constructor() {
    effect((limpiar) => {
      const id = this.inspeccionId();
      untracked(() => {
        this.solicitudes = new Subscription();
        this.historial.set(null);
        this.error.set(null);
        this.mensaje.set(null);
        this.recalculando.set(false);
        this.cargando.set(false);
        if (id) this.cargar();
      });
      const solicitudes = this.solicitudes;
      limpiar(() => solicitudes.unsubscribe());
    });
  }

  cargar(): void {
    if (this.cargando() || this.recalculando()) return;
    this.cargando.set(true);
    this.error.set(null);
    this.solicitudes.add(
      this.api.historial(this.inspeccionId()).subscribe({
        next: (historial) => {
          this.historial.set(historial);
          this.cargando.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.error.set(this.errores.normalize(error));
          this.cargando.set(false);
        },
      }),
    );
  }

  recalcular(): void {
    const historial = this.historial();
    if (!this.puedeRecalcular() || this.recalculando() || this.cargando() || !historial) return;
    this.recalculando.set(true);
    this.error.set(null);
    this.mensaje.set(null);
    this.solicitudes.add(
      this.api.recalcular(this.inspeccionId()).subscribe({
        next: (resultado) => {
          const existente = historial.recalculos.some((r) => r.id === resultado.id);
          const recalculos = [
            resultado,
            ...historial.recalculos.filter((r) => r.id !== resultado.id),
          ].sort((a, b) => b.calculadoEn.localeCompare(a.calculadoEn) || b.id.localeCompare(a.id));
          this.historial.set({ ...historial, recalculos });
          this.mensaje.set(existente ? 'existente' : 'creado');
          this.recalculando.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.error.set(this.errores.normalize(error));
          this.recalculando.set(false);
        },
      }),
    );
  }
}
