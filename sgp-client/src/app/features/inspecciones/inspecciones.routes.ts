import { Routes } from '@angular/router';

export const inspeccionesRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./pages/mis-inspecciones/mis-inspecciones.page').then((m) => m.MisInspeccionesPage),
  },
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
