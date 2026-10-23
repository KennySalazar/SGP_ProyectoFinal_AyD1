import { Routes } from '@angular/router';

export const foroRoutes: Routes = [
  {
    path: 'moderacion',
    loadComponent: () =>
      import('./pages/moderacion/moderacion.page').then((m) => m.ModeracionForoPage),
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'moderacion',
  },
];
