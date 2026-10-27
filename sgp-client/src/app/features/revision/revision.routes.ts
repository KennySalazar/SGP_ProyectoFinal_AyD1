import { Routes } from '@angular/router';

export const revisionRoutes: Routes = [
  {
    path: ':revisionId',
    loadComponent: () =>
      import('./pages/detalle-revision/detalle-revision.page').then((m) => m.DetalleRevisionPage),
  },
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./pages/bandeja-revision/bandeja-revision.page').then((m) => m.BandejaRevisionPage),
  },
];
