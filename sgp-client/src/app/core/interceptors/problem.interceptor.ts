import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ApiErrorService } from '../services/api-error.service';

export const problemInterceptor: HttpInterceptorFn = (req, next) => {
  const errors = inject(ApiErrorService);
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      errors.normalize(error);
      return throwError(() => error);
    }),
  );
};
