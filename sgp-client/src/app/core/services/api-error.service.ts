import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { ProblemDetails } from '../models/problem-details';

@Injectable({ providedIn: 'root' })
export class ApiErrorService {
  private readonly transloco = inject(TranslocoService);
  readonly lastMessage = signal<string | null>(null);

  normalize(error: HttpErrorResponse): string {
    const problem = error.error as ProblemDetails | null;
    const translatedMessage = this.transloco.translate('common.operationFailed');

    const message =
      problem?.detail ??
      problem?.title ??
      translatedMessage ??
      'No fue posible completar la operacion.';

    this.lastMessage.set(message);
    return message;
  }

  clear(): void {
    this.lastMessage.set(null);
  }
}
