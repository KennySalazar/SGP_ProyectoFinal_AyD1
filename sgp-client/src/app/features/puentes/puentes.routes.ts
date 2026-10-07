import { Routes } from '@angular/router';
import { authGuard } from '../../core/guards/auth.guard';
import { roleGuard } from '../../core/guards/role.guard';

export const puentesRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./pages/catalogo-puentes/catalogo-puentes.page').then((m) => m.CatalogoPuentesPage),
  },
  {
    path: '',
    canActivate: [authGuard, roleGuard('ADMINISTRADOR')],
    loadComponent: () =>
      import('../../layouts/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: 'nuevo',
        loadComponent: () =>
          import('./pages/registrar-puente/registrar-puente.page').then(
            (m) => m.RegistrarPuentePage,
          ),
      },
    ],
  },
  {
    path: '',
    canActivate: [authGuard, roleGuard('CATEDRATICO')],
    loadComponent: () =>
      import('../../layouts/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: 'asignaciones',
        loadComponent: () =>
          import('./pages/asignaciones-puentes/asignaciones-puentes.page').then(
            (m) => m.AsignacionesPuentesPage,
          ),
      },
    ],
  },
  {
    path: '',
    canActivate: [authGuard, roleGuard('ESTUDIANTE')],
    loadComponent: () =>
      import('../../layouts/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: 'mis-asignaciones',
        loadComponent: () =>
          import('./pages/mis-puentes-asignados/mis-puentes-asignados.page').then(
            (m) => m.MisPuentesAsignadosPage,
          ),
      },
    ],
  },
];
