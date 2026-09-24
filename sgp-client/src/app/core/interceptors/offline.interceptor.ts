import { HttpInterceptorFn } from '@angular/common/http';

export const offlineInterceptor: HttpInterceptorFn = (req, next) => next(req);
