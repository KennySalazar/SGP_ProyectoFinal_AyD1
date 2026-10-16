import { Routes } from '@angular/router';

export const inspeccionesRoutes: Routes = [
  {
    path: ':id',
    loadComponent: () =>
      import('./pages/completar-inspeccion/completar-inspeccion.page').then(
        (m) => m.CompletarInspeccionPage,
      ),
  },
  {
    path: ':id/borrador',
    loadComponent: () =>
      import('./pages/completar-inspeccion/completar-inspeccion.page').then(
        (m) => m.CompletarInspeccionPage,
      ),
  },
];
