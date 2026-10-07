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
      {
        path: ':id/editar',
        loadComponent: () =>
          import('./pages/editar-puente/editar-puente.page').then((m) => m.EditarPuentePage),
      },
    ],
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./pages/ficha-puente/ficha-puente.page').then((m) => m.FichaPuentePage),
  },
];
