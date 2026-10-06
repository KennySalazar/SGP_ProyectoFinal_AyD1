import { Routes } from '@angular/router';

export const adminRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/catalogo-cursos/catalogo-cursos.page').then((m) => m.CatalogoCursosPage),
  },
];
