import { HttpErrorResponse, HttpInterceptorFn, HttpContextToken } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ApiErrorService } from '../services/api-error.service';

// Las operaciones offline presentan errores normalizados en su propio diagnóstico.
export const ERROR_GESTIONADO_LOCALMENTE = new HttpContextToken<boolean>(() => false);

export const problemInterceptor: HttpInterceptorFn = (req, next) => {
  const errors = inject(ApiErrorService);
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (!req.context.get(ERROR_GESTIONADO_LOCALMENTE)) errors.normalize(error);
      return throwError(() => error);
    }),
  );
};
