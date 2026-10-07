import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards/role.guard';

export const usuarioPublicRoutes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'login/verificar',
    loadComponent: () =>
      import('./pages/login-verify/login-verify.page').then((m) => m.LoginVerifyPage),
  },
  {
    path: 'registro',
    loadComponent: () => import('./pages/register/register.page').then((m) => m.RegisterPage),
  },
  {
    path: 'registro/verificar',
    loadComponent: () =>
      import('./pages/register-verify/register-verify.page').then((m) => m.RegisterVerifyPage),
  },
  {
    path: 'recuperar',
    loadComponent: () =>
      import('./pages/recovery-request/recovery-request.page').then((m) => m.RecoveryRequestPage),
  },
  {
    path: 'recuperar/restablecer',
    loadComponent: () =>
      import('./pages/recovery-reset/recovery-reset.page').then((m) => m.RecoveryResetPage),
  },
];

export const usuarioProtectedRoutes: Routes = [
  {
    path: 'seguridad',
    loadComponent: () => import('./pages/security/security.page').then((m) => m.SecurityPage),
  },
  {
    path: 'estudiantes/pendientes',
    canActivate: [roleGuard('CATEDRATICO')],
    loadComponent: () =>
      import('./pages/activacion-estudiantes/activacion-estudiantes.page').then(
        (m) => m.ActivacionEstudiantesPage,
      ),
  },
];
