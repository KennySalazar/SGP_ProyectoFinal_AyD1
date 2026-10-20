import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore } from '../../core/services/auth.store';
export const salidaGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const u = auth.user();
  return u &&
    u.active &&
    u.activated &&
    u.verified &&
    ['ESTUDIANTE', 'CATEDRATICO', 'PROFESIONAL_EXTERNO'].includes(u.role) &&
    !auth.colegiadoPendiente()
    ? true
    : inject(Router).createUrlTree(['/']);
};
