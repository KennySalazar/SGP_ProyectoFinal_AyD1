import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { RoleName } from '../models/auth.models';
import { AuthStore } from '../services/auth.store';

export function roleGuard(...roles: RoleName[]): CanActivateFn {
  return () => {
    const auth = inject(AuthStore);
    const role = auth.user()?.role;
    return role && roles.includes(role) ? true : inject(Router).createUrlTree(['/']);
  };
}
