import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/usuario/pages/login/login.page').then((m) => m.LoginPage) },
  { path: 'login/verificar', loadComponent: () => import('./features/usuario/pages/login-verify/login-verify.page').then((m) => m.LoginVerifyPage) },
  { path: 'registro', loadComponent: () => import('./features/usuario/pages/register/register.page').then((m) => m.RegisterPage) },
  { path: 'registro/verificar', loadComponent: () => import('./features/usuario/pages/register-verify/register-verify.page').then((m) => m.RegisterVerifyPage) },
  { path: 'recuperar', loadComponent: () => import('./features/usuario/pages/recovery-request/recovery-request.page').then((m) => m.RecoveryRequestPage) },
  { path: 'recuperar/restablecer', loadComponent: () => import('./features/usuario/pages/recovery-reset/recovery-reset.page').then((m) => m.RecoveryResetPage) },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layouts/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      { path: '', loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage) },
      { path: 'seguridad', loadComponent: () => import('./features/usuario/pages/security/security.page').then((m) => m.SecurityPage) },
      { path: 'offline', loadComponent: () => import('./offline/pages/diagnostics/diagnostics.page').then((m) => m.DiagnosticsPage) },
      { path: 'admin', canActivate: [roleGuard('ADMINISTRADOR')], loadComponent: () => import('./features/admin/admin.placeholder').then((m) => m.AdminPlaceholderPage) }
    ]
  },
  { path: '**', redirectTo: '' }
];
