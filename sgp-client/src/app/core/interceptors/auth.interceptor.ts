import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthStore } from '../services/auth.store';
import { TokenRefreshService } from '../services/token-refresh.service';

const PUBLIC_AUTH_PATHS = [
  '/api/v1/auth/login',
  '/api/v1/auth/register',
  '/api/v1/auth/password-recovery',
];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthStore);
  const refresh = inject(TokenRefreshService);
  const router = inject(Router, { optional: true });
  const token = auth.accessToken();
  const withCookie = req.clone({
    withCredentials: true,
    setHeaders: token ? { Authorization: `Bearer ${token}` } : {},
  });

  return next(withCookie).pipe(
    catchError((error: HttpErrorResponse) => {
      const shouldRefresh =
        error.status === 401 &&
        (token !== null || auth.authenticated()) &&
        !req.url.includes('/api/v1/auth/refresh') &&
        !PUBLIC_AUTH_PATHS.some((path) => req.url.includes(path));
      if (!shouldRefresh) return throwError(() => error);
      return refresh.refresh().pipe(
        switchMap((result) =>
          next(
            req.clone({
              withCredentials: true,
              setHeaders: { Authorization: `Bearer ${result.accessToken}` },
            }),
          ),
        ),
        catchError((refreshError: unknown) => {
          const sinConexion =
            refreshError instanceof HttpErrorResponse &&
            (refreshError.status === 0 || refreshError.status >= 500);
          if (!sinConexion) {
            auth.clear();
            void router?.navigate(['/login']);
          }
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
