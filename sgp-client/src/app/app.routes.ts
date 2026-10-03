import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { usuarioProtectedRoutes, usuarioPublicRoutes } from './features/usuario/usuario.routes';

export const routes: Routes = [
  ...usuarioPublicRoutes,
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./layouts/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      ...usuarioProtectedRoutes,
      {
        path: 'offline',
        loadComponent: () =>
          import('./offline/pages/diagnostics/diagnostics.page').then((m) => m.DiagnosticsPage),
      },

      {
        path: 'puentes',
        canActivate: [roleGuard('ADMINISTRADOR')],
        loadChildren: () =>
          import('./features/puentes/puentes.routes').then((m) => m.puentesRoutes),
      },
      {
        path: 'admin',
        canActivate: [roleGuard('ADMINISTRADOR')],
        loadChildren: () => import('./features/admin/admin.routes').then((m) => m.adminRoutes),
      },
      {
        path: '',
        pathMatch: 'full',
        loadChildren: () =>
          import('./features/dashboard/dashboard.routes').then((m) => m.dashboardRoutes),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
