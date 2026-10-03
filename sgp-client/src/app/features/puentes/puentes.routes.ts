import { Routes } from '@angular/router';

export const puentesRoutes: Routes = [
  {
    path: 'nuevo',
    loadComponent: () =>
      import('./pages/registrar-puente/registrar-puente.page').then((m) => m.RegistrarPuentePage),
  },
];
