import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore } from '../services/auth.store';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  return auth.authenticated() ? true : inject(Router).createUrlTree(['/login']);
};
