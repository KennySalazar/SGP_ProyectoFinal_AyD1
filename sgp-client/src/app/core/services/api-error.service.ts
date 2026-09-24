import { Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetails } from '../models/problem-details';

@Injectable({ providedIn: 'root' })
export class ApiErrorService {
  readonly lastMessage = signal<string | null>(null);
  normalize(error: HttpErrorResponse): string {
    const problem = error.error as ProblemDetails | null;
    const message = problem?.detail ?? problem?.title ?? 'No fue posible completar la operacion.';
    this.lastMessage.set(message);
    return message;
  }
  clear(): void { this.lastMessage.set(null); }
}
