import { Routes } from '@angular/router';

export const observacionesAutorRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./pages/observaciones-recibidas/observaciones-recibidas.page').then(
        (m) => m.ObservacionesRecibidasPage,
      ),
  },
  {
    path: ':revisionId',
    loadComponent: () =>
      import('./pages/detalle-revision/detalle-revision.page').then((m) => m.DetalleRevisionPage),
  },
];
