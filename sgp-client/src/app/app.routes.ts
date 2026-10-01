import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: '',
    loadChildren: () =>
      import('./features/usuario/usuario.routes').then((m) => m.usuarioPublicRoutes),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./layouts/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: '',
        loadChildren: () =>
          import('./features/usuario/usuario.routes').then((m) => m.usuarioProtectedRoutes),
      },
      {
        path: '',
        loadChildren: () =>
          import('./features/dashboard/dashboard.routes').then((m) => m.dashboardRoutes),
      },
      {
        path: 'offline',
        loadComponent: () =>
          import('./offline/pages/diagnostics/diagnostics.page').then((m) => m.DiagnosticsPage),
      },
      {
        path: 'admin',
        canActivate: [roleGuard('ADMINISTRADOR')],
        loadChildren: () => import('./features/admin/admin.routes').then((m) => m.adminRoutes),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
