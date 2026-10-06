import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, OnDestroy, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { ProblemDetails } from '../models/problem-details';

@Injectable({ providedIn: 'root' })
export class ApiErrorService implements OnDestroy {
  private readonly transloco = inject(TranslocoService);
  private temporizador: ReturnType<typeof setTimeout> | null = null;

  readonly lastMessage = signal<string | null>(null);

  normalize(error: HttpErrorResponse): string {
    this.cancelarTemporizador();

    const problem = error.error as (ProblemDetails & { code?: string }) | null;

    const message =
      problem?.detail ??
      problem?.title ??
      this.transloco.translate('common.operationFailed') ??
      'No fue posible completar la operacion.';

    this.lastMessage.set(message);

    if (problem?.code === 'ubicacion_fuera_de_guatemala') {
      this.temporizador = setTimeout(() => {
        this.temporizador = null;
        this.lastMessage.set(null);
      }, 4000);
    }

    return message;
  }

  clear(): void {
    this.cancelarTemporizador();
    this.lastMessage.set(null);
  }

  ngOnDestroy(): void {
    this.cancelarTemporizador();
  }

  private cancelarTemporizador(): void {
    if (this.temporizador !== null) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
  }
}
