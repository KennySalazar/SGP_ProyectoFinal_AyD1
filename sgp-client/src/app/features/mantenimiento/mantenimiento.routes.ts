import { Routes } from '@angular/router';

export const mantenimientoRoutes: Routes = [
  {
    path: 'reglas',
    loadComponent: () =>
      import('./pages/catalogo-reglas/catalogo-reglas.page').then((m) => m.CatalogoReglasPage),
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'reglas',
  },
];
